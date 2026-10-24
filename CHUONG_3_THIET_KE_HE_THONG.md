CHƯƠNG 3. PHÂN TÍCH VÀ THIẾT KẾ HỆ THỐNG

3.1. Kiến trúc tổng thể

Hệ thống được thiết kế theo mô hình Client–Server ba thành phần:

Client 1, Client 2, ...  →  TCP Socket  →  Server  →  JDBC  →  MySQL

Client cung cấp giao diện, nhận thao tác của người chơi và hiển thị dữ liệu do Server gửi về. Server tiếp nhận nhiều kết nối, xác thực tài khoản, quản lý session và trạng thái lobby/phòng/trận, xử lý luật chơi và truy cập cơ sở dữ liệu. MySQL lưu tài khoản, thống kê và lịch sử trận đấu. Client không kết nối trực tiếp tới MySQL.

Server là nguồn quyết định chính thức cho trạng thái trận. Client gửi các thao tác như di chuyển hoặc bắn; Server kiểm tra và cập nhật vị trí, đạn, HP, thời gian, va chạm và kết quả, sau đó gửi snapshot cho các Client trong trận.

Hình 3.1 — Sơ đồ kiến trúc/deployment cần thể hiện nhiều Client có thể kết nối tới một Server qua TCP, và Server kết nối MySQL qua JDBC. Ghi chú port mặc định 5000; không nối Client trực tiếp với Database.

3.2. Các thành phần chính

3.2.1. Phía Client

Client được chia thành các nhóm lớp giao diện, điều phối, kết nối/protocol và model:

- ClientApp khởi tạo ứng dụng và cấu hình địa chỉ Server.
- Các lớp giao diện đảm nhiệm đăng nhập, đăng ký, lobby, phòng chờ và gameplay.
- ClientController nhận sự kiện từ giao diện, gọi kết nối mạng và chuyển phản hồi tới màn hình phù hợp.
- ClientConnection quản lý TCP socket, gửi yêu cầu và đọc message từ Server.
- Các model lưu dữ liệu hồ sơ, người chơi, phòng, trạng thái trận, ranking và history.

3.2.2. Phía Server

ServerMain khởi tạo cấu hình Database, repository, dịch vụ tài khoản, session manager, challenge manager, room manager và game state manager. ClientHandler xử lý message của một kết nối. Các module nghiệp vụ được phân chia như sau:

- AccountService xác thực, đăng ký tài khoản và cập nhật thống kê.
- PlayerSessionManager quản lý session, trạng thái người chơi và phát roster.
- ChallengeManager quản lý lời mời, quyền phản hồi và timeout.
- RoomManager tạo phòng, đếm ngược, bắt đầu hoặc đóng phòng.
- GameStateManager mô phỏng trận đấu, cập nhật trạng thái và xử lý kết quả.
- Các repository thực hiện thao tác JDBC với MySQL.

Server dùng worker xử lý các kết nối Client và scheduler cho timeout, countdown và cập nhật trận. Mỗi kết nối có luồng đọc và luồng gửi message riêng; các thao tác ghi được xếp hàng để tránh nhiều luồng ghi đồng thời vào cùng socket. Worker pool có giới hạn tối đa 200 kết nối xử lý đồng thời theo cấu hình hiện tại.

Hình 3.2 — Sơ đồ thành phần nên nhóm lớp Client, Server và Persistence thành các cụm, thể hiện các phụ thuộc chính giữa controller–connection, handler–service/manager và repository–Database.

3.3. Thiết kế các luồng nghiệp vụ

3.3.1. Đăng nhập

Người chơi nhập username và mật khẩu trên Client. Client gửi yêu cầu tới Server; AccountService xác thực thông tin qua repository. Nếu hợp lệ và tài khoản chưa đăng nhập ở kết nối khác, Server tạo session, đặt trạng thái ONLINE, trả hồ sơ và phát danh sách người chơi. Nếu thông tin sai hoặc tài khoản đang có phiên khác, Server trả lỗi và Client giữ người chơi ở màn hình đăng nhập.

3.3.2. Thách đấu và tạo phòng

Người chơi gửi lời mời tới đối thủ đang ONLINE. ChallengeManager kiểm tra cả hai người còn kết nối, không phải cùng một tài khoản và chưa bận. Lời mời có mã định danh và hết hạn sau 30 giây; hai người chuyển sang WAITING. Người nhận có thể chấp nhận hoặc từ chối; người gửi có thể hủy.

Khi được chấp nhận, RoomManager tạo phòng và gửi ROOM_STATE với các mốc đếm ngược 3, 2, 1. Khi countdown hoàn tất, trạng thái hai người chuyển sang PLAYING; GameStateManager khởi tạo trận và Server phát MATCH_START. Nếu lời mời bị từ chối, hủy, hết hạn hoặc có người ngắt kết nối trước khi bắt đầu, lời mời/phòng được đóng và trạng thái được giải phóng.

3.3.3. Gameplay và kết thúc trận

Client gửi thao tác MOVE_LEFT, MOVE_RIGHT, SHOOT hoặc EXIT. GameStateManager kiểm tra người gửi thuộc trận, cập nhật trạng thái game và phát GAME_STATE cho hai bên. Khi đạn trúng xe tăng, Server giảm HP và có thể phát HIT_EFFECT.

Trận kết thúc khi một xe tăng hết HP, hết thời gian, hết đạn hoặc người chơi rời trận. Server xác định winner/draw, cập nhật thống kê, ghi lịch sử và gửi GAME_OVER. Sau trận, chỉ khi cả hai người chơi cùng chọn PLAY_AGAIN thì Server mới reset trạng thái và bắt đầu ván mới.

Hình 3.3 — Sơ đồ hoạt động tổng quát nên thể hiện luồng đăng nhập → lobby → gửi lời mời → nhánh chấp nhận/từ chối/hủy/hết hạn → phòng và countdown → gameplay → kết quả → chơi lại hoặc trở về lobby.

3.4. Mô hình trạng thái người chơi

Trạng thái được lưu và điều khiển ở Server:

ONLINE → WAITING → PLAYING → ONLINE

- ONLINE: người chơi đã đăng nhập, đang rảnh và có thể nhận/gửi lời mời.
- WAITING: người chơi đang có lời mời hoặc ở phòng chờ.
- PLAYING: trận đã bắt đầu.
- OFFLINE: session đã đóng; người chơi được loại khỏi roster trực tuyến.

Từ WAITING, lời mời bị từ chối, hủy, hết hạn hoặc phòng bị hủy sẽ đưa người còn kết nối về ONLINE. Khi trận/phòng kết thúc, người còn kết nối cũng trở về ONLINE. Nếu mất kết nối, session chuyển sang trạng thái offline thông qua việc loại khỏi danh sách session.

Hình 3.4 — State diagram cần vẽ các trạng thái ONLINE, WAITING, PLAYING và OFFLINE cùng các chuyển tiếp do challenge, countdown, kết thúc trận, logout và disconnect.

3.5. Thiết kế lớp

Class diagram Client nên thể hiện các quan hệ chính:

- ClientApp khởi tạo ClientController.
- ClientController sử dụng ClientConnection và điều khiển các frame.
- ClientController triển khai ClientConnection.Listener để nhận sự kiện mạng.
- PlayerFrame chứa các panel giao diện lobby, phòng chờ và gameplay.
- ClientConnection chuyển message thành các model như PlayerProfile, RoomState và GameState.

Class diagram Server nên thể hiện:

- ServerMain khởi tạo AccountService, PlayerSessionManager, ChallengeManager, RoomManager và GameStateManager.
- ClientHandler nhận message, gọi các dịch vụ và manager tương ứng.
- ChallengeManager sử dụng PlayerSessionManager và RoomManager.
- RoomManager quản lý phòng, countdown và gọi callback của GameStateManager khi trận bắt đầu hoặc người chơi rời phòng.
- GameStateManager sử dụng AccountService và MatchHistoryRepository để cập nhật kết quả.
- JdbcAccountRepository và MatchHistoryRepository truy cập MySQL thông qua DatabaseConfig.

Hình 3.5 — Class diagram Client và Hình 3.6 — Class diagram Server nên tách riêng để dễ đọc. Chỉ đưa lớp và quan hệ quan trọng, không cần liệt kê getter/setter hoặc toàn bộ thuộc tính.

3.6. Thiết kế dữ liệu và đồng bộ trạng thái

Trạng thái người chơi đang kết nối được giữ trong bộ nhớ Server; dữ liệu tài khoản và kết quả trận được lưu trong MySQL. Các bảng chính gồm player_accounts để lưu tài khoản/thống kê, matches để lưu thông tin trận và match_players để lưu từng người tham gia cùng kết quả. Chi tiết khóa và cấu trúc bảng được trình bày tại Chương 5.

Khi trạng thái lobby thay đổi, PlayerSessionManager tạo roster mới và phát cho các Client đang kết nối. Trong trận, GameStateManager phát snapshot chứa thời gian, trạng thái hai xe tăng và danh sách đạn. Cách tổ chức này giúp hai Client hiển thị cùng một trạng thái do Server tạo ra.
