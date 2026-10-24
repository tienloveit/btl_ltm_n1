CHƯƠNG 6. CÀI ĐẶT CHỨC NĂNG
6.1. Cấu trúc chương trình
Hệ thống gồm hai ứng dụng Java độc lập, được biên dịch và chạy riêng:
client/src/tank/client/
app: điểm khởi động
controller: điều phối giao diện và yêu cầu mạng
network: TCP socket, gửi và nhận message
protocol: tên message phía Client
model: dữ liệu hồ sơ, người chơi, phòng, trận, ranking và history
ui: các cửa sổ và panel Swing
server/src/tank/server/
app: khởi động TCP Server
auth: đăng ký, xác thực và thống kê tài khoản
session: xử lý kết nối, session và trạng thái người chơi
lobby: lời mời thách đấu và timeout
room: phòng chờ, countdown và đóng phòng
game: trạng thái và logic trận đấu
persistence: JDBC, repository và cấu hình MySQL
protocol: tên message phía Server
Ở Client, ClientApp khởi tạo ClientController. Controller tiếp nhận sự kiện UI và gọi ClientConnection; ClientConnection gửi/nhận dữ liệu và chuyển message thành callback/model. Ở Server, ServerMain khởi tạo ClientHandler cùng các service/manager. ClientHandler phân phối yêu cầu tới AccountService, PlayerSessionManager, ChallengeManager, RoomManager hoặc GameStateManager. Repository thực hiện truy vấn MySQL. Hai phía duy trì lớp Protocol riêng nên tên message và thứ tự trường phải thống nhất.
Ví dụ, sau khi Server accept một socket, socket được chuyển cho ClientHandler cùng các thành phần nghiệp vụ để xử lý:
workers.execute(new ClientHandler(socket, accountService, sessions, challenges, rooms, games, matchHistoryRepository));
Đoạn mã minh họa điểm kết nối giữa tầng tiếp nhận TCP và tầng xử lý session; không cần đưa toàn bộ lớp vào báo cáo.
6.2. Client
ClientApp đọc host và port từ tham số dòng lệnh hoặc các biến TANK_SERVER_HOST, TANK_SERVER_PORT; mặc định là 127.0.0.1:5000. ClientController quản lý quá trình đăng nhập, đăng xuất, mở màn hình và gửi yêu cầu nghiệp vụ. ClientConnection tạo TCP socket, gửi các message và có một luồng đọc phản hồi.
Các màn hình đăng nhập và đăng ký thu thập thông tin tài khoản, gửi lên Server và hiển thị phản hồi thành công hoặc lỗi. Sau khi đăng nhập, PlayerFrame hiển thị thông tin người chơi, điểm, thắng/thua/hòa và danh sách đối thủ cùng trạng thái. Người chơi chọn đối thủ đang rảnh để gửi lời mời; hộp thoại nhận lời mời cung cấp lựa chọn chấp nhận hoặc từ chối. Người gửi có thể hủy lời mời đang chờ.
Khi lời mời được chấp nhận, Client hiển thị phòng chờ và các mốc countdown do Server gửi. Trong gameplay, GamePlayPanel gửi thao tác trái/phải, bắn, thoát hoặc chơi lại. Client không tự tính va chạm và kết quả; màn hình được cập nhật từ snapshot GameState do Server phát. Các màn hình ranking và history hiển thị dữ liệu Server trả về.
6.3. Server
6.3.1. Tiếp nhận kết nối
ServerMain mở ServerSocket, mặc định cổng 5000, và giao mỗi socket cho một ClientHandler. Worker pool được giới hạn tối đa 200 worker; khi hết khả năng tiếp nhận, kết nối mới bị từ chối. Mỗi ClientHandler đọc yêu cầu từ một socket và sử dụng hàng đợi gửi riêng để tuần tự hóa các message trả về.
6.3.2. Tài khoản và session
AccountService kiểm tra dữ liệu đăng ký, tạo salt và hash mật khẩu; khi đăng nhập, dịch vụ kiểm tra hash trước khi cho phép tạo session. PlayerSessionManager ngăn một tài khoản đăng nhập trên nhiều kết nối đồng thời, lưu trạng thái hiện thời và phát roster sau khi có thay đổi. Các trạng thái chính gồm ONLINE, WAITING và PLAYING; người chơi mất kết nối bị gỡ khỏi roster.
6.3.3. Lời mời và phòng đấu
ChallengeManager chỉ cho phép thách đấu khi cả hai người đang trực tuyến và rảnh. Mỗi lời mời có mã định danh, hết hạn sau 30 giây; người nhận có quyền chấp nhận/từ chối, người gửi có quyền hủy. Các thao tác cạnh tranh được đồng bộ để một người không đồng thời tham gia nhiều lời mời.
Khi lời mời được chấp nhận, RoomManager tạo phòng và gửi ROOM_STATE cho hai Client. Server phát countdown 3–2–1, sau đó chuyển trạng thái hai người sang PLAYING và phát MATCH_START. Nếu lời mời bị từ chối, hủy, hết hạn hoặc một người disconnect trong lúc chờ, phòng/countdown được hủy và trạng thái người còn kết nối được giải phóng.
6.3.4. Gameplay và kết quả
GameStateManager quản lý trạng thái trận trong bộ nhớ và cập nhật theo chu kỳ 50 ms. Mỗi trận bắt đầu với 100 HP, 50 viên đạn và 180 giây. Xe tăng di chuyển ngang trong giới hạn sân; mỗi lần bắn tiêu thụ một viên đạn và bị giới hạn bởi cooldown 350 ms. Đạn di chuyển về phía đối thủ; Server kiểm tra va chạm và trừ 20 HP khi trúng.
Trận kết thúc khi một xe tăng hết HP, hết thời gian, cả hai hết đạn đang có thể sử dụng hoặc một người rời trận. Server xác định người thắng/hòa, gửi GAME_OVER và gọi AccountService cập nhật thống kê. MatchHistoryRepository ghi kết quả trận và hai người tham gia. Sau kết quả, chỉ khi cả hai cùng chọn PLAY_AGAIN thì trận mới được khởi tạo lại.
6.4. Giao diện và luồng sử dụng
Phần này cần chèn ảnh chụp giao diện thực tế của nhóm, mỗi ảnh có số thứ tự, chú thích và mô tả ngắn. Có thể bố trí theo thứ tự:
Hình 6.1 — Màn hình đăng nhập.
Hình 6.2 — Màn hình đăng ký.
Hình 6.3 — Lobby và danh sách người chơi.
Hình 6.4 — Hộp thoại nhận lời mời thách đấu.
Hình 6.5 — Phòng chờ và countdown.
Hình 6.6 — Màn hình gameplay.
Hình 6.7 — Màn hình kết quả trận đấu.
Hình 6.8 — Bảng xếp hạng hoặc lịch sử trận đấu.
Ảnh preview trong client/out/preview được dựng bằng dữ liệu mẫu để kiểm tra bố cục UI. Nếu sử dụng các ảnh này, cần ghi rõ đây là preview; không dùng chúng làm bằng chứng rằng trận đấu hoặc truy vấn MySQL đã chạy end-to-end. Ảnh minh chứng vận hành cần được chụp từ phiên chạy thật.
6.5. Phân công và tích hợp
Theo tài liệu phân công, các mảng nhiệm vụ gồm:
Người 1: Authentication & Player — kết nối, đăng nhập/đăng ký, tài khoản, hồ sơ, trạng thái, logout/disconnect.
Người 2: Lobby & Challenge — danh sách người chơi, lời mời, Accept/Reject, quản lý phòng và giao diện lobby/phòng chờ.
Người 3: Gameplay & Game UI — xe tăng, đạn, điều khiển, HP, ammo, timer, va chạm, game loop và giao diện trận.
Người 4: Result, Ranking & History — kết quả, thống kê, lịch sử, chơi lại, ranking và giao diện liên quan.
Cả nhóm: thống nhất protocol/model, tích hợp Client–Server, kiểm thử nhiều Client và hoàn thiện sản phẩm.
Tên thành viên, phần việc đã hoàn thành và minh chứng cần được điền theo phân công thực tế; không suy ra người thực hiện chỉ từ tên package. Các module tích hợp qua TCP với tên message và payload thống nhất. Ở Client, ClientController nhận callback từ ClientConnection; ở Server, RoomManager gọi GameStateManager qua GameListener khi trận bắt đầu hoặc người chơi rời phòng.
CHƯƠNG 7. KIỂM THỬ VÀ ĐÁNH GIÁ
7.1. Môi trường kiểm thử
Các script hiện có biên dịch mã với javac --release 17 trên môi trường Windows. Kiểm thử lobby dùng ClientConnection và ClientHandler thật qua TCP, nhưng sử dụng repository tài khoản trong bộ nhớ thay cho MySQL. Kiểm tra UI chạy các thành phần Swing trên Event Dispatch Thread và xuất ảnh preview. Phiên bản MySQL, cấu hình LAN và phần cứng kiểm thử thực tế cần được bổ sung theo môi trường nhóm sử dụng.
7.2. Phương pháp và phạm vi
Kiểm thử được thực hiện ở hai phạm vi. Kiểm thử tích hợp TCP/lobby xác nhận trao đổi message và các chuyển trạng thái giữa nhiều Client. Kiểm tra UI xác nhận một số thành phần giao diện, thao tác nút và điều hướng. Các script này không kiểm tra kết nối JDBC/MySQL thật, trận gameplay end-to-end hoặc triển khai nhiều máy qua LAN.
Lệnh chạy từ thư mục gốc dự án:
server\run-lobby-tests.bat
client\run-ui-checks.bat
Trong lần chạy đã ghi nhận, test TCP/lobby kết thúc với PASS: 96 checks. Test UI in PASS: 26 Swing checks và tạo preview, nhưng tiến trình Java vẫn chạy sau khi in PASS và phải dừng thủ công. Đây là kết quả script hiện có, không phải kết quả kiểm thử các chức năng chưa nằm trong phạm vi của script.
7.3. Test case
Bảng 7.1 trình bày kết quả theo phạm vi kiểm thử đã có. Với test chưa chạy, kết quả thực tế được ghi rõ là chưa kiểm thử.
Mã | Chức năng/tình huống | Tiền điều kiện | Thao tác/dữ liệu vào | Kết quả mong đợi | Kết quả thực tế | Trạng thái
--- | --- | --- | --- | --- | --- | ---
T01 | Đăng nhập/đăng ký, roster và session | Test server chạy; kho tài khoản bộ nhớ | Kết nối ba Client, đăng nhập/đăng ký và logout | Phản hồi hợp lệ; roster và session cập nhật | Được bao phủ bởi test tích hợp TCP | PASS, nằm trong 96 checks
T02 | Tự thách đấu, đối thủ offline/bận, lời mời cạnh tranh | Nhiều Client đã đăng nhập | Gửi lời mời tới các trường hợp hợp lệ/không hợp lệ và gửi cạnh tranh | Yêu cầu sai bị từ chối; chỉ một lời mời cạnh tranh thành công | Được bao phủ bởi test tích hợp TCP | PASS, nằm trong 96 checks
T03 | Accept, Reject, Cancel, timeout và ID sai | Có lời mời đang chờ | Thử phản hồi, hủy, chờ hết hạn và dùng ID cũ/sai người nhận | Lời mời đóng đúng lý do; trạng thái người chơi được giải phóng | Được bao phủ bởi test tích hợp TCP | PASS, nằm trong 96 checks
T04 | Countdown, vào/rời phòng và disconnect | Hai người chơi đã đăng nhập | Chấp nhận lời mời, theo dõi countdown, rời phòng hoặc ngắt kết nối | Cùng nhận 3–2–1; bắt đầu một lần; phòng được đóng đúng cách | Được bao phủ bởi test tích hợp TCP | PASS, nằm trong 96 checks
T05 | Điều khiển giao diện lobby và các màn hình | Có môi trường chạy Swing | Kiểm tra nút, chọn người chơi, hộp thoại, điều hướng và render preview | Giao diện phản hồi đúng theo dữ liệu kiểm tra | In PASS: 26 checks; tiến trình không tự thoát | PASS có lưu ý
T06 | Gameplay thực giữa hai Client | Server và hai Client chạy cùng phiên bản | Chơi trận từ lúc bắt đầu tới khi có kết quả | Hai Client nhận cùng trạng thái và kết quả đúng | Chưa có kết quả trong các script hiện có | Chưa kiểm thử
T07 | JDBC/MySQL, ranking và history trên DB thật | MySQL và cấu hình Server hợp lệ | Đăng ký, ghi kết quả và truy vấn ranking/history | Dữ liệu được ghi/đọc đúng trong DB | Chưa có kết quả trong các script hiện có | Chưa kiểm thử
T08 | Kết nối LAN và tải nhiều Client | Server và các máy Client trên mạng thử nghiệm | Kết nối từ nhiều máy và tăng số Client | Kết nối hoạt động trong phạm vi mục tiêu, lỗi được xử lý | Chưa có kết quả trong các script hiện có | Chưa kiểm thử
7.4. Ảnh và phân tích kết quả
Có thể chèn ảnh terminal sau khi chạy hai script, ghi tên lệnh và môi trường ngay dưới ảnh. Ảnh UI preview nên ghi rõ được dựng bởi LobbyUiCheck với dữ liệu mẫu; ảnh này chỉ chứng minh giao diện được render trong kiểm tra, không chứng minh server/database đã tham gia.
Kết quả đã xác nhận gồm 96 checks cho TCP/lobby và 26 checks cho giao diện. Các test TCP sử dụng kho dữ liệu giả nên không xác nhận JDBC/MySQL. Các kiểm tra UI không thực hiện một trận đấu thật. Vì vậy, gameplay end-to-end, MySQL thật, LAN và tải nhiều Client vẫn cần kết quả riêng trước khi có thể kết luận đã được kiểm thử.
CHƯƠNG 8. HƯỚNG DẪN CÀI ĐẶT VÀ SỬ DỤNG
8.1. Yêu cầu cài đặt
- JDK 17 trở lên trên máy chạy Client và Server.
- MySQL 8 trở lên trên máy chạy Server.
- MySQL Connector/J 9.4.0 cho Server; script run-server.bat sẽ tìm hoặc tải driver nếu chưa có.
- Mở TCP port 5000 trên firewall của máy Server nếu Client kết nối qua LAN.
- Máy Client cần truy cập được địa chỉ IP/hostname và port của Server; không cần MySQL.
8.2. Cài đặt Database và cấu hình Server
Từ thư mục gốc dự án, tạo database và bảng cho lần cài mới:
mysql -u root -p -e "source server/database/schema.sql"
Nếu nâng cấp database cũ, chạy migration:
mysql -u root -p -e "source server/database/migrate_player_profile.sql"
Nên tạo tài khoản MySQL riêng cho ứng dụng thay vì dùng root. Cấp quyền phù hợp để Server đọc tài khoản, tạo tài khoản, cập nhật thống kê và ghi lịch sử. Do Server có bước khởi tạo/điều chỉnh schema, quyền DDL cần được cấp theo cách triển khai thực tế.
Tạo file server/.env trên máy chạy Server:
TANK_DB_URL=jdbc:mysql://127.0.0.1:3306/tank_game?serverTimezone=Asia%2FHo_Chi_Minh
TANK_DB_USER=tank_app
TANK_DB_PASSWORD=<mật-khẩu-của-tài-khoản-ứng-dụng>
Không đưa mật khẩu thật vào mã nguồn hoặc báo cáo. Biến môi trường hệ điều hành được ưu tiên hơn giá trị trong file .env.
8.3. Khởi chạy ứng dụng
1. Khởi động MySQL và kiểm tra database tank_game đã được tạo.
2. Mở terminal tại thư mục server và chạy run-server.bat.
3. Trên cùng máy, mở terminal tại thư mục gốc và chạy client\run-client.bat.
4. Trên máy khác, chạy client\run-client.bat <server-ip> 5000.
Server mặc định lắng nghe cổng 5000; có thể truyền port làm đối số khi chạy. Client nhận host/port từ đối số dòng lệnh hoặc biến TANK_SERVER_HOST và TANK_SERVER_PORT. Nếu không kết nối được, kiểm tra Server đã khởi động, địa chỉ/port chính xác, firewall cho phép TCP và cấu hình JDBC/driver hợp lệ.
8.4. Hướng dẫn người chơi
1. Tại màn hình đăng nhập, đăng nhập bằng tài khoản hiện có hoặc mở màn hình đăng ký để tạo tài khoản.
2. Sau khi vào lobby, xem tên, điểm và trạng thái của người chơi. Chọn một đối thủ đang rảnh để gửi lời mời.
3. Người nhận chọn Chấp nhận hoặc Từ chối. Người gửi có thể hủy lời mời đang chờ; lời mời không được phản hồi sẽ hết hạn sau 30 giây.
4. Khi được chấp nhận, hai người vào phòng chờ và đợi Server đếm ngược 3–2–1.
5. Trong trận, dùng phím trái/phải để di chuyển, Space để bắn và Exit để rời trận. Trạng thái HP, đạn và thời gian được cập nhật từ Server.
6. Khi trận kết thúc, xem kết quả. Chỉ khi cả hai chọn chơi tiếp thì ván mới bắt đầu. Có thể chuyển sang ranking/history hoặc đăng xuất.
Giao diện và chức năng có thể thay đổi theo bản tích hợp đang chạy; hướng dẫn cuối cùng cần được đối chiếu với phiên bản nhóm trình diễn.
CHƯƠNG 9. ĐÁNH GIÁ, HẠN CHẾ VÀ HƯỚNG PHÁT TRIỂN
9.1. Kết quả đạt được
Đối chiếu với mục tiêu, source hiện có hai ứng dụng Client và Server độc lập, giao tiếp qua TCP. Các module tài khoản, session, lobby/thách đấu, phòng chờ, gameplay, kết quả, ranking/history và giao diện đã có trong mã nguồn. Server giữ vai trò quản lý trạng thái trận; dữ liệu tài khoản và kết quả được thiết kế truy cập qua JDBC/MySQL.
Các bằng chứng kiểm thử hiện có gồm test tích hợp TCP/lobby đạt 96 checks và kiểm tra giao diện in PASS với 26 checks. Kết quả này chỉ áp dụng trong phạm vi test: phần lobby dùng kho tài khoản bộ nhớ; phần UI sử dụng dữ liệu kiểm tra và có lưu ý tiến trình không tự thoát.
9.2. Hạn chế
- TCP chưa được mã hóa bằng TLS.
- Chưa có kết quả kiểm thử gameplay end-to-end giữa hai Client trong các script hiện có.
- Chưa xác nhận thao tác JDBC/MySQL thật, kết nối nhiều máy qua LAN hoặc kiểm thử tải.
- Trạng thái trận giữ trong bộ nhớ Server nên không được khôi phục sau khi Server dừng đột ngột.
- Script UI in PASS nhưng tiến trình không tự kết thúc trong lần chạy đã ghi nhận.
- Một số nội dung README còn chưa đồng bộ với chức năng hiện có trong source; cần cập nhật theo bản tích hợp thực tế.
Do đó, số worker tối đa cấu hình trong source không nên được xem là số Client đã chứng minh có thể phục vụ ổn định. Mức độ hoàn thành cuối cùng cần được đánh giá sau khi nhóm chạy kiểm thử tích hợp và thu thập ảnh minh chứng.
9.3. Hướng phát triển
- Bổ sung TLS để bảo vệ dữ liệu trên đường truyền.
- Bổ sung kiểm thử tự động cho gameplay, JDBC/MySQL, ranking/history, xử lý kết quả và chơi lại.
- Kiểm thử nhiều Client và nhiều máy qua LAN; đo tải và xác định giới hạn thực tế.
- Xây dựng cơ chế reconnect hoặc khôi phục hợp lý khi Client/Server mất kết nối.
- Cải thiện logging, cấu hình và xử lý lỗi; đồng bộ README, protocol và hướng dẫn triển khai.
Đây là các hướng đề xuất, không phải chức năng được khẳng định đã hoàn thành.
CHƯƠNG 10. KẾT LUẬN
10.1. Tổng kết
Đề tài xây dựng Tank Arena theo mô hình Client–Server, sử dụng Java Swing cho giao diện, TCP Socket cho trao đổi dữ liệu và JDBC/MySQL cho lưu trữ. Hệ thống tổ chức các luồng tài khoản, lobby/thách đấu, phòng đấu, gameplay, kết quả và tra cứu. Server quản lý trạng thái tập trung để các Client nhận dữ liệu trận thống nhất.
10.2. Tự đánh giá và bài học
Quá trình thực hiện giúp nhóm vận dụng kiến thức về socket, giao thức ứng dụng, xử lý đồng thời, đồng bộ trạng thái và truy cập cơ sở dữ liệu. Việc chia hệ thống thành các module giúp phân công và tích hợp, đồng thời cho thấy tầm quan trọng của việc thống nhất tên message, thứ tự payload và trách nhiệm của từng thành phần.
Sản phẩm đã có các module chính trong source và có kết quả kiểm tra cho TCP/lobby cùng giao diện. Tuy nhiên, để đánh giá đầy đủ mức độ hoàn thành, nhóm cần bổ sung minh chứng chạy gameplay thật, JDBC/MySQL và kết nối LAN; đồng thời xác nhận thông tin môi trường, phân công thành viên và kết quả cuối cùng trước khi nộp báo cáo.
TÀI LIỆU THAM KHẢO
Liệt kê giáo trình môn Lập trình mạng, tài liệu Java/JDBC, tài liệu MySQL và các tài liệu kỹ thuật mà nhóm thực sự sử dụng. Thống nhất một kiểu trích dẫn trong toàn báo cáo; chỉ đưa vào danh sách những tài liệu đã đọc hoặc được dùng để xây dựng sản phẩm.
PHỤ LỤC
Phụ lục A — Bảng protocol đầy đủ và payload từng message.
Phụ lục B — Script schema, migration và hướng dẫn cấu hình.
Phụ lục C — Test case chi tiết, log hoặc ảnh kết quả chạy.
Phụ lục D — Các sơ đồ kích thước lớn, ảnh giao diện bổ sung và đoạn mã tiêu biểu.
Đưa vào phụ lục những nội dung bổ trợ; trong chương chính vẫn cần dẫn chiếu và giải thích các kết quả quan trọng. Không đưa password, thông tin kết nối bí mật hoặc kết quả kiểm thử chưa thực hiện vào phụ lục.