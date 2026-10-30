package com.rivalesfc.game.sim;

import com.rivalesfc.game.Constants;

/**
 * MK8: reglas del sistema de faltas. Es Java puro (sin Box2D ni LibGDX gráfico) a propósito: así se puede
 * probar sin ventana ni física (ver {@code FoulSelfTest}). {@link MatchSimulation} le pasa posiciones y
 * velocidades de ANTES del paso de física (después del choque Box2D la velocidad de cierre ya se perdió) y
 * ejecuta lo que decide.
 *
 * Qué se cobra:
 *  - SLIDE: un planchazo que llega a un rival SIN haber tocado antes la pelota en ese mismo planchazo.
 *    Si toca la pelota primero, es una entrada limpia (aunque después arrastre al rival).
 *  - CHARGE: un choque a mucha velocidad contra un rival cuando el que choca no está jugando la pelota.
 *    En un choque de frente a velocidades parecidas no se cobra nada (nadie es el culpable).
 *
 * Gravedad (1 a 4): 1 base + 1 por detrás + 1 si el rival tenía la pelota + 1 si fue una entrada muy fuerte.
 *  - gravedad >= 3: tarjeta amarilla; gravedad 4: roja directa.
 *  - amarilla por reincidencia: cada {@link Constants#FOUL_REPEAT_YELLOW_EVERY} faltas del mismo jugador.
 *  - segunda amarilla = roja.
 */
public final class FoulRules {

    private FoulRules() {
    }

    public enum Type { NONE, SLIDE, CHARGE }

    public enum Card { NONE, YELLOW, RED }

    /** Resultado de juzgar un contacto. */
    public static final class Call {
        public Type type = Type.NONE;
        public int severity = 0;
        public boolean fromBehind;
        public boolean victimHadBall;
        public boolean hardHit;

        public boolean isFoul() {
            return type != Type.NONE;
        }
    }

    /**
     * Juzga el contacto de un atacante contra un rival.
     *
     * @param attackerSliding   el atacante está en pleno planchazo
     * @param attackerTouchedBall en este planchazo ya tocó la pelota (entrada limpia)
     * @param victimSliding     el rival también está en planchazo (dos planchazos que se cruzan: no se cobra)
     * @param attackerPlaysBall la pelota está al alcance del atacante (está jugando la pelota)
     * @param victimHadBall     la pelota estaba al alcance del rival
     * @param ax,ay,avx,avy     posición y velocidad del atacante antes del paso de física
     * @param vx,vy,vvx,vvy     posición y velocidad del rival antes del paso de física
     */
    public static Call judge(boolean attackerSliding, boolean attackerTouchedBall, boolean victimSliding,
                             boolean attackerPlaysBall, boolean victimHadBall,
                             float ax, float ay, float avx, float avy,
                             float vx, float vy, float vvx, float vvy, Call out) {
        out.type = Type.NONE;
        out.severity = 0;
        out.fromBehind = false;
        out.victimHadBall = false;
        out.hardHit = false;

        float dx = vx - ax, dy = vy - ay;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.0001f) {
            return out;
        }
        float nx = dx / len, ny = dy / len;               // del atacante hacia el rival
        float attackerForward = avx * nx + avy * ny;       // cuánto avanza el atacante hacia el rival
        float victimTowards = -(vvx * nx + vvy * ny);      // cuánto avanza el rival hacia el atacante
        float closing = attackerForward + victimTowards;

        if (attackerSliding) {
            if (attackerTouchedBall || victimSliding) {
                return out;
            }
            out.type = Type.SLIDE;
        } else {
            if (attackerPlaysBall) {
                return out;
            }
            if (attackerForward < Constants.CHARGE_FOUL_SPEED
                    || attackerForward < victimTowards + Constants.CHARGE_FOUL_MARGIN) {
                return out;
            }
            out.type = Type.CHARGE;
        }

        int sev = 1;
        // "Por detrás": el rival se aleja del que entra (va en la misma dirección que la entrada).
        float victimAway = vvx * nx + vvy * ny;
        if (victimAway >= Constants.FOUL_BEHIND_VICTIM_SPEED && attackerForward > 1f) {
            out.fromBehind = true;
            sev++;
        }
        if (victimHadBall) {
            out.victimHadBall = true;
            sev++;
        }
        if (closing >= Constants.FOUL_HARD_CLOSING_SPEED) {
            out.hardHit = true;
            sev++;
        }
        out.severity = Math.min(4, sev);
        return out;
    }

    /** Libro de disciplina: faltas y amarillas por jugador (índices 0..3 = izq., der., compañero izq., compañero der.). */
    public static final class DisciplineBook {
        private final int[] fouls = new int[4];
        private final int[] yellows = new int[4];

        /**
         * Anota una falta del jugador {@code idx} con la gravedad dada y devuelve la tarjeta que corresponde.
         * Una roja (directa o por segunda amarilla) reinicia las amarillas de ese jugador: al volver empieza limpio.
         */
        public Card record(int idx, int severity) {
            fouls[idx]++;
            Card card = Card.NONE;
            if (severity >= Constants.FOUL_RED_SEVERITY) {
                card = Card.RED;
            } else if (severity >= Constants.FOUL_YELLOW_SEVERITY
                    || fouls[idx] % Constants.FOUL_REPEAT_YELLOW_EVERY == 0) {
                card = Card.YELLOW;
            }
            if (card == Card.YELLOW) {
                yellows[idx]++;
                if (yellows[idx] >= 2) {
                    card = Card.RED;
                }
            }
            if (card == Card.RED) {
                yellows[idx] = 0;
            }
            return card;
        }

        public int fouls(int idx) {
            return fouls[idx];
        }

        public int yellows(int idx) {
            return yellows[idx];
        }

        public void reset() {
            java.util.Arrays.fill(fouls, 0);
            java.util.Arrays.fill(yellows, 0);
        }
    }

    /** ¿El punto está dentro del área grande del equipo {@code defendingTeam} (0 = izquierda, 1 = derecha)? */
    public static boolean inPenaltyArea(int defendingTeam, float x, float y) {
        float side = defendingTeam == 0 ? -1f : 1f;
        float goalLine = side * (Constants.FIELD_WIDTH / 2f);
        float depth = (x - goalLine) * -side; // metros hacia adentro de la cancha desde la línea de gol propia
        return depth >= 0f && depth <= Constants.PENALTY_BOX_DEPTH && Math.abs(y) <= Constants.PENALTY_BOX_HEIGHT / 2f;
    }
}
