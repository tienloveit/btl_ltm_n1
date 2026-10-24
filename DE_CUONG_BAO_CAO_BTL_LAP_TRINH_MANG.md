# Đề cương báo cáo BTL môn Lập trình mạng

## Thông tin chung cần điền

- Tên đề tài: Game bắn xe tăng trực tuyến (Tank Arena)
- Môn học, lớp học phần, giảng viên
- Nhóm thực hiện, danh sách thành viên và phân công
- Trường/khoa, thời gian hoàn thành

## Bố cục báo cáo đề xuất

Khung phân cấp chi tiết hơn, gồm các mục con và checklist sơ đồ/hình/bảng cần chuẩn bị, nằm tại [KHUNG_BAO_CAO_CHI_TIET.md](./KHUNG_BAO_CAO_CHI_TIET.md). Dùng tài liệu đó làm khung triển khai; đề cương này là bản tóm tắt nội dung và lưu ý kỹ thuật.

### 1. Mở đầu

- Lý do chọn đề tài và ý nghĩa của game nhiều người chơi qua mạng.
- Mục tiêu: xây dựng game client-server, nhiều client kết nối đồng thời, trao đổi dữ liệu qua TCP.
- Phạm vi: tài khoản, lobby/thách đấu, phòng chờ, gameplay 1v1, lưu thống kê và lịch sử.
- Công nghệ chính: Java 17+, Swing, TCP Socket, JDBC, MySQL.

### 2. Phân tích yêu cầu

- Yêu cầu chức năng:
  - Đăng ký, đăng nhập, đăng xuất và quản lý kết nối.
  - Hiển thị hồ sơ, danh sách người chơi và trạng thái.
  - Gửi/nhận lời mời, chấp nhận/từ chối/hủy, xử lý hết hạn.
  - Tạo phòng, đếm ngược, chơi trận 1v1, kết thúc và chơi lại.
  - Lưu thống kê, bảng xếp hạng và lịch sử trận đấu.
- Yêu cầu phi chức năng: nhiều kết nối, đồng bộ trạng thái, an toàn dữ liệu, xử lý lỗi và khả năng chạy qua LAN.
- Luật chơi đã cài đặt: 100 HP, 50 viên đạn, 180 giây, mỗi lần trúng gây 20 sát thương; kết quả theo HP khi hết giờ/hết đạn.
- Quy tắc điểm: thắng +3, hòa +1 mỗi người, thua +0; thứ tự xếp hạng theo điểm, thắng, hòa, thua và tên.

### 3. Thiết kế hệ thống

- Mô hình tổng thể: Client Swing ↔ TCP Server ↔ MySQL.
- Các sơ đồ cần chuẩn bị:
  - **Sơ đồ kiến trúc/deployment:** Client chạy trên máy người chơi, TCP Server xử lý nhiều client và MySQL lưu dữ liệu; ghi rõ TCP Socket và JDBC.
  - **Use case:** các chức năng của người chơi như đăng ký/đăng nhập, xem lobby/hồ sơ, thách đấu, chơi trận, xem ranking/history và đăng xuất.
  - **Activity diagram:** mô tả một luồng nghiệp vụ tiêu biểu, ưu tiên thách đấu → vào phòng → chơi → nhận kết quả.
  - **State diagram:** vòng đời trạng thái người chơi `ONLINE` → `WAITING` → `PLAYING` → `ONLINE`, gồm nhánh từ chối/hủy/hết hạn lời mời và disconnect.
  - **Sequence diagram:** vẽ các luồng chi tiết theo checklist ngay dưới đây; tối thiểu nên có đăng nhập, thách đấu/tạo phòng và gameplay/kết thúc trận.
  - **ERD:** thể hiện bảng `player_accounts`, `matches`, `match_players` (trình bày chi tiết ở mục 5).
  - **Class diagram:** nên tách thành Client và Server để sơ đồ dễ đọc; thành phần đề xuất ghi ở mục dưới.
  - Có thể gộp sơ đồ kiến trúc với component diagram; không cần vẽ riêng nếu nội dung bị trùng.
- **Class diagram phía Client:**
  - Lớp khởi động/điều phối/kết nối: `ClientApp`, `ClientController`, `ClientConnection`.
  - UI tiêu biểu: `LoginFrame`, `RegisterFrame`, `PlayerFrame`, `OnlinePlayersPanel`, `WaitingRoomPanel`, `GamePlayPanel`.
  - Model: `PlayerProfile`, `PlayerSummary`, `RoomState`, `GameState`, `RankingEntry`, `MatchHistoryEntry`.
  - Protocol: `Protocol`; `ClientController` triển khai `ClientConnection.Listener`.
  - Quan hệ chính: `ClientApp` khởi tạo `ClientController`; controller dùng `ClientConnection` và điều khiển các frame; `PlayerFrame` chứa các panel chính; connection dùng protocol và chuyển dữ liệu thành model.
- **Class diagram phía Server:**
  - Khởi động/kết nối/session: `ServerMain`, `ClientHandler`, `PlayerSessionManager`, `PlayerStatus`.
  - Nghiệp vụ: `AccountService`, `ChallengeManager`, `RoomManager`, `GameStateManager`.
  - Persistence: `AccountRepository`, `JdbcAccountRepository`, `MatchHistoryRepository`, `DatabaseConfig`; các record `AccountRecord`, `RankingRecord`, `MatchHistoryRecord`.
  - Protocol: `Protocol`; `GameStateManager` triển khai `RoomManager.GameListener`; `JdbcAccountRepository` triển khai `AccountRepository`.
  - Quan hệ chính: `ServerMain` khởi tạo và nối các manager/service; `ClientHandler` gọi xác thực, session, lobby/room/game; `ChallengeManager` dùng session và room; `RoomManager` dùng session và phát callback gameplay; `GameStateManager` dùng account/history để ghi kết quả; repository dùng `DatabaseConfig` để truy cập MySQL.
  - Chỉ ghi các thuộc tính/phương thức tiêu biểu và quan hệ chính; không cần đưa getter/setter hoặc toàn bộ lớp phụ trợ vào sơ đồ.
- **Sequence diagram — checklist nội dung:**
  1. **Đăng nhập (bắt buộc):**
     - Lifeline: Người chơi, `LoginFrame`, `ClientController`, `ClientConnection`, `ClientHandler`, `AccountService`, `JdbcAccountRepository`/MySQL, `PlayerSessionManager`.
     - Luồng thành công: nhập thông tin → gửi `LOGIN_REQUEST` → xác thực tài khoản → tạo session và đặt `ONLINE` → trả `LOGIN_SUCCESS` và `PLAYER_LIST`.
     - Dùng nhánh `alt` cho sai thông tin hoặc tài khoản đang đăng nhập ở kết nối khác; thể hiện phản hồi lỗi về giao diện.
  2. **Thách đấu, chấp nhận và vào phòng (bắt buộc):**
     - Lifeline: Người chơi A/Client A, `ClientConnection` A, `ClientHandler`/Server, `ChallengeManager`, `PlayerSessionManager`, `RoomManager`, `GameStateManager`, Client B/Người chơi B.
     - Luồng chính: A gửi `CHALLENGE` → server kiểm tra trạng thái hai người → chuyển cả hai sang `WAITING` → gửi `CHALLENGE_PENDING` cho A và `CHALLENGE_RECEIVED` cho B → B gửi `ACCEPT` → server đóng lời mời với reason `ACCEPTED`, tạo phòng và gửi `ROOM_STATE`.
     - Dùng khung `loop` hoặc các message lặp cho countdown server gửi `3`, `2`, `1`; sau đó chuyển sang `PLAYING`, gọi callback bắt đầu trận và gửi `MATCH_START` cho cả hai.
     - Dùng nhánh `alt` cho `REJECT`, `CHALLENGE_CANCEL`, timeout hoặc disconnect; thể hiện đóng lời mời, trả người còn kết nối về `ONLINE` và cập nhật roster. Có thể ghi chú các trường hợp sai người nhận/ID không hợp lệ nhận `LOBBY_ERROR`.
  3. **Gameplay và kết thúc trận (bắt buộc):**
     - Lifeline: Người chơi A/Client A, Người chơi B/Client B, `ClientConnection`, `ClientHandler`, `GameStateManager`, `AccountService`, `MatchHistoryRepository`, MySQL, `PlayerSessionManager`.
     - Dùng khung `loop` cho chu kỳ client gửi input (`MOVE_LEFT`, `MOVE_RIGHT`, `SHOOT`) → server kiểm tra/cập nhật trạng thái trận → server phát `GAME_STATE` cho hai client; khi trúng đạn có thể phát thêm `HIT_EFFECT`.
     - Dùng nhánh `alt` cho kết thúc do hết HP, hết giờ, hết đạn hoặc người chơi thoát/rời trận; server xác định winner/draw, cập nhật thống kê/điểm, lưu lịch sử, phát `GAME_OVER`.
     - Nếu thể hiện chơi tiếp, thêm nhánh `opt`/`alt`: cả hai cùng gửi `PLAY_AGAIN` thì reset trận và gửi `MATCH_START`; không mô tả như đồng ý một phía là đủ.
  4. **Đăng ký (nên có nếu cần trình bày đủ tài khoản):**
     - Lifeline: Người chơi, `RegisterFrame`, `ClientController`, `ClientConnection`, `ClientHandler`, `AccountService`, `JdbcAccountRepository`/MySQL.
     - Thể hiện client kiểm tra hai mật khẩu khớp, gửi `REGISTER_REQUEST`; server validate username/email/password, tạo salt và hash PBKDF2 rồi ghi tài khoản.
     - Dùng nhánh `alt` cho dữ liệu không hợp lệ hoặc username/email đã tồn tại; trả `REGISTER_FAIL`; thành công trả `REGISTER_SUCCESS`.
  5. **Đăng xuất hoặc mất kết nối (nên có):**
     - Lifeline: Client, `ClientConnection`, `ClientHandler`, `PlayerSessionManager`, `ChallengeManager`, `RoomManager`/`GameStateManager`, các client còn lại.
     - Dùng nhánh `alt`: logout chủ động gửi `LOGOUT_REQUEST` và nhận `LOGOUT_SUCCESS`; hoặc socket đóng/mất kết nối khiến server phát hiện EOF/disconnect.
     - Thể hiện cleanup session một lần, hủy lời mời/countdown hoặc xử lý người rời trận theo callback, rồi broadcast roster mới cho người chơi còn kết nối.
  - Trên mọi sơ đồ, ghi tên message đúng theo protocol, thứ tự gọi đúng theo source và phân biệt phản hồi thành công/lỗi bằng `alt`; không vẽ chi tiết getter/setter hoặc mọi message phụ.
- **Sơ đồ thành phần và trách nhiệm:**
  - Client: giao diện, controller, model, kết nối/protocol.
  - Server: xử lý kết nối, xác thực, session, lobby, phòng, gameplay, persistence.
  - Database: tài khoản, thống kê và lịch sử trận.
- Mô hình đa luồng: accept kết nối, worker xử lý từng client, writer gửi dữ liệu, scheduler cho timeout/countdown/game tick.
- Nguyên tắc authoritative server: server quyết định trạng thái trận và kết quả; client chỉ gửi input và hiển thị snapshot.
- Nếu cần giới hạn số hình, ưu tiên sơ đồ kiến trúc, use case, ERD, class diagram Server và 2–3 sequence diagram quan trọng. Tránh để activity và sequence lặp lại cùng một mức thông tin.

### 4. Thiết kế giao thức mạng

- TCP Socket và cách thiết lập kết nối; tham số host/port.
- Framing/message: `DataInputStream.readUTF` và `DataOutputStream.writeUTF`; mô tả riêng trường số lượng `PLAYER_LIST` dùng `readInt/writeInt`.
- Bảng message theo từng luồng:
  - Authentication: `REGISTER_REQUEST`, `LOGIN_REQUEST` và các phản hồi.
  - Presence/lobby: `PLAYER_LIST`, `CHALLENGE_*`, `ACCEPT`, `REJECT`.
  - Room/game: `ROOM_STATE`, `MATCH_START`, `MOVE_*`, `SHOOT`, `GAME_STATE`, `GAME_OVER`, `EXIT`, `PLAY_AGAIN`.
  - Dữ liệu phụ: ranking, history, profile update, lỗi server.
- Với mỗi message cần ghi: hướng truyền, payload, thứ tự trường và thời điểm sử dụng.
- Nêu xử lý message không hợp lệ, mất kết nối, đăng nhập trùng và người chơi bận.

### 5. Thiết kế và cài đặt cơ sở dữ liệu

- Sơ đồ ERD và mô tả bảng `player_accounts`, `matches`, `match_players`.
- Khóa chính/khóa ngoại, ràng buộc email, thống kê và quan hệ người chơi-trận đấu.
- JDBC repository, prepared statements, cấu hình kết nối qua biến môi trường hoặc `server/.env`.
- Mật khẩu không lưu dạng rõ: salt ngẫu nhiên và PBKDF2-HMAC-SHA256.
- Quy trình ghi kết quả và truy vấn lịch sử/xếp hạng.

### 6. Cài đặt các chức năng

- Đăng ký, xác thực đăng nhập và quản lý session.
- Roster online và cập nhật `ONLINE` / `WAITING` / `PLAYING`.
- Lời mời thách đấu: quyền gửi/nhận phản hồi, timeout 30 giây, hủy và giải phóng trạng thái.
- Phòng chờ: tạo phòng sau khi chấp nhận, countdown 3–2–1, thoát hoặc disconnect.
- Gameplay: điều khiển trái/phải, bắn, di chuyển đạn, va chạm, HP, ammo, timer và điều kiện kết thúc.
- Kết quả: cập nhật điểm/thắng/thua/hòa, ghi lịch sử, gửi trạng thái về client, đồng ý chơi lại.
- Giao diện: Login/Sign Up, Lobby, hồ sơ, lời mời, phòng chờ, gameplay, ranking/history.

### 7. Kiểm thử và đánh giá

- Môi trường kiểm thử: hệ điều hành, JDK, MySQL, cấu hình mạng và cấu hình server.
- Kiểm thử TCP/lobby bằng `server\run-lobby-tests.bat`: đăng nhập, lời mời, cạnh tranh lời mời, timeout, countdown, thoát/disconnect, đăng ký và logout.
- Kiểm tra UI bằng `client\run-ui-checks.bat`: trạng thái nút, lựa chọn người chơi, dialog và ảnh giao diện.
- Bổ sung bảng test case: mã test, đầu vào/tình huống, kết quả mong đợi, kết quả thực tế.
- Chụp ảnh màn hình các luồng chính; ghi rõ kiểm thử nào dùng database thật và kiểm thử nào dùng kho dữ liệu giả.
- Không kết luận đã kiểm thử MySQL/LAN/gameplay end-to-end nếu chưa chạy các kịch bản đó trên môi trường tương ứng.

### 8. Hướng dẫn cài đặt và sử dụng

- Yêu cầu môi trường và cài MySQL/schema.
- Cấu hình `TANK_DB_URL`, `TANK_DB_USER`, `TANK_DB_PASSWORD` cho server.
- Chạy `server\run-server.bat`; chạy client bằng `client\run-client.bat <server-ip> 5000`.
- Mở firewall cho cổng TCP 5000 khi kết nối qua LAN.
- Hướng dẫn đăng ký, đăng nhập, thách đấu, chơi trận và xem kết quả.

### 9. Đánh giá, hạn chế và hướng phát triển

- Kết quả đạt được so với yêu cầu ban đầu.
- Hạn chế cần nêu trung thực: TCP hiện chưa có TLS; test hiện có chưa bao phủ đầy đủ JDBC/MySQL và kiểm thử đa máy thực tế.
- Hướng phát triển: mã hóa kết nối, kiểm thử tải/nhiều client, xử lý reconnect, cải thiện quản lý cấu hình và mở rộng trải nghiệm game.
- Nêu rõ phần nào đã triển khai trong mã nguồn, phần nào mới là định hướng.

### 10. Kết luận

- Tóm tắt sản phẩm và kiến thức mạng đã áp dụng: TCP, client-server, đa luồng, đồng bộ trạng thái, JDBC.
- Đánh giá mức độ hoàn thành, bài học và công việc còn lại.

## Phụ lục nên chuẩn bị

- Sơ đồ kiến trúc/deployment, use case, activity, state, sequence, class diagram Client/Server và ERD theo phạm vi báo cáo.
- Bảng protocol đầy đủ và payload từng message.
- Một số đoạn mã tiêu biểu: server accept/worker, reader-writer, quản lý phòng, xử lý kết quả.
- Script schema/migration, hướng dẫn chạy, bảng test case và ảnh chụp giao diện.

## Lưu ý khi hoàn thiện báo cáo

- Đối chiếu mô tả với source code hiện tại. Một số đoạn trong README/context còn ghi gameplay, ranking/history là chưa tích hợp, nhưng mã nguồn hiện đã có các module này.
- Chỉ mô tả chức năng là “đã kiểm thử” khi có kết quả kiểm thử tương ứng; test hiện có chủ yếu tập trung vào TCP/lobby và UI, không thay thế kiểm thử JDBC/MySQL hoặc chạy thật qua LAN.
- Điền thông tin thành viên, phân công, môi trường chạy, kết quả test và ảnh minh chứng của chính nhóm.
