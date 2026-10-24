# Tank Game: Java Client / Server

Phần **Người 2 — Lobby & Challenge** đã có: Lobby theo bố cục mẫu, gửi/hủy lời mời, Accept/Reject, phòng chờ đếm ngược 10 giây, trạng thái Sẵn sàng và đếm ngược 3–2–1 do Server điều khiển. Hướng dẫn protocol và điểm tích hợp Người 3/4: [docs/LOBBY_PROTOCOL.md](resourse/LOBBY_PROTOCOL.md).

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
mysql -u root -p -e "source server/database/migrate_3nf.sql"
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

CSDL theo 3NF gồm ba bảng: `player_accounts` lưu thông tin tài khoản, `matches` lưu thời gian/trạng thái trận, `match_players` lưu điểm trong trận và kết quả WIN/LOSS/DRAW của mỗi người tham gia. Hồ sơ và xếp hạng được tổng hợp từ các trận COMPLETED (thắng 3 điểm xếp hạng, hòa 1, thua 0). Điểm trong trận khác với điểm xếp hạng. Trận và cả hai người tham gia được ghi trong cùng một transaction trước khi cập nhật hồ sơ trên client.

Với database cũ, dừng mọi game server và sao lưu đầy đủ bằng `mysqldump` trước khi chạy `migrate_3nf.sql` bằng tài khoản quản trị. Migration dừng trước khi bỏ cột nếu thống kê hoặc kết quả cũ lệch lịch sử; cần đối soát dữ liệu trước. MySQL DDL tự commit, vì vậy cần bản sao lưu để khôi phục khi có sự cố. Có thể chạy lại migration sau khi thành công. `schema.sql` dùng cho cài mới; CREATE TABLE IF NOT EXISTS không nâng cấp bảng đã tồn tại. Tài khoản cũ có thể giữ email NULL; đăng ký mới yêu cầu email duy nhất.

Kiểm thử JDBC: tạo một database thử nghiệm riêng có tên kết thúc bằng `_3nf_test`, áp dụng `schema.sql` vào database đó, rồi đặt `TANK_DB_URL` trỏ tới database thử nghiệm và chạy `tank.server.DatabaseNormalizationTest` với classpath `server/test-out` và MySQL Connector/J. Không dùng database thật cho kiểm thử này. Bộ kiểm thử kiểm tra thống kê, xếp hạng, lịch sử, CHECK và rollback khi ghi trận lỗi.

Trạng thái kết nối `ONLINE`/`WAITING`/`PLAYING` được giữ trong session RAM, không lưu MySQL. `ONLINE` là rảnh; `WAITING` là đang có lời mời hoặc phòng chờ; `PLAYING` là đã bắt đầu trận. Người chơi offline được loại khỏi roster. Các số liệu trận/điểm mới là dữ liệu lâu dài nên lưu trong MySQL.

`server/.env` la file rieng tren may chay server; tao file theo mau o tren va khong commit/chia se. Bien moi truong cua he dieu hanh, neu duoc dat, se duoc uu tien hon gia tri trong `.env`.

## Chay tren Windows

1. Mo terminal tai `server/`, chay `run-server.bat`.
2. Tren may server, lay dia chi IP LAN cua may do.
3. Trên cùng máy, chạy `client/run-client.bat`. Trên máy khác, chạy `client/run-client.bat <server-ip> 5000`; IP/port không nằm trên form login để giao diện khớp PDF.
4. Sau login, Lobby hiển thị username, điểm, thắng/thua/hòa; menu bên trái và bảng đối thủ có nút Thách đấu. Click username để xem Player Information. Xếp hạng và Lịch sử có nút Về Lobby; các thanh của Lobby ẩn ở mọi màn khác. Đối thủ chấp nhận thì cả hai vào phòng chờ với đồng hồ 10 giây; nếu cả hai cùng bấm Sẵn sàng, đồng hồ rút về 3 giây. Hết 10 giây trận vẫn tự bắt đầu. Màn đấu chiếm trọn vùng cửa sổ, có thể chuyển toàn màn hình; thoát trận sẽ trở lại Lobby với tổng điểm đã cập nhật từ server.

## Kiểm thử Lobby và giao diện

Chạy tại workspace root, không cần MySQL hoặc Server đang chạy:

```powershell
server\run-lobby-tests.bat
client\run-ui-checks.bat
```

`LobbyIntegrationTest` dùng handler Server và kết nối Client thực qua TCP với kho tài khoản trong bộ nhớ. Bao phủ 3 Client, cạnh tranh lời mời, timeout, countdown, thoát/mất kết nối, đăng ký và đăng xuất. `LobbyUiCheck` kiểm tra nút Swing trên EDT và xuất ảnh preview vào `client/out/preview/` mà không mở cửa sổ tương tác. Đây không phải kiểm thử JDBC/MySQL.

## Giao diện kết quả trận đấu

Khi nhận `GAME_OVER`, màn chơi hiển thị hai thẻ kết quả lớn theo phong cách Arcade: người thắng bên trái với cúp vàng và pháo giấy, người thua bên phải với huy hiệu đỏ. Hai thẻ bay lên, nảy nhẹ rồi giữ nguyên; hiệu ứng dừng sau khoảng 3 giây. Trận hòa hiển thị hai thẻ xanh bạc. Mỗi thẻ hiện điểm xếp hạng được cộng trong trận này và tổng điểm mới nhất đã lưu từ backend; nếu dữ liệu điểm đến muộn, hai thẻ cập nhật tại chỗ. Nếu backend không lưu được thống kê, tổng điểm hiện “chưa đồng bộ”.

Khi xe tăng trúng đạn, Client vẽ chớp sáng, tia lửa, hai vòng xung kích và khói ngắn theo tọa độ `HIT_EFFECT` của Server. Thẻ HP và thân xe nháy màu; xe của mình bị trúng thì chỉ sân đấu rung nhẹ. Khi snapshot `GAME_STATE` báo HP về 0, Client khóa di chuyển/bắn và chạy hiệu ứng kết liễu riêng trong 1 giây: chớp nổ, tia lửa, mảnh vỡ, khói và xác xe cháy. Hiệu ứng kích hoạt một lần cho mỗi xe, kể cả khi thiếu `HIT_EFFECT`. Bảng kết quả chỉ hiện khi đã nhận `GAME_OVER` và hiệu ứng kết liễu đã xong; hết giờ/thoát khi cả hai còn HP thì hiện ngay. Đổi toàn màn hình giữ nguyên thời điểm hiệu ứng, còn rời trận/chơi lại xóa hiệu ứng và kết quả đang chờ.

Bản đồ giữ hệ tọa độ 800×420, phóng đều để chiếm vùng chơi lớn nhất mà không cắt bản đồ. Toàn bộ hình xe và hitbox phía Server cùng giảm 23,5% so với kích thước ban đầu quanh tâm thân xe; hitbox hiện rộng 42,84 và cao 23,715 đơn vị thế giới. Tốc độ, sát thương và giới hạn di chuyển giữ nguyên. Sau khi cập nhật, chạy lại cả `server\run-server.bat` và `client\run-client.bat` để biên dịch hai phía với cùng kích thước xe; không cần đổi `.env`.

Chọn **CHƠI TIẾP** để chờ đối thủ đồng ý hoặc **THOÁT** để rời trận. Có thể chuyển toàn màn hình ngay trên màn kết quả; Escape đưa về cửa sổ.

`client\run-ui-checks.bat` kiểm tra kết quả, thao tác chơi tiếp, vòng đời animation và bộ đếm thời gian Swing thực. Ảnh xem trước nằm trong `client/out/preview/`: `game-over.png`, `game-over-entering.png`, `game-over-confetti.png`, `game-over-loss.png`, `game-over-draw.png`, `game-over-waiting.png`, `game-over-minimum.png`, `game-over-fullscreen.png` và `game-over-long-names.png`.

Kiểm thử hình học bao phủ 900×540, Full HD và 4K ở cả hai góc nhìn. Các ảnh `compact-game-*-p1.png` / `compact-game-*-p2.png` cho thấy tỷ lệ xe/bản đồ; `finisher-burst-p*.png`, `finisher-smoke-p*.png` và `finisher-wreck-p*.png` cho thấy các giai đoạn kết liễu. Kiểm thử còn kiểm tra snapshot HP0 lặp, kết quả đến sớm/muộn, tạo lại cửa sổ toàn màn hình và dọn hiệu ứng khi rời/chơi lại. `server\run-lobby-tests.bat` kiểm tra đạn sát biên trong/ngoài hitbox mới.

## Âm thanh arcade

Nút **Cài đặt** ở Lobby, phòng chờ, gameplay và bảng kết quả mở hai thanh âm lượng **Nhạc nền** và **Hiệu ứng âm thanh** (0–100%). Hai kênh điều chỉnh độc lập, có hiệu lực ngay và tự lưu trên máy cho lần mở game sau. Mặc định nhạc 20%, hiệu ứng 100%; chọn 0 để tắt kênh tương ứng. Hiệu ứng gồm tiếng bắn, trúng đạn, nổ xe, đếm ngược và giọng báo kết quả. Xem [hướng dẫn nhạc nền](client/resources/music/README.md) để thay nhạc đóng gói.

Tiếng bắn phát khi server xác nhận số đạn giảm, không phát chỉ vì nhấn Space. Nổ xe chỉ phát một lần khi HP về 0; tiếng kết quả phát sau hiệu ứng kết liễu. Chơi lại/rời phòng dừng tiếng cũ; đổi toàn màn hình giữ trạng thái âm thanh. Bộ phát dùng Java Sound trên luồng riêng; thiếu file hoặc thiết bị âm thanh không ngăn game hoạt động.

Các file WAV nằm trong `client/resources/audio/`; có thể thay file cùng tên bằng PCM WAV. `client/run-client.bat` và `client/run-ui-checks.bat` tự sao chép tài nguyên vào `client/out`; thư mục tài nguyên cũng được đánh dấu trong module IntelliJ. Chỉ cần chạy lại client, không đổi `.env` hay server. Bộ âm gốc được tạo bằng `python client/tools/generate_audio.py`; Python không cần thiết khi chơi.

`client/run-ui-checks.bat` kiểm tra dữ liệu WAV, phát đồng thời, tắt âm, dọn tài nguyên, sự kiện trùng, file thiếu và giả lập không có thiết bị phát. Để nghe thử toàn bộ hiệu ứng sau khi chạy bộ kiểm tra:

```powershell
java -cp client/out tank.client.audio.AudioPreview
```

## Protocol ban dau

Client và server dùng `DataInputStream.readUTF` / `DataOutputStream.writeUTF`. LOGIN_SUCCESS gửi username, matches played, score, wins, losses, draws, status. PLAYER_LIST gửi số lượng rồi username, score, status cho từng người.

Message hiện tại gồm `REGISTER_REQUEST`, `REGISTER_SUCCESS`, `REGISTER_FAIL`, `LOGIN_REQUEST`, `LOGIN_SUCCESS`, `LOGIN_FAIL`, `PLAYER_LIST`, `LOGOUT_REQUEST`, `LOGOUT_SUCCESS`, và `SERVER_ERROR`. `LOGIN_SUCCESS` mang username, matches played, score, wins, losses, draws, status; `PLAYER_LIST` mang username, score, status. Khi đổi payload, cập nhật cả hai file `Protocol.java` trong package `protocol/`.

Payload Lobby/Challenge/Room cùng message điểm kết quả `MATCH_RESULT` nằm trong [LOBBY_PROTOCOL.md](resourse/LOBBY_PROTOCOL.md). Client/server cần cập nhật cùng phiên bản để giải mã payload này.

## Ghi chu

- Server mô phỏng trận đấu và lưu thống kê tài khoản/lịch sử; Client chỉ gửi thao tác và vẽ snapshot từ server.
- Dùng `RoomManager.GameListener` để nhận bắt đầu/rời trận, và `RoomManager.finishRoom(...)` khi module kết quả giải phóng phòng. Phần Lobby không ghi kết quả hay cộng điểm.
- Server dang dung MySQL/JDBC; thong tin ket noi lay tu cac bien moi truong `TANK_DB_URL`, `TANK_DB_USER`, `TANK_DB_PASSWORD`.
- TCP thuong khong ma hoa. Chi nen dung tren mang tin cay khi chua bo sung TLS.
