package com.vku.pulseoj.backend.discovery;

import com.vku.pulseoj.common.dto.NetworkPacketLogDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * [TV4] UDP LAN Auto-Discovery Responder hosted on Master Server.
 * Listens on UDP port 8888 for broadcast requests from Contestants and Workers.
 */
public class UdpDiscoveryServer implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(UdpDiscoveryServer.class);

    public static final int DISCOVERY_PORT = 8888;
    public static final String MAGIC_REQUEST = "PULSE_DISCOVERY_REQ";
    public static final String MAGIC_RESPONSE_PREFIX = "PULSE_DISCOVERY_RESP|";

    private final String masterLanIp;
    private final int masterRmiPort;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private DatagramSocket socket;
    private Thread serverThread;
    private ClusterMonitorListener monitorListener;

    public UdpDiscoveryServer(String masterLanIp, int masterRmiPort) {
        this.masterLanIp = masterLanIp;
        this.masterRmiPort = masterRmiPort;
    }

    public void setMonitorListener(ClusterMonitorListener listener) {
        this.monitorListener = listener;
    }

    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            try {
                socket = new DatagramSocket(DISCOVERY_PORT);
                socket.setBroadcast(true);
                serverThread = new Thread(this, "UdpDiscoveryServer-Thread");
                serverThread.setDaemon(true);
                serverThread.start();
                logger.info("UdpDiscoveryServer listening on UDP port {}", DISCOVERY_PORT);
            } catch (Exception e) {
                logger.error("Failed to start UdpDiscoveryServer on port {}: {}", DISCOVERY_PORT, e.getMessage());
                running.set(false);
            }
        }
    }

    public synchronized void stop() {
        running.set(false);
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        if (serverThread != null) {
            serverThread.interrupt();
        }
    }

    @Override
    public void run() {
        byte[] buffer = new byte[1024];
        while (running.get()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();
                String senderHost = packet.getAddress().getHostAddress();

                if (monitorListener != null) {
                    monitorListener.onPacketLogged(new NetworkPacketLogDTO(
                            "UDP-DISCOVERY",
                            senderHost + ":" + packet.getPort(),
                            masterLanIp + ":" + DISCOVERY_PORT,
                            packet.getLength(),
                            "Discovery Probe Received",
                            message
                    ));
                }

                if (message.startsWith(MAGIC_REQUEST)) {
                    String responsePayload = MAGIC_RESPONSE_PREFIX + masterLanIp + "|" + masterRmiPort;
                    byte[] responseBytes = responsePayload.getBytes(StandardCharsets.UTF_8);

                    DatagramPacket responsePacket = new DatagramPacket(
                            responseBytes, responseBytes.length,
                            packet.getAddress(), packet.getPort()
                    );
                    socket.send(responsePacket);

                    if (monitorListener != null) {
                        monitorListener.onPacketLogged(new NetworkPacketLogDTO(
                                "UDP-DISCOVERY",
                                masterLanIp + ":" + DISCOVERY_PORT,
                                senderHost + ":" + packet.getPort(),
                                responseBytes.length,
                                "Discovery Response Sent",
                                responsePayload
                        ));
                    }
                }
            } catch (Exception e) {
                if (!running.get()) break;
                logger.debug("UdpDiscoveryServer receive error: {}", e.getMessage());
            }
        }
    }
}
