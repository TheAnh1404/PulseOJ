# 📘 THƯ MỤC QUY CHUẨN KỸ THUẬT & PHÂN CHIA NHIỆM VỤ (/rule)

Thư mục `/rule` là nơi tập trung toàn bộ các quy chuẩn về cú pháp, kiến trúc, đặc tả chữ ký hàm và tài liệu nhiệm vụ chi tiết được **phân chia độc lập theo từng thành viên** trong dự án **PulseOJ**.

---

## 📂 Cấu Trúc Các Thư Mục Con

```
PulseOJ/
└── rule/
    ├── README.md                      # [Chỉ mục] Hướng dẫn điều hướng & áp dụng quy chuẩn
    ├── GENERAL_SYNTAX_RULES.md        # [Chung] Chuẩn cú pháp Java 17, đặt tên, tách lớp, an toàn luồng EDT, tuần tự hóa
    │
    ├── member1/                       # [THÀNH VIÊN 1: MASTER & NEON DB]
    │   ├── RULES.md                   # Quy chuẩn RMI Server (1099), Neon DB HikariCP, SubmissionDispatcher, Auto-Failover
    │   └── TASK.md                    # Cẩm nang triển khai chi tiết, sơ đồ tuần tự và kịch bản bảo vệ TV1
    │
    ├── member2/                       # [THÀNH VIÊN 2: JUDGE WORKER & SANDBOX]
    │   ├── RULES.md                   # Quy chuẩn IJudgeWorkerService, ProcessBuilder Sandbox, TLE Timeout Kill, Cleanup
    │   └── TASK.md                    # Cẩm nang triển khai chi tiết, sơ đồ tuần tự và kịch bản bảo vệ TV2
    │
    ├── member3/                       # [THÀNH VIÊN 3: CONTESTANT CLIENT & CALLBACK (BẠN ĐẢM NHIỆM)]
    │   ├── RULES.md                   # Quy chuẩn RMI Callback 2 chiều, CompletableFuture Async, ICPC Calculator, UI 3 cột
    │   └── TASK.md                    # Cẩm nang triển khai chi tiết, sơ đồ tuần tự và kịch bản bảo vệ TV3
    │
    └── member4/                       # [THÀNH VIÊN 4: UDP DISCOVERY & CLUSTER MONITOR]
        ├── RULES.md                   # Quy chuẩn UDP Broadcast (8888), UDP Heartbeat (8889), Watchdog 6s, Packet Inspector
        └── TASK.md                    # Cẩm nang triển khai chi tiết, sơ đồ tuần tự và kịch bản bảo vệ TV4
```

---

## 🗂️ Bảng Tra Cứu Nhanh Cho Từng Thành Viên

| Thành viên | Thư mục chuyên trách | Quy chuẩn cú pháp & Hàm (`RULES.md`) | Chi tiết nhiệm vụ & Demo (`TASK.md`) |
| :--- | :--- | :--- | :--- |
| **Chung cả nhóm** | [`rule/`](file:///d:/Users/BT/N3_K1/LTrMang/PulseOJ/rule/) | [rule/GENERAL_SYNTAX_RULES.md](file:///d:/Users/BT/N3_K1/LTrMang/PulseOJ/rule/GENERAL_SYNTAX_RULES.md) | [RULES_AND_TASKS.md](file:///d:/Users/BT/N3_K1/LTrMang/PulseOJ/RULES_AND_TASKS.md) |
| **Thành viên 1** | [`rule/member1/`](file:///d:/Users/BT/N3_K1/LTrMang/PulseOJ/rule/member1/) | [rule/member1/RULES.md](file:///d:/Users/BT/N3_K1/LTrMang/PulseOJ/rule/member1/RULES.md) | [rule/member1/TASK.md](file:///d:/Users/BT/N3_K1/LTrMang/PulseOJ/rule/member1/TASK.md) |
| **Thành viên 2** | [`rule/member2/`](file:///d:/Users/BT/N3_K1/LTrMang/PulseOJ/rule/member2/) | [rule/member2/RULES.md](file:///d:/Users/BT/N3_K1/LTrMang/PulseOJ/rule/member2/RULES.md) | [rule/member2/TASK.md](file:///d:/Users/BT/N3_K1/LTrMang/PulseOJ/rule/member2/TASK.md) |
| **Thành viên 3 (Bạn)** | [`rule/member3/`](file:///d:/Users/BT/N3_K1/LTrMang/PulseOJ/rule/member3/) | [rule/member3/RULES.md](file:///d:/Users/BT/N3_K1/LTrMang/PulseOJ/rule/member3/RULES.md) | [rule/member3/TASK.md](file:///d:/Users/BT/N3_K1/LTrMang/PulseOJ/rule/member3/TASK.md) |
| **Thành viên 4** | [`rule/member4/`](file:///d:/Users/BT/N3_K1/LTrMang/PulseOJ/rule/member4/) | [rule/member4/RULES.md](file:///d:/Users/BT/N3_K1/LTrMang/PulseOJ/rule/member4/RULES.md) | [rule/member4/TASK.md](file:///d:/Users/BT/N3_K1/LTrMang/PulseOJ/rule/member4/TASK.md) |

---

## 📌 Nguyên Tắc Áp Dụng Khi Phát Triển
1. **Quản lý theo thư mục riêng:** Mỗi thành viên chỉ cần mở đúng thư mục con `memberX/` của mình để xem trọn vẹn cả **Quy tắc cú pháp/hàm** (`RULES.md`) và **Đặc tả nhiệm vụ** (`TASK.md`).
2. **Không can thiệp chéo package:** Chỉ chỉnh sửa trong package thuộc quyền sở hữu của mình.
3. **Khóa cứng module `common`:** Mọi thay đổi đối với Remote Interface hoặc DTO trong `common` phải được cả nhóm thảo luận và đồng thuận trước khi sửa.
4. **Tuân thủ quy tắc luồng:** Mọi thao tác mạng từ UI phải chạy bất đồng bộ qua `CompletableFuture`, mọi cập nhật UI từ mạng phải nằm trong `SwingUtilities.invokeLater()`.
