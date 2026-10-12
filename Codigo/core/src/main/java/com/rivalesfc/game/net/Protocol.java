package com.rivalesfc.game.net;

import com.rivalesfc.game.input.PlayerInput;

import java.nio.ByteBuffer;

/**
 * Formato de los mensajes UDP (Etapa 2, paso 1: lo básico).
 *
 * Todos los paquetes empiezan con: MAGIC (2 bytes) + VERSION (1) + TIPO (1).
 * HELLO / WELCOME / BYE no llevan más datos.
 * INPUT: seq (int) + moveX (float) + moveY (float) + flags (byte)  -> 17 bytes en total.
 * SNAPSHOT: ver {@link Snapshot} (unos 200 bytes).
 * PING: marca de tiempo del cliente (int, ms) + último ping que midió (short, ms; -1 = aún no mide).
 * PONG: la misma marca de tiempo (int), sin tocar.
 */
public final class Protocol {

    public static final short MAGIC = 0x5246; // "RF"
    public static final byte VERSION = 2; // MK5: v2 agrega PING/PONG (una MK4.5 y una MK5 no se conectan entre sí)
    public static final int DEFAULT_PORT = 7777;
    public static final int MAX_PACKET = 256;

    public static final byte HELLO = 1;   // cliente -> host: "quiero entrar"
    public static final byte WELCOME = 2; // host -> cliente: "entraste"
    public static final byte INPUT = 3;   // cliente -> host: estado de sus controles
    public static final byte BYE = 4;     // cualquiera: "me voy"
    public static final byte SNAPSHOT = 5; // host -> cliente: estado del partido
    public static final byte PING = 6;    // cliente -> host: marca de tiempo + último ping medido (MK5)
    public static final byte PONG = 7;    // host -> cliente: devuelve la misma marca de tiempo (MK5)

    private static final int F_SPRINT = 1;
    private static final int F_KICK_HELD = 2;
    private static final int F_KICK_RELEASED = 4;
    private static final int F_SWITCH = 8;
    private static final int F_SLIDE = 16;

    private Protocol() {
    }

    /** Deja el buffer listo para escribir el cuerpo del mensaje. */
    public static void writeHeader(ByteBuffer b, byte type) {
        b.clear();
        b.putShort(MAGIC);
        b.put(VERSION);
        b.put(type);
    }

    /** Devuelve el tipo de mensaje, o -1 si el paquete no es de este juego / versión. */
    public static int readType(ByteBuffer b) {
        if (b.remaining() < 4) return -1;
        if (b.getShort() != MAGIC) return -1;
        if (b.get() != VERSION) return -1;
        return b.get();
    }

    public static void writeInput(ByteBuffer b, PlayerInput in) {
        b.putInt(in.sequence);
        b.putFloat(in.moveX);
        b.putFloat(in.moveY);
        int flags = 0;
        if (in.sprint) flags |= F_SPRINT;
        if (in.kickHeld) flags |= F_KICK_HELD;
        if (in.kickReleased) flags |= F_KICK_RELEASED;
        if (in.switchPlayer) flags |= F_SWITCH;
        if (in.slidePressed) flags |= F_SLIDE;
        b.put((byte) flags);
    }

    /** Lee un INPUT. Devuelve false si el paquete está incompleto. */
    public static boolean readInput(ByteBuffer b, PlayerInput out) {
        if (b.remaining() < 13) return false;
        out.sequence = b.getInt();
        out.moveX = clampAxis(b.getFloat());
        out.moveY = clampAxis(b.getFloat());
        int flags = b.get();
        out.sprint = (flags & F_SPRINT) != 0;
        out.kickHeld = (flags & F_KICK_HELD) != 0;
        out.kickReleased = (flags & F_KICK_RELEASED) != 0;
        out.switchPlayer = (flags & F_SWITCH) != 0;
        out.slidePressed = (flags & F_SLIDE) != 0;
        return true;
    }

    /** Cuerpo de PING: marca de tiempo local (ms, puede dar la vuelta al int) + último ping medido. */
    public static void writePing(ByteBuffer b, int stampMs, int lastPingMs) {
        b.putInt(stampMs);
        b.putShort((short) Math.max(-1, Math.min(Short.MAX_VALUE, lastPingMs)));
    }

    /** Lee la marca de tiempo de un PING/PONG; devuelve false si está incompleto. */
    public static boolean hasStamp(ByteBuffer b) {
        return b.remaining() >= 4;
    }

    // ------------------------------------------------------------------
    // SNAPSHOT (host -> cliente)
    // ------------------------------------------------------------------

    public static void writeSnapshot(ByteBuffer b, Snapshot s) {
        b.putInt(s.tick);
        b.put(s.phase);
        b.put(s.half);
        b.put(s.scoreLeft);
        b.put(s.scoreRight);
        b.put(s.possessionLeft);
        b.put((byte) (s.lastGoalByLeft ? 1 : 0));
        b.putFloat(s.halfTimeRemaining);
        b.putFloat(s.phaseTimer);
        b.putFloat(s.replayProgress);
        b.putFloat(s.ballX).putFloat(s.ballY).putFloat(s.ballAngle).putFloat(s.ballVX).putFloat(s.ballVY);
        for (int i = 0; i < 2; i++) {
            b.putFloat(s.px[i]).putFloat(s.py[i]).putFloat(s.pvx[i]).putFloat(s.pvy[i]);
            b.put((byte) (s.sliding[i] ? 1 : 0));
            b.putFloat(s.kickPower[i]);
        }
        for (int i = 0; i < 2; i++) {
            b.putFloat(s.ky[i]).putFloat(s.kvy[i]).putFloat(s.kdive[i]);
            b.put((byte) (s.kdiving[i] ? 1 : 0));
        }
        b.putInt(s.kickCount).putInt(s.slideCount).putInt(s.saveCount);
        b.putFloat(s.lastKickPower);
        for (int i = 0; i < 2; i++) {
            b.put(s.shots[i]).put(s.shotsOnTarget[i]).put(s.tackles[i]).put(s.saves[i]);
        }
    }

    /** Lee un SNAPSHOT. Devuelve false si el paquete está incompleto. */
    public static boolean readSnapshot(ByteBuffer b, Snapshot s) {
        if (b.remaining() < SNAPSHOT_BYTES) return false;
        s.tick = b.getInt();
        s.phase = b.get();
        s.half = b.get();
        s.scoreLeft = b.get();
        s.scoreRight = b.get();
        s.possessionLeft = b.get();
        s.lastGoalByLeft = b.get() != 0;
        s.halfTimeRemaining = b.getFloat();
        s.phaseTimer = b.getFloat();
        s.replayProgress = b.getFloat();
        s.ballX = b.getFloat(); s.ballY = b.getFloat(); s.ballAngle = b.getFloat();
        s.ballVX = b.getFloat(); s.ballVY = b.getFloat();
        for (int i = 0; i < 2; i++) {
            s.px[i] = b.getFloat(); s.py[i] = b.getFloat(); s.pvx[i] = b.getFloat(); s.pvy[i] = b.getFloat();
            s.sliding[i] = b.get() != 0;
            s.kickPower[i] = b.getFloat();
        }
        for (int i = 0; i < 2; i++) {
            s.ky[i] = b.getFloat(); s.kvy[i] = b.getFloat(); s.kdive[i] = b.getFloat();
            s.kdiving[i] = b.get() != 0;
        }
        s.kickCount = b.getInt(); s.slideCount = b.getInt(); s.saveCount = b.getInt();
        s.lastKickPower = b.getFloat();
        for (int i = 0; i < 2; i++) {
            s.shots[i] = b.get(); s.shotsOnTarget[i] = b.get(); s.tackles[i] = b.get(); s.saves[i] = b.get();
        }
        return true;
    }

    /** Tamaño del cuerpo de un SNAPSHOT (sin cabecera). */
    private static final int SNAPSHOT_BYTES = 4 + 6 + 12 + 20 + 2 * 21 + 2 * 13 + 16 + 8;

    /** El host no confía en el cliente: NaN o valores fuera de rango no pasan. */
    private static float clampAxis(float v) {
        if (Float.isNaN(v)) return 0f;
        return Math.max(-1f, Math.min(1f, v));
    }
}
