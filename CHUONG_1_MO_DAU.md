CHƯƠNG 1. MỞ ĐẦU

1.1. Lý do chọn đề tài

Sự phát triển của Internet đã tạo điều kiện cho các ứng dụng tương tác trực tuyến, trong đó có trò chơi nhiều người chơi, ngày càng phổ biến. Khác với trò chơi chạy độc lập trên một máy tính, trò chơi trực tuyến cần tiếp nhận thao tác từ nhiều người chơi, trao đổi dữ liệu qua mạng và duy trì trạng thái chung để các bên cùng quan sát một diễn biến nhất quán. Vì vậy, xây dựng một trò chơi trực tuyến là cơ hội để vận dụng các kiến thức về lập trình mạng và tổ chức hệ thống phần mềm.

Từ yêu cầu đó, nhóm lựa chọn đề tài “Game bắn xe tăng trực tuyến (Tank Arena)”. Trò chơi có cách điều khiển tương đối đơn giản nhưng đặt ra nhiều vấn đề kỹ thuật phù hợp với nội dung môn học: thiết lập kết nối giữa Client và Server, quản lý nhiều phiên người chơi, trao đổi thông điệp qua TCP, đồng bộ trạng thái trận đấu, xử lý va chạm và lưu trữ dữ liệu.

Đề tài còn giúp nhóm thực hành việc phân chia một ứng dụng thành các thành phần có trách nhiệm riêng. Client cung cấp giao diện và gửi thao tác của người chơi; Server xử lý nghiệp vụ và giữ vai trò quyết định trạng thái chính thức; cơ sở dữ liệu lưu thông tin tài khoản, thống kê và lịch sử trận đấu. Qua quá trình phát triển và tích hợp các thành phần này, nhóm có cơ hội củng cố kiến thức về lập trình hướng đối tượng, xử lý đồng thời, giao thức ứng dụng và kết nối cơ sở dữ liệu.

1.2. Mục tiêu đề tài

Mục tiêu chung của đề tài là xây dựng một trò chơi bắn xe tăng đối kháng trực tuyến theo mô hình Client–Server. Người chơi sử dụng ứng dụng Client để đăng nhập, tìm đối thủ, tham gia trận đấu và nhận kết quả do Server xử lý.

Các mục tiêu cụ thể gồm:

- Xây dựng ứng dụng Client có giao diện đồ họa và ứng dụng Server hoạt động độc lập; Client có thể cấu hình địa chỉ máy chủ và cổng kết nối.
- Tổ chức các chức năng tài khoản và lobby, gồm đăng ký, đăng nhập, hiển thị hồ sơ, danh sách người chơi, trạng thái hiện diện và lời mời thách đấu.
- Xây dựng luồng chấp nhận lời mời, tạo phòng đấu, đếm ngược và bắt đầu trận 1v1.
- Để Server quản lý trạng thái trận đấu, bao gồm vị trí xe tăng, đạn, HP, số đạn, thời gian, va chạm và điều kiện kết thúc; Client gửi thao tác điều khiển và hiển thị trạng thái nhận được.
- Lưu thông tin tài khoản, thống kê và lịch sử trận đấu bằng cơ sở dữ liệu; hỗ trợ truy vấn bảng xếp hạng và lịch sử người chơi.
- Tích hợp các module của nhóm và kiểm thử các luồng chức năng trong phạm vi môi trường có thể triển khai.

Luật chơi được sử dụng trong hệ thống quy định mỗi người bắt đầu với 100 HP và 50 viên đạn, thời lượng tối đa của một trận là 180 giây, mỗi lần trúng đạn gây 20 HP sát thương. Khi một xe tăng hết HP, đối thủ thắng. Nếu hết giờ hoặc hết đạn, người có HP cao hơn thắng; nếu HP bằng nhau thì trận đấu hòa. Người thắng được cộng 3 điểm, mỗi người được cộng 1 điểm khi hòa, người thua không được cộng điểm. Bảng xếp hạng ưu tiên điểm cao, số trận thắng cao, số trận hòa cao, số trận thua thấp, sau đó sắp xếp theo tên.

1.3. Đối tượng và phạm vi đề tài

1.3.1. Đối tượng

Đối tượng sử dụng trực tiếp của hệ thống là người chơi chạy ứng dụng Client trên máy tính và kết nối tới Server. Mỗi người chơi sử dụng tài khoản riêng để đăng nhập, tham gia lobby, thách đấu và chơi trận với một người chơi khác.

Server là thành phần trung tâm tiếp nhận kết nối, xác thực người chơi, quản lý session và trạng thái trong lobby/phòng/trận. Cơ sở dữ liệu MySQL được sử dụng ở phía Server để lưu trữ dữ liệu lâu dài. Client giao tiếp với Server qua TCP và không kết nối trực tiếp tới cơ sở dữ liệu.

1.3.2. Phạm vi chức năng

Phạm vi chức năng của đề tài bao gồm các luồng chính sau:

- Quản lý tài khoản: đăng ký, đăng nhập và đăng xuất.
- Quản lý người chơi: hiển thị thông tin cá nhân, danh sách người chơi trực tuyến và trạng thái hoạt động.
- Lobby và thách đấu: gửi, nhận, chấp nhận, từ chối hoặc hủy lời mời; xử lý lời mời hết hạn.
- Phòng đấu: tạo phòng sau khi lời mời được chấp nhận, hiển thị trạng thái phòng và thực hiện đếm ngược trước khi bắt đầu.
- Gameplay: điều khiển xe tăng, bắn đạn, cập nhật trạng thái trận, xử lý va chạm và xác định kết quả.
- Kết quả và tra cứu: cập nhật thống kê, lưu lịch sử, xem bảng xếp hạng và lịch sử trận đấu; hỗ trợ lựa chọn chơi lại theo luật của hệ thống.

Trạng thái người chơi được Server quản lý. Trong quá trình hoạt động, các trạng thái chính gồm `ONLINE`, `WAITING` và `PLAYING`; người chơi đã ngắt kết nối được loại khỏi danh sách trực tuyến. Trạng thái trận đấu và kết quả chính thức do Server quyết định nhằm tránh việc mỗi Client tự xác định một diễn biến khác nhau.

1.3.3. Phạm vi triển khai và giới hạn

Đối chiếu với mã nguồn hiện tại, dự án đã có các module Client và Server cho tài khoản, lobby/thách đấu, quản lý phòng, gameplay, xử lý kết quả và truy vấn ranking/history. Đây là mô tả về phạm vi chức năng đã có trong source, không đồng nghĩa mọi chức năng đã được kiểm thử đầy đủ trên môi trường triển khai thực tế.

Các kịch bản kiểm thử hiện có tập trung chủ yếu vào TCP/lobby và kiểm tra giao diện. Việc kiểm thử toàn diện với MySQL thật, kết nối nhiều máy qua LAN và toàn bộ luồng gameplay cần được đánh giá theo kết quả chạy thực tế của nhóm. Vì vậy, báo cáo cần ghi rõ môi trường, dữ liệu kiểm thử và phạm vi đã xác nhận; không kết luận một chức năng đã được kiểm thử end-to-end nếu chưa có minh chứng tương ứng.

Đề tài tập trung vào ứng dụng Java chạy trong môi trường có thể kết nối tới Server. Các nội dung như mã hóa kết nối bằng TLS, khôi phục trận đấu sau khi mất kết nối, triển khai dịch vụ Internet quy mô lớn và kiểm thử tải chuyên sâu được xem là giới hạn hoặc hướng phát triển nếu chưa được triển khai và kiểm chứng.

1.4. Công nghệ và phương pháp thực hiện

1.4.1. Công nghệ sử dụng

- Java 17 trở lên: ngôn ngữ và môi trường phát triển cho Client, Server.
- Java Swing: xây dựng giao diện đồ họa phía Client.
- TCP Socket: thiết lập kết nối và trao đổi thông điệp giữa Client với Server.
- JDBC: kết nối và thực hiện thao tác dữ liệu từ Server tới cơ sở dữ liệu.
- MySQL: lưu trữ tài khoản, thống kê và lịch sử trận đấu.

Hệ thống được tổ chức theo mô hình ba thành phần: Client ↔ Server ↔ MySQL. Client gửi yêu cầu và thao tác của người chơi tới Server, sau đó hiển thị phản hồi hoặc trạng thái nhận được. Server xử lý nghiệp vụ, quản lý các trạng thái dùng chung và truy cập cơ sở dữ liệu khi cần. Client không truy cập trực tiếp MySQL.

Thông tin mật khẩu không được lưu dưới dạng văn bản rõ. Phía Server sử dụng salt ngẫu nhiên và PBKDF2-HMAC-SHA256 để tạo giá trị băm phục vụ xác thực. Kết nối TCP trong phiên bản hiện tại chưa được mã hóa bằng TLS, do đó đây là một giới hạn cần được nêu khi đánh giá hệ thống.

1.4.2. Phương pháp thực hiện

Nhóm thực hiện đề tài theo các bước chính:

1. Tổng hợp mô tả bài toán, luật chơi, yêu cầu chức năng và phân công nhiệm vụ.
2. Phân tích các tác nhân, luồng nghiệp vụ và trách nhiệm của Client, Server, cơ sở dữ liệu.
3. Thiết kế kiến trúc, giao thức trao đổi, mô hình dữ liệu và các luồng tương tác quan trọng.
4. Chia hệ thống thành các module tài khoản, lobby/thách đấu, gameplay, kết quả và giao diện.
5. Cài đặt các module ở Client và Server, sau đó tích hợp thông qua giao thức TCP đã thống nhất.
6. Kiểm tra các luồng đã triển khai bằng test giao thức, test giao diện và các kịch bản phù hợp với môi trường hiện có.

Kết quả kiểm thử trong báo cáo cần phản ánh đúng những kịch bản nhóm đã thực sự chạy. Các chức năng có trong source nhưng chưa được kiểm tra trên môi trường tương ứng phải được ghi nhận riêng, không suy diễn kết quả kiểm thử từ việc chức năng đã được cài đặt.

1.5. Bố cục báo cáo

Ngoài phần mở đầu, kết luận, tài liệu tham khảo và phụ lục, báo cáo gồm các chương chính sau:

- Chương 1 — Mở đầu: trình bày lý do chọn đề tài, mục tiêu, đối tượng, phạm vi, công nghệ và phương pháp thực hiện.
- Chương 2 — Khảo sát và phân tích yêu cầu: mô tả bài toán, tác nhân, yêu cầu chức năng và phi chức năng, luật chơi và các use case.
- Chương 3 — Phân tích và thiết kế hệ thống: trình bày kiến trúc, luồng nghiệp vụ, các sơ đồ thiết kế, mô hình lớp và trạng thái.
- Chương 4 — Thiết kế giao thức mạng: mô tả kết nối TCP, cấu trúc thông điệp, payload và cách xử lý lỗi giao tiếp.
- Chương 5 — Thiết kế và cài đặt cơ sở dữ liệu: trình bày mô hình dữ liệu, cấu trúc bảng, truy cập JDBC và quy trình lưu/truy vấn dữ liệu.
- Chương 6 — Cài đặt chức năng: mô tả cấu trúc chương trình, các module Client/Server, giao diện và quá trình tích hợp.
- Chương 7 — Kiểm thử và đánh giá: nêu môi trường, phương pháp, các test case, kết quả thực tế và phạm vi chưa kiểm thử.
- Chương 8 — Hướng dẫn cài đặt và sử dụng: hướng dẫn chuẩn bị môi trường, cấu hình cơ sở dữ liệu, chạy chương trình và sử dụng các chức năng.
- Chương 9 — Đánh giá, hạn chế và hướng phát triển: đối chiếu kết quả với mục tiêu, chỉ ra giới hạn và đề xuất cải tiến.
- Chương 10 — Kết luận: tổng kết sản phẩm, kiến thức đã áp dụng, bài học và công việc còn lại.


CHƯƠNG 2. KHẢO SÁT VÀ PHÂN TÍCH YÊU CẦU

2.1. Mô tả bài toán

Tank Arena là trò chơi bắn xe tăng trực tuyến 1v1. Người chơi đăng nhập từ Client, tìm đối thủ trong lobby và gửi lời mời thách đấu. Khi lời mời được chấp nhận, Server tạo phòng, đếm ngược và bắt đầu trận. Trong trận, Client gửi thao tác điều khiển; Server cập nhật trạng thái và gửi kết quả đồng bộ cho hai bên. Thông tin tài khoản, thống kê và lịch sử được lưu ở MySQL.

Server là nơi quyết định trạng thái trận, va chạm và kết quả; Client chỉ gửi thao tác và hiển thị trạng thái nhận được.

2.2. Tác nhân

- Người chơi: đăng ký, đăng nhập, xem lobby/hồ sơ, thách đấu, chơi trận, xem kết quả, ranking/history và đăng xuất.
- Server: xác thực, quản lý session/trạng thái/phòng, xử lý trận và trao đổi dữ liệu với Database.
- MySQL: lưu tài khoản, thống kê và lịch sử; không lưu trạng thái kết nối tức thời.

2.3. Yêu cầu chức năng

Mã | Chức năng | Mô tả
--- | --- | ---
F01 | Tài khoản | Đăng ký, đăng nhập, đăng xuất và xử lý mất kết nối.
F02 | Lobby | Hiển thị hồ sơ, danh sách người chơi và trạng thái ONLINE, WAITING, PLAYING.
F03 | Thách đấu | Gửi, nhận, chấp nhận, từ chối, hủy và hết hạn lời mời.
F04 | Phòng đấu | Tạo phòng khi được chấp nhận, đếm ngược, thoát và xử lý disconnect.
F05 | Gameplay | Điều khiển xe, bắn, cập nhật đạn, va chạm, HP và thời gian.
F06 | Kết quả | Xác định thắng/thua/hòa, cập nhật thống kê, lưu lịch sử và hỗ trợ chơi lại.
F07 | Tra cứu | Xem bảng xếp hạng và lịch sử trận đấu.

2.4. Yêu cầu phi chức năng

- Hỗ trợ nhiều Client kết nối đồng thời; quản lý trạng thái dùng chung an toàn.
- Server giữ trạng thái chính thức để hai Client nhận cùng kết quả.
- Xử lý đăng nhập trùng, người chơi bận, yêu cầu không hợp lệ và mất kết nối.
- Mật khẩu được lưu dưới dạng salt và PBKDF2-HMAC-SHA256; truy vấn DB dùng prepared statement. TCP hiện chưa có TLS.
- Client và Server chạy độc lập; Client cấu hình được host/port, chỉ Server kết nối MySQL.

2.5. Luật chơi và tính điểm

2.5.1. Bố trí trận đấu

Mỗi trận có hai người chơi trong cùng một phòng. Xe tăng người chơi 1 ở phía dưới, người chơi 2 ở phía trên; khu vực chiến đấu nằm ở giữa. Cả hai Client cùng hiển thị HP, số đạn, điểm và thời gian trận đấu.

2.5.2. Điều khiển và thông số

- Mũi tên trái/phải: di chuyển xe tăng theo chiều ngang, không vượt khỏi phạm vi màn hình.
- Phím Space: bắn một viên đạn theo hướng đối thủ.
- Mỗi người bắt đầu với 100 HP và 50 viên đạn; thời gian trận tối đa là 3 phút.
- Mỗi lần đạn trúng xe tăng đối phương làm giảm 20 HP.
- Để hạn chế bắn liên tục, hệ thống giới hạn số đạn đồng thời trên màn hình hoặc áp dụng khoảng nghỉ giữa các lần bắn.

2.5.3. Va chạm và kết thúc trận

Server kiểm tra va chạm giữa đạn và xe tăng, cập nhật HP rồi đồng bộ trạng thái cho cả hai Client. Khi một xe tăng hết HP, người chơi còn lại thắng. Nếu hết giờ hoặc hết đạn, người có HP cao hơn thắng; nếu HP bằng nhau thì hòa. Người chơi chủ động nhấn Exit trong trận bị xử lý là thua và Server thông báo cho đối thủ.

2.5.4. Điểm, thống kê và chơi lại

- Thắng: cộng 3 điểm và tăng số trận thắng.
- Thua: không cộng điểm và tăng số trận thua.
- Hòa: mỗi người tăng số trận hòa và được cộng 1 điểm.
- Sau trận, Server gửi kết quả cho hai người và lưu thống kê, lịch sử trận đấu.
- Hai người có thể chọn Play Again hoặc Exit. Chỉ tạo ván mới khi cả hai cùng chọn Play Again; nếu một người chọn Exit, người chơi trở về danh sách online.

2.5.5. Thứ tự bảng xếp hạng

Bảng xếp hạng sắp xếp theo tổng điểm giảm dần, số trận thắng giảm dần, số trận hòa giảm dần và số trận thua tăng dần. Nếu các chỉ số bằng nhau, sắp xếp tiếp theo tên người chơi.

2.6. Use case chính

- Đăng ký và đăng nhập: người chơi gửi thông tin; Server kiểm tra và tạo session nếu hợp lệ.
- Xem lobby: người chơi xem hồ sơ, danh sách và trạng thái đối thủ.
- Thách đấu: người gửi mời người chơi đang rảnh; người nhận chấp nhận/từ chối. Người gửi có thể hủy; lời mời hết hạn sau 30 giây.
- Vào phòng và chơi trận: Server tạo phòng, đếm ngược 3–2–1; Client gửi input, Server cập nhật và đồng bộ trạng thái.
- Kết quả và chơi lại: Server xác định kết quả, cập nhật thống kê/lịch sử; chỉ bắt đầu ván mới khi cả hai chọn chơi tiếp.
- Xem ranking/history và đăng xuất: người chơi yêu cầu dữ liệu hoặc kết thúc phiên; Server phản hồi và cập nhật roster.

Sơ đồ cần vẽ: Hình 2.1 — Use Case tổng quát. Actor chính là Người chơi; các use case gồm Đăng ký/Đăng nhập, Xem lobby, Thách đấu, Phản hồi lời mời, Vào phòng, Chơi trận, Xem kết quả, Chơi lại, Xem ranking/history và Đăng xuất. Server và MySQL thể hiện trong sơ đồ kiến trúc, không cần coi là actor.