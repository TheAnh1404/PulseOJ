package com.vku.pulseoj.backend.master;

import com.vku.pulseoj.backend.db.ProblemDAO;
import com.vku.pulseoj.backend.db.SubmissionDAO;
import com.vku.pulseoj.common.dto.ProblemDTO;
import com.vku.pulseoj.common.dto.SubmissionDTO;
import com.vku.pulseoj.common.dto.TestCaseDTO;
import com.vku.pulseoj.common.enums.Verdict;
import com.vku.pulseoj.common.enums.WorkerStatus;
import com.vku.pulseoj.common.rmi.IJudgeWorkerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * [TV1] Core Submission Dispatcher & Fault-Tolerant Queue Manager.
 * Distributes judging tasks to idle workers and performs automatic failover if a worker crashes.
 */
public class SubmissionDispatcher implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(SubmissionDispatcher.class);

    private final BlockingQueue<SubmissionDTO> queue = new LinkedBlockingQueue<>();
    private final Map<String, WorkerSession> activeWorkers = new ConcurrentHashMap<>();
    private final Map<Integer, SubmissionDTO> inFlightTasks = new ConcurrentHashMap<>();
    private final Map<Integer, String> taskToWorkerMap = new ConcurrentHashMap<>();

    private final ProblemDAO problemDAO;
    private final SubmissionDAO submissionDAO;
    private MasterUiListener uiListener;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread dispatcherThread;

    public SubmissionDispatcher(ProblemDAO problemDAO, SubmissionDAO submissionDAO) {
        this.problemDAO = problemDAO;
        this.submissionDAO = submissionDAO;
    }

    public void setUiListener(MasterUiListener listener) {
        this.uiListener = listener;
    }

    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            dispatcherThread = new Thread(this, "SubmissionDispatcher-Thread");
            dispatcherThread.setDaemon(true);
            dispatcherThread.start();
            logger.info("SubmissionDispatcher started.");
        }
    }

    public synchronized void stop() {
        running.set(false);
        if (dispatcherThread != null) {
            dispatcherThread.interrupt();
        }
    }

    /**
     * Enqueues a new submission for judging.
     */
    public void enqueueSubmission(SubmissionDTO submission) {
        submission.setVerdict(Verdict.PENDING);
        queue.offer(submission);
        logger.info("Enqueued submission #{} for problem [{}] from user [{}]",
                submission.getId(), submission.getProblemCode(), submission.getContestantUsername());

        if (uiListener != null) {
            uiListener.onSubmissionQueued(submission);
        }
    }

    /**
     * Registers an available worker into the cluster pool.
     */
    public void registerWorker(String workerId, String host, int port, IJudgeWorkerService stub) {
        WorkerSession session = new WorkerSession(workerId, host, port, stub);
        activeWorkers.put(workerId, session);
        logger.info("Registered worker [{}] ({}:{}) in dispatcher pool.", workerId, host, port);
        if (uiListener != null) {
            uiListener.onServerLog("Worker [" + workerId + "] registered (" + host + ":" + port + ")");
        }
    }

    /**
     * Removes an offline or disconnected worker.
     */
    public void unregisterWorker(String workerId) {
        WorkerSession removed = activeWorkers.remove(workerId);
        if (removed != null) {
            logger.warn("Worker [{}] unregistered.", workerId);
            handleWorkerDown(workerId);
        }
    }

    /**
     * Updates worker status (e.g. from UDP Heartbeat watchdog).
     */
    public void updateWorkerStatus(String workerId, WorkerStatus status) {
        WorkerSession session = activeWorkers.get(workerId);
        if (session != null) {
            session.status = status;
            if (status == WorkerStatus.OFFLINE) {
                handleWorkerDown(workerId);
            }
        }
    }

    /**
     * Fault-tolerance: Recovers tasks currently handled by a crashed/dead worker.
     */
    public synchronized void handleWorkerDown(String workerId) {
        logger.warn("Handling failover for failed worker [{}]", workerId);

        // Find any task being judged by this worker
        for (Map.Entry<Integer, String> entry : taskToWorkerMap.entrySet()) {
            if (workerId.equals(entry.getValue())) {
                int submissionId = entry.getKey();
                taskToWorkerMap.remove(submissionId);
                SubmissionDTO task = inFlightTasks.remove(submissionId);
                if (task != null) {
                    logger.warn("FAILOVER TRIGGERED: Re-enqueuing in-flight submission #{} from crashed worker [{}]",
                            submissionId, workerId);
                    task.setVerdict(Verdict.PENDING);
                    queue.offer(task);
                    if (uiListener != null) {
                        uiListener.onServerLog("FAILOVER: Re-queued submission #" + submissionId + " (Worker " + workerId + " died)");
                    }
                }
            }
        }
    }

    /**
     * Called when a worker finishes judging a submission.
     */
    public void onTaskCompleted(int submissionId, String workerId) {
        inFlightTasks.remove(submissionId);
        taskToWorkerMap.remove(submissionId);

        WorkerSession session = activeWorkers.get(workerId);
        if (session != null) {
            session.status = WorkerStatus.IDLE;
            session.currentSubmissionId = null;
        }
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                // Block until a submission is in the queue
                SubmissionDTO submission = queue.take();

                // Find an idle worker
                WorkerSession targetWorker = null;
                while (running.get() && targetWorker == null) {
                    for (WorkerSession worker : activeWorkers.values()) {
                        if (worker.status == WorkerStatus.IDLE) {
                            targetWorker = worker;
                            break;
                        }
                    }

                    if (targetWorker == null) {
                        // No idle workers available, wait briefly
                        Thread.sleep(500);
                    }
                }

                if (!running.get()) break;

                // Mark worker as busy and dispatch
                targetWorker.status = WorkerStatus.BUSY;
                targetWorker.currentSubmissionId = submission.getId();
                submission.setVerdict(Verdict.RUNNING);
                submission.setWorkerId(targetWorker.workerId);

                inFlightTasks.put(submission.getId(), submission);
                taskToWorkerMap.put(submission.getId(), targetWorker.workerId);

                // Fetch hidden test cases from DB
                ProblemDTO problem = problemDAO.getProblemById(submission.getProblemId(), true);
                List<TestCaseDTO> testCases = problem != null ? problem.getTestCases() : List.of();

                logger.info("Dispatching submission #{} (problem {}) to worker [{}]",
                        submission.getId(), submission.getProblemCode(), targetWorker.workerId);

                if (uiListener != null) {
                    uiListener.onSubmissionDispatched(submission.getId(), targetWorker.workerId);
                }

                try {
                    targetWorker.stub.submitJob(submission, problem, testCases);
                } catch (RemoteException re) {
                    logger.error("RMI dispatch error to worker [{}]: {}. Triggering failover...",
                            targetWorker.workerId, re.getMessage());
                    targetWorker.status = WorkerStatus.OFFLINE;
                    handleWorkerDown(targetWorker.workerId);
                }

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error in dispatch loop: {}", e.getMessage(), e);
            }
        }
    }

    public int getQueueSize() {
        return queue.size();
    }

    public Map<String, WorkerSession> getActiveWorkers() {
        return activeWorkers;
    }

    public static class WorkerSession {
        public final String workerId;
        public final String host;
        public final int port;
        public final IJudgeWorkerService stub;
        public volatile WorkerStatus status = WorkerStatus.IDLE;
        public volatile Integer currentSubmissionId = null;

        public WorkerSession(String workerId, String host, int port, IJudgeWorkerService stub) {
            this.workerId = workerId;
            this.host = host;
            this.port = port;
            this.stub = stub;
        }
    }
}
