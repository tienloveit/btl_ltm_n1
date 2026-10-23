# Game audio

## Tiếng bắn và tiếng xe tăng nổ

Hai hiệu ứng đã được cắt từ file trên máy người dùng và đóng gói trong dự án:

| Hiệu ứng | File trong game | File gốc | Đoạn dùng |
|---|---|---|---|
| Xe tăng bắn đạn | `shot.wav` | `127845__garyq__tank-fire-mixed.wav` | 0,00–0,55 giây |
| Xe tăng hết máu và nổ | `explosion.wav` | `523365__lilmati__bomb-explosion.wav` | 0,78–1,68 giây |

Đầu ra là WAV PCM 16-bit, mono, 44.100 Hz; đỉnh âm lượng được chuẩn hóa về 26%.
Tiếng bắn có fade đầu 2 ms/cuối 60 ms; tiếng nổ có fade đầu 5 ms/cuối 120 ms.
`sources.json` lưu tên file gốc, đoạn cắt và SHA-256 của nguồn/đầu ra.
File gốc được giữ nguyên; game chỉ đọc các tài nguyên trong dự án.

Trong game, mở **Cài đặt → Hiệu ứng âm thanh** để chỉnh âm lượng tiếng bắn,
nổ, trúng đạn, đếm ngược và giọng thông báo. Thanh **Nhạc nền** điều chỉnh riêng
nhạc lobby/phòng chờ và trong trận. Hai mức âm lượng được lưu trên máy.
Kéo một thanh về 0% tắt cả âm đang phát của kênh đó. Cả hai thanh ở 0% thì
game hoàn toàn im lặng; đổi màn hình không tự bật lại âm thanh.

Để nhập lại từ hai WAV 16-bit, 44.100 Hz (mono hoặc stereo), chạy từ thư mục gốc:

```powershell
python client/tools/prepare_effects.py --shot "D:\AmThanh\ban.wav" --explosion "D:\AmThanh\no.wav"
```

Thay đường dẫn ví dụ bằng file trên máy. Công cụ kiểm tra cả hai file trước khi
ghi, chỉ dùng thư viện chuẩn Python và áp dụng các đoạn cắt trong bảng trên.
Sau khi đổi hiệu ứng, tắt client và chạy lại `client/run-client.bat`.

## Giọng thông báo thắng/thua

Hai âm kết quả dùng toàn bộ WAV đã được người dùng cắt sẵn, giữ giọng và hiệu ứng đi kèm:

| Kết quả | File trong game | File gốc | Đoạn dùng |
|---|---|---|---|
| Thua — Defeat | `defeat.wav` | `defeat_2s_4s.wav` | Toàn bộ: 0–2 giây |
| Thắng — Victory | `victory.wav` | `victory_4s_7s.wav` | Toàn bộ: 0–3 giây |

Đầu ra là WAV PCM 16-bit, mono, 44.100 Hz, đỉnh âm lượng 26%, fade đầu 5 ms
và cuối 80 ms. `result_sources.json` lưu đoạn cắt, thời lượng và SHA-256 của
nguồn/đầu ra. Hai WAV nằm trong dự án để có thể đưa lên Git; game không cần
đọc file trong Downloads. Thanh **Hiệu ứng âm thanh** điều chỉnh cả hai âm này.

Nhập lại hai file từ thư mục gốc dự án:

```powershell
python client/tools/prepare_results.py --defeat "C:\Users\ADMIN\Downloads\defeat_2s_4s.wav" --victory "C:\Users\ADMIN\Downloads\victory_4s_7s.wav" --defeat-start 0 --defeat-end 2 --victory-start 0 --victory-end 3
```

Có thể đổi đoạn cắt bằng `--defeat-start`, `--defeat-end`, `--victory-start`,
`--victory-end` (đơn vị giây). Công cụ kiểm tra cả hai file trước khi thay âm,
dùng FFmpeg trên PATH hoặc bản đi kèm `imageio-ffmpeg` nếu có; cũng có thể
truyền `--ffmpeg "D:\Tools\ffmpeg.exe"`. Công cụ nhập dùng FFmpeg để đọc nguồn;
game chỉ cần Java để phát WAV đã đóng gói.
Chạy game bằng `client/run-client.bat` để sao chép hai WAV mới vào classpath.

Sau khi chạy `client/run-ui-checks.bat`, có thể nghe thử riêng hai âm:

```powershell
java -cp client/out tank.client.audio.AudioPreview DEFEAT VICTORY
```

## Other arcade effects

The hit, countdown tick, start and draw effects are generated with Python's
standard library. This command preserves the imported shot, explosion and result audio:

```powershell
python client/tools/generate_audio.py
```

Phòng chờ phát một tiếng tít mỗi giây từ 10 đến 1, không đọc số 3–2–1.
Game chỉ cần Java để đọc các WAV đã đóng gói và không gọi dịch vụ đọc văn bản.
