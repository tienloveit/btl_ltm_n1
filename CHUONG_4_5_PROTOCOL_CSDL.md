CHƯƠNG 4. THIẾT KẾ GIAO THỨC MẠNG
4.1. Thiết lập kết nối
Client và Server là hai chương trình độc lập, trao đổi dữ liệu qua TCP Socket. Client nhận host và port từ tham số chạy hoặc biến môi trường; mặc định là 127.0.0.1:5000. Server mặc định lắng nghe cổng 5000. Chỉ Server kết nối tới MySQL.
Mỗi kết nối có luồng đọc message và luồng gửi riêng. Phía Server đưa message cần gửi vào hàng đợi của kết nối để tránh nhiều luồng ghi đồng thời lên cùng socket. Khi kết nối đóng hoặc lỗi, Server dọn session và cập nhật danh sách người chơi.
4.2. Định dạng message
Mỗi message bắt đầu bằng tên loại message được ghi bằng writeUTF. Các trường dạng chuỗi tiếp theo được ghi theo đúng thứ tự bằng writeUTF. Riêng PLAYER_LIST, sau tên message là số lượng người chơi kiểu int, rồi đến các trường UTF của từng người. Các số trong payload UTF được biểu diễn thành chuỗi thập phân.
Hai phía Client và Server có các lớp Protocol riêng, vì vậy tên message và thứ tự payload phải được giữ đồng nhất. Không truyền trực tiếp Java object qua socket.
4.3. Các message chính
Message | Hướng | Payload theo thứ tự
--- | --- | ---
REGISTER_REQUEST | Client → Server | username, email, password
REGISTER_SUCCESS | Server → Client | message thông báo
REGISTER_FAIL | Server → Client | lý do thất bại
LOGIN_REQUEST | Client → Server | username, password
LOGIN_SUCCESS | Server → Client | username, matchesPlayed, score, wins, losses, draws, status
LOGIN_FAIL | Server → Client | lý do thất bại
PLAYER_LIST | Server → Client | số lượng kiểu int; lặp: username, score, status
PROFILE_UPDATE | Server → Client | username, matchesPlayed, score, wins, losses, draws, status
LOGOUT_REQUEST | Client → Server | không có
LOGOUT_SUCCESS | Server → Client | không có
CHALLENGE | Client → Server | targetUsername
CHALLENGE_PENDING | Server → người gửi | challengeId, opponentUsername, timeoutSeconds
CHALLENGE_RECEIVED | Server → người nhận | challengeId, opponentUsername, timeoutSeconds
ACCEPT | Client → Server | challengeId
REJECT | Client → Server | challengeId
CHALLENGE_CANCEL | Client → Server | challengeId
CHALLENGE_CLOSED | Server → hai bên | challengeId, reason
ROOM_STATE | Server → hai bên | roomId, player1, player2, secondsRemaining
ROOM_LEAVE | Client → Server | roomId
ROOM_CLOSED | Server → hai bên | roomId, reason
MATCH_START | Server → hai bên | roomId, player1, player2
MOVE_LEFT, MOVE_RIGHT, SHOOT, EXIT, PLAY_AGAIN | Client → Server | không có
GAME_STATE | Server → hai bên | roomId, secondsRemaining, trạng thái player1, trạng thái player2, số lượng đạn, danh sách đạn
HIT_EFFECT | Server → hai bên | roomId, x, y
GAME_OVER | Server → hai bên | roomId, winner hoặc DRAW/WAITING, reason
RANKING_REQUEST | Client → Server | không có
RANKING_RESPONSE | Server → Client | số lượng; lặp: username, matchesPlayed, score, wins, losses, draws
HISTORY_REQUEST | Client → Server | không có
HISTORY_RESPONSE | Server → Client | số lượng; lặp: matchId, endedAt, opponent, playerScore, opponentScore, result
LOBBY_ERROR | Server → Client | nội dung lỗi lobby/phòng
SERVER_ERROR | Server → Client | nội dung lỗi chung hoặc lỗi xử lý dữ liệu
Trong GAME_STATE, trạng thái của mỗi xe tăng gồm x, HP, ammo và điểm trong trận. Mỗi viên đạn gồm username chủ sở hữu, x và y. Các trường số trong payload này được gửi dưới dạng UTF, ngoại trừ số lượng người chơi trong PLAYER_LIST được gửi bằng int.
4.4. Xử lý lỗi và trạng thái
Server từ chối các thao tác yêu cầu đăng nhập khi Client chưa xác thực; kiểm tra trạng thái người chơi trước khi tạo lời mời/phòng; và trả lỗi khi lời mời hoặc phòng không hợp lệ. Đăng nhập thất bại nếu thông tin không đúng hoặc tài khoản đang hoạt động trên kết nối khác. Nếu socket bị đóng, Server dọn session và thông báo roster mới cho các Client còn kết nối.
Các phản hồi REGISTER_FAIL, LOGIN_FAIL, LOBBY_ERROR và SERVER_ERROR mang nội dung lỗi để Client hiển thị. Client cũng kiểm tra giới hạn số lượng bản ghi và tính hợp lệ của trường số trong các message danh sách/trạng thái; message không nhận diện được dẫn tới đóng kết nối.
CHƯƠNG 5. THIẾT KẾ VÀ CÀI ĐẶT CƠ SỞ DỮ LIỆU
5.1. Công nghệ và cấu hình kết nối
Server sử dụng MySQL thông qua JDBC và MySQL Connector/J. Thông tin kết nối được lấy từ các thiết lập TANK_DB_URL, TANK_DB_USER và TANK_DB_PASSWORD; giá trị môi trường hệ điều hành được ưu tiên hơn file .env. Mật khẩu kết nối không được ghi vào mã nguồn. Cơ sở dữ liệu mặc định có tên tank_game.
5.2. Mô hình dữ liệu
Cơ sở dữ liệu gồm ba bảng chính: player_accounts lưu tài khoản và thống kê; matches lưu thông tin chung của trận; match_players lưu kết quả từng người trong trận.
Bảng player_accounts
Trường | Ý nghĩa
--- | ---
username | Khóa chính, tên tài khoản
display_name | Tên hiển thị
email | Email; duy nhất. Bắt buộc trong schema cài mới, có thể NULL với tài khoản cũ sau migration
password_salt | Salt ngẫu nhiên 16 byte
password_hash | Giá trị băm mật khẩu 32 byte
matches_played | Tổng số trận
score | Tổng điểm xếp hạng
wins, losses, draws | Số trận thắng, thua và hòa
created_at | Thời điểm tạo tài khoản
Bảng matches
Trường | Ý nghĩa
--- | ---
match_id | Khóa chính tự tăng
started_at | Thời điểm tạo bản ghi trận
ended_at | Thời điểm kết thúc được ghi nhận
status | Trạng thái bản ghi, mặc định COMPLETED
winner_username | Tài khoản thắng, tham chiếu player_accounts; NULL nếu hòa
loser_username | Tài khoản thua, tham chiếu player_accounts; NULL nếu hòa
is_draw | Đánh dấu trận hòa
Bảng match_players
Trường | Ý nghĩa
--- | ---
match_id | Khóa ngoại tới matches
username | Khóa ngoại tới player_accounts
score | Điểm ghi nhận cho người chơi trong trận
result | Kết quả của người chơi: WIN, LOSS hoặc DRAW
Khóa chính ghép của match_players là match_id và username, bảo đảm một tài khoản chỉ có một bản ghi tham gia cho mỗi trận. Các khóa ngoại liên kết trận với người thắng/thua và các người tham gia. Bảng player_accounts có chỉ mục duy nhất cho email.
Hình 5.1 — ERD cần thể hiện player_accounts liên kết với matches qua vai trò winner/loser, và liên kết với match_players; mỗi trận có các bản ghi người tham gia trong match_players. Ghi rõ khóa chính, khóa ngoại và ràng buộc email duy nhất.
5.3. Bảo vệ tài khoản và truy cập dữ liệu
Khi đăng ký, Server chuẩn hóa username/email, kiểm tra định dạng và độ dài mật khẩu, tạo salt ngẫu nhiên rồi băm bằng PBKDF2-HMAC-SHA256 với 120.000 vòng lặp và khóa 256 bit. Khi đăng nhập, Server tính lại hash từ mật khẩu nhập vào và salt đã lưu, sau đó so sánh an toàn với hash trong cơ sở dữ liệu. Mật khẩu gốc không được lưu trong bảng.
Các thao tác truy vấn và cập nhật tài khoản dùng PreparedStatement. Tài khoản hoặc email trùng được xử lý thành lỗi đăng ký phù hợp. Cấu hình Database được đọc từ biến môi trường hoặc file .env ở phía Server.
5.4. Ghi thống kê, lịch sử và truy vấn
Khi trận kết thúc, AccountService cập nhật tổng số trận, điểm, thắng, thua hoặc hòa cho từng người. Điểm được cộng theo luật: thắng +3, hòa +1, thua +0. PlayerSessionManager cập nhật hồ sơ trong session trực tuyến và phát PROFILE_UPDATE/PLAYER_LIST.
MatchHistoryRepository tạo bản ghi trong matches, lấy match_id vừa sinh, sau đó ghi hai người chơi vào match_players trong cùng một transaction. Khi một thao tác lưu các bảng lịch sử gặp lỗi, transaction bị rollback. Truy vấn history lọc theo người chơi, ghép với đối thủ và trả tối đa 100 bản ghi gần nhất.
Truy vấn ranking sắp xếp theo score giảm dần, wins giảm dần, draws giảm dần, losses tăng dần và username tăng dần; Server trả tối đa 100 người chơi. Thống kê tài khoản và việc ghi bản ghi lịch sử là các thao tác repository riêng; báo cáo không giả định chúng được gộp chung vào một transaction nếu triển khai hiện tại không đảm bảo điều đó.