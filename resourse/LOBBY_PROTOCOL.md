# Lobby & Challenge — phần Người 2

## Hồ sơ công khai và lịch sử người chơi

- Bấm một hàng trong lobby hoặc bảng xếp hạng để xem hồ sơ và lịch sử; cột Thách đấu giữ hành vi gửi lời mời. Có thể chọn hàng và nhấn Enter để xem hồ sơ.
- Client đã đăng nhập gửi `PLAYER_DETAILS_REQUEST`, tiếp theo là `username` bằng `writeUTF`.
- Server trả `PLAYER_DETAILS_RESPONSE`: `username`, `matchesPlayed`, `score`, `wins`, `losses`, `draws`, `status`, `historyCount`; sau đó mỗi trận gồm `matchId`, `endedAt`, `opponent`, `playerScore`, `opponentScore`, `result`. Tất cả trường (kể cả số và count) dùng `writeUTF`, như các response thống kê hiện có.
- Hồ sơ chỉ chứa tên, thống kê và trạng thái công khai, kể cả tài khoản offline. Lịch sử tối đa 100 trận mới nhất, điểm và WIN/LOSS/DRAW theo người được yêu cầu.
- Lỗi trả `PLAYER_DETAILS_ERROR`, `requestedUsername`, `message`; không làm đóng socket. Client hiển thị lỗi và Thử lại trong cửa sổ hồ sơ, bỏ qua response khi đã đóng hoặc đổi người.
- Không thay đổi schema MySQL hay `.env`. Cần build và khởi động lại cả client và server để dùng các message mới; các message cũ giữ nguyên định dạng.

## Tỉ lệ sân đấu và va chạm

- Client vẽ sân theo tọa độ server 800×420 rồi thu phóng đồng đều để vừa vùng còn lại dưới HUD gọn ở phía trên; có khoảng trống ở cạnh nếu cửa sổ khác tỉ lệ.
- Xe người 1 có gốc y=370, xe người 2 y=10. Hai xe nằm sát hai mép sân mà vẫn hiện đủ hình. Vùng thân chịu sát thương là x±21,42, từ y+8,6425 đến y+32,3575 trong tọa độ trận; server quyết định trúng/trượt.
- Hình xe, đạn và hiệu ứng dùng cùng phép biến đổi tọa độ. Góc nhìn người 2 phản chiếu toàn bộ cảnh theo chiều dọc, không đổi luật va chạm theo độ phân giải màn hình.

## Chạy và bố cục

- Client/Server Java thuần, JDK 17+, Swing và TCP. Build/run bằng các file `.bat`; script tự lấy tất cả source trong `src`.
- Server thật vẫn cần cấu hình MySQL theo README. Client dùng `run-client.bat <host> <port>`, mặc định `127.0.0.1:5000`.
- Lobby: thanh username/điểm/thắng/thua/hòa ở trên; menu trái; bảng tên/điểm/trạng thái/thao tác. Username mở thông tin người chơi; bảng bỏ tài khoản của mình.
- Chỉ Lobby hiện thanh tài khoản và menu trái. Xếp hạng và Lịch sử có nút Về Lobby; phòng chờ, trận đấu và kết quả dùng toàn bộ vùng nội dung.
- Lời mời: tên người gửi và nút Chấp nhận/Từ chối. Đóng dialog tương đương Reject. Người gửi có nút Hủy lời mời.
- Phòng chờ: hai người chơi ở hai bên, trạng thái Sẵn sàng từng người, countdown ở giữa, nút Sẵn sàng và Thoát ở dưới.

## Trạng thái và xử lý

| Trạng thái Server | Hiển thị | Có thể thách đấu |
|---|---|---|
| `ONLINE` | Đang chờ | Có |
| `WAITING` | Đang chuẩn bị | Không |
| `PLAYING` | Đang chơi | Không |
| `OFFLINE` | Không có trong danh sách | Không |

Server là nguồn xác định trạng thái. Một người chỉ có một lời mời hoặc một phòng. Lời mời hết hạn sau 30 giây; dùng UUID cho lời mời/phòng. Người gửi giữ slot 1, người nhận slot 2. Accept chỉ dành cho người nhận; Cancel chỉ dành cho người gửi. ID trùng, cũ hoặc không thuộc người chơi không tạo phòng mới, trả `LOBBY_ERROR` tại Lobby.

Accept đóng lời mời với reason `ACCEPTED`, giữ hai bên `WAITING` và tạo phòng với countdown 10 giây. Mỗi người có thể gửi tín hiệu `ROOM_READY` một lần. Khi cả hai đã sẵn sàng, server rút đồng hồ về 3 giây nếu còn hơn 3; nếu còn 3 giây trở xuống thì không tăng lại. Hết 10 giây trận tự bắt đầu dù một hoặc cả hai người chưa sẵn sàng. Server chuyển cả hai sang `PLAYING` rồi gửi đúng một `MATCH_START`. Client không tự giảm bộ đếm. Reject/Cancel/timeout trả cả hai về `ONLINE`. Thoát/disconnect khi đếm ngược hủy phòng và scheduled task; người còn kết nối trở về Lobby. Rời phòng sau khi bắt đầu gọi callback module gameplay/result rồi đóng phòng; phần Người 2 không chấm điểm.

Mọi chuyển trạng thái dùng chung monitor của `PlayerSessionManager`. Hai người được cập nhật trạng thái theo nhóm trước khi broadcast một snapshot roster. Mỗi socket có một reader và một writer; hàng đợi tối đa 256 message, đóng kết nối nếu không theo kịp. Lobby không giữ khóa trong lúc ghi TCP. LOGOUT được xác nhận trước khi writer đóng socket; cleanup session/phòng/lời mời chạy một lần. Không khôi phục phòng sau reconnect: người chơi đăng nhập lại ở Lobby.

## Wire protocol

Tên message và mỗi trường bên dưới được ghi/đọc theo thứ tự bằng `writeUTF/readUTF`; số trong message Lobby là chuỗi thập phân. Giữ framing có sẵn: `PLAYER_LIST` có số lượng bằng `writeInt/readInt`, rồi ba trường UTF username/score/status cho từng người. Không gửi Java object qua socket.

| Message | Hướng | Payload theo thứ tự |
|---|---|---|
| `CHALLENGE` | C → S | targetUsername |
| `CHALLENGE_PENDING` | S → người gửi | challengeId, opponentUsername, timeoutSeconds |
| `CHALLENGE_RECEIVED` | S → người nhận | challengeId, opponentUsername, timeoutSeconds |
| `ACCEPT` | C → S | challengeId |
| `REJECT` | C → S | challengeId |
| `CHALLENGE_CANCEL` | C → S | challengeId |
| `CHALLENGE_CLOSED` | S → cả hai | challengeId, reason |
| `ROOM_STATE` | S → cả hai | roomId, player1, player2, secondsRemaining, player1Ready, player2Ready |
| `ROOM_LEAVE` | C → S | roomId |
| `ROOM_READY` | C → S | roomId |
| `ROOM_CLOSED` | S → cả hai | roomId, reason |
| `MATCH_START` | S → cả hai | roomId, player1, player2 |
| `MATCH_RESULT` | S → cả hai | roomId, player1AwardedPoints, player1TotalScore, player2AwardedPoints, player2TotalScore |
| `LOBBY_ERROR` | S → người yêu cầu | reason |

`MATCH_RESULT` được gửi sau khi Server thử lưu thống kê tài khoản và trước `GAME_OVER`. Điểm nhận theo kết quả là thắng `3`, hòa `1`, thua `0`; tổng điểm là giá trị backend trả về sau cập nhật. Tổng điểm bằng `-1` nghĩa là không đọc/lưu được và Client hiển thị “chưa đồng bộ”. `GAME_OVER` giữ payload cũ để thông báo kết quả và trạng thái chờ chơi lại.

`reason` là thông báo tiếng Việt để hiển thị; riêng `ACCEPTED` là dấu hiệu đóng lời mời để chuyển phòng. Đọc hết payload trước khi từ chối do chưa đăng nhập/sai trạng thái. Hai file `Protocol.java` phải được cập nhật cùng nhau; chưa tách `common` trong phần việc này.

## Điểm tích hợp Người 3/4

Client: đăng ký callback trên Swing EDT, ví dụ tại `ClientApp` trước `showLogin()`:

```java
ClientController controller = new ClientController(host, port);
controller.setMatchStartHandler((roomId, player1, player2) -> {
    // Mở Game UI của Người 3. Server vẫn quyết định state và kết quả.
});
controller.setNavigationHandlers(
    () -> { /* Mở Ranking của Người 4. */ },
    () -> { /* Mở History của Người 4. */ }
);
controller.showLogin();
```

Không đăng ký thì Ranking/History bị vô hiệu hóa với tooltip “Chưa tích hợp”; MATCH_START giữ màn hình thông báo chờ gameplay, cho phép Thoát. `ClientConnection.Listener` có callback cho Lobby, Room và gameplay; chỉ reader của `ClientConnection` đọc socket. `MATCH_RESULT` cập nhật điểm kết quả trước khi `GAME_OVER` mở thẻ thắng/thua.

Server: gắn listener ngay sau khi tạo `RoomManager` tại `ServerMain`:

```java
rooms.setGameListener(new RoomManager.GameListener() {
    @Override public void onMatchStart(RoomManager.RoomInfo room) {
        // Đăng ký trận authoritative với roomId, player1, player2.
    }
    @Override public void onPlayerLeft(RoomManager.RoomInfo room, String username) {
        // Người 3/4 xử lý bỏ cuộc, lưu kết quả/thống kê theo luật game.
    }
});
```

Listener được gọi trong khóa chuyển trạng thái; `GameStateManager` bắt đầu mô phỏng server-authoritative và xử lý kết quả trận. Dùng `sessions.sendTo(...)` cho message phù hợp; gameplay hiện truyền từng trường dưới dạng UTF như mô tả ở trên.

`rooms.finishRoom(roomId, reason)` giải phóng phòng và đưa người còn kết nối về Lobby; gọi lặp lại an toàn. Module kết quả chỉ gọi sau khi hoàn tất kết quả và quyết định rời phòng; nếu hai bên Play Again thì module tích hợp cần thêm luồng reset trận, không tự giải phóng phòng trước đó. Lobby chưa hỗ trợ rematch, chưa lưu lịch sử và không cập nhật điểm.

## Kiểm thử

Tại workspace root:

```powershell
server\run-lobby-tests.bat
client\run-ui-checks.bat
```

Kiểm thử TCP dùng handler/kết nối production, kho tài khoản bộ nhớ, port tự chọn và timeout lời mời 2 giây. Bao phủ Accept/Reject/Cancel/expiry, ID cũ/không thuộc người chơi, self/offline/busy, lời mời cạnh tranh, countdown 10 giây tự bắt đầu, trạng thái sẵn sàng đồng bộ, rút về 3 giây, start một lần, thoát/disconnect, đăng ký và logout. Không cần/tác động tới MySQL.

Kiểm thử Swing thực hiện trên EDT: bỏ bản thân khỏi bảng, khóa nút theo trạng thái, chọn đúng username, chặn bấm lặp và đóng lời mời bằng Reject. Ảnh preview được tạo trong `../client/out/preview` (build artifact được gitignore). Kiểm thử JDBC/LAN và gameplay thật cần môi trường/module tương ứng.
