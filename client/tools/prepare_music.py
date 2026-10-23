"""Import two local PCM WAV files as loopable music (Python standard library only)."""
from array import array
import argparse
import hashlib
from io import BytesIO
import json
from pathlib import Path
import sys
import wave

RATE = 44100
OUTPUT = Path(__file__).resolve().parents[1] / "resources" / "music"


def prepare(path):
    path = path.resolve()
    if path in {(OUTPUT / (name + ".wav")).resolve() for name in ("lobby", "battle")}:
        raise ValueError("Choose an original WAV outside the generated music files: " + str(path))
    original = path.read_bytes()
    with wave.open(BytesIO(original), "rb") as source:
        if (source.getnchannels() != 1 or source.getsampwidth() != 2
                or source.getframerate() != RATE or source.getcomptype() != "NONE"):
            raise ValueError("Expected uncompressed mono 16-bit PCM WAV at 44100 Hz: " + str(path))
        frames = source.getnframes()
        decoded = source.readframes(frames)
        if len(decoded) != frames * 2:
            raise ValueError("Incomplete WAV audio data: " + str(path))
    samples = array("h")
    samples.frombytes(decoded)
    if sys.byteorder != "little":
        samples.byteswap()
    overlap = round(RATE * 0.25)
    if len(samples) < overlap * 4:
        raise ValueError("Music must be at least one second long: " + str(path))
    # Overlap the tail with the head; the next loop continues immediately after the head.
    joined = array("h", samples[overlap:])
    for i in range(overlap):
        weight = i / (overlap - 1)
        joined[len(joined) - overlap + i] = round(samples[-overlap + i] * (1 - weight) + samples[i] * weight)
    peak = max(map(abs, joined))
    if peak == 0:
        raise ValueError("Music file is silent: " + str(path))
    gain = 0.85 * 32767 / peak
    normalized = array("h", (round(value * gain) for value in joined))
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
            "original_seconds": round(frames / RATE, 3), "seconds": round(len(joined) / RATE, 3),
            "prepared_sha256": hashlib.sha256(prepared).hexdigest()}
    return prepared, info


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--lobby", required=True, type=Path, help="Original WAV for lobby and waiting room")
    parser.add_argument("--battle", required=True, type=Path, help="Original WAV for matches")
    args = parser.parse_args()
    # Validate and prepare BOTH files before replacing any existing resources.
    try:
        tracks = {"lobby": prepare(args.lobby), "battle": prepare(args.battle)}
    except (OSError, ValueError, wave.Error, EOFError) as error:
        parser.error(str(error))
    OUTPUT.mkdir(parents=True, exist_ok=True)
    manifest = {"source": "user-selected local WAV files",
                "processing": {"peak": 0.85, "loop_overlap_seconds": 0.25}, "tracks": {}}
    for name, (prepared, info) in tracks.items():
        (OUTPUT / (name + ".wav")).write_bytes(prepared)
        manifest["tracks"][name] = info
        print(info["source_file"], "->", name + ".wav", info["seconds"], "seconds")
    (OUTPUT / "sources.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
