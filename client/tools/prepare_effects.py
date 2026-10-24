"""Import and trim local tank effects as PCM WAVs (Python standard library only)."""
from array import array
import argparse
import hashlib
from io import BytesIO
import json
from pathlib import Path
import sys
import wave

RATE = 44100
OUTPUT = Path(__file__).resolve().parents[1] / "resources" / "audio"
PEAK = 0.26
EFFECTS = {
    "shot": {"start": 0.0, "duration": 0.55, "fade_in": 0.002, "fade_out": 0.06},
    "explosion": {"start": 0.78, "duration": 0.9, "fade_in": 0.005, "fade_out": 0.12},
}


def prepare(path, settings):
    path = path.resolve()
    if path in {(OUTPUT / (name + ".wav")).resolve() for name in EFFECTS}:
        raise ValueError("Choose an original WAV outside the generated effects: " + str(path))
    original = path.read_bytes()
    with wave.open(BytesIO(original), "rb") as source:
        channels = source.getnchannels()
        if (channels not in (1, 2) or source.getsampwidth() != 2
                or source.getframerate() != RATE or source.getcomptype() != "NONE"):
            raise ValueError("Expected mono/stereo 16-bit PCM WAV at 44100 Hz: " + str(path))
        start = round(settings["start"] * RATE)
        frames = round(settings["duration"] * RATE)
        if start + frames > source.getnframes():
            raise ValueError("Source is shorter than the selected effect segment: " + str(path))
        source.setpos(start)
        decoded = source.readframes(frames)
        if len(decoded) != frames * channels * 2:
            raise ValueError("Incomplete WAV audio data: " + str(path))
    samples = array("h")
    samples.frombytes(decoded)
    if sys.byteorder != "little":
        samples.byteswap()
    values = []
    attack = max(1, round(settings["fade_in"] * RATE))
    release = max(1, round(settings["fade_out"] * RATE))
    for frame in range(frames):
        mono = sum(samples[frame * channels:(frame + 1) * channels]) / channels
        fade = min(1.0, frame / attack, (frames - 1 - frame) / release)
        values.append(mono * fade)
    peak = max(map(abs, values), default=0)
    if peak == 0:
        raise ValueError("Selected segment is silent: " + str(path))
    gain = PEAK * 32767 / peak
    normalized = array("h", (round(value * gain) for value in values))
    if sys.byteorder != "little":
        normalized.byteswap()
    output = BytesIO()
    with wave.open(output, "wb") as target:
        target.setnchannels(1)
        target.setsampwidth(2)
        target.setframerate(RATE)
        target.writeframes(normalized.tobytes())
    prepared = output.getvalue()
    info = {"source_file": path.name, "original_sha256": hashlib.sha256(original).hexdigest(),
            "source_channels": channels, "crop_start_seconds": settings["start"],
            "seconds": frames / RATE, "fade_in_seconds": settings["fade_in"],
            "fade_out_seconds": settings["fade_out"],
            "prepared_sha256": hashlib.sha256(prepared).hexdigest()}
    return prepared, info


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--shot", required=True, type=Path, help="Original WAV for tank fire")
    parser.add_argument("--explosion", required=True, type=Path, help="Original WAV for tank destruction")
    args = parser.parse_args()
    # Fully validate both inputs before replacing either bundled effect.
    try:
        effects = {name: prepare(getattr(args, name), settings) for name, settings in EFFECTS.items()}
    except (OSError, ValueError, wave.Error, EOFError) as error:
        parser.error(str(error))
    OUTPUT.mkdir(parents=True, exist_ok=True)
    manifest = {"source": "user-selected local WAV files", "peak": PEAK, "effects": {}}
    for name, (prepared, info) in effects.items():
        (OUTPUT / (name + ".wav")).write_bytes(prepared)
        manifest["effects"][name] = info
        print(info["source_file"], "->", name + ".wav", info["seconds"], "seconds")
    (OUTPUT / "sources.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
