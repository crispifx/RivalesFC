package com.rivalesfc.game.sim;

/**
 * Estadísticas acumuladas del partido (MK4). Índice de equipo: 0 = AZUL (izquierda), 1 = ROJO (derecha).
 * Cada equipo tiene un jugador de campo y un arquero, así que las notas individuales salen de acá.
 */
public class MatchStats {

    public static final int FIELD_LEFT = 0;
    public static final int KEEPER_LEFT = 1;
    public static final int FIELD_RIGHT = 2;
    public static final int KEEPER_RIGHT = 3;

    public final int[] shots = new int[2];
    public final int[] shotsOnTarget = new int[2];
    public final int[] tackles = new int[2];
    public final int[] saves = new int[2];

    /** Notas 1..10 en el orden FIELD_LEFT, KEEPER_LEFT, FIELD_RIGHT, KEEPER_RIGHT. */
    public float[] ratings(int scoreLeft, int scoreRight) {
        float[] r = new float[4];
        r[FIELD_LEFT] = fieldRating(0, scoreLeft, scoreRight);
        r[FIELD_RIGHT] = fieldRating(1, scoreRight, scoreLeft);
        r[KEEPER_LEFT] = keeperRating(0, scoreRight, scoreLeft);
        r[KEEPER_RIGHT] = keeperRating(1, scoreLeft, scoreRight);
        return r;
    }

    public int mvpIndex(int scoreLeft, int scoreRight) {
        float[] r = ratings(scoreLeft, scoreRight);
        int best = 0;
        for (int i = 1; i < r.length; i++) {
            if (r[i] > r[best] + 0.0001f) {
                best = i;
            }
        }
        return best;
    }

    private float fieldRating(int team, int goalsFor, int goalsAgainst) {
        float r = 5.5f + goalsFor * 1.5f + shotsOnTarget[team] * 0.3f + shots[team] * 0.15f + tackles[team] * 0.5f;
        r += resultBonus(goalsFor, goalsAgainst);
        return clamp(r);
    }

    private float keeperRating(int team, int goalsConceded, int goalsScored) {
        float r = 5.5f + saves[team] * 0.7f - goalsConceded * 0.5f;
        if (goalsConceded == 0) {
            r += 1.0f;
        }
        r += resultBonus(goalsScored, goalsConceded);
        return clamp(r);
    }

    private float resultBonus(int goalsFor, int goalsAgainst) {
        if (goalsFor > goalsAgainst) return 0.5f;
        if (goalsFor < goalsAgainst) return -0.3f;
        return 0f;
    }

    private float clamp(float v) {
        return Math.max(1f, Math.min(10f, v));
    }
}
