# Tank Game: Java Client / Server

Phần **Người 2 — Lobby & Challenge** đã có: Lobby theo bố cục mẫu, gửi/hủy lời mời, Accept/Reject, phòng chờ và đếm ngược 3–2–1 do Server điều khiển. Hướng dẫn protocol và điểm tích hợp Người 3/4: [docs/LOBBY_PROTOCOL.md](resourse/LOBBY_PROTOCOL.md).

`client/` va `server/` la hai ung dung Java doc lap, khong dung Maven. Client dung Swing va TCP; server xu ly nhieu client dong thoi va luu tai khoan trong MySQL. Source root la `src/`, package duoc chia theo trach nhiem.

```text
client/src/tank/client/
	app/          diem khoi dong
	controller/   dieu phoi UI va network
	network/      TCP socket va protocol I/O
	protocol/     ten message wire protocol
	model/        model nguoi choi
	ui/           cac cua so Swing

server/src/tank/server/
	app/          diem khoi dong TCP server
	auth/         validate tai khoan, hash va xac thuc password
	persistence/  JDBC repository, account record va cau hinh MySQL
	protocol/     ten message wire protocol
	session/      worker, session va presence
	lobby/        loi moi, chap nhan/tu choi, het han
	room/         phong cho, dem nguoc, ban giao gameplay

server/test/tank/server/
	test tich hop protocol TCP
```

## Yeu cau

- JDK 17 tro len.
- MySQL 8 tro len.
- Mo port TCP 5000 tren firewall cua may chay server neu ket noi qua LAN.
- Server can MySQL Connector/J 9.4.0. `run-server.bat` se tai driver vao `server/lib/` neu chua co.
- Client chi can JDK, khong can MySQL hay server local.

## MySQL

Tao database va bang cho lan cai dat moi:

```powershell
mysql -u root -p -e "source server/database/schema.sql"
```

Neu da co database theo schema cu, chay migration nang cap (co the chay lai an toan):

```powershell
mysql -u root -p -e "source server/database/migrate_player_profile.sql"
```

Tao user rieng cho ung dung, khong dung root de chay game:

```sql
CREATE USER 'tank_app'@'%' IDENTIFIED BY 'dat-mat-khau-rieng';
GRANT SELECT, INSERT, CREATE, ALTER, INDEX ON tank_game.* TO 'tank_app'@'%';
```

Tren may chay server, mo `server/.env` va dien password cua user `tank_app`:

```dotenv
TANK_DB_URL=jdbc:mysql://127.0.0.1:3306/tank_game?serverTimezone=UTC
TANK_DB_USER=tank_app
TANK_DB_PASSWORD=mat-khau-da-tao-o-mysql
```

Bảng `player_accounts` lưu username, email, hash mật khẩu và các thống kê `matches_played`, `score`, `wins`, `losses`, `draws` để phục vụ Player Information và Online Player. `auth/AccountService` validate username/email và xử lý PBKDF2-HMAC-SHA256; `persistence/JdbcAccountRepository` dùng prepared statements, đọc hồ sơ và có thao tác cập nhật thống kê sau khi module trận đấu ghi kết quả. Khi nâng từ schema cũ, migration thêm email ở dạng nullable để giữ tài khoản cũ chưa có email; tài khoản đăng ký mới bắt buộc email và email phải duy nhất.

Trạng thái kết nối `ONLINE`/`WAITING`/`PLAYING` được giữ trong session RAM, không lưu MySQL. `ONLINE` là rảnh; `WAITING` là đang có lời mời hoặc phòng chờ; `PLAYING` là đã bắt đầu trận. Người chơi offline được loại khỏi roster. Các số liệu trận/điểm mới là dữ liệu lâu dài nên lưu trong MySQL.

`server/.env` la file rieng tren may chay server; tao file theo mau o tren va khong commit/chia se. Bien moi truong cua he dieu hanh, neu duoc dat, se duoc uu tien hon gia tri trong `.env`.

## Chay tren Windows

1. Mo terminal tai `server/`, chay `run-server.bat`.
2. Tren may server, lay dia chi IP LAN cua may do.
3. Trên cùng máy, chạy `client/run-client.bat`. Trên máy khác, chạy `client/run-client.bat <server-ip> 5000`; IP/port không nằm trên form login để giao diện khớp PDF.
4. Sau login, Lobby hiển thị username, điểm, thắng/thua/hòa; menu bên trái và bảng đối thủ có nút Thách đấu. Click username để xem Player Information. Đối thủ chấp nhận thì cả hai vào phòng chờ, Server đếm 3–2–1 rồi bắt đầu. Khi chưa ghép gameplay, màn hình ghi rõ đang chờ tích hợp; có thể Thoát để về Lobby. Xếp hạng/Lịch sử đang vô hiệu hóa đến khi Người 4 gắn callback.

## Kiểm thử Lobby và giao diện

Chạy tại workspace root, không cần MySQL hoặc Server đang chạy:

```powershell
server\run-lobby-tests.bat
client\run-ui-checks.bat
```

`LobbyIntegrationTest` dùng handler Server và kết nối Client thực qua TCP với kho tài khoản trong bộ nhớ. Bao phủ 3 Client, cạnh tranh lời mời, timeout, countdown, thoát/mất kết nối, đăng ký và đăng xuất. `LobbyUiCheck` kiểm tra nút Swing trên EDT và xuất ảnh preview vào `client/out/preview/` mà không mở cửa sổ tương tác. Đây không phải kiểm thử JDBC/MySQL.

## Protocol ban dau

Client và server dùng `DataInputStream.readUTF` / `DataOutputStream.writeUTF`. LOGIN_SUCCESS gửi username, matches played, score, wins, losses, draws, status. PLAYER_LIST gửi số lượng rồi username, score, status cho từng người.

Message hiện tại gồm `REGISTER_REQUEST`, `REGISTER_SUCCESS`, `REGISTER_FAIL`, `LOGIN_REQUEST`, `LOGIN_SUCCESS`, `LOGIN_FAIL`, `PLAYER_LIST`, `LOGOUT_REQUEST`, `LOGOUT_SUCCESS`, và `SERVER_ERROR`. `LOGIN_SUCCESS` mang username, matches played, score, wins, losses, draws, status; `PLAYER_LIST` mang username, score, status. Khi đổi payload, cập nhật cả hai file `Protocol.java` trong package `protocol/`.

Các message Lobby/Challenge/Room đã được bổ sung; payload đầy đủ nằm trong [LOBBY_PROTOCOL.md](resourse/LOBBY_PROTOCOL.md). Các tên gameplay khác trong `Protocol.java` hiện chỉ là khai báo, chưa có xử lý.

## Ghi chu

- Có phần tài khoản và Lobby/Challenge/Room; gameplay, kết quả, ranking/history thuộc các module tiếp theo.
- Dùng `RoomManager.GameListener` để nhận bắt đầu/rời trận, và `RoomManager.finishRoom(...)` khi module kết quả giải phóng phòng. Phần Lobby không ghi kết quả hay cộng điểm.
- Server dang dung MySQL/JDBC; thong tin ket noi lay tu cac bien moi truong `TANK_DB_URL`, `TANK_DB_USER`, `TANK_DB_PASSWORD`.
- TCP thuong khong ma hoa. Chi nen dung tren mang tin cay khi chua bo sung TLS.
