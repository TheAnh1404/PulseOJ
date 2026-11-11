# 🎯 QUY TẮC MÃ NGUỒN & ĐẶC TẢ HÀM: THÀNH VIÊN 3 (CONTESTANT CLIENT & CALLBACK)

> **Phụ trách:** Thành viên 3 (Contestant Client Experience & Reverse RMI Callback Engineer)  
> **Git Branch:** `feat/member3-client-callback`  
> **Package sở hữu độc quyền:**  
> - `backend/src/main/java/com/vku/pulseoj/backend/client/`  
> - `frontend/src/main/java/com/vku/pulseoj/frontend/ui/client/`  
> - `frontend/src/main/java/com/vku/pulseoj/frontend/ClientAppLauncher.java`

---

## 1. Bản Giao Kèo Giao Thức (Interface Contract)

Thành viên 3 cài đặt và làm việc với các Remote Interface dùng chung từ `common`:
- [`com.vku.pulseoj.common.rmi.IClientCallbackService`](file:///d:/Users/BT/N3_K1/LTrMang/PulseOJ/common/src/main/java/com/vku/pulseoj/common/rmi/IClientCallbackService.java)
- [`com.vku.pulseoj.common.rmi.IMasterSubmissionService`](file:///d:/Users/BT/N3_K1/LTrMang/PulseOJ/common/src/main/java/com/vku/pulseoj/common/rmi/IMasterSubmissionService.java)

---

## 2. Đặc Tả Chi Tiết Từng Lớp & Chữ Ký Hàm (Function Signatures)

### 2.1. Lớp `ClientCallbackServiceImpl.java` (RMI Remote Object)
* **Kế thừa & Cài đặt:**
  ```java
  public class ClientCallbackServiceImpl extends UnicastRemoteObject implements IClientCallbackService
  ```
* **Constructor bắt buộc:**
  ```java
  public ClientCallbackServiceImpl() throws RemoteException {
      super(); // Tự động export đối tượng trên Anonymous TCP Port
  }
  ```
* **Quy chuẩn chữ ký hàm (Mandatory Method Signatures):**
  1. `public void onTestProgress(JudgeResultDTO result) throws RemoteException`
     - **Mục đích:** Nhận tiến độ từng test case khi máy Worker vừa chấm xong.
     - **Xử lý:** Kiểm tra `uiListener != null`, gọi `uiListener.onTestProgress(result)`.
  2. `public void onSubmissionFinalVerdict(SubmissionDTO submission) throws RemoteException`
     - **Mục đích:** Nhận phán quyết chung cuộc (`AC`, `WA`, `TLE`, `RE`, `CE`) kèm thời gian và bộ nhớ.
     - **Xử lý:** Gọi `uiListener.onFinalVerdict(submission)`.
  3. `public void onScoreboardUpdated(List<ScoreboardRowDTO> scoreboard) throws RemoteException`
     - **Mục đích:** Nhận bảng điểm cập nhật khi có thí sinh trong phòng thi vừa giải đúng bài.
     - **Xử lý:** Gọi `uiListener.onScoreboardUpdated(scoreboard)`.
  4. `public void onSystemBroadcast(String message) throws RemoteException`
     - **Mục đích:** Nhận tin nhắn thông báo khẩn từ Giáo viên.
     - **Xử lý:** Gọi `uiListener.onBroadcastMessage(message)`.
* **CẤM:** Tuyệt đối KHÔNG gọi trực tiếp các Swing components (`lblStatus.setText(...)`) trong các hàm này, bắt buộc phải thông qua `ClientUiListener`.

---

### 2.2. Lớp `ClientBackendController.java` (Bộ Điều Khiển Mạng Bất Đồng Bộ)
* **Thuộc tính bắt buộc:**
  ```java
  private final ExecutorService networkExecutor = Executors.newCachedThreadPool();
  private IMasterSubmissionService masterService;
  private ClientCallbackServiceImpl callbackService;
  private ContestantDTO currentContestant;
  private String clientHost;
  private boolean isConnected = false;
  private ClientUiListener uiListener;
  ```
* **Quy chuẩn chữ ký các hàm bất đồng bộ (`CompletableFuture`):**
  1. `public CompletableFuture<Boolean> connectToMaster(String masterHost, int masterPort)`
     - Duyệt IP qua `NetworkUtils.configureRmiHostname()`.
     - Lấy Registry: `LocateRegistry.getRegistry(masterHost, masterPort)`.
     - Lookup service: `registry.lookup(IMasterSubmissionService.SERVICE_NAME)`.
     - Khởi tạo `callbackService = new ClientCallbackServiceImpl()`.
  2. `public CompletableFuture<LoginResponseDTO> loginAsync(String username, String password)`
     - Gửi `LoginRequestDTO(username, password, clientHost)` tới Master.
     - Nếu thành công, lưu `this.currentContestant = resp.getContestant()`.
  3. `public CompletableFuture<List<ProblemDTO>> getProblemsAsync()`
     - Triệu gọi `masterService.getAllProblems()`.
  4. `public CompletableFuture<SubmissionDTO> submitSolutionAsync(ProblemDTO problem, Language lang, String code)`
     - Đóng gói `SubmissionDTO` (gồm `contestantId`, `problemCode`, `language`, `sourceCode`).
     - Triệu gọi `masterService.submitSolution(sub, callbackService)`. Lưu ý: **Bắt buộc truyền `callbackService` (Stub) kèm theo** để Master đăng ký nhận sự kiện đẩy.
  5. `public CompletableFuture<List<ScoreboardRowDTO>> getScoreboardAsync()`
     - Triệu gọi `masterService.getScoreboard()`.

---

### 2.3. Lớp `IcpcScoreboardCalculator.java` (Thuật Toán Tính Điểm Chuẩn ACM-ICPC)
* **Hằng số quy chuẩn:**
  ```java
  public static final int PENALTY_MINUTES_PER_REJECTED = 20;
  ```
* **Chữ ký hàm chính:**
  ```java
  public static List<ScoreboardRowDTO> calculate(
          List<ContestantDTO> contestants,
          List<SubmissionDTO> submissions,
          long contestStartTimestamp
  )
  ```
* **Quy tắc giải thuật bắt buộc tuân thủ 100%:**
  1. **Thứ hạng:** Sắp xếp danh sách theo:
     - Số bài giải được (`solvedCount`): Giảm dần (Đội giải nhiều bài hơn đứng trước).
     - Tổng thời gian phạt (`totalPenaltyMinutes`): Tăng dần (Bằng số bài thì đội phạt ít hơn đứng trước).
  2. **Thời gian giải (`Solved Time`):** `(submissionTime - contestStartTimestamp) / (1000 * 60)`.
  3. **Điểm phạt 20 phút (`Penalty Rule`):** Mỗi lần nộp sai (`WA`, `TLE`, `RE`) **chỉ bị cộng 20 phút phạt khi và chỉ khi bài đó sau này giải đúng (`AC`)**.
  4. **Các trường hợp bỏ qua:**
     - Lỗi biên dịch (`CE`): **0 phút phạt**.
     - Bài chưa từng `AC`: Toàn bộ các lần nộp sai của bài đó **không được tính vào tổng penalty**.
     - Sau khi đã `AC`: Mọi bài nộp sau thời điểm `AC` của cùng bài toán **bị bỏ qua hoàn toàn**.

---

### 2.4. Phân Hệ Frontend (`frontend/src/main/java/com/vku/pulseoj/frontend/ui/client/`)

1. **`LanDiscoveryLoginDialog.java`:**
   - Nút **"🔍 Auto-Discover LAN"**: Kích hoạt `UdpDiscoveryClient.discoverMaster()` để tự điền IP Master.
   - Trường nhập `Username`, `Password`.
   - Nút **"Đăng Nhập"**: Khóa nút tránh spam double-click, gọi `controller.connectToMaster()` $\rightarrow$ `controller.loginAsync()`.
2. **`ClientMainFrame.java` (Bố cục 3 cột):**
   - **Cột Trái:** `JComboBox` chọn đề bài, thông tin Time/Memory Limit, mô tả đề thi và test mẫu.
   - **Cột Giữa:** Trình soạn thảo `RSyntaxTextArea` (tô màu Java, C++, Python), nút **"Reset Code Template"**, nút bấm lớn màu xanh **"🚀 Submit Solution"**.
   - **Cột Phải:** `JProgressBar` hiển thị tiến độ % và dòng text: `Test x/N (WorkerId)`. Bảng lịch sử các bài đã nộp.
3. **`ScoreboardPanel.java`:**
   - Bảng hiển thị `Rank`, `Username`, `Team Name`, các cột bài tập (`PROB_A`, `PROB_B`,...), `Solved`, `Penalty`.
   - Tô màu ô bài nộp:
     * Màu xanh lá (`#2ea44f`): `+` hoặc `+K (T phút)`.
     * Màu đỏ (`#cf222e`): `-K` (Nộp sai K lần, chưa AC).
     * Màu vàng (`#d4a72c`): `?` (Đang chấm).
     * Dấu chấm (`.`): Chưa nộp.

---

## 3. Quy Tắc "Sống Còn" Khi Lập Trình (Survival Rules)

> [!WARNING]
> 1. **Quy tắc luồng Swing:** Bất kỳ thao tác cập nhật giao diện nào xuất phát từ RMI Callback bắt buộc phải nằm trong `SwingUtilities.invokeLater(() -> { ... })`.
> 2. **Quy tắc IP Hostname:** Tại `ClientAppLauncher.java`, dòng đầu tiên của hàm `main()` bắt buộc phải gọi `NetworkUtils.configureRmiHostname();`.
> 3. **Quy tắc RMI Stub:** Không bao giờ truyền trực tiếp `this` (đối tượng UI) qua RMI, chỉ truyền `callbackService` (đối tượng kế thừa `UnicastRemoteObject`).

