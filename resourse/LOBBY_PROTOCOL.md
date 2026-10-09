# Lobby & Challenge — phần Người 2

## Chạy và bố cục

- Client/Server Java thuần, JDK 17+, Swing và TCP. Build/run bằng các file `.bat`; script tự lấy tất cả source trong `src`.
- Server thật vẫn cần cấu hình MySQL theo README. Client dùng `run-client.bat <host> <port>`, mặc định `127.0.0.1:5000`.
- Lobby: thanh username/điểm/thắng/thua/hòa ở trên; menu trái; bảng tên/điểm/trạng thái/thao tác. Username mở thông tin người chơi; bảng bỏ tài khoản của mình.
- Lời mời: tên người gửi và nút Chấp nhận/Từ chối. Đóng dialog tương đương Reject. Người gửi có nút Hủy lời mời.
- Phòng chờ: hai người chơi ở hai bên, mã phòng và countdown ở giữa, nút Thoát ở dưới. Server tự đếm 3–2–1; không có nút Sẵn sàng.

## Trạng thái và xử lý

| Trạng thái Server | Hiển thị | Có thể thách đấu |
|---|---|---|
| `ONLINE` | Đang chờ | Có |
| `WAITING` | Đang chuẩn bị | Không |
| `PLAYING` | Đang chơi | Không |
| `OFFLINE` | Không có trong danh sách | Không |

Server là nguồn xác định trạng thái. Một người chỉ có một lời mời hoặc một phòng. Lời mời hết hạn sau 30 giây; dùng UUID cho lời mời/phòng. Người gửi giữ slot 1, người nhận slot 2. Accept chỉ dành cho người nhận; Cancel chỉ dành cho người gửi. ID trùng, cũ hoặc không thuộc người chơi không tạo phòng mới, trả `LOBBY_ERROR` tại Lobby.

Accept đóng lời mời với reason `ACCEPTED`, giữ hai bên `WAITING` và tạo phòng. Server gửi `ROOM_STATE` ở mốc 3, 2, 1, rồi chuyển cả hai sang `PLAYING` và gửi đúng một `MATCH_START`. Client không tự giảm bộ đếm. Reject/Cancel/timeout trả cả hai về `ONLINE`. Thoát/disconnect khi đếm ngược hủy phòng và scheduled task; người còn kết nối trở về Lobby. Rời phòng sau khi bắt đầu gọi callback module gameplay/result rồi đóng phòng; phần Người 2 không chấm điểm.

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
| `ROOM_STATE` | S → cả hai | roomId, player1, player2, secondsRemaining |
| `ROOM_LEAVE` | C → S | roomId |
| `ROOM_CLOSED` | S → cả hai | roomId, reason |
| `MATCH_START` | S → cả hai | roomId, player1, player2 |
| `LOBBY_ERROR` | S → người yêu cầu | reason |

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

Không đăng ký thì Ranking/History bị vô hiệu hóa với tooltip “Chưa tích hợp”; MATCH_START giữ màn hình thông báo chờ gameplay, cho phép Thoát. `ClientConnection.Listener` đã có callback cho các sự kiện Lobby/Room. Chỉ có reader của `ClientConnection` được đọc socket; khi thêm gameplay cần mở rộng reader này và payload ở hai bên.

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

Listener được gọi trong khóa chuyển trạng thái; chỉ đăng ký state/đưa công việc vào hàng đợi, không chạy game loop hoặc JDBC chặn trong callback. Nếu callback bắt đầu ném exception, phòng bị hủy và không phát MATCH_START. Dùng `sessions.sendTo(...)` cho message phù hợp; transport chỉ ghi UTF nên gameplay cần bổ sung codec khi dùng định dạng khác.

`rooms.finishRoom(roomId, reason)` giải phóng phòng và đưa người còn kết nối về Lobby; gọi lặp lại an toàn. Module kết quả chỉ gọi sau khi hoàn tất kết quả và quyết định rời phòng; nếu hai bên Play Again thì module tích hợp cần thêm luồng reset trận, không tự giải phóng phòng trước đó. Lobby chưa hỗ trợ rematch, chưa lưu lịch sử và không cập nhật điểm.

## Kiểm thử

Tại workspace root:

```powershell
server\run-lobby-tests.bat
client\run-ui-checks.bat
```

Kiểm thử TCP dùng handler/kết nối production, kho tài khoản bộ nhớ, port tự chọn và timeout lời mời 2 giây để chạy nhanh. Bao phủ Accept/Reject/Cancel/expiry, ID cũ/không thuộc người chơi, self/offline/busy, lời mời cạnh tranh, hai bộ đếm cùng 3–2–1, start một lần, thoát/disconnect, đăng ký và logout. Không cần/tác động tới MySQL.

Kiểm thử Swing thực hiện trên EDT: bỏ bản thân khỏi bảng, khóa nút theo trạng thái, chọn đúng username, chặn bấm lặp và đóng lời mời bằng Reject. Ảnh preview được tạo trong `../client/out/preview` (build artifact được gitignore). Kiểm thử JDBC/LAN và gameplay thật cần môi trường/module tương ứng.
