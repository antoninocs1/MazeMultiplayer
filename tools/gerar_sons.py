"""
Gera os efeitos sonoros do Labirinto do Ratinho (assets/sounds/*.wav).

Todos os sons são sintetizados aqui (ondas simples + envelopes), então não há
problema de direitos autorais. Para trocar um som, basta substituir o .wav
correspondente por outro arquivo WAV (PCM 16 bits) com o mesmo nome.

Uso:  python tools/gerar_sons.py
"""
import math
import os
import random
import struct
import wave

RATE = 44100
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "assets", "sounds")


def silence(dur):
    return [0.0] * int(RATE * dur)


def tone(freq, dur, wave_type="sine", vol=0.5, attack=0.005, release=0.05, slide=0.0, vibrato=0.0):
    """Um tom com envelope. slide = variação de frequência (Hz) ao longo do som."""
    n = int(RATE * dur)
    out = []
    phase = 0.0
    for i in range(n):
        t = i / RATE
        f = freq + slide * (i / max(1, n - 1))
        if vibrato:
            f *= 1.0 + 0.02 * math.sin(2 * math.pi * vibrato * t)
        phase += 2 * math.pi * f / RATE
        if wave_type == "sine":
            v = math.sin(phase)
        elif wave_type == "square":
            v = 1.0 if math.sin(phase) >= 0 else -1.0
            v *= 0.45
        elif wave_type == "triangle":
            v = 2 / math.pi * math.asin(math.sin(phase))
        elif wave_type == "saw":
            v = ((phase / math.pi) % 2.0) - 1.0
            v *= 0.5
        else:
            v = 0.0
        env = 1.0
        if t < attack:
            env = t / attack
        rem = dur - t
        if rem < release:
            env *= max(0.0, rem / release)
        out.append(v * env * vol)
    return out


def noise(dur, vol=0.4, lowpass=0.1, attack=0.01, release=0.2, sweep=0.0):
    """Ruído filtrado (para 'whoosh' e impactos). sweep muda o filtro ao longo do som."""
    n = int(RATE * dur)
    out = []
    y = 0.0
    rnd = random.Random(7)
    for i in range(n):
        t = i / RATE
        k = min(1.0, max(0.001, lowpass + sweep * (i / max(1, n - 1))))
        y += k * (rnd.uniform(-1, 1) - y)
        env = 1.0
        if t < attack:
            env = t / attack
        rem = dur - t
        if rem < release:
            env *= max(0.0, rem / release)
        out.append(y * env * vol * 3)
    return out


def mix(*tracks):
    n = max(len(t) for t in tracks)
    out = [0.0] * n
    for tr in tracks:
        for i, v in enumerate(tr):
            out[i] += v
    return out


def seq(*parts):
    out = []
    for p in parts:
        out.extend(p)
    return out


def save(name, samples):
    os.makedirs(OUT, exist_ok=True)
    peak = max(1e-9, max(abs(s) for s in samples))
    gain = min(1.0, 0.9 / peak)
    path = os.path.join(OUT, name + ".wav")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, s * gain)) * 32767)) for s in samples))
    print("gerado", os.path.normpath(path))


def main():
    # Menu
    save("menu_move", tone(880, 0.05, "square", 0.25, release=0.03))
    save("menu_select", seq(tone(660, 0.06, "square", 0.3, release=0.03),
                            tone(990, 0.10, "square", 0.3, release=0.06)))

    # Contagem regressiva e largada
    save("countdown", tone(523, 0.16, "square", 0.35, release=0.08))
    save("go", mix(tone(1046, 0.40, "square", 0.35, release=0.25),
                   tone(523, 0.40, "triangle", 0.4, release=0.25)))

    # Paredes do labirinto mudando de lugar
    save("wall_change", mix(noise(0.45, 0.35, lowpass=0.02, sweep=0.10, attack=0.05, release=0.3),
                            tone(110, 0.45, "triangle", 0.35, slide=-40, release=0.3)))

    # Passo do ratinho (bem curtinho e baixo)
    save("step", tone(1800, 0.025, "sine", 0.15, attack=0.002, release=0.02, slide=-600))

    # Ratinho chegou ao queijo (outros jogadores ainda correndo)
    save("finish", seq(tone(784, 0.08, "triangle", 0.4, release=0.04),
                       tone(1175, 0.16, "triangle", 0.4, release=0.1)))

    # Vitória: arpejo ascendente
    notes = [523, 659, 784, 1046]
    win = seq(*[tone(f, 0.11, "square", 0.3, release=0.05) for f in notes[:-1]])
    win = seq(win, mix(tone(1046, 0.5, "square", 0.3, release=0.35, vibrato=6),
                       tone(523, 0.5, "triangle", 0.35, release=0.35)))
    save("win", win)

    # Derrota: descendente
    save("lose", seq(tone(523, 0.16, "triangle", 0.4, release=0.06),
                     tone(440, 0.16, "triangle", 0.4, release=0.06),
                     tone(349, 0.45, "triangle", 0.4, release=0.3, slide=-40)))

    # Gato: miado (sobe e desce, com vibrato)
    meow = seq(tone(520, 0.12, "saw", 0.35, attack=0.03, release=0.02, slide=260, vibrato=7),
               tone(780, 0.30, "saw", 0.35, attack=0.005, release=0.2, slide=-380, vibrato=7))
    save("meow", mix(meow, noise(0.42, 0.05, lowpass=0.3, release=0.2)))

    # Ratinho pego pelo gato: guincho + "zum" de volta ao início
    save("caught", seq(tone(1400, 0.10, "square", 0.3, release=0.03, slide=500),
                       tone(1200, 0.35, "triangle", 0.4, release=0.2, slide=-1000)))

    # Entrou num túnel: "plop" + eco
    save("plop", mix(tone(300, 0.18, "sine", 0.6, attack=0.002, release=0.12, slide=600),
                     seq(silence(0.09), tone(450, 0.15, "sine", 0.25, release=0.1, slide=500))))

    # Ratoeira fechando: estalo metálico
    save("snap", mix(noise(0.08, 0.6, lowpass=0.9, attack=0.001, release=0.07),
                     tone(1900, 0.12, "square", 0.25, attack=0.001, release=0.1, slide=-900),
                     tone(140, 0.15, "sine", 0.5, attack=0.001, release=0.12)))

    # Comeu o biscoito de força: mordidinhas + subida "power-up"
    save("eat", seq(noise(0.04, 0.4, lowpass=0.5, attack=0.002, release=0.03), silence(0.03),
                    noise(0.04, 0.4, lowpass=0.5, attack=0.002, release=0.03), silence(0.02),
                    seq(*[tone(f, 0.06, "square", 0.3, release=0.02) for f in (523, 659, 784, 1046, 1318)])))

    # Gato espantado pelo ratinho forte: chiado + miado assustado
    save("hiss", seq(noise(0.30, 0.5, lowpass=0.95, attack=0.02, release=0.1),
                     tone(900, 0.30, "saw", 0.3, attack=0.01, release=0.2, slide=500, vibrato=12)))

    # Fim de jogo (sem vidas): descida lenta e grave
    save("gameover", seq(tone(392, 0.25, "square", 0.3, release=0.08),
                         tone(370, 0.25, "square", 0.3, release=0.08),
                         tone(349, 0.25, "square", 0.3, release=0.08),
                         mix(tone(330, 0.9, "square", 0.3, release=0.6, vibrato=5),
                             tone(165, 0.9, "triangle", 0.4, release=0.6))))

    # Rede: alguém entrou / saiu / erro
    save("join", seq(tone(660, 0.09, "sine", 0.45, release=0.04),
                     tone(990, 0.20, "sine", 0.45, release=0.15)))
    save("leave", seq(tone(880, 0.09, "sine", 0.45, release=0.04),
                      tone(587, 0.20, "sine", 0.45, release=0.15)))
    save("error", seq(tone(196, 0.14, "saw", 0.4, release=0.03), silence(0.04),
                      tone(147, 0.30, "saw", 0.4, release=0.15)))


if __name__ == "__main__":
    main()
