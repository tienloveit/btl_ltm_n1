"""Trim local victory/defeat recordings and bundle them as portable PCM WAV files."""
from array import array
import argparse
import hashlib
from io import BytesIO
import json
import math
from pathlib import Path
import shutil
import subprocess
import sys
import wave

RATE = 44100
PEAK = 0.26
FADE_IN = 0.005
FADE_OUT = 0.08
OUTPUT = Path(__file__).resolve().parents[1] / "resources" / "audio"


def find_ffmpeg(explicit):
    if explicit:
        return str(explicit.resolve(strict=True))
    installed = shutil.which("ffmpeg")
    if installed:
        return installed
    try:
        import imageio_ffmpeg
        return imageio_ffmpeg.get_ffmpeg_exe()
    except ImportError:
        raise ValueError("FFmpeg is required for importing MP3; supply --ffmpeg or install it on PATH.") from None


def prepare(path, start, end, ffmpeg):
    if not math.isfinite(start) or not math.isfinite(end) or start < 0 or end <= start:
        raise ValueError("Crop times must be finite, with 0 <= start < end.")
    path = path.resolve(strict=True)
    if path in {(OUTPUT / (name + ".wav")).resolve() for name in ("victory", "defeat")}:
        raise ValueError("Choose an original recording outside the generated result files.")
    original = path.read_bytes()
    decoded = subprocess.run(
        [ffmpeg, "-v", "error", "-nostdin", "-i", str(path), "-map", "0:a:0", "-vn",
         "-ac", "1", "-ar", str(RATE), "-c:a", "pcm_s16le", "-f", "s16le", "pipe:1"],
        capture_output=True, timeout=60)
    if decoded.returncode:
        raise ValueError("Could not decode " + path.name + ": "
                         + decoded.stderr.decode("utf-8", errors="replace").strip())
    first, last = round(start * RATE), round(end * RATE)
    if len(decoded.stdout) < last * 2:
        raise ValueError("Source is shorter than the selected segment: " + path.name)
    samples = array("h")
    samples.frombytes(decoded.stdout[first * 2:last * 2])
    if sys.byteorder != "little":
        samples.byteswap()
    attack, release = round(FADE_IN * RATE), round(FADE_OUT * RATE)
    values = [sample * min(1.0, i / attack, (len(samples) - 1 - i) / release)
              for i, sample in enumerate(samples)]
    peak = max(map(abs, values), default=0)
    if peak == 0:
        raise ValueError("Selected segment is silent: " + path.name)
    gain = PEAK * 32767 / peak
    pcm = array("h", (round(sample * gain) for sample in values))
    if sys.byteorder != "little":
        pcm.byteswap()
    output = BytesIO()
    with wave.open(output, "wb") as target:
        target.setnchannels(1)
        target.setsampwidth(2)
        target.setframerate(RATE)
        target.writeframes(pcm.tobytes())
    prepared = output.getvalue()
    return prepared, {
        "source_file": path.name,
        "original_sha256": hashlib.sha256(original).hexdigest(),
        "source_seconds": len(decoded.stdout) / (RATE * 2),
        "crop_start_seconds": first / RATE,
        "crop_end_seconds": last / RATE,
        "seconds": len(samples) / RATE,
        "fade_in_seconds": FADE_IN,
        "fade_out_seconds": FADE_OUT,
        "prepared_sha256": hashlib.sha256(prepared).hexdigest(),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--defeat", required=True, type=Path, help="Original defeat recording")
    parser.add_argument("--victory", required=True, type=Path, help="Original victory recording")
    parser.add_argument("--defeat-start", type=float, default=0.0)
    parser.add_argument("--defeat-end", type=float, default=4.1)
    parser.add_argument("--victory-start", type=float, default=1.8)
    parser.add_argument("--victory-end", type=float, default=5.4)
    parser.add_argument("--ffmpeg", type=Path, help="Optional path to the FFmpeg executable")
    args = parser.parse_args()
    # Decode and validate both recordings before changing either bundled sound.
    try:
        ffmpeg = find_ffmpeg(args.ffmpeg)
        results = {name: prepare(getattr(args, name), getattr(args, name + "_start"),
                                 getattr(args, name + "_end"), ffmpeg)
                   for name in ("defeat", "victory")}
    except (OSError, ValueError, subprocess.SubprocessError) as error:
        parser.error(str(error))
    OUTPUT.mkdir(parents=True, exist_ok=True)
    manifest = {"source": "user-selected local result recordings", "peak": PEAK,
                "sample_rate": RATE, "channels": 1, "sample_bits": 16, "effects": {}}
    for name, (prepared, info) in results.items():
        (OUTPUT / (name + ".wav")).write_bytes(prepared)
        manifest["effects"][name] = info
        print(info["source_file"], "->", name + ".wav", info["seconds"], "seconds")
    (OUTPUT / "result_sources.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
