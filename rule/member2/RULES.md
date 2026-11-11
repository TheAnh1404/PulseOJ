# 🎯 QUY TẮC MÃ NGUỒN & ĐẶC TẢ HÀM: THÀNH VIÊN 2 (JUDGE WORKER & SANDBOX)

> **Phụ trách:** Thành viên 2 (Judge Worker Node & Isolated Code Sandbox Execution Engine)  
> **Git Branch:** `feat/member2-worker-sandbox`  
> **Package sở hữu độc quyền:**  
> - `backend/src/main/java/com/vku/pulseoj/backend/worker/`  
> - `frontend/src/main/java/com/vku/pulseoj/frontend/ui/worker/`  
> - `frontend/src/main/java/com/vku/pulseoj/frontend/WorkerAppLauncher.java`

---

## 1. Bản Giao Kèo Giao Thức (Interface Contract)

Thành viên 2 cài đặt và làm việc với các Remote Interface:
- [`com.vku.pulseoj.common.rmi.IJudgeWorkerService`](file:///d:/Users/BT/N3_K1/LTrMang/PulseOJ/common/src/main/java/com/vku/pulseoj/common/rmi/IJudgeWorkerService.java) (Interface export trên máy Worker)
- [`com.vku.pulseoj.common.rmi.IMasterClusterService`](file:///d:/Users/BT/N3_K1/LTrMang/PulseOJ/common/src/main/java/com/vku/pulseoj/common/rmi/IMasterClusterService.java) (Gọi lên Master để đăng ký và báo kết quả)

---

## 2. Đặc Tả Chi Tiết Từng Lớp & Chữ Ký Hàm

### 2.1. Lớp `JudgeWorkerServiceImpl.java` (RMI Remote Object trên Worker)
* **Khai báo lớp:**
  ```java
  public class JudgeWorkerServiceImpl extends UnicastRemoteObject implements IJudgeWorkerService
  ```
* **Chữ ký hàm chính:**
  1. `public void evaluateSubmission(SubmissionDTO submission, List<TestCaseDTO> testCases) throws RemoteException`
     - Chuyển trạng thái máy sang `WorkerStatus.BUSY`.
     - Cập nhật nhịp tim UDP sang `BUSY`.
     - Mở luồng chạy `CodeSandboxExecutor.execute(...)`.
     - Sau khi chấm xong, chuyển trạng thái máy về `WorkerStatus.IDLE` và gọi `masterStub.reportFinalVerdict(...)`.
  2. `public WorkerStatus getStatus() throws RemoteException`
     - Trả về `WorkerStatus.IDLE` hoặc `WorkerStatus.BUSY`.
  3. `public boolean ping() throws RemoteException`
     - Trả về `true` để Master kiểm tra tính khả dụng.

### 2.2. Lớp `CodeSandboxExecutor.java` (Hộp Cát Thực Thi Mã Nguồn)
* **Quy trình 3 bước bắt buộc:**
  1. **Tạo thư mục tạm cô lập:**
     `Path workDir = Paths.get("sandbox_work", workerId + "_" + submissionId);`
     Tạo thư mục và ghi file mã nguồn (`Solution.java`, `solution.cpp`, hoặc `solution.py`).
  2. **Biên dịch (Compile Phase):**
     - Với Java: `new ProcessBuilder("javac", "Solution.java").directory(workDir.toFile()).start()`
     - Với C++: `new ProcessBuilder("g++", "-O3", "solution.cpp", "-o", "solution.exe").start()`
     - Với Python: Bỏ qua bước compile.
     - Nếu `process.waitFor() != 0`, đọc luồng lỗi `process.getErrorStream()` và trả về ngay **`CE (Compilation Error)`**.
  3. **Chấm từng Test Case (Run Phase):**
     - Duyệt qua từng `TestCaseDTO`.
     - Bơm `testCase.getInputData()` vào `process.getOutputStream()`.
     - **Cơ chế cắt tiến trình quá giờ (TLE Kill):**
       ```java
       boolean finishedInTime = process.waitFor(timeLimitMs, TimeUnit.MILLISECONDS);
       if (!finishedInTime) {
           process.destroyForcibly(); // Tiêu diệt tiến trình con ngay lập tức!
           return Verdict.TLE;
       }
       ```
     - Đọc kết quả từ `process.getInputStream()`, chuẩn hóa khoảng trắng `trim()` và so sánh với `expectedOutput`. Khớp thì `AC`, lệch thì trả về `WA`.
  4. **Dọn dẹp (Cleanup Phase):**
     - Đặt trong khối `finally`: Tự động xóa sạch thư mục tạm và toàn bộ file `.class`/`.exe` sau khi chấm xong.

### 2.3. Lớp `WorkerBackendController.java` & Frontend
* Tích hợp công tắc **"Chế độ Demo Chậm (400ms/test)"**: Chèn `Thread.sleep(400)` giữa mỗi test case để người xem nhìn thấy từng test nhảy trên màn hình.
* Tích hợp nút **"🛑 Giả Lập Sập Máy Chấm (Kill Worker)"**: Gọi `System.exit(0)` hoặc hủy đăng ký RMI để phô diễn tính năng Auto-Failover của TV1.

