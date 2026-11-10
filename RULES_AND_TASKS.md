# 📘 PULSE OJ – QUY TẮC KIẾN TRÚC & PHÂN CÔNG NHIỆM VỤ CHI TIẾT (4 THÀNH VIÊN)

> **Tên dự án:** **PulseOJ** (*Real-time Callback & Heartbeat-Driven Distributed Online Judge*)
> **Kiến trúc cốt lõi:** 100% Java (JDK 17+) | Tách biệt Backend & Frontend | Hybrid Java RMI + UDP Broadcast/Heartbeat | Cơ sở dữ liệu Cloud **Neon Serverless PostgreSQL**.

---

## PHẦN I. QUY TẮC KIẾN TRÚC & CODING RULES BẮT BUỘC (PROJECT RULES)

Tất cả 4 thành viên (và các AI Coding Agent hỗ trợ từng thành viên) **BẮT BUỘC** tuân thủ 6 quy tắc kỹ thuật sau đây:

### Rule 1: Tách biệt tuyệt đối Backend và Frontend trong Java (`Strict Layer Decoupling`)
Dự án được xây dựng 100% bằng Java nhưng chia làm 3 module độc lập:
1. **`common`**: Chỉ chứa `Remote` Interfaces, `Serializable` DTOs, Enums và `NetworkUtils`.
2. **`backend`**: Chứa toàn bộ logic mạng (RMI Server/Client, UDP Sockets), xử lý đa luồng (`BlockingQueue`, `ExecutorService`), chấm bài (`ProcessBuilder`) và truy vấn cơ sở dữ liệu (`Neon PostgreSQL DAO`).
   * **CẤM:** Tuyệt đối không được `import javax.swing.*` hoặc `java.awt.*` bên trong `backend`.
   * **Cơ chế giao tiếp:** Khi Backend nhận được gói tin mạng hoặc chấm xong 1 test case, Backend gọi qua các interface `MasterUiListener`, `WorkerUiListener`, `ClientUiListener`, `ClusterMonitorListener` để báo cho Frontend cập nhật màn hình.
3. **`frontend`**: Chứa toàn bộ giao diện người dùng (`Java Swing` + `FlatLaf` + `MigLayout` + `RSyntaxTextArea`).
   * **CẤM:** Tuyệt đối không viết câu lệnh SQL, không mở `DatagramSocket` hay khởi tạo `LocateRegistry` trực tiếp trong các lớp `JFrame`/`JPanel`. Mọi thao tác phải gọi qua các lớp `*BackendController`.

### Rule 2: Quy tắc Bảo mật & Kết nối Cơ sở dữ liệu Neon PostgreSQL (`Single DB Entry Point`)
* Toàn bộ tài khoản thí sinh (do máy Giáo viên tạo), danh sách đề thi, bộ Test Case và lịch sử nộp bài được lưu trữ trên **Neon Serverless PostgreSQL**.
* **Chỉ duy nhất máy Giáo viên (`Master Server`) được phép giữ chuỗi kết nối (`NEON_DB_URL`) và kết nối tới Neon Database** thông qua `HikariCP Connection Pool` (`sslmode=require`).
* Máy Thí sinh (`Contestant Client`) và Máy Chấm (`Judge Worker`) **KHÔNG** kết nối trực tiếp vào Neon DB mà phải gọi qua hàm RMI của Master Server. Thiết kế này vừa bảo mật tuyệt đối thông tin DB và bộ Test Case ẩn, vừa thể hiện đúng bản chất mô hình phân tán trong môn Lập trình mạng.
* Mật khẩu tài khoản thí sinh khi máy Giáo viên khởi tạo phải được băm bằng **`BCrypt`** (hoặc `SHA-256 + Salt`) trước khi `INSERT` lên Neon DB; tuyệt đối không lưu mật khẩu dạng văn bản thuần (Plaintext).

### Rule 3: Quy tắc chống lỗi `127.0.0.1` khi chạy RMI trên mạng LAN nhiều máy
* Ở dòng đầu tiên của hàm `main()` trên cả 3 ứng dụng (`MasterAppLauncher`, `WorkerAppLauncher`, `ClientAppLauncher`), **BẮT BUỘC** phải gọi:
  ```java
  NetworkUtils.configureRmiHostname();
  ```
* Hàm này tự động quét card mạng Wi-Fi/Ethernet đang hoạt động để lấy địa chỉ IPv4 LAN thực tế (ví dụ `192.168.1.10`) và gán vào `System.setProperty("java.rmi.server.hostname", localIp)`. Nếu thiếu dòng này, `Stub` truyền sang máy khác sẽ gọi nhầm vào `127.0.0.1` và gây lỗi `Connection refused` khi đi bảo vệ.

### Rule 4: Quy tắc Đa luồng trên Giao diện (`Non-blocking UI & Thread Safety`)
* **Chiều từ Frontend gọi xuống Backend (RMI Call):** Mọi lời gọi hàm RMI từ nút bấm trên giao diện (như Đăng nhập, Nộp bài, Tạo tài khoản thí sinh trên Neon DB) phải được chạy bất đồng bộ qua `CompletableFuture.supplyAsync(...)` hoặc `SwingWorker`. Tuyệt đối không gọi trực tiếp trên luồng giao diện (`EDT`) gây đơ cứng cửa sổ.
* **Chiều từ Backend đẩy lên Frontend (RMI Callback / UDP Packet):** Mọi hàm trong `UiListener` khi cập nhật bảng biểu, thanh tiến trình hay log trên giao diện Swing bắt buộc phải bọc trong `SwingUtilities.invokeLater(() -> { ... })`.

### Rule 5: Quy tắc Tuần tự hóa (`Serialization`) & Dọn dẹp Kết nối Chết (`Dead Stub Cleanup`)
* Tất cả các lớp trong `com.vku.pulseoj.common.dto` phải `implements Serializable` và khai báo cố định `private static final long serialVersionUID = 1L;`.
* Khi Master Server duyệt danh sách `IClientCallbackService` để phát thông báo hoặc cập nhật Bảng xếp hạng, phải bọc `try-catch (RemoteException e)` cho từng Client độc lập. Nếu Client nào ném `RemoteException` (do thí sinh tắt ngang phần mềm), Master tự động xóa `Stub` của Client đó khỏi `ConcurrentHashMap` để không làm gián đoạn các máy còn lại.

### Rule 6: Quy tắc Phân nhánh Git & Không đụng độ Code (`Zero-Conflict Workflow`)
* Nhánh `main`: Chỉ chứa code đã tích hợp ổn định.
* Giai đoạn đầu tiên, cả nhóm thống nhất và khóa (freeze) module `common`.
* Mỗi thành viên làm việc trên nhánh riêng và chỉ chỉnh sửa đúng package được phân công:
  * **Thành viên 1:** `branch: feat/member1-master-neondb` (`backend.db`, `backend.master`, `frontend.ui.master`)
  * **Thành viên 2:** `branch: feat/member2-worker-sandbox` (`backend.worker`, `frontend.ui.worker`)
  * **Thành viên 3:** `branch: feat/member3-client-callback` (`backend.client`, `frontend.ui.client`)
  * **Thành viên 4:** `branch: feat/member4-udp-monitor` (`backend.discovery`, `frontend.ui.monitor`)

---

## PHẦN II. THIẾT KẾ CƠ SỞ DỮ LIỆU NEON POSTGRESQL (`schema.sql`)

Cơ sở dữ liệu trên **Neon Serverless PostgreSQL** gồm 4 bảng cốt lõi phục vụ quản lý tài khoản thí sinh do Giáo viên tạo, đề thi, bộ test và lịch sử chấm bài:

```sql
-- 1. Bảng lưu tài khoản thí sinh do Máy Giáo Viên khởi tạo
CREATE TABLE IF NOT EXISTS contestants (
    contestant_id SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    team_name VARCHAR(100) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    last_login_ip VARCHAR(45),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Bảng lưu danh sách đề bài trong kỳ thi
CREATE TABLE IF NOT EXISTS problems (
    problem_id VARCHAR(20) PRIMARY KEY,       -- Ví dụ: PROB_A, PROB_B
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    time_limit_ms INT NOT NULL DEFAULT 1000,  -- Giới hạn thời gian chạy mỗi test (ms)
    memory_limit_mb INT NOT NULL DEFAULT 256,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. Bảng lưu bộ Test Case chuẩn (Chỉ Master và Worker được phép truy cập qua RMI)
CREATE TABLE IF NOT EXISTS test_cases (
    testcase_id SERIAL PRIMARY KEY,
    problem_id VARCHAR(20) NOT NULL REFERENCES problems(problem_id) ON DELETE CASCADE,
    test_order INT NOT NULL,
    input_data TEXT NOT NULL,
    expected_output TEXT NOT NULL
);

-- 4. Bảng lưu lịch sử nộp bài và kết quả chấm từ các Worker
CREATE TABLE IF NOT EXISTS submissions (
    submission_id VARCHAR(50) PRIMARY KEY,
    username VARCHAR(50) NOT NULL REFERENCES contestants(username),
    problem_id VARCHAR(20) NOT NULL REFERENCES problems(problem_id),
    language VARCHAR(20) NOT NULL,            -- JAVA, CPP, PYTHON
    source_code TEXT NOT NULL,
    verdict VARCHAR(20) NOT NULL,             -- PENDING, RUNNING, AC, WA, TLE, RE, CE
    passed_tests INT DEFAULT 0,
    total_tests INT DEFAULT 0,
    execution_time_ms BIGINT DEFAULT 0,
    judged_by_worker VARCHAR(50),
    error_details TEXT,
    submitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## PHẦN III. PHÂN CÔNG NHIỆM VỤ CỤ THỂ CHO 4 THÀNH VIÊN
*(Mỗi thành viên nắm giữ trọn vẹn: 1 Phân hệ Backend + 1 Giao thức/Interface Mạng + 1 Phân hệ Frontend)*

> [!IMPORTANT]
> **Tài liệu triển khai chi tiết từng hàm, sơ đồ tuần tự và kịch bản bảo vệ chuyên sâu đã được tách thành các file cẩm nang độc lập:**
> - 📄 **Thành viên 1:** [tasks/MEMBER_1_MASTER_NEONDB.md](file:///C:/TheAnhproject/PulseOJ/tasks/MEMBER_1_MASTER_NEONDB.md) — *Master RMI Server, Neon PostgreSQL & Submission Dispatcher (Auto-Failover)*
> - 📄 **Thành viên 2:** [tasks/MEMBER_2_WORKER_SANDBOX.md](file:///C:/TheAnhproject/PulseOJ/tasks/MEMBER_2_WORKER_SANDBOX.md) — *Judge Worker RMI Node & Code Sandbox Execution Engine (ProcessBuilder)*
> - 📄 **Thành viên 3:** [tasks/MEMBER_3_CLIENT_CALLBACK.md](file:///C:/TheAnhproject/PulseOJ/tasks/MEMBER_3_CLIENT_CALLBACK.md) — *Contestant Client, Bi-directional RMI Callback & Live ACM-ICPC Scoreboard*
> - 📄 **Thành viên 4:** [tasks/MEMBER_4_UDP_MONITOR.md](file:///C:/TheAnhproject/PulseOJ/tasks/MEMBER_4_UDP_MONITOR.md) — *LAN Auto-Discovery (UDP 8888), Heartbeat Telemetry (UDP 8889) & Cluster Monitor*

---

### 👤 THÀNH VIÊN 1: Master RMI Server, Quản Trị Tài Khoản Neon DB & Bộ Điều Phối Chịu Lỗi (Dispatcher & Failover)

#### Chức năng 1.1: Quản trị Tài khoản Thí sinh & Đề thi trên Neon PostgreSQL
* **Cụ thể cần làm gì (What to build):**
  * **Ở Backend (`backend.db`):** Viết lớp `NeonDataSourceConfig` kết nối tới Neon PostgreSQL qua `HikariCP` (đọc `NEON_DB_URL` từ file `.env`). Viết `ContestantAccountDAO` và `ProblemDAO` với các phương thức: tạo tài khoản thí sinh đơn lẻ hoặc tạo hàng loạt theo danh sách (tự động sinh mật khẩu và mã hóa bằng `BCrypt`), khóa/mở khóa tài khoản thí sinh, kiểm tra đăng nhập (`authenticate(username, rawPassword, clientIp)`), và thêm/sửa đề thi kèm bộ Test Case.
  * **Ở Frontend (`frontend.ui.master`):** Thiết kế Tab **"Quản lý Thí sinh & Đề thi (Neon DB)"** trên máy Giáo viên: có bảng danh sách tài khoản thí sinh, nút **"Tạo Tài Khoản Thí Sinh"**, nút **"Cấp lại mật khẩu / Khóa tài khoản"** và form nhập Đề bài + Test Cases.
* **Chức năng đó hoạt động như thế nào (How it works):**
  * Khi Giáo viên nhập thông tin đội thi (ví dụ `team_vku_01`) trên giao diện Master và bấm Tạo, Frontend gọi xuống `MasterBackendController`. Controller băm mật khẩu bằng `BCrypt.hashpw()` và gọi `ContestantAccountDAO` thực thi lệnh `INSERT` qua kết nối SSL tới Cloud Neon PostgreSQL.
  * Khi thí sinh ở máy Client bấm Đăng nhập qua RMI (`IMasterSubmissionService.login()`), Master Server nhận `username/password`, truy vấn bảng `contestants` trên Neon DB để đối chiếu `BCrypt.checkpw()`. Nếu hợp lệ và `is_active == true`, Master cập nhật `last_login_ip` lên Neon DB và lưu `IClientCallbackService` của thí sinh vào bộ nhớ.
* **Mục đích để làm gì (Why it matters):**
  * Đảm bảo dữ liệu tài khoản, đề thi và lịch sử nộp bài được lưu trữ bền vững trên Cloud Database thực tế; giáo viên quản lý tập trung toàn bộ quyền truy cập phòng thi mà không cần cài đặt PostgreSQL cục bộ trên từng máy.

#### Chức năng 1.2: Hàng đợi Bài nộp An toàn Luồng (`BlockingQueue`) & Điều phối Chia tải (`SubmissionDispatcher`)
* **Cụ thể cần làm gì (What to build):**
  * **Ở Backend (`backend.master`):** Cài đặt `MasterSubmissionServiceImpl` (Port RMI `1099`) và lớp `SubmissionDispatcher`. Sử dụng `LinkedBlockingQueue<SubmissionDTO>` để chứa các bài nộp đang chờ và `ConcurrentHashMap<String, IJudgeWorkerService>` để quản lý danh sách các máy chấm Worker.
  * **Ở Frontend (`frontend.ui.master`):** Thiết kế bảng **"Hàng Đợi Bài Nộp Thời Gian Thực (Live Queue)"** hiển thị các bài đang ở trạng thái `PENDING` / `RUNNING` và máy Worker nào đang xử lý.
* **Chức năng đó hoạt động như thế nào (How it works):**
  * Ngay khi thí sinh gọi hàm RMI `submitSolution(SubmissionDTO)`, Master lưu bản ghi trạng thái `PENDING` vào Neon DB, đẩy đối tượng `SubmissionDTO` vào `LinkedBlockingQueue` và trả về mã `submissionId` ngay lập tức (chỉ mất vài mili-giây).
  * Luồng nền `SubmissionDispatcher` chạy liên tục dùng hàm `queue.take()` lấy bài nộp ra, tìm một máy Worker đang ở trạng thái `IDLE` (ưu tiên máy có `% CPU` thấp nhất do module của Thành viên 4 cung cấp), rồi mở luồng trong `ExecutorService` gọi hàm RMI `workerStub.evaluateSubmission(...)`.
* **Mục đích để làm gì (Why it matters):**
  * Giải quyết triệt để hiện tượng nghẽn cổ chai (Bottleneck) khi 30–50 thí sinh bấm nộp bài cùng một giây. Hàng đợi `BlockingQueue` giúp hệ thống không bao giờ bị quá tải hay mất bài.

#### Chức năng 1.3: Tự động Thu hồi và Chuyển tải khi Sập Máy chấm (`Auto-Failover`)
* **Cụ thể cần làm gì (What to build):**
  * **Ở Backend (`backend.master`):** Lập trình khối xử lý ngoại lệ `catch (RemoteException e)` trong `SubmissionDispatcher` và phương thức `handleWorkerCrash(String deadWorkerId)` phối hợp với bộ canh gác nhịp tim của Thành viên 4.
  * **Ở Frontend (`frontend.ui.master`):** Khung nhật ký **"Failover & Dispatcher Log"** tô màu nổi bật các sự kiện thu hồi bài và chuyển máy chấm.
* **Chức năng đó hoạt động như thế nào (How it works):**
  * Khi một máy Worker đang chấm bài (ví dụ đang chạy đến Test 5/10) mà bị tắt ngang phần mềm hoặc rớt mạng, lời gọi RMI `workerStub.evaluateSubmission(...)` trên Master sẽ lập tức ném ra `RemoteException`.
  * Bộ điều phối của Thành viên 1 bắt ngoại lệ này, xóa ngay Worker bị sập khỏi danh sách `activeWorkers`, ghi log cảnh báo đỏ lên màn hình Giáo viên, đồng thời đẩy nguyên vẹn gói `SubmissionDTO` đó quay trở lại đầu hàng đợi `LinkedBlockingQueue` để giao ngay cho một máy Worker khác đang sống chấm lại từ đầu.
* **Mục đích để làm gì (Why it matters):**
  * Đây là **tính năng ăn điểm A+ đắt giá nhất khi Demo**: Chứng minh hệ thống phân tán có khả năng chịu lỗi (Fault Tolerance) – rút dây mạng 1 máy chấm giữa chừng nhưng thí sinh vẫn nhận được kết quả chấm chính xác từ máy chấm dự phòng.

---

### 👤 THÀNH VIÊN 2: Judge Worker RMI Node & Hộp Cát Chấm Bài Cô Lập (`Sandbox Execution Engine`)

#### Chức năng 2.1: Dịch vụ Máy chấm Từ xa (`IJudgeWorkerService`) & Tự đăng ký vào Cụm
* **Cụ thể cần làm gì (What to build):**
  * **Ở Backend (`backend.worker`):** Cài đặt lớp `JudgeWorkerServiceImpl extends UnicastRemoteObject implements IJudgeWorkerService` và `WorkerBackendController`.
  * **Ở Frontend (`frontend.ui.worker`):** Thiết kế màn hình điều khiển của máy Worker hiển thị định danh máy (`Worker-ID`), địa chỉ IP LAN, trạng thái kết nối tới Master (`REGISTERED / DISCONNECTED`) và đèn báo trạng thái máy (`🟢 IDLE - Sẵn sàng` / `🟠 BUSY - Đang chấm`).
* **Chức năng đó hoạt động như thế nào (How it works):**
  * Khi bật ứng dụng Worker trên một laptop bất kỳ, Worker dùng module UDP của Thành viên 4 tìm ra IP của Master, sau đó khởi tạo đối tượng từ xa `JudgeWorkerServiceImpl` và gọi hàm RMI `masterClusterStub.registerWorker(workerId, localIp, thisWorkerStub)`.
  * Kể từ thời điểm đó, Master giữ `Stub` của Worker và có thể gọi ngược hàm `evaluateSubmission(...)` xuống máy Worker bất cứ lúc nào.
* **Mục đích để làm gì (Why it matters):**
  * Biến bất kỳ laptop nào trong mạng LAN thành một nút tính toán phân tán (Worker Node), giúp mở rộng năng lực chấm bài theo chiều ngang (Horizontal Scaling) chỉ bằng cách bật thêm máy.

#### Chức năng 2.2: Hộp cát Biên dịch & Thực thi Mã nguồn Đa ngôn ngữ (`CodeSandboxExecutor`)
* **Cụ thể cần làm gì (What to build):**
  * **Ở Backend (`backend.worker`):** Xây dựng lớp `CodeSandboxExecutor` sử dụng `java.lang.ProcessBuilder` để tự động tạo thư mục tạm cô lập, biên dịch (`javac` / `g++` / `python`) và chạy từng Test Case với dữ liệu đầu vào (`stdin`) và thu thập kết quả đầu ra (`stdout`, `stderr`).
  * **Ở Frontend (`frontend.ui.worker`):** Thiết kế bảng **"Tiến độ từng Test Case"** và khung **"Nhật ký Tiến trình Hệ điều hành (`ProcessBuilder Console`)"**.
* **Chức năng đó hoạt động như thế nào (How it works):**
  * Khi nhận gói `SubmissionDTO` và `List<TestCaseDTO>` từ Master, Worker tạo thư mục riêng `sandbox_work/<workerId>_<submissionId>/` để không bị đụng độ file nếu chạy đa luồng.
  * Bước 1 (Compile): Gọi `ProcessBuilder` chạy trình biên dịch. Nếu `exitValue() != 0`, đọc luồng `ErrorStream` lấy thông báo lỗi cú pháp và trả về ngay trạng thái **`CE (Compile Error)`**.
  * Bước 2 (Run Tests): Duyệt qua từng `TestCaseDTO`, khởi tạo tiến trình chạy code, bơm chuỗi `inputData` vào `process.getOutputStream()`, đọc kết quả từ `process.getInputStream()`, chuẩn hóa khoảng trắng/xuống dòng và so sánh với `expectedOutput`. Nếu khớp thì tăng số test đúng; nếu lệch thì dừng và trả về **`WA (Wrong Answer)`**. Sau khi chấm xong, tự động xóa sạch thư mục tạm.
* **Mục đích để làm gì (Why it matters):**
  * Thực thi mã nguồn thực tế trên máy trạm và tự động chấm điểm chính xác như các hệ thống Codeforces, LeetCode, DOMjudge.

#### Chức năng 2.3: Kiểm soát Giới hạn Thời gian (`Timeout Kill`) & Chế độ Trình diễn (`Demo Mode`)
* **Cụ thể cần làm gì (What to build):**
  * **Ở Backend (`backend.worker`):** Lập trình cơ chế cắt tiến trình quá giờ bằng `process.waitFor(timeLimitMs, TimeUnit.MILLISECONDS)` kết hợp `process.destroyForcibly()`, báo cáo tiến độ từng test qua `masterClusterStub.reportTestCaseProgress(...)`, và thêm cờ `demoSlowMode` (nghỉ `400ms` giữa mỗi test case khi bật chế độ Demo).
  * **Ở Frontend (`frontend.ui.worker`):** Thêm công tắc bật/tắt **"Chế độ Demo Chậm (400ms/test)"** và nút bấm đỏ **"🛑 Giả Lập Sập Máy Chấm (Kill Worker)"**.
* **Chức năng đó hoạt động như thế nào (How it works):**
  * Nếu thí sinh nộp đoạn code chứa vòng lặp vô hạn `while(true) {}` với giới hạn thời gian `1000ms`, đúng ở mili-giây thứ `1001`, hàm `waitFor()` trả về `false`. Worker lập tức gọi `destroyForcibly()` để tiêu diệt tiến trình con của hệ điều hành, bảo vệ CPU máy chấm và trả về kết quả **`TLE (Time Limit Exceeded)`**.
  * Khi đi bảo vệ, bật nút **"Chế độ Demo Chậm"**, một bài 10 test sẽ chấm trong khoảng 4 giây – vừa đủ thời gian để giảng viên nhìn thấy từng test nhảy từ 1 đến 10 trên màn hình Client, và đủ thời gian để bạn bấm nút **"Giả Lập Sập Máy Chấm"** ngay ở Test thứ 5 nhằm phô diễn tính năng Auto-Failover.
* **Mục đích để làm gì (Why it matters):**
  * Ngăn chặn mã độc hoặc vòng lặp vô hạn làm treo máy chấm, đồng thời cung cấp công cụ trực quan để trình diễn kịch bản rớt mạng ngay trên bục giảng.

---

### 👤 THÀNH VIÊN 3: Contestant Client, RMI Callback 2 Chiều & Bảng Xếp Hạng Chuẩn ACM-ICPC

#### Chức năng 3.1: Xác thực Thí sinh & Đăng ký Đối tượng RMI Callback (`IClientCallbackService`)
* **Cụ thể cần làm gì (What to build):**
  * **Ở Backend (`backend.client`):** Cài đặt lớp `ClientCallbackServiceImpl extends UnicastRemoteObject implements IClientCallbackService` và lớp `ClientBackendController` bọc toàn bộ các lệnh gọi RMI lên Master trong `CompletableFuture`.
  * **Ở Frontend (`frontend.ui.client`):** Thiết kế màn hình **`LoginDialog`** có nút **"🔍 Tự động quét tìm Master trong LAN"**, ô nhập `Username`, `Password` (tài khoản do máy Giáo viên tạo trên Neon DB) và nút Đăng nhập.
* **Chức năng đó hoạt động như thế nào (How it works):**
  * Khi thí sinh bấm Đăng nhập, `ClientBackendController` khởi tạo đối tượng `ClientCallbackServiceImpl` (mở một cổng TCP ẩn danh trên máy thí sinh để nhận lệnh chiều về), sau đó gọi hàm RMI `masterStub.loginAndRegisterCallback(username, password, clientCallbackStub)` trên một luồng bất đồng bộ.
  * Nếu Master xác thực tài khoản hợp lệ với **Neon PostgreSQL**, Master lưu `clientCallbackStub` vào danh sách phiên đang hoạt động và trả về danh sách đề bài cùng bảng xếp hạng hiện tại để mở cửa sổ làm bài chính.
* **Mục đích để làm gì (Why it matters):**
  * Thiết lập kênh giao tiếp **RMI 2 chiều (Bi-directional RMI)** – biến máy Client từ một nút thụ động thành một nút có khả năng nhận dữ liệu đẩy (Server Push) tức thì từ Server.

#### Chức năng 3.2: Không gian Làm bài, Nộp Code & Nhận Tiến độ Từng Test Case Trực tiếp (`Live Verdict Push`)
* **Cụ thể cần làm gì (What to build):**
  * **Ở Backend (`backend.client`):** Cài đặt các phương thức nhận sự kiện đẩy trong `ClientCallbackServiceImpl`: `onSubmissionStatusChanged(subId, status, currentTest, totalTests, workerId)` và `onJudgeCompleted(JudgeResultDTO result)`.
  * **Ở Frontend (`frontend.ui.client`):** Thiết kế giao diện **`ContestantMainFrame`** chia 3 cột hiện đại: Cột trái đọc đề bài, Cột giữa là trình soạn thảo code có tô màu cú pháp (`RSyntaxTextArea`) kèm nút chọn ngôn ngữ (`Java`/`C++`/`Python`) và nút **Nộp bài**, Cột phải là danh sách bài nộp có **Thanh tiến trình (`JProgressBar`) nhảy trực tiếp từng Test Case**.
* **Chức năng đó hoạt động như thế nào (How it works):**
  * Khi thí sinh bấm **Nộp bài**, code được đóng gói vào `SubmissionDTO` gửi lên Master (truyền theo giá trị `Pass-by-Value`).
  * Trong lúc máy Worker đang chấm từng test, cứ mỗi test chạy xong, Master lại gọi ngược vào hàm `onSubmissionStatusChanged(...)` trên `ClientCallbackServiceImpl` của thí sinh đó. Hàm này lập tức gọi `SwingUtilities.invokeLater()` để cập nhật thanh tiến trình trên màn hình thí sinh: *"🟠 Running on test 4/10 [Tại máy: Worker-02]"* và đổi sang *"🟢 ACCEPTED (10/10 tests | 45ms)"* khi hoàn tất.
* **Mục đích để làm gì (Why it matters):**
  * Chứng minh rõ nét ưu thế của **RMI Callback** so với mô hình hỏi vòng (Polling): Client không cần bắn hàng trăm request hỏi "chấm xong chưa?", mà Server chủ động gọi hàm trên Client ngay khi từng test case vừa chạy xong.

#### Chức năng 3.3: Bảng Xếp Hạng Trực Tiếp Chuẩn Luật Thi Đấu ACM-ICPC (`Live ICPC Scoreboard`)
* **Cụ thể cần làm gì (What to build):**
  * **Ở Backend (`backend.client` & `backend.master`):** Xây dựng lớp `IcpcScoreboardCalculator` tính toán thứ hạng các đội thi và cài đặt hàm Callback `onScoreboardUpdated(List<ScoreboardRowDTO> newBoard)` cùng `onSystemAnnouncement(String message)`.
  * **Ở Frontend (`frontend.ui.client`):** Thiết kế bảng **`Live ICPC Scoreboard`** ở nửa dưới màn hình thí sinh, tô màu xanh lá cho các ô bài đã `AC`, màu đỏ kèm số lần thử sai cho các bài chưa giải được, và thanh Banner chạy thông báo từ Giáo viên.
* **Chức năng đó hoạt động như thế nào (How it works):**
  * Áp dụng đúng công thức xếp hạng của kỳ thi **ACM-ICPC**:
    1. Đội nào có **Tổng số bài giải đúng (`AC`) nhiều hơn** sẽ xếp trên.
    2. Nếu số bài `AC` bằng nhau, đội nào có **Tổng thời gian phạt (`Penalty`) thấp hơn** sẽ xếp trên.
    3. Công thức tính `Penalty` cho mỗi bài đã `AC`: `Penalty = Thời gian giải đúng (phút tính từ lúc bắt đầu thi) + 20 phút × Số lần nộp sai trước khi AC` (các bài chưa `AC` không bị tính điểm phạt).
  * Ngay khi có bất kỳ thí sinh nào trong phòng thi giải đúng 1 bài mới, Master tính lại bảng xếp hạng và phát sóng (Broadcast) qua toàn bộ danh sách `IClientCallbackService` để màn hình của tất cả thí sinh cùng nhảy bảng xếp hạng đồng loạt.
* **Mục đích để làm gì (Why it matters):**
  * Thể hiện cơ chế đồng bộ hóa trạng thái thời gian thực cho nhiều máy trạm cùng lúc (`Multi-client State Synchronization via RMI Callback`).

---

### 👤 THÀNH VIÊN 4: Khám Phá Mạng LAN Tự Động (`UDP Broadcast`), Giám Sát Nhịp Tim (`UDP Heartbeat`) & Bảng Điều Khiển Cụm Máy

#### Chức năng 4.1: Giao thức Quét Tìm Master Server Tự động trong Mạng LAN (`UDP Broadcast Discovery - Port 8888`)
* **Cụ thể cần làm gì (What to build):**
  * **Ở Backend (`backend.discovery`):** Xây dựng `UdpDiscoveryServer` (chạy ngầm trên máy Master ở cổng UDP `8888`) và `UdpDiscoveryClient` (được gọi bởi cả máy Worker và máy Thí sinh khi khởi động hoặc khi bấm nút Quét LAN).
  * **Ở Frontend (`frontend.ui.monitor`):** Hiển thị trạng thái cổng `UDP Discovery 8888` và danh sách các máy vừa quét tìm IP trên bảng giám sát.
* **Chức năng đó hoạt động như thế nào (How it works):**
  * Khi máy Client hoặc máy Worker bấm **"Tự động quét LAN"**, `UdpDiscoveryClient` mở `DatagramSocket`, bật `setBroadcast(true)` và bắn gói tin mang chuỗi định danh `"PULSE_OJ_DISCOVER_REQ"` tới địa chỉ quảng bá toàn mạng LAN `255.255.255.255:8888`.
  * Máy Master đang lắng nghe ở cổng `8888` nhận được gói tin sẽ lập tức đóng gói địa chỉ IP LAN thực của mình và phản hồi trực tiếp về IP/Port của người gửi bản tin: `"PULSE_OJ_MASTER_RES|192.168.1.10|1099"`.
  * Máy gửi tách chuỗi theo dấu `|` để lấy IP `192.168.1.10` và tự động kết nối RMI mà người dùng không cần gõ một chữ số IP nào.
* **Mục đích để làm gì (Why it matters):**
  * Giải quyết bài toán thực tế khi đi thi: Mạng Wi-Fi ở phòng bảo vệ luôn cấp IP động (DHCP) khác với ở nhà. Nhờ dùng **UDP Broadcast**, cả 4 laptop bật lên là tự nhận diện thấy nhau trong 1 giây.

#### Chức năng 4.2: Giao thức Đo Nhịp tim & Giám sát Phần cứng Thời gian thực (`UDP Heartbeat Telemetry - Port 8889`)
* **Cụ thể cần làm gì (What to build):**
  * **Ở Backend (`backend.discovery`):** Xây dựng `UdpHeartbeatSender` (chạy định kỳ mỗi 2 giây trên mỗi máy Worker bằng `ScheduledExecutorService`) và `UdpHeartbeatWatchdog` (chạy trên máy Master lắng nghe cổng UDP `8889`).
  * **Ở Frontend (`frontend.ui.monitor`):** Thiết kế lưới các thẻ **`Worker Health Cards`** hiển thị trực quan thanh `% CPU`, dung lượng `RAM trống (MB)`, số bài đã chấm và trạng thái `ONLINE / BUSY / OFFLINE` của từng máy chấm.
* **Chức năng đó hoạt động như thế nào (How it works):**
  * Cứ mỗi 2 giây, `UdpHeartbeatSender` trên máy Worker dùng thư viện hệ thống `com.sun.management.OperatingSystemMXBean` đọc thông số `% tải CPU thực tế` và `RAM trống` của laptop đó, đóng gói thành bản tin UDP nhẹ theo định dạng:
    `HEARTBEAT|<WorkerID>|<LocalIP>|<CpuLoad%>|<FreeRamMB>|<Status>` và bắn về cổng `8889` của Master.
  * Trên Master, `UdpHeartbeatWatchdog` cập nhật thời điểm nhận gói tin cuối cùng (`lastSeenTimestamp`) của từng Worker. Nếu quá **6 giây** (mất 3 nhịp tim liên tiếp) không thấy gói UDP của một Worker, Watchdog lập tức chuyển trạng thái thẻ của Worker đó trên UI sang màu đỏ `🔴 OFFLINE` và gọi sang bộ điều phối của Thành viên 1 để thu hồi bài đang chấm.
* **Mục đích để làm gì (Why it matters):**
  * Kết hợp hoàn hảo giao thức **UDP Unicast** với **RMI/TCP**: Dữ liệu đo đạc phần cứng liên tục cần tốc độ nhanh, gói tin nhỏ và không tốn chi phí giữ kết nối, rất lý tưởng để trả lời câu hỏi của giảng viên về sự khác biệt giữa TCP/RMI và UDP.

#### Chức năng 4.3: Khung Bắt Gói Tin Trực Tiếp (`Network Packet Inspector`) & Công cụ Bắn Tải (`Stress Test Generator`)
* **Cụ thể cần làm gì (What to build):**
  * **Ở Backend (`backend.discovery`):** Xây dựng bộ thu thập sự kiện mạng `NetworkPacketLogger` ghi nhận mọi gói tin `UDP-8888`, `UDP-8889`, `RMI-1099` và bộ phát tải giả lập `StressTestGenerator` bắn đồng thời 20 bài nộp đa luồng vào hệ thống.
  * **Ở Frontend (`frontend.ui.monitor`):** Thiết kế khung **`Network Packet Inspector`** (có bộ lọc theo giao thức `UDP Discovery`, `UDP Heartbeat`, `RMI Call`, `RMI Callback`) và nút bấm **`⚡ Bắn 20 Bài Nộp Cùng Lúc (Stress Test)`**.
* **Chức năng đó hoạt động như thế nào (How it works):**
  * Mỗi khi có bản tin UDP gửi/nhận hoặc có lời gọi RMI/Callback đi qua hệ thống, một dòng log định dạng chuẩn `[Thời gian] [Giao thức:Cổng] [IP Nguồn -> IP Đích] [Kích thước & Tóm tắt Payload]` được đẩy lên bảng Inspector theo thời gian thực.
  * Khi bấm nút **Stress Test**, hệ thống tự động sinh 20 luồng giả lập 20 thí sinh nộp code cùng một lúc để cả hội đồng nhìn thấy hàng đợi `BlockingQueue` (Tab của TV1) tăng vọt và tất cả các máy Worker (Tab của TV4) cùng chuyển sang trạng thái `BUSY` chia nhau chấm song song.
* **Mục đích để làm gì (Why it matters):**
  * Giúp "nhìn thấy tận mắt" các gói tin mạng đang chạy bên dưới. Khi giảng viên hỏi *"Em chứng minh chỗ này đang chạy UDP hay RMI đi"*, chỉ cần bật bộ lọc trên `Network Packet Inspector` là thuyết phục tuyệt đối.

---

## PHẦN IV. TRÌNH TỰ KHỞI CHẠY KHI DEMO BẢO VỆ CUỐI KỲ

1. **Bước 1 (Chuẩn bị Neon DB):** Đặt chuỗi kết nối `NEON_DB_URL` vào file `.env` trên máy của **Thành viên 1 (Master Server)**.
2. **Bước 2 (Khởi chạy Master):** Chạy `MasterAppLauncher` trên Laptop 1 -> Hệ thống tự động kết nối **Neon PostgreSQL**, khởi tạo bảng, mở `RMI Registry (1099)`, `UDP Discovery (8888)` và `UDP Heartbeat (8889)`. Giáo viên tạo nhanh 3 tài khoản thí sinh trên giao diện Master (dữ liệu lưu thẳng lên Neon DB).
3. **Bước 3 (Khởi chạy các Máy chấm):** Chạy `WorkerAppLauncher` trên Laptop 2 và Laptop 3 -> Bấm **Tự động quét LAN (UDP 8888)** -> Tự động tìm thấy Laptop 1, đăng ký `IJudgeWorkerService` và bắt đầu bắn nhịp tim `% CPU / RAM` mỗi 2 giây về Laptop 1.
4. **Bước 4 (Khởi chạy Máy Thí sinh):** Chạy `ClientAppLauncher` trên Laptop 4 -> Bấm **Tự động quét LAN**, đăng nhập bằng tài khoản vừa tạo trên Neon DB -> Nộp bài thử nghiệm `CE`, `TLE`, `AC`, bấm **Stress Test 20 bài** và bấm **Ngắt ngang 1 máy Worker** để trình diễn trọn vẹn 5 kịch bản điểm A+.
