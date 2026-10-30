package com.rivalesfc.game.net;

/**
 * Foto del estado del partido que el host manda al cliente (30 veces por segundo).
 * Es solo datos: no depende de LibGDX. Índices: 0 = jugador/equipo AZUL (host), 1 = ROJO (cliente).
 *
 * Los eventos de un solo disparo (sonido de pateo, planchazo, atajada) viajan como
 * CONTADORES: si un paquete se pierde, el cliente igual nota que el contador subió.
 */
public class Snapshot {

    public int tick;
    public byte phase;      // ordinal de MatchSimulation.Phase
    public byte half;
    public byte scoreLeft;
    public byte scoreRight;
    public byte possessionLeft;
    public boolean lastGoalByLeft;
    public float halfTimeRemaining;
    public float phaseTimer;
    public float replayProgress;

    public float ballX, ballY, ballAngle, ballVX, ballVY;

    public final float[] px = new float[2];
    public final float[] py = new float[2];
    public final float[] pvx = new float[2];
    public final float[] pvy = new float[2];
    public final boolean[] sliding = new boolean[2];
    public final float[] kickPower = new float[2];

    public final float[] ky = new float[2];
    public final float[] kvy = new float[2];
    public final float[] kdive = new float[2];
    public final boolean[] kdiving = new boolean[2];

    public int kickCount, slideCount, saveCount;
    public float lastKickPower;

    public final byte[] shots = new byte[2];
    public final byte[] shotsOnTarget = new byte[2];
    public final byte[] tackles = new byte[2];
    public final byte[] saves = new byte[2];

    // --- MK8: faltas ---
    /** Contador acumulado de faltas cobradas (evento para el silbato, como los otros contadores). */
    public int foulCount;
    /** MK8: equipo que se beneficia de la ventaja en curso (0/1) o -1 si no hay. */
    public byte advantageTeam = -1;
    public int advantageCount;
    /** Quién cometió la última falta: índice de jugador (0 = AZUL/host, 1 = ROJO/cliente); -1 si todavía no hubo. */
    public byte foulerIdx = -1;
    /** 0 = sin tarjeta, 1 = amarilla, 2 = roja. */
    public byte foulCard;
    /** 0 = ninguna, 1 = planchazo, 2 = carga. */
    public byte foulType;
    public boolean foulPenalty;
    public float foulX, foulY;
    public final byte[] fouls = new byte[2];
    public final byte[] yellows = new byte[2];
    public final byte[] reds = new byte[2];
    public final boolean[] suspended = new boolean[2];
    public final float[] suspendRemaining = new float[2];

    public void copyFrom(Snapshot o) {
        tick = o.tick;
        phase = o.phase;
        half = o.half;
        scoreLeft = o.scoreLeft;
        scoreRight = o.scoreRight;
        possessionLeft = o.possessionLeft;
        lastGoalByLeft = o.lastGoalByLeft;
        halfTimeRemaining = o.halfTimeRemaining;
        phaseTimer = o.phaseTimer;
        replayProgress = o.replayProgress;
        ballX = o.ballX; ballY = o.ballY; ballAngle = o.ballAngle; ballVX = o.ballVX; ballVY = o.ballVY;
        for (int i = 0; i < 2; i++) {
            px[i] = o.px[i]; py[i] = o.py[i]; pvx[i] = o.pvx[i]; pvy[i] = o.pvy[i];
            sliding[i] = o.sliding[i]; kickPower[i] = o.kickPower[i];
            ky[i] = o.ky[i]; kvy[i] = o.kvy[i]; kdive[i] = o.kdive[i]; kdiving[i] = o.kdiving[i];
            shots[i] = o.shots[i]; shotsOnTarget[i] = o.shotsOnTarget[i];
            tackles[i] = o.tackles[i]; saves[i] = o.saves[i];
            fouls[i] = o.fouls[i]; yellows[i] = o.yellows[i]; reds[i] = o.reds[i];
            suspended[i] = o.suspended[i]; suspendRemaining[i] = o.suspendRemaining[i];
        }
        advantageTeam = o.advantageTeam; advantageCount = o.advantageCount; foulCount = o.foulCount; foulerIdx = o.foulerIdx; foulCard = o.foulCard; foulType = o.foulType;
        foulPenalty = o.foulPenalty; foulX = o.foulX; foulY = o.foulY;
        kickCount = o.kickCount; slideCount = o.slideCount; saveCount = o.saveCount;
        lastKickPower = o.lastKickPower;
    }
}
