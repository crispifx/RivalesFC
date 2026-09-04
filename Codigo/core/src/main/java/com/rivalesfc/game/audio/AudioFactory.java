package com.rivalesfc.game.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Genera efectos de sonido cortos en tiempo de ejecución (ondas cuadradas /
 * senoidales sintetizadas a mano, sin descargar ni empaquetar ningún .wav o
 * .mp3), siguiendo la misma filosofía que {@code gfx/PixelArtFactory}: este
 * entorno no tiene acceso a internet, así que el "arte sonoro" también se
 * genera por código.
 *
 * Cada tono se arma como un WAV PCM de 16 bits en memoria y se escribe a un
 * archivo temporal local (libGDX no permite crear un {@code Sound} a partir
 * de bytes en memoria, solo desde un {@link FileHandle}), que se borra al
 * salir del juego.
 */
public final class AudioFactory {

    private static final int SAMPLE_RATE = 22050;

    private AudioFactory() {
    }

    /** Pitido simple (patea/golpea la pelota): un tono corto que sube de tono con la potencia. */
    public static Sound kickTone(float power) {
        float freqStart = 220f + power * 260f;
        float freqEnd = freqStart * 1.6f;
        return synth(0.09f, freqStart, freqEnd, Wave.SQUARE, 0.35f);
    }

    /** Golpe seco y grave para el planchazo/barrida. */
    public static Sound thudTone() {
        return synth(0.12f, 140f, 70f, Wave.TRIANGLE, 0.5f);
    }

    /** Silbato de arranque/entretiempo: dos tonos agudos cortos. */
    public static Sound whistleTone() {
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        appendTone(pcm, 0.12f, 1800f, 1800f, Wave.SQUARE, 0.3f);
        appendSilence(pcm, 0.03f);
        appendTone(pcm, 0.18f, 1800f, 1800f, Wave.SQUARE, 0.3f);
        return toSound(pcm);
    }

    /** Pequeño "fanfarreo" ascendente de 4 notas para festejar un gol. */
    public static Sound goalFanfare() {
        float[] notes = {523.25f, 659.25f, 783.99f, 1046.5f}; // C5 E5 G5 C6
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        for (float note : notes) {
            appendTone(pcm, 0.11f, note, note, Wave.SQUARE, 0.32f);
        }
        return toSound(pcm);
    }

    /** Bip corto y grave para el pitazo final. */
    public static Sound fullTimeTone() {
        return synth(0.5f, 300f, 180f, Wave.TRIANGLE, 0.35f);
    }

    private enum Wave {SQUARE, TRIANGLE, SINE}

    private static Sound synth(float seconds, float freqStart, float freqEnd, Wave wave, float volume) {
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        appendTone(pcm, seconds, freqStart, freqEnd, wave, volume);
        return toSound(pcm);
    }

    private static void appendSilence(ByteArrayOutputStream pcm, float seconds) {
        int samples = (int) (SAMPLE_RATE * seconds);
        for (int i = 0; i < samples; i++) {
            pcm.write(0);
            pcm.write(0);
        }
    }

    private static void appendTone(ByteArrayOutputStream pcm, float seconds, float freqStart, float freqEnd, Wave wave, float volume) {
        int samples = (int) (SAMPLE_RATE * seconds);
        for (int i = 0; i < samples; i++) {
            float t = i / (float) SAMPLE_RATE;
            float progress = samples <= 1 ? 0f : i / (float) (samples - 1);
            float freq = freqStart + (freqEnd - freqStart) * progress;
            float phase = (t * freq) % 1f;

            float sample;
            switch (wave) {
                case SQUARE:
                    sample = phase < 0.5f ? 1f : -1f;
                    break;
                case TRIANGLE:
                    sample = 4f * Math.abs(phase - 0.5f) - 1f;
                    break;
                default:
                    sample = (float) Math.sin(2 * Math.PI * phase);
                    break;
            }

            // Envolvente simple (fade-in/out) para evitar "clicks" al empezar/terminar.
            float fade = Math.min(1f, Math.min(i / 200f, (samples - i) / 200f));
            short value = (short) (sample * volume * fade * Short.MAX_VALUE);

            pcm.write(value & 0xFF);
            pcm.write((value >> 8) & 0xFF);
        }
    }

    private static Sound toSound(ByteArrayOutputStream pcm) {
        byte[] data = pcm.toByteArray();
        byte[] wav = wrapAsWav(data);
        try {
            FileHandle handle = Gdx.files.local(".rivalesfc_sfx_" + System.nanoTime() + ".wav");
            handle.writeBytes(wav, false);
            Sound sound = Gdx.audio.newSound(handle);
            handle.file().deleteOnExit();
            return sound;
        } catch (Exception e) {
            Gdx.app.error("AudioFactory", "No se pudo sintetizar el sonido", e);
            return null;
        }
    }

    /** Envuelve PCM crudo de 16 bits mono en un header WAV válido. */
    private static byte[] wrapAsWav(byte[] pcmData) {
        int byteRate = SAMPLE_RATE * 2;
        int dataSize = pcmData.length;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            writeString(out, "RIFF");
            writeInt(out, 36 + dataSize);
            writeString(out, "WAVE");
            writeString(out, "fmt ");
            writeInt(out, 16);
            writeShort(out, (short) 1); // PCM
            writeShort(out, (short) 1); // mono
            writeInt(out, SAMPLE_RATE);
            writeInt(out, byteRate);
            writeShort(out, (short) 2); // block align
            writeShort(out, (short) 16); // bits per sample
            writeString(out, "data");
            writeInt(out, dataSize);
            out.write(pcmData);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return out.toByteArray();
    }

    private static void writeString(ByteArrayOutputStream out, String s) throws IOException {
        out.write(s.getBytes("US-ASCII"));
    }

    private static void writeInt(ByteArrayOutputStream out, int v) {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
        out.write((v >> 16) & 0xFF);
        out.write((v >> 24) & 0xFF);
    }

    private static void writeShort(ByteArrayOutputStream out, short v) {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
    }
}
