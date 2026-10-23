# Nhạc nền tự chọn

Hai bài đang dùng được nhập từ file WAV trên máy người dùng:

| Vị trí | Tệp trong dự án | File gốc |
|---|---|---|
| Lobby và phòng chờ | `lobby.wav` | `the_mountain-battle-wave-136095_PCM16_mono_44100.wav` |
| Trong trận và màn kết quả | `battle.wav` | `stereo_color-battle-drum-493709_PCM16_mono_44100.wav` |

`sources.json` lưu tên file gốc, mã SHA-256 của file gốc/đầu ra và thời lượng.
Các tệp gốc được giữ nguyên; công cụ chỉ ghi vào thư mục tài nguyên của client.

Nhạc dùng định dạng WAV PCM 16-bit, mono, 44.100 Hz, chuẩn hóa đỉnh âm lượng
về 85% và nối vòng với đoạn chồng 250 ms. Nhạc mặc định ở mức 20% và chuyển
mức âm trong 300 ms. Khi đếm 3–2–1 và hiện kết quả, nhạc giảm còn 30% mức
đã chọn (ví dụ chọn 20% thì còn 6%).

## Đổi nhạc từ máy tính

Chuẩn bị hai file WAV PCM 16-bit, mono, 44.100 Hz, sau đó chạy từ thư mục gốc dự án:

```powershell
python client/tools/prepare_music.py --lobby "D:\Nhac\lobby.wav" --battle "D:\Nhac\battle.wav"
```

Thay hai đường dẫn ví dụ bằng đường dẫn file của bạn. Công cụ kiểm tra cả hai
file trước khi thay nhạc, không tải nhạc trên mạng và chỉ dùng thư viện chuẩn Python.
Nếu thiếu tham số hoặc file không hợp lệ, các bản nhạc hiện có được giữ nguyên.

Tắt client và chạy lại `client/run-client.bat` để đưa nhạc mới vào game.
Game phát WAV đã đóng gói, không phụ thuộc đường dẫn file gốc trong Downloads.

Nút **Cài đặt** ở Lobby, phòng chờ, trong trận và màn kết quả mở hai thanh kéo
**Nhạc nền** và **Hiệu ứng âm thanh**, từ 0–100%. Hiệu ứng bao gồm tiếng bắn,
nổ xe, đếm ngược và giọng thông báo kết quả; mặc định 100%. Mức 0 tắt riêng
kênh tương ứng. Thay đổi có hiệu lực ngay, tự lưu trên máy bằng Java Preferences
và được khôi phục khi mở lại client; không cần chỉnh `.env`.

Bấm bên ngoài hoặc Escape để đóng cài đặt. Khi mở cài đặt trong trận, phím
điều khiển xe và bắn được khóa cho đến khi đóng popup; trận đấu vẫn tiếp tục.
Chuyển toàn màn hình hoặc nhận cập nhật phòng không khởi động lại nhạc;
chơi lại bắt đầu nhạc giao chiến từ đầu.
