package com.rivalesfc.game.net;

public class Snapshot {

    public int tick;
    public byte phase;
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
        }
        kickCount = o.kickCount; slideCount = o.slideCount; saveCount = o.saveCount;
        lastKickPower = o.lastKickPower;
    }
}
