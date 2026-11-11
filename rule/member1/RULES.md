# 🎯 QUY TẮC MÃ NGUỒN & ĐẶC TẢ HÀM: THÀNH VIÊN 1 (MASTER & NEON DB)

> **Phụ trách:** Thành viên 1 (Master RMI Server, Cloud Neon PostgreSQL & Auto-Failover Dispatcher)  
> **Git Branch:** `feat/member1-master-neondb`  
> **Package sở hữu độc quyền:**  
> - `backend/src/main/java/com/vku/pulseoj/backend/db/`  
> - `backend/src/main/java/com/vku/pulseoj/backend/master/`  
> - `frontend/src/main/java/com/vku/pulseoj/frontend/ui/master/`  
> - `frontend/src/main/java/com/vku/pulseoj/frontend/MasterAppLauncher.java`

---

## 1. Bản Giao Kèo Giao Thức (Interface Contract)

Thành viên 1 cung cấp và cài đặt các dịch vụ RMI cốt lõi của máy chủ trung tâm:
- [`com.vku.pulseoj.common.rmi.IMasterSubmissionService`](file:///d:/Users/BT/N3_K1/LTrMang/PulseOJ/common/src/main/java/com/vku/pulseoj/common/rmi/IMasterSubmissionService.java) (Cổng RMI cho Client)
- [`com.vku.pulseoj.common.rmi.IMasterClusterService`](file:///d:/Users/BT/N3_K1/LTrMang/PulseOJ/common/src/main/java/com/vku/pulseoj/common/rmi/IMasterClusterService.java) (Cổng RMI cho Worker)

---

## 2. Đặc Tả Chi Tiết Từng Lớp & Chữ Ký Hàm

### 2.1. Lớp `NeonDataSourceConfig.java` (HikariCP Connection Pool)
* **Phương thức khởi tạo:**
  `public static synchronized void init()`
  - Đọc biến môi trường `NEON_DB_URL`, `NEON_DB_USER`, `NEON_DB_PASSWORD` từ file `.env` hoặc hệ thống.
  - Tự động chuẩn hóa format URI và cấu hình HikariConfig (`sslmode=require`, `maximumPoolSize=10`, `connectionTimeout=15000`).
  - Nếu không có URL hợp lệ hoặc có cờ `PULSE_OFFLINE_MOCK_DB=true`, kích hoạt Mock In-Memory Database Fallback.
* **Phương thức lấy kết nối:**
  `public static Connection getConnection() throws SQLException`

### 2.2. Lớp `SubmissionDispatcher.java` (Bộ Điều Phối & Chịu Lỗi Phân Tán)
* **Thuộc tính quản lý luồng:**
  ```java
  private final BlockingQueue<SubmissionDTO> queue = new LinkedBlockingQueue<>();
  private final Map<String, WorkerSession> activeWorkers = new ConcurrentHashMap<>();
  private final Map<Integer, SubmissionDTO> inFlightTasks = new ConcurrentHashMap<>();
  private final Map<Integer, Integer> retryCounts = new ConcurrentHashMap<>();
  ```
* **Chữ ký hàm điều phối cốt lõi:**
  1. `public void enqueueSubmission(SubmissionDTO submission)`
     - Thêm bài nộp vào hàng đợi an toàn luồng `queue.put(submission)`.
  2. `public void registerWorker(String workerId, String host, int port, IJudgeWorkerService stub)`
     - Đưa Worker vào danh sách `activeWorkers`.
  3. `public void unregisterWorker(String workerId)`
     - Xóa Worker khỏi pool khi tắt máy bình thường.
  4. `public void handleWorkerDown(String deadWorkerId)`
     - **Thu hồi bài nộp (Auto-Failover):** Tìm bài nộp đang chấm dở bởi worker bị sập trong `inFlightTasks`.
     - Kiểm tra số lần retry: Nếu `retryCount >= 4` $\rightarrow$ Đánh dấu lỗi `RE` (Cơ chế chống Poison Pill). Nếu chưa vượt, đẩy bài ngược lại vào `queue` để chuyển máy khác chấm.
  5. `public WorkerSession selectBestWorker()`
     - Lọc danh sách worker có trạng thái `IDLE`.
     - Chọn máy có `% CPU load` thấp nhất và dung lượng RAM trống nhiều nhất (dựa trên dữ liệu từ nhịp tim UDP của TV4).

### 2.3. Lớp `MasterSubmissionServiceImpl.java` (RMI Service cho Thí sinh)
* `public LoginResponseDTO login(LoginRequestDTO request) throws RemoteException`
  - Đối chiếu mật khẩu băm `BCrypt.checkpw()`.
* `public SubmissionDTO submitSolution(SubmissionDTO submission, IClientCallbackService callback) throws RemoteException`
  - Lưu bài vào DB ở trạng thái `PENDING`.
  - Lưu `callback` của Client vào `submissionCallbacks` map.
  - Đẩy vào `dispatcher.enqueueSubmission(saved)`.

### 2.4. Lớp `MasterClusterServiceImpl.java` (RMI Service cho Máy Chấm)
* `public void reportTestProgress(JudgeResultDTO result) throws RemoteException`
  - Nhận tiến độ từng test từ Worker $\rightarrow$ Chuyển tiếp tới Client tương ứng qua `callback.onTestProgress(result)`.
* `public void reportFinalVerdict(SubmissionDTO completedSubmission) throws RemoteException`
  - Cập nhật điểm chung cuộc vào Neon DB.
  - Báo giải phóng Worker (`dispatcher.onTaskCompleted()`).
  - Đẩy kết quả cuối về Client (`callback.onSubmissionFinalVerdict()`).
  - Phát sóng Bảng xếp hạng mới tới tất cả Client (`broadcastScoreboard()`) kèm cơ chế dọn dẹp Stub chết (**Dead Stub Cleanup**).

