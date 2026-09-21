package com.rivalesfc.game.sim;

/** Foto de la escena en un tick, para la repetición de gol (MK4). Solo datos de presentación. */
public class ReplayFrame {
    public float ballX, ballY, ballAngle;
    public float lx, ly, lvx, lvy;
    public boolean lSliding;
    public float rx, ry, rvx, rvy;
    public boolean rSliding;
    public float kly, klvy, klDive;
    public float kry, krvy, krDive;

    public void copyFrom(ReplayFrame o) {
        ballX = o.ballX; ballY = o.ballY; ballAngle = o.ballAngle;
        lx = o.lx; ly = o.ly; lvx = o.lvx; lvy = o.lvy; lSliding = o.lSliding;
        rx = o.rx; ry = o.ry; rvx = o.rvx; rvy = o.rvy; rSliding = o.rSliding;
        kly = o.kly; klvy = o.klvy; klDive = o.klDive;
        kry = o.kry; krvy = o.krvy; krDive = o.krDive;
    }
}
