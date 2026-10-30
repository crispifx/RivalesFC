package com.rivalesfc.game.sim;

import com.badlogic.gdx.math.Vector2;
import com.rivalesfc.game.Constants;

/**
 * MK8: dónde se acomoda cada uno para un tiro libre o un penal. Java puro (solo {@link Vector2}), sin Box2D.
 *
 * - La pelota va donde fue la falta (dentro de márgenes para no quedar contra el muro) o, si fue penal, al punto de penal.
 * - El que patea se ubica detrás de la pelota mirando al arco rival (si no hay lugar, al costado).
 * - Los rivales forman una "barrera" a {@link Constants#FREE_KICK_WALL_DIST} entre la pelota y su arco; si ese
 *   lugar cae afuera de la cancha (falta pegada al fondo) se prueban otros ángulos.
 * - En un penal todos los demás jugadores de campo esperan en el círculo central.
 */
public final class SetPieceLayout {

    public final int takingTeam;
    public final boolean penalty;
    public final Vector2 ball = new Vector2();
    /** Dirección unitaria de la pelota hacia el arco que ataca el que patea. */
    public final Vector2 toGoal = new Vector2();
    private final Vector2 perp = new Vector2();
    private final float attackX;

    private static final float[] WALL_ANGLES = {0f, 40f, -40f, 90f, -90f, 140f, -140f, 180f};

    public SetPieceLayout(int takingTeam, float foulX, float foulY, boolean penalty) {
        this.takingTeam = takingTeam;
        this.penalty = penalty;
        this.attackX = takingTeam == 0 ? 1f : -1f;
        float halfW = Constants.FIELD_WIDTH / 2f;
        float halfH = Constants.FIELD_HEIGHT / 2f;
        if (penalty) {
            ball.set(attackX * (halfW - Constants.PENALTY_SPOT_DIST), 0f);
            toGoal.set(attackX, 0f);
        } else {
            float mx = halfW - Constants.SET_PIECE_EDGE_MARGIN_X;
            float my = halfH - Constants.SET_PIECE_EDGE_MARGIN_Y;
            ball.set(clamp(foulX, -mx, mx), clamp(foulY, -my, my));
            toGoal.set(attackX * halfW - ball.x, -ball.y);
            if (toGoal.len2() < 0.01f) {
                toGoal.set(attackX, 0f);
            }
            toGoal.nor();
        }
        perp.set(-toGoal.y, toGoal.x);
    }

    /** Dónde se para el que patea. */
    public Vector2 takerSpot(Vector2 out) {
        float d = penalty ? 1.6f : 1.15f;
        Vector2[] dirs = {
                new Vector2(toGoal).scl(-1f), new Vector2(perp), new Vector2(perp).scl(-1f), new Vector2(toGoal)};
        for (Vector2 dir : dirs) {
            out.set(ball).mulAdd(dir, d);
            if (inside(out, 0.8f)) {
                return out;
            }
        }
        return out.set(ball).mulAdd(dirs[0], d); // no debería pasar: la pelota siempre queda con margen
    }

    /** Compañero del que patea (k = 0, 1, ...). */
    public Vector2 mateSpot(int k, Vector2 out) {
        if (penalty) {
            return out.set(-attackX * 4f, (k % 2 == 0 ? 3.5f : -3.5f));
        }
        float side = (k % 2 == 0) ? 1f : -1f;
        out.set(ball).mulAdd(perp, 3.5f * side).mulAdd(toGoal, -2.5f);
        return clampInside(out, 1.0f);
    }

    /** Rival del que patea (k = 0, 1, ...): la barrera, o el círculo central en un penal. */
    public Vector2 wallSpot(int k, Vector2 out) {
        if (penalty) {
            return out.set(0f, (k % 2 == 0 ? 3.5f : -3.5f));
        }
        float lateral = k == 0 ? 0f : (k % 2 == 1 ? 1.7f : -1.7f);
        for (float deg : WALL_ANGLES) {
            Vector2 dir = new Vector2(toGoal).rotateDeg(deg);
            Vector2 side = new Vector2(-dir.y, dir.x);
            out.set(ball).mulAdd(dir, Constants.FREE_KICK_WALL_DIST).mulAdd(side, lateral);
            if (inside(out, 1.0f)) {
                return out;
            }
        }
        return clampInside(out.set(ball).mulAdd(toGoal, Constants.FREE_KICK_WALL_DIST), 1.0f);
    }

    private static boolean inside(Vector2 p, float margin) {
        return Math.abs(p.x) <= Constants.FIELD_WIDTH / 2f - margin - 0.2f
                && Math.abs(p.y) <= Constants.FIELD_HEIGHT / 2f - margin;
    }

    private static Vector2 clampInside(Vector2 p, float margin) {
        p.x = clamp(p.x, -(Constants.FIELD_WIDTH / 2f - margin - 0.2f), Constants.FIELD_WIDTH / 2f - margin - 0.2f);
        p.y = clamp(p.y, -(Constants.FIELD_HEIGHT / 2f - margin), Constants.FIELD_HEIGHT / 2f - margin);
        return p;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
