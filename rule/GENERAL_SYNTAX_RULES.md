# 📜 QUY CHUẨN CÚ PHÁP & QUY TẮC MÃ NGUỒN CHUNG (GENERAL SYNTAX RULES)

> **Dự án:** PulseOJ (*Distributed Online Judge*)  
> **Nền tảng:** Java 17 (LTS), Maven Multi-Module, FlatLaf UI, Neon PostgreSQL, RMI + UDP.

---

## 1. Chuẩn Cú pháp & Định dạng Mã nguồn (Code Conventions)

* **Phiên bản Java & Bảng mã:** Java 17+, UTF-8 toàn bộ các file nguồn.
* **Thụt lề (Indentation):** 4 khoảng trắng (Spaces). Tuyệt đối không dùng ký tự Tab (`\t`).
* **Quy tắc Đặt tên (Naming Conventions):**
  * **Class / Interface / Enum:** `UpperCamelCase` (Ví dụ: `ClientCallbackServiceImpl`, `IClientCallbackService`, `SubmissionDTO`, `Verdict`).
  * **Method / Function:** `lowerCamelCase`, động từ đứng đầu (Ví dụ: `submitSolutionAsync()`, `onTestProgress()`, `calculateScoreboard()`).
  * **Biến (Variable) / Trường (Field):** `lowerCamelCase` (Ví dụ: `submissionId`, `currentContestant`, `networkExecutor`).
  * **Hằng số (Constant):** `UPPER_SNAKE_CASE` (Ví dụ: `PENALTY_MINUTES_PER_REJECTED = 20`, `DEFAULT_RMI_PORT = 1099`).
  * **Package:** Chữ thường toàn bộ, tuân thủ `com.vku.pulseoj.<module>.<subpackage>`.

---

## 2. Thiết Luật Kiến Trúc Tách Lớp (Layer Decoupling Rules)

1. **Module `common`:**
   * Chỉ chứa các Remote Interfaces (`com.vku.pulseoj.common.rmi`), DTOs (`com.vku.pulseoj.common.dto`), Enums (`com.vku.pulseoj.common.enums`) và tiện ích dùng chung (`com.vku.pulseoj.common.util`).
   * **CẤM:** Không chứa bất kỳ logic cơ sở dữ liệu (JDBC/HikariCP), logic biên dịch (ProcessBuilder) hay giao diện (Swing).
2. **Module `backend`:**
   * Chứa toàn bộ xử lý nghiệp vụ, mạng (RMI Services, UDP DatagramSockets), cơ sở dữ liệu (DAO), đa luồng (ExecutorService, BlockingQueue).
   * **CẤM 100%:** Tuyệt đối KHÔNG import `javax.swing.*`, `java.awt.*` bên trong `backend`. Giao tiếp với UI bắt buộc qua các interface listener: `ClientUiListener`, `MasterUiListener`, `WorkerUiListener`.
3. **Module `frontend`:**
   * Chỉ chứa giao diện Swing, FlatLaf, MigLayout, RSyntaxTextArea.
   * **CẤM 100%:** Tuyệt đối KHÔNG viết câu lệnh SQL, không trực tiếp mở Socket mạng hay Registry trong `JFrame`/`JPanel`. Toàn bộ thao tác phải thông qua các lớp Controller (`ClientBackendController`, `MasterBackendController`, `WorkerBackendController`).

---

## 3. Quy tắc Tuần tự hóa Đối tượng (Serialization Rules)

* Mọi đối tượng truyền qua giao thức RMI (DTOs) bắt buộc phải:
  ```java
  public class ExampleDTO implements java.io.Serializable {
      private static final long serialVersionUID = 1L;
      // Thuộc tính, Getters, Setters, Constructors
  }
  ```
* Không truyền các đối tượng không Serializable hoặc các thành phần giao diện qua RMI.

---

## 4. Quy tắc Đa luồng & An toàn Giao diện (Thread Safety Rules)

* **EDT Non-blocking Rule:** Mọi lệnh gọi hàm mạng RMI từ giao diện bấm nút bắt buộc phải bọc trong `CompletableFuture.supplyAsync(..., networkExecutor)` hoặc `SwingWorker`. Tuyệt đối không gọi đồng bộ trên luồng Event Dispatch Thread (EDT).
* **UI Dispatch Rule:** Mọi cập nhật giao diện (Label, Table, ProgressBar) khi nhận được sự kiện mạng từ luồng ngoài bắt buộc phải bọc trong:
  ```java
  SwingUtilities.invokeLater(() -> {
      // Cập nhật giao diện an toàn luồng
  });
  ```

---

## 5. Quy tắc Logging & Xử lý Ngoại lệ (Logging & Exceptions)

* Sử dụng SLF4J Logger, không dùng `System.out.println` trong mã nguồn sản phẩm (trừ các script kiểm thử độc lập):
  ```java
  private static final Logger logger = LoggerFactory.getLogger(ClassName.class);
  ```
* Bắt ngoại lệ tường minh, không để trống khối `catch` (`Empty catch block`). Mọi ngoại lệ mạng phải ghi log rõ IP, Port và mã lỗi tương ứng.

