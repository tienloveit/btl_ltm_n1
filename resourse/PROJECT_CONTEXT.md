# Project Context: Game Ban Xe Tang Online

Tai lieu nay la nguon tham chieu khi phan tich va code project. Doc tai lieu nay truoc khi thay doi chuc nang game. Noi dung duoc tong hop tu mo ta yeu cau cua nguoi dung va PDF `resourse/Le Xuan Tien_B23DCCN818_Nhom1.pdf` (ten file PDF thuc te co dau). Tai lieu mo ta yeu cau/thiet ke, khong khang dinh cac chuc nang da duoc code.

## 1. Trang thai workspace

- Workspace co cac thu muc `client/`, `server/`, `resourse/`.
- Khung Java ban dau hien nam trong `client/` va `server/`; truoc khi sua tiep, kiem tra source thuc te va cap nhat context theo implementation.
- Dinh huong nguoi dung da neu: Java thuần, client giao dien bang Java Swing; ket noi client-server theo TCP Socket. Client va server la hai chuong trinh/doc lap, build va chay rieng.
- Mot server phuc vu nhieu client; moi client co the duoc chay tren mot may khac va ket noi toi cung server qua mang. Client khong duoc mac dinh chi ket noi `localhost`, khong khoi dong server ben trong client; dia chi IP/hostname va port server phai cau hinh duoc.
- PDF de cap den TCP Socket giua client va server va JDBC giua server va database. Server hien dung JDBC/MySQL cho tai khoan; client khong ket noi truc tiep database.
- Source root la `client/src` va `server/src` (Java thuần, build bang `javac`/`.bat`, khong dung Maven); package tach theo trach nhiem: client `app/controller/network/protocol/model/ui`; server `app/auth/persistence/protocol/session`; integration test nam trong `server/test`.
- Server doc `TANK_DB_URL`, `TANK_DB_USER`, `TANK_DB_PASSWORD` de ket noi MySQL; schema nam o `server/database/schema.sql`. `auth/AccountService` xu ly validate/hash/xac thuc, `persistence/JdbcAccountRepository` thuc hien JDBC.

### Rang buoc chay doc lap

- Server mo mot cong TCP tren may server, chap nhan nhieu ket noi dong thoi va quan ly cac session.
- Client chi can chuong trinh Java client va cau hinh dia chi server (hostname/IP, port); khong can source, tien trinh hay database tren may client.
- Giao tiep client-server chi qua protocol TCP da thong nhat; khong dung truc tiep class state trong bo nho cua nhau.
- Co the chia se tai lieu/schema protocol; neu tach module protocol dung chung thi module do phai dong goi rieng va co mat o ca hai ben, khong lam client phu thuoc vao viec chay chung tien trinh voi server.
- Khi chay tren LAN/Internet, server phai bind vao interface phu hop va firewall cho phep port; loi ket noi phai duoc hien thi ro tren client.
- Server phai xu ly nhieu client dong thoi bang multithreading: mot luong accept ket noi va cac worker/session xu ly doc message cho tung client. Dung `ExecutorService`/thread pool co gioi han phu hop thay vi de moi client tao thread tuy y khong gioi han.
- Du lieu session va danh sach nguoi choi dung chung phai thread-safe (vi du `ConcurrentHashMap` va thao tac nguyen tu/co khoa khi chuyen trang thai). Bao dam moi socket chi co mot luong doc; cac luong ghi tren cung socket phai duoc tuan tu hoa de message khong bi xen ke.
- Khi disconnect, worker phai don dep session dung mot lan, dong socket/tai nguyen va cap nhat danh sach online cho cac client con ket noi.

## 2. Pham vi phan viec cua nguoi dung

Day la pham vi duy nhat can thiet ke va trien khai cho phan viec cua nguoi dung, bang Java thuần va Java Swing o client:

- Client-server connection va xu ly Login.
- Quan ly tai khoan nguoi choi.
- Quan ly trang thai `Online`, `Waiting`, `Playing`.
- Giao dien Login.
- Giao dien thong tin nguoi choi.
- Giao dien danh sach nguoi choi Online.
- Xu ly Logout va Disconnect.

### Luong can bao phu trong pham vi nay

1. **Ket noi:** client mo TCP socket toi server; hien thi loi ket noi that bai va cho phep thu lai/dong ung dung an toan.
2. **Login:** giao dien nhan thong tin dang nhap -> client gui yeu cau login -> server xac thuc tai khoan -> client nhan ket qua. Chi khi thanh cong moi hien giao dien thong tin nguoi choi va danh sach online.
3. **Tai khoan:** form SIGN UP theo PDF gom username, password, rewrite password va email; server validate va luu password dang hash, khong luu password goc.
4. **Trang thai:** server quan ly va phat trang thai `Online`, `Waiting`, `Playing` cho client. Client hien thi trang thai nhan duoc, khong tu quyet dinh trang thai chinh thuc. Khi mat ket noi/dang xuat, server loai nguoi choi khoi danh sach online va cap nhat cho cac client con ket noi.
5. **Danh sach va thong tin:** client hien thi thong tin nguoi dang nhap va danh sach nguoi choi online theo du lieu server gui; cap nhat khi co login, logout, disconnect hoac thay doi trang thai.
6. **Logout/Disconnect:** logout chu dong gui yeu cau den server truoc khi dong socket; disconnect bat ngo duoc server phat hien va don dep session. Ca hai truong hop can cap nhat danh sach online va giai phong tai nguyen.

Gameplay tank, loi moi/thach dau, room/tran dau, ranking va match history la boi canh he thong trong PDF, khong thuoc pham vi phan viec nay va khong duoc tu y trien khai them.

## 3. Muc tieu he thong

- Game doi khang ban xe tang online, gom mot server trung tam va nhieu client.
- Server la nguon su that duy nhat cho trang thai tran: vi tri tank, dan, HP, ammo, timer, va ket qua. Client gui input va hien thi trang thai server gui ve; khong tu quyet dinh va cham hay ket qua.
- Ho tro dang ky/dang nhap, danh sach nguoi choi, loi moi thach dau, tran dau 1v1, xem lich su ca nhan, bang xep hang, va choi lai.

## 4. Nguoi choi va lobby

- Sau khi dang nhap, hien danh sach nguoi choi online voi ten, tong diem va trang thai.
- Trang thai duoc neu trong mo ta yeu cau: dang cho, dang choi, roi khoi. PDF dung `ONLINE`, `PLAYING`, `OFFLINE`; can thong nhat ten va cach hien thi truoc khi code.
- Nguoi choi chon mot nguoi dang online de gui loi moi.
- Nguoi duoc moi co the chap nhan (`ACCEPT`) hoac tu choi (`REJECT`). Chi tao room/tran sau khi chap nhan.
- Co chuc nang dang xuat va xu ly mat ket noi; PDF mo ta cap nhat online/offline khi login/logout.

## 5. Luat tran dau

- Tran 1v1; hai xe tang o hai phia cua san dau, chi di chuyen ngang trai/phai va khong vuot khoi bien san.
- Phim mui ten trai/phai dieu khien di chuyen; Space ban dan theo huong doi thu.
- Moi nguoi bat dau voi 100 HP va 50 vien dan; thoi luong tran la 3 phut.
- Moi lan dan trung xe tang doi phuong gay 20 sat thuong.
- Gioi han toc do ban bang cooldown va/hoac gioi han so dan dang ton tai tren man hinh de tranh spam. Gia tri cooldown, suc chua dan tren man hinh, toc do tank va toc do dan chua duoc quy dinh.
- Server tinh vi tri, di chuyen dan, va cham, HP, ammo, timer va dong bo cung mot trang thai cho ca hai client.
- Tran ket thuc khi HP cua mot tank ve 0; mo ta cung neu het gio va thoat tran la cac tinh huong ket thuc. Cach xu ly hoa khi het gio chua duoc quy dinh.
- Neu mot nguoi nhan Exit trong tran, nguoi do thua va doi thu duoc thong bao.
- Sau ket qua, moi ben co the chon choi tiep. Chi mo tran moi khi ca hai dong y; neu khong, quay ve danh sach online/lobby.

## 6. Ket qua, diem va du lieu

- Luu tai khoan, diem, so tran thang/thua/hoa va lich su tran.
- Sau tran, cap nhat ket qua cho ca hai nguoi va gui thong bao ket thuc.
- Bang xep hang can co cac tieu chi sap xep, nhung mo ta ban dau chua liet ke cu the tieu chi/thu tu uu tien.
- PDF cho thay database chua `User / Account`, `Match History`, `Ranking / Statistics`.
- Cac truong bang va quy tac cong/tru diem chua duoc chot. Khong tu suy dien diem thuong/phat hoac cach xu ly tran hoa.

## 7. Kien truc va trach nhiem

### Server

- Authentication: xac thuc tai khoan va xu ly dang ky/dang nhap.
- Player Manager: quan ly nguoi choi, ket noi va trang thai online.
- Lobby Manager: danh sach nguoi choi va cac thao tac lobby.
- Challenge Manager: gui, chap nhan, tu choi va het han loi moi.
- Room / Match Manager: tao room sau khi accept, ket thuc tran va dieu phoi choi lai.
- Game State Manager: cap nhat va phat trang thai tran.
- Collision / Game Logic: di chuyen, dan, va cham, HP, ammo, timer va dieu kien ket thuc.
- Ranking / History: truy van bang xep hang va lich su; ghi ket qua tran vao database.
- Chiu trach nhiem validate input va khong tin client ve vi tri, va cham, HP hay ket qua.

### Client

- Giao dien dang nhap/dang ky, lobby, loi moi, game, ket qua, bang xep hang va lich su.
- Gui cac hanh dong nguoi choi qua ket noi toi server.
- Render `GAME_STATE` server phat; khong tu cap nhat ket qua chinh thuc.

### Database

- Luu thong tin tai khoan va thong ke lau dai.
- Trang thai ket noi thoi gian thuc nen do server quan ly; chi ghi vao DB neu thiet ke sau nay can persistence cho trang thai do.

## 8. Protocol tham chieu trong PDF

| Message | Huong | Y nghia trong PDF |
|---|---|---|
| `REGISTER` | Client -> Server | Gui thong tin tao tai khoan moi |
| `REGISTER_SUCCESS` | Server -> Client | Dang ky thanh cong |
| `REGISTER_FAIL` | Server -> Client | Dang ky that bai, kem ma/ly do loi |
| `LOGIN` | Client -> Server | Dang nhap |
| `LOGIN_SUCCESS` | Server -> Client | Dang nhap thanh cong |
| `LOGIN_FAIL` | Server -> Client | Dang nhap that bai |
| `PLAYER_LIST` | Server -> Client | Danh sach nguoi choi online |
| `CHALLENGE` | Client -> Server hoac Server -> Client | Gui/chuyen loi moi thach dau |
| `ACCEPT` | Client -> Server | Chap nhan loi moi |
| `REJECT` | Client -> Server | Tu choi loi moi |
| `MATCH_START` | Server -> Client | Bat dau tran |
| `MOVE_LEFT` | Client -> Server | Yeu cau di chuyen trai |
| `MOVE_RIGHT` | Client -> Server | Yeu cau di chuyen phai |
| `SHOOT` | Client -> Server | Yeu cau ban |
| `GAME_STATE` | Server -> Client | Dong bo trang thai game |
| `GAME_OVER` | Server -> Client | Ket thuc tran |
| `PLAY_AGAIN` | Client -> Server | Yeu cau choi lai |
| `EXIT` | Client -> Server | Thoat tran/roi phong |

`REGISTER`, `REGISTER_SUCCESS`, va `REGISTER_FAIL` duoc bo sung tu yeu cau co giao dien dang ky; PDF co giao dien Sign Up nhung khong liet ke message dang ky trong bang protocol. Payload de xuat cho `REGISTER` gom username, password va cac truong dang ky can thiet; khong gui password dang ro qua mang neu co the dung TLS va chinh sach bao mat phu hop. Server validate du lieu, kiem tra username/email trung lap, luu password bang hash an toan (khong luu password goc), roi tra thanh cong hoac loi. Schema va truong dang ky cu the can chot truoc khi code.

PDF con goi y cac message `ACTION: MOVE/FIRE`, `UPDATE_STATE`, `RESULT` trong so do luong giao tiep. Can chon mot bo ten message thong nhat; bang tren gom message ro nhat trong PDF va bo sung protocol dang ky, khong phai protocol da duoc cai dat.

Protocol chua quy dinh payload, ma loi, ID tran, ID loi moi, sequence/timestamp cho input, co che xac thuc sau login, hay cach xu ly message lap/tre. Chot cac noi dung nay khi thiet ke API/network; khong tu coi ten message la schema day du.

## 9. Cac luong tham chieu

1. **Register:** client gui username, email va password sau khi xac nhan rewrite password khop -> server validate va tao tai khoan -> tra `REGISTER_SUCCESS` hoac `REGISTER_FAIL`.
2. **Login:** form PDF chi co username/password va link Sign up -> server xac thuc -> gui thanh cong/that bai. Thanh cong thi set `ONLINE`, gui thong tin/thong ke ca nhan va danh sach online.
3. **Challenge:** client A gui loi moi -> server chuyen den B -> B accept/reject -> neu accept, server tao room va gui `MATCH_START` cho ca hai.
4. **Gameplay:** client gui input -> server validate va ap dung input -> server tinh vat ly/va cham -> phat `GAME_STATE` cho hai ben; lap lai den khi tran ket thuc.
5. **End game:** server xac dinh thang/thua/hoa theo dieu kien da chot -> cap nhat thong ke/lich su -> gui ket qua -> hoi choi lai -> chi reset tran khi ca hai dong y.
6. **Disconnect/Exit:** server cap nhat trang thai lobby; neu dang trong tran, xu ly theo quy tac forfeiture khi nguoi choi chu dong thoat. Quy tac mat ket noi dot ngot/chinh sach cho reconnect can duoc chot rieng.

## 10. Cac diem can xac nhan khi bat dau implementation

- Ten va tap gia tri trang thai nguoi choi: `WAITING/PLAYING/LEFT` theo mo ta hay `ONLINE/PLAYING/OFFLINE` theo PDF.
- Sap xep ranking va cong thuc tinh diem; ket qua het gio va dieu kien hoa.
- Cac truong dang ky, quy tac validate, chinh sach username/email trung lap, chinh sach password va viec tu dang nhap sau khi dang ky.
- Payload chinh xac va bo ten message duy nhat; PDF co su khac nhau giua bang protocol va so do.
- Toc do di chuyen/ban, cooldown, so dan toi da tren man hinh, huong/toc do dan, kich thuoc san va kich thuoc va cham.
- Hanh vi khi mat ket noi, timeout ket noi, reconnect, va khi nguoi choi roi lobby/room.
- Stack va schema database sau khi xem source/config hien huu.

## 11. Ghi chu ve pham vi trong PDF

Trang “Phan ca nhan” trong PDF ghi phan viec ca nhan la client-server connection/login va logout/disconnect. Day la thong tin phan cong trong tai lieu nhom, khong tu dong thay the cac yeu cau gameplay tong the o tren.

## Huong dan cho cac lan lam viec tiep theo

### Cập nhật phạm vi hiện tại: Người 2 — Lobby & Challenge

Người dùng xác nhận mình là Người 2 và yêu cầu triển khai cả UI lẫn luồng online theo ảnh mẫu. Phạm vi này thay thế giới hạn cá nhân Người 1 ở mục 2 cho công việc hiện tại. Giữ Java thuần/Swing/TCP, bố cục mẫu kết hợp theme tối/xanh hiện có.

- Đã triển khai Lobby, lời mời Accept/Reject/Cancel/timeout 30 giây, tạo phòng, đếm ngược 3–2–1 trên Server, thoát và disconnect.
- `ONLINE` = rảnh (hiển thị Đang chờ), `WAITING` = lời mời/phòng chờ, `PLAYING` = đã phát MATCH_START; offline loại khỏi roster.
- Gameplay, cộng điểm, ranking/history và Play Again chưa triển khai; có callback tích hợp cho Người 3/4. Chi tiết hành vi và payload: `LOBBY_PROTOCOL.md`.
- Luật mới người dùng đã chốt: 100 HP, 50 đạn, 3 phút, 20 sát thương/đạn; hết giờ/đạn so HP, bằng HP thì hòa; thắng +3 điểm, thua +0, hòa mỗi bên +1. Ranking: điểm giảm, thắng giảm, hòa giảm, thua tăng, rồi tên. Các mô tả “chưa chốt” tương ứng ở mục 5/6/10 đã được thay thế bởi yêu cầu này.
- `server/run-lobby-tests.bat` kiểm tra TCP với kho tài khoản bộ nhớ; `client/run-ui-checks.bat` kiểm tra UI và xuất preview. Server thật vẫn dùng JDBC/MySQL.

### Nguyên tắc làm việc

- Truoc tien doc file nay va kiem tra source hien tai; cap nhat hieu biet theo code thuc te neu project da thay doi.
- Neu mo ta yeu cau, PDF va source mau thuan, neu ro mau thuan va hoi/ghi nhan truoc khi tu chon mot hanh vi.
- Giu server authoritative cho gameplay va ket qua.
- Chi sua code khi nguoi dung yeu cau; neu chi hoi doc/phan tich thi khong tao hoac sua ma nguon.
- Khi bo sung quyet dinh, cap nhat muc tuong ung trong file nay de lam context lau dai.
