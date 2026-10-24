# Khung chi tiết báo cáo BTL Lập trình mạng

Đề tài: **Game bắn xe tăng trực tuyến (Tank Arena)**

Khung này phân cấp nội dung đến các mục nhỏ và liệt kê những hình, bảng cần chuẩn bị. Khi hoàn thiện, cần phân biệt rõ: (1) yêu cầu/thiết kế, (2) chức năng có trong source, (3) chức năng đã được kiểm thử. Không trình bày ảnh hoặc kết quả kiểm thử chưa thực hiện.

## Phần đầu báo cáo

### Trang bìa

- Tên trường, khoa/bộ môn, môn học, lớp học phần.
- Tên đề tài, giảng viên hướng dẫn, nhóm thực hiện.
- Danh sách thành viên, mã sinh viên và phân công thực tế.
- Địa điểm và thời gian hoàn thành.

### Các trang mở đầu

- Lời cảm ơn (nếu yêu cầu).
- Mục lục.
- Danh mục hình, danh mục bảng.
- Danh mục từ viết tắt: TCP, JDBC, UI, HP, ERD, UML, v.v.

## Chương 1. Mở đầu

### 1.1. Lý do chọn đề tài

- Nhu cầu xây dựng trò chơi nhiều người chơi qua mạng.
- Giá trị thực hành các kiến thức lập trình mạng, client-server, đa luồng và cơ sở dữ liệu.

### 1.2. Mục tiêu đề tài

- Xây dựng ứng dụng client-server để nhiều người chơi kết nối tới server.
- Tổ chức tài khoản, lobby/thách đấu, phòng đấu và trận 1v1.
- Đồng bộ trạng thái trận và lưu thống kê/lịch sử.

### 1.3. Đối tượng và phạm vi

- Đối tượng: người chơi sử dụng ứng dụng Client; Server quản lý phiên, trận đấu và dữ liệu.
- Phạm vi chức năng dự kiến và phạm vi thực tế đã triển khai.
- Nêu rõ các phần thuộc phân công từng thành viên và phần tích hợp chung.

### 1.4. Công nghệ và phương pháp thực hiện

- Java 17+, Java Swing, TCP Socket, JDBC, MySQL.
- Mô hình Client ↔ Server ↔ Database; Client không truy cập trực tiếp Database.
- Tóm tắt cách phân tích yêu cầu, thiết kế, cài đặt và kiểm thử.

## Chương 2. Khảo sát và phân tích yêu cầu

### 2.1. Mô tả bài toán

- Mục tiêu trò chơi và các vai trò Client, Server, Database.
- Luồng tổng quát từ đăng ký/đăng nhập đến lobby, trận đấu và kết quả.

### 2.2. Tác nhân và quyền hạn

- Người chơi chưa đăng nhập: đăng ký, đăng nhập.
- Người chơi đã đăng nhập: xem lobby/hồ sơ, thách đấu, chơi trận, xem ranking/history, đăng xuất.
- Server: xác thực, quản lý trạng thái và phòng, xử lý game, lưu kết quả.

### 2.3. Yêu cầu chức năng

- **F01 — Tài khoản:** đăng ký, đăng nhập, đăng xuất; kiểm tra dữ liệu đầu vào.
- **F02 — Người chơi:** hồ sơ, danh sách online, trạng thái `ONLINE`/`WAITING`/`PLAYING`.
- **F03 — Thách đấu:** gửi, nhận, chấp nhận, từ chối, hủy và hết hạn lời mời.
- **F04 — Phòng:** tạo sau khi Accept, countdown, thoát phòng và xử lý disconnect.
- **F05 — Gameplay:** di chuyển, bắn, va chạm, HP, đạn, thời gian và đồng bộ trạng thái.
- **F06 — Kết quả:** xác định thắng/thua/hòa, cập nhật thống kê, lưu lịch sử và chơi lại.
- **F07 — Tra cứu:** xem bảng xếp hạng và lịch sử trận.

### 2.4. Yêu cầu phi chức năng

- Nhiều client kết nối đồng thời; có giới hạn kết nối phía server.
- Đồng bộ trạng thái nhất quán; server là nguồn quyết định chính thức.
- Xử lý lỗi giao tiếp, đăng nhập trùng, người chơi bận và mất kết nối.
- Bảo vệ mật khẩu trong lưu trữ; nêu giới hạn hiện tại của TCP chưa có TLS.
- Khả năng cấu hình server để chạy qua LAN.

### 2.5. Luật chơi và quy tắc tính điểm

- 1v1; mỗi người bắt đầu 100 HP, 50 viên đạn; thời lượng tối đa 180 giây.
- Mỗi lần trúng gây 20 sát thương; đạn, va chạm và trạng thái do Server xử lý.
- Hết HP: đối thủ thắng; hết giờ/hết đạn: so HP, bằng HP thì hòa.
- Thắng +3 điểm, hòa +1 điểm mỗi người, thua +0.
- Ranking: điểm giảm dần, thắng giảm dần, hòa giảm dần, thua tăng dần, sau đó tên.
- Chơi lại chỉ bắt đầu khi cả hai cùng chọn Play Again; Exit khi đang đấu được xử lý là bỏ cuộc.

### 2.6. Use case

- Liệt kê tác nhân và các use case chính.
- Mô tả đặc tả ngắn cho các use case quan trọng: Đăng nhập, Thách đấu, Chấp nhận lời mời, Chơi trận, Xem kết quả, Xem ranking/history.

**Hình cần vẽ — Use case diagram:**

- Actor chính: Người chơi.
- Use case: Đăng ký/Đăng nhập, xem hồ sơ/lobby, thách đấu, phản hồi lời mời, vào phòng, chơi trận, xem kết quả, xem ranking, xem history, đăng xuất.
- Có thể nhóm hoặc dùng `include`/`extend` cho các luồng phụ; không đưa chi tiết message vào use case.

**Bảng nên có:** mã yêu cầu, mô tả, mức ưu tiên, trạng thái (đã triển khai/chưa triển khai).

## Chương 3. Phân tích và thiết kế hệ thống

### 3.1. Kiến trúc tổng thể

- Mô tả Client Swing, TCP Server và MySQL.
- Trách nhiệm từng thành phần; đường đi dữ liệu giữa các thành phần.

**Hình 3.1 — Sơ đồ kiến trúc/deployment:**

- Hai hoặc nhiều Client trên máy người chơi; một Server; MySQL.
- Ghi rõ kết nối TCP Socket Client–Server và JDBC Server–MySQL.
- Thể hiện port server (mặc định 5000), không vẽ Client kết nối thẳng Database.

### 3.2. Phân rã thành phần và đa luồng

- Client: UI, controller, TCP connection, protocol, model.
- Server: acceptor, worker/session, auth, lobby/challenge, room, game, persistence.
- Luồng accept; worker xử lý từng client; writer gửi message; scheduler cho timeout/countdown/game tick.

**Hình 3.2 — Component diagram (hoặc gộp vào kiến trúc):**

- Chỉ thể hiện module và phụ thuộc chính; không vẽ trùng hoàn toàn với deployment.

### 3.3. Thiết kế các luồng nghiệp vụ

**Hình 3.3 — Activity diagram luồng thách đấu đến kết quả:**

- Đăng nhập → chọn người chơi → gửi lời mời → nhánh Reject/Cancel/Timeout hoặc Accept.
- Accept → tạo phòng → countdown → bắt đầu trận → kết thúc → lưu kết quả → Play Again hoặc về Lobby.
- Dùng swimlane Người chơi A, Server, Người chơi B nếu cần làm rõ trách nhiệm.

**Hình 3.4 — State diagram trạng thái người chơi:**

- `ONLINE` → `WAITING` khi có lời mời/phòng chờ.
- `WAITING` → `ONLINE` khi Reject/Cancel/timeout hoặc phòng bị hủy.
- `WAITING` → `PLAYING` khi countdown hoàn tất.
- `PLAYING` → `ONLINE` khi trận/phòng kết thúc.
- Nhánh disconnect/offline và ghi chú trạng thái offline không xuất hiện trong roster.

### 3.4. Sequence diagram

Vẽ message đúng thứ tự và đúng tên theo protocol; dùng `alt` cho nhánh lỗi, `loop` cho lặp, `opt` cho tùy chọn.

**Hình 3.5 — Đăng nhập:**

- Lifeline: Người chơi, `LoginFrame`, `ClientController`, `ClientConnection`, `ClientHandler`, `AccountService`, `JdbcAccountRepository`/MySQL, `PlayerSessionManager`.
- Luồng thành công: `LOGIN_REQUEST` → xác thực → tạo session `ONLINE` → `LOGIN_SUCCESS` và `PLAYER_LIST`.
- Nhánh sai thông tin hoặc tài khoản đang đăng nhập ở kết nối khác.

**Hình 3.6 — Thách đấu, Accept và vào phòng:**

- Lifeline: Client A/Người chơi A, `ClientHandler`, `ChallengeManager`, `PlayerSessionManager`, `RoomManager`, `GameStateManager`, Client B/Người chơi B.
- `CHALLENGE` → `CHALLENGE_PENDING`/`CHALLENGE_RECEIVED` → `ACCEPT` → tạo phòng → `ROOM_STATE` countdown 3–2–1 → `MATCH_START`.
- Nhánh `REJECT`, `CHALLENGE_CANCEL`, timeout, disconnect và lỗi ID/trạng thái.

**Hình 3.7 — Gameplay, kết quả và chơi lại:**

- Lifeline: hai người chơi/Client, `ClientConnection`, `ClientHandler`, `GameStateManager`, `AccountService`, `MatchHistoryRepository`, MySQL, `PlayerSessionManager`.
- `loop`: input `MOVE_LEFT`/`MOVE_RIGHT`/`SHOOT` → Server cập nhật → `GAME_STATE`; có thể phát `HIT_EFFECT`.
- `alt`: hết HP, hết giờ, hết đạn, Exit/disconnect; xác định winner/draw, cập nhật điểm/thống kê, ghi history, gửi `GAME_OVER`.
- `opt`/`alt` cho Play Again: phải cả hai đồng ý mới reset và gửi `MATCH_START`.





### 3.5. Thiết kế lớp

**Hình 3.10 — Class diagram Client:**

- Nhóm lớp: `ClientApp`, `ClientController`, `ClientConnection`, UI frame/panel, model, `Protocol`.
- Thể hiện quan hệ khởi tạo/sử dụng/chứa và `ClientController` làm listener.
- Có thể chọn lớp tiêu biểu; không cần getter/setter hoặc mọi lớp phụ trợ.

**Hình 3.11 — Class diagram Server:**

- Nhóm lớp: `ServerMain`, `ClientHandler`, `PlayerSessionManager`, `AccountService`, `ChallengeManager`, `RoomManager`, `GameStateManager`, repository/config, record và `Protocol`.
- Thể hiện quan hệ khởi tạo, phụ thuộc và các interface quan trọng.

### 3.6. Thiết kế model và trạng thái

- Cấu trúc dữ liệu hồ sơ, người chơi, phòng, trạng thái game, ranking/history.
- Trạng thái session lưu trong RAM; dữ liệu tài khoản/thống kê/lịch sử lưu lâu dài trong Database.

## Chương 4. Thiết kế giao thức mạng

### 4.1. Kết nối TCP

- Client mở socket tới host/port; mặc định host `127.0.0.1`, port 5000 nếu không cấu hình.
- Keep-alive, timeout kết nối, xử lý EOF và đóng kết nối.

### 4.2. Định dạng và framing message

- Message và các trường chuỗi dùng `DataInputStream.readUTF`/`DataOutputStream.writeUTF`.
- `PLAYER_LIST`, `RANKING_RESPONSE`, `HISTORY_RESPONSE` có trường số lượng đọc/ghi bằng int; mô tả chính xác từng payload theo code.
- Các trường số trong payload UTF được biểu diễn thành chuỗi thập phân; không gửi Java object qua socket.

### 4.3. Nhóm message

- Authentication: `REGISTER_REQUEST`, `LOGIN_REQUEST` và phản hồi.
- Presence/lobby: `PLAYER_LIST`, `PROFILE_UPDATE`, `CHALLENGE_*`, `ACCEPT`, `REJECT`.
- Room/game: `ROOM_STATE`, `ROOM_LEAVE`, `MATCH_START`, `MOVE_LEFT`, `MOVE_RIGHT`, `SHOOT`, `GAME_STATE`, `HIT_EFFECT`, `GAME_OVER`, `EXIT`, `PLAY_AGAIN`.
- Tra cứu/lỗi: ranking, history, `LOBBY_ERROR`, `SERVER_ERROR`, logout.

**Bảng 4.1 — Bảng protocol:**

- Cột đề xuất: Message, hướng, payload theo thứ tự, thời điểm gửi, phản hồi/nhánh lỗi.
- Ghi chính xác format thực tế; không đưa message mới chỉ có trong tài liệu yêu cầu nhưng chưa được code xử lý như chức năng đã hoàn thành.

### 4.4. Xử lý lỗi giao thức

- Message không hỗ trợ, payload sai, chưa đăng nhập, tài khoản trùng phiên, người chơi bận, ID lời mời/phòng không hợp lệ.
- Mất kết nối, hàng đợi gửi đầy và cleanup tài nguyên.

## Chương 5. Thiết kế và cài đặt cơ sở dữ liệu

### 5.1. Công nghệ và cấu hình

- MySQL, JDBC, Connector/J.
- Cấu hình `TANK_DB_URL`, `TANK_DB_USER`, `TANK_DB_PASSWORD`; không ghi mật khẩu thật vào báo cáo.

### 5.2. Thiết kế dữ liệu

**Hình 5.1 — ERD:**

- `player_accounts`: tài khoản, email, password salt/hash, thống kê.
- `matches`: thời điểm, trạng thái, winner/loser/draw.
- `match_players`: người tham gia, điểm trong trận, kết quả.
- Thể hiện PK/FK, quan hệ 1-n và ràng buộc unique.

**Bảng 5.1 — Mô tả bảng/cột:** tên cột, kiểu dữ liệu, PK/FK/unique, nullable, ý nghĩa.

### 5.3. Bảo vệ tài khoản và truy cập dữ liệu

- Mật khẩu được salt ngẫu nhiên và băm PBKDF2-HMAC-SHA256; không lưu mật khẩu dạng rõ.
- Repository dùng prepared statements; phân biệt dữ liệu trong RAM và dữ liệu lưu MySQL.

### 5.4. Ghi và truy vấn kết quả

- Cập nhật thống kê người chơi sau trận.
- Lưu bản ghi trận và hai bản ghi người tham gia.
- Truy vấn ranking theo thứ tự điểm/thắng/hòa/thua/tên; truy vấn history theo người chơi và thời gian.
- Nêu cách xử lý lỗi JDBC theo đúng hành vi source; không khẳng định các thao tác tách rời là một giao dịch nguyên tử nếu code không đảm bảo điều đó.

## Chương 6. Cài đặt chức năng

### 6.1. Cấu trúc chương trình

- Sơ đồ thư mục/package Client và Server.
- Vai trò các lớp chính; chọn đoạn code ngắn minh họa thay vì chép dài.

### 6.2. Client

- Khởi động và cấu hình host/port.
- Đăng ký/đăng nhập; controller điều phối UI và kết nối.
- Lobby, hồ sơ, lời mời, phòng chờ.
- Giao diện gameplay hiển thị snapshot Server; phím điều khiển gửi input.
- Ranking/history và giao diện kết quả/chơi lại.

### 6.3. Server

- Accept kết nối và giới hạn worker.
- Xác thực, tạo session và cập nhật roster.
- Challenge, trạng thái người chơi và countdown phòng.
- Game loop authoritative: di chuyển, đạn, va chạm, HP, ammo, timer.
- Xác định kết quả, ghi thống kê/history, ranking và rematch.

### 6.4. Giao diện và luồng sử dụng

- Chèn ảnh thực tế: Login, Register, Lobby, dialog lời mời, phòng chờ, Gameplay, Game Over, Ranking, History.
- Mỗi ảnh có số thứ tự, caption và một đoạn giải thích ngắn.
- Không dùng ảnh preview/mock để thay bằng chứng luồng chạy thật nếu chưa ghi rõ nguồn ảnh.

### 6.5. Phân công và tích hợp

- Bảng thành viên, nhiệm vụ được giao, phần đã hoàn thành và minh chứng source.
- Mô tả protocol/model dùng chung và cách tích hợp Client–Server.

## Chương 7. Kiểm thử và đánh giá

### 7.1. Môi trường kiểm thử

- Hệ điều hành, JDK, MySQL/driver, cấu hình server/client, mạng và loại kho dữ liệu.
- Ghi rõ mỗi test dùng database thật hay repository giả trong bộ nhớ.

### 7.2. Phương pháp và phạm vi

- Kiểm thử protocol/TCP, lobby, UI, gameplay, JDBC, LAN; đánh dấu phần đã chạy và chưa chạy.
- Nêu lệnh/script test đã dùng.

### 7.3. Test case

**Bảng 7.1 — Kết quả kiểm thử:**

- Cột đề xuất: Mã, chức năng, tiền điều kiện, thao tác/dữ liệu vào, kết quả mong đợi, kết quả thực tế, Pass/Fail, ghi chú.
- Bao phủ đăng ký/đăng nhập, lời mời hợp lệ và cạnh tranh, Reject/Cancel/timeout, countdown, disconnect, trận đấu và kết quả, ranking/history.
- Chỉ ghi test case đã thực hiện; trường hợp chưa chạy ghi “Chưa kiểm thử”, không tự đánh dấu Pass.

### 7.4. Ảnh và phân tích kết quả

- Ảnh màn hình của các luồng đã chạy; ghi điều kiện, số client và môi trường.
- Tổng hợp số test Pass/Fail/Chưa chạy; giải thích lỗi và giới hạn.
- Không suy ra đã kiểm thử MySQL, đa máy LAN hay gameplay end-to-end từ test lobby/UI.

## Chương 8. Hướng dẫn cài đặt và sử dụng

### 8.1. Yêu cầu cài đặt

- JDK, MySQL, driver, port mạng và quyền firewall.

### 8.2. Cài đặt Database và cấu hình Server

- Chạy schema/migration.
- Tạo tài khoản MySQL riêng; đặt biến môi trường hoặc file cấu hình riêng không chia sẻ.
- Mô tả các tham số cần điền nhưng không đưa password thật.

### 8.3. Khởi chạy ứng dụng

- Chạy server, sau đó chạy client cùng máy hoặc máy khác với `<server-ip> 5000`.
- Cách kiểm tra kết nối và các lỗi cấu hình thường gặp.

### 8.4. Hướng dẫn người chơi

- Đăng ký, đăng nhập, xem lobby/hồ sơ.
- Thách đấu, phản hồi lời mời, vào phòng và điều khiển.
- Xem kết quả, chơi lại/thoát, xem ranking/history, đăng xuất.

## Chương 9. Đánh giá, hạn chế và hướng phát triển

### 9.1. Kết quả đạt được

- Đối chiếu mục tiêu và yêu cầu với trạng thái triển khai có bằng chứng.

### 9.2. Hạn chế

- Nêu giới hạn hiện tại: TCP chưa có TLS; mức độ kiểm thử JDBC/MySQL, LAN và tải nhiều client phụ thuộc kết quả thực tế.
- Nêu các chức năng hoặc tình huống chưa hoàn thiện nếu còn.

### 9.3. Hướng phát triển

- TLS, reconnect, kiểm thử tải, mở rộng khả năng giám sát/cấu hình và cải thiện trải nghiệm.
- Phân biệt đề xuất tương lai với chức năng đã làm.

## Chương 10. Kết luận

### 10.1. Tổng kết

- Tóm tắt sản phẩm và kiến thức áp dụng: TCP, client-server, đa luồng, đồng bộ trạng thái, JDBC.

### 10.2. Tự đánh giá và bài học

- Mức độ hoàn thành, bài học kỹ thuật/làm việc nhóm và công việc còn lại.

## Tài liệu tham khảo

- Giáo trình/tài liệu môn học, tài liệu Java/JDBC/MySQL và nguồn tham khảo thực sự đã sử dụng.
- Thống nhất kiểu trích dẫn; không liệt kê nguồn chưa đọc hoặc chưa dùng.

## Phụ lục

- Bảng protocol đầy đủ nếu Chương 4 cần rút gọn.
- Các test case chi tiết, log/kết quả chạy, hướng dẫn chạy.
- Đoạn code tiêu biểu, schema/migration, ảnh bổ sung.
- Sơ đồ lớn có thể đặt ở phụ lục, nhưng trong nội dung chính vẫn cần dẫn chiếu và giải thích.

## Thứ tự ưu tiên nếu cần giới hạn số trang/hình

1. Kiến trúc/deployment và ERD.
2. Use case và class diagram Server.
3. Sequence đăng nhập, thách đấu vào phòng, gameplay/kết quả.
4. State diagram hoặc activity diagram; tránh để hai hình lặp cùng một luồng ở cùng mức chi tiết.
5. Ảnh UI và bảng test case có kết quả thực tế.

## Thông tin nhóm cần bổ sung trước khi chốt báo cáo

- Thành viên, mã sinh viên, lớp, giảng viên, phân công thực tế.
- Môi trường chạy, cấu hình MySQL/LAN và kết quả kiểm thử thực tế.
- Ảnh minh chứng của nhóm; xác nhận các chức năng đã tích hợp end-to-end.
