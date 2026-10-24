"""Generate synthetic UI/hit effects; preserve imported shot/explosion WAVs."""
from pathlib import Path
import math
import random
import struct
import wave

RATE = 44100
OUTPUT = Path(__file__).resolve().parents[1] / "resources" / "audio"


def envelope(t, duration, attack=0.006, release=0.035):
    return min(1.0, t / attack, max(0.0, (duration - t) / release))


def write(name, values):
    peak = max(abs(value) for value in values) or 1.0
    # Conservative peak, also applied on mixers that do not expose a gain control.
    pcm = b"".join(struct.pack("<h", round(value / peak * 0.26 * 32767)) for value in values)
    with wave.open(str(OUTPUT / (name + ".wav")), "wb") as stream:
        stream.setnchannels(1)
        stream.setsampwidth(2)
        stream.setframerate(RATE)
        stream.writeframes(pcm)


def sweep(name, duration, start, end, noise_amount, decay, seed):
    random_source = random.Random(seed)
    phase = 0.0
    low_noise = 0.0
    values = []
    for i in range(round(duration * RATE)):
        t = i / RATE
        frequency = start * (end / start) ** (t / duration)
        phase += math.tau * frequency / RATE
        low_noise += 0.13 * (random_source.uniform(-1, 1) - low_noise)
        tone = math.sin(phase) + 0.2 * math.sin(phase * 2)
        value = tone * (1 - noise_amount) + low_noise * noise_amount * 3
        values.append(value * math.exp(-decay * t) * envelope(t, duration))
    write(name, values)


def melody(name, notes, note_duration):
    values = []
    for frequency in notes:
        for i in range(round(note_duration * RATE)):
            t = i / RATE
            phase = math.tau * frequency * t
            tone = math.sin(phase) + 0.18 * math.sin(phase * 3)
            values.append(tone * envelope(t, note_duration, release=0.045))
    write(name, values)


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    sweep("hit", 0.17, 420, 95, 0.65, 16, 2)
    melody("countdown_tick", [880, 1174.66], 0.085)
    melody("start", [330, 440, 660], 0.13)
    melody("draw", [392, 523.25, 392], 0.17)
    print("Generated 4 synthetic WAV effects in", OUTPUT, "(imported shot/explosion preserved)")


if __name__ == "__main__":
    main()
