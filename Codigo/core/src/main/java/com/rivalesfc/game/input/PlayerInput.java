package com.rivalesfc.game.input;

public class PlayerInput {

    public int sequence;

    public float moveX;

    public float moveY;

    public boolean sprint;

    public boolean kickHeld;

    public boolean kickReleased;

    public boolean switchPlayer;

    public boolean slidePressed;

    public PlayerInput() {
    }

    public void clearTransient() {
        kickReleased = false;
        switchPlayer = false;
        slidePressed = false;
    }

    public void normalizeMove() {
        float len = (float) Math.sqrt(moveX * moveX + moveY * moveY);
        if (len > 1f) {
            moveX /= len;
            moveY /= len;
        }
    }
}
