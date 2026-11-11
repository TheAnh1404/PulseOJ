# 🎯 QUY TẮC MÃ NGUỒN & ĐẶC TẢ HÀM: THÀNH VIÊN 4 (UDP & CLUSTER MONITOR)

> **Phụ trách:** Thành viên 4 (LAN Auto-Discovery, UDP Heartbeat Telemetry & Cluster Monitor)  
> **Git Branch:** `feat/member4-udp-monitor`  
> **Package sở hữu độc quyền:**  
> - `backend/src/main/java/com/vku/pulseoj/backend/discovery/`  
> - `frontend/src/main/java/com/vku/pulseoj/frontend/ui/monitor/`

---

## 1. Cổng Giao Tiếp Mạng & Định Dạng Gói Tin (Network Ports & Payloads)

| Giao thức | Cổng (Port) | Địa chỉ đích | Định dạng bản tin (Payload Format) |
| :--- | :--- | :--- | :--- |
| **UDP Discovery Req** | `8888` | `255.255.255.255` (Broadcast) | `"PULSE_OJ_DISCOVER_REQ"` |
| **UDP Discovery Res** | `8888` | IP máy gửi (Unicast) | `"PULSE_OJ_MASTER_RES\|<MasterIP>\|<RmiPort>"` |
| **UDP Heartbeat** | `8889` | IP Master (Unicast) | `"HEARTBEAT\|<WorkerID>\|<IP>\|<Cpu%>\|<FreeRamMB>\|<Status>"` |

---

## 2. Đặc Tả Chi Tiết Từng Lớp & Chữ Ký Hàm

### 2.1. Lớp `UdpDiscoveryServer.java` (Chạy trên Master - Port 8888)
* **Phương thức khởi chạy:**
  `public void start()`
  - Mở `DatagramSocket(8888)`.
  - Vòng lặp lắng nghe `socket.receive(packet)`.
  - Nếu payload nhận được là `"PULSE_OJ_DISCOVER_REQ"`:
    - Đóng gói chuỗi: `"PULSE_OJ_MASTER_RES|" + localLanIp + "|" + rmiPort`.
    - Gửi trả gói tin `DatagramPacket` ngược về `packet.getAddress()` và `packet.getPort()`.

### 2.2. Lớp `UdpDiscoveryClient.java` (Dùng chung cho Client và Worker)
* **Phương thức quét tìm:**
  `public static MasterInfo discoverMaster(int timeoutMs)`
  - Mở `DatagramSocket`, bật `socket.setBroadcast(true)`.
  - Bắn bản tin `"PULSE_OJ_DISCOVER_REQ"` tới `255.255.255.255:8888`.
  - Cài đặt `socket.setSoTimeout(timeoutMs)`.
  - Đọc gói tin trả lời, tách chuỗi theo ký tự `|` để lấy địa chỉ IP và Port của Master.

### 2.3. Lớp `UdpHeartbeatSender.java` (Chạy trên máy Worker - Port 8889)
* **Cơ chế đo đạc phần cứng:**
  - Sử dụng `ScheduledExecutorService` chạy lặp lại mỗi **2 giây**.
  - Dùng `com.sun.management.OperatingSystemMXBean` đọc thông số:
    * `double cpuLoad = osBean.getCpuLoad() * 100;`
    * `long freeMemMb = osBean.getFreeMemorySize() / (1024 * 1024);`
  - Đóng gói thành chuỗi: `String.format("HEARTBEAT|%s|%s|%.1f|%d|%s", workerId, host, cpu, mem, status)`
  - Bắn qua `DatagramSocket` về địa chỉ `masterIp:8889`.

### 2.4. Lớp `UdpHeartbeatWatchdog.java` (Bộ Canh Gác trên Master)
* **Cơ chế phát hiện node sập:**
  - Duy trì `Map<String, Long> lastSeenTimestamps = new ConcurrentHashMap<>();`.
  - Chạy một luồng kiểm tra định kỳ mỗi 1 giây:
    ```java
    long now = System.currentTimeMillis();
    for (Map.Entry<String, Long> entry : lastSeenTimestamps.entrySet()) {
        if (now - entry.getValue() > 6000) { // Quá 6 giây = mất 3 nhịp tim
            String deadWorkerId = entry.getKey();
            logger.warn("Worker [{}] missed 3 heartbeats. Triggering failover!", deadWorkerId);
            dispatcher.handleWorkerDown(deadWorkerId); // Báo TV1 chuyển bài nộp
            uiListener.onWorkerOffline(deadWorkerId);
        }
    }
    ```

### 2.5. Phân Hệ Frontend (`frontend/src/main/java/com/vku/pulseoj/frontend/ui/monitor/`)
* **`WorkerHealthCardsPanel.java`:** Hiển thị thẻ card đo tải của từng máy Worker với thanh % CPU, RAM trống và đèn báo trạng thái `ONLINE (Xanh) / BUSY (Cam) / OFFLINE (Đỏ)`.
* **`NetworkPacketInspectorPanel.java`:** Bảng bắt gói tin mạng theo thời gian thực (Thời gian, Giao thức, IP Nguồn $\rightarrow$ IP Đích, Kích thước, Payload tóm tắt).
* **Nút bấm `⚡ Bắn 20 Bài Nộp Cùng Lúc (Stress Test)`:** Sử dụng `ExecutorService` sinh đồng thời 20 luồng nộp bài vào Master để trình diễn khả năng chia tải đa luồng.

