package com.rivalesfc.game.sim;

/** Foto de la escena en un tick, para la repetición de gol (MK4). Solo datos de presentación. */
public class ReplayFrame {
    public float ballX, ballY, ballAngle;
    public float lx, ly, lvx, lvy;
    public boolean lSliding;
    public float rx, ry, rvx, rvy;
    public boolean rSliding;
    // MK7: segundos jugadores del 2v2 local (en los demás modos no se usan)
    public float mlx, mly, mlvx, mlvy;
    public boolean mlSliding;
    public float mrx, mry, mrvx, mrvy;
    public boolean mrSliding;
    public float kly, klvy, klDive;
    public float kry, krvy, krDive;

    public void copyFrom(ReplayFrame o) {
        ballX = o.ballX; ballY = o.ballY; ballAngle = o.ballAngle;
        lx = o.lx; ly = o.ly; lvx = o.lvx; lvy = o.lvy; lSliding = o.lSliding;
        rx = o.rx; ry = o.ry; rvx = o.rvx; rvy = o.rvy; rSliding = o.rSliding;
        mlx = o.mlx; mly = o.mly; mlvx = o.mlvx; mlvy = o.mlvy; mlSliding = o.mlSliding;
        mrx = o.mrx; mry = o.mry; mrvx = o.mrvx; mrvy = o.mrvy; mrSliding = o.mrSliding;
        kly = o.kly; klvy = o.klvy; klDive = o.klDive;
        kry = o.kry; krvy = o.krvy; krDive = o.krDive;
    }
}
