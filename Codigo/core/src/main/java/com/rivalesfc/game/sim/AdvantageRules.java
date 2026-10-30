package com.rivalesfc.game.sim;

import com.rivalesfc.game.Constants;

/**
 * MK8: reglas de la ley de la ventaja. Java puro (sin Box2D ni LibGDX gráfico) para poder probarlas sin ventana
 * (ver {@code FoulSelfTest}); {@link MatchSimulation} le pasa quién tiene la pelota en cada tick y ejecuta lo que decide.
 *
 * Cuando se comete una falta y el equipo perjudicado conserva la pelota, no se corta el juego: se abre una ventana
 * de {@link Constants#ADVANTAGE_SECONDS}. Si el perjudicado la aprovecha (la controla un rato o remata), la falta
 * queda anotada (y su tarjeta) pero no hay tiro libre. Si la pierde, o la ventana vence sin que la haya aprovechado,
 * se vuelve atrás y se cobra la falta donde fue.
 */
public final class AdvantageRules {

    private AdvantageRules() {
    }

    public enum Verdict { PENDING, REALIZED, CALL_BACK }

    /**
     * ¿Corresponde dar ventaja? No se da en un penal ni con roja (esos cortan siempre), ni si el equipo perjudicado
     * no tiene la pelota, ni cuando queda poco tiempo (la jugada no llegaría a resolverse).
     */
    public static boolean eligible(boolean enabled, boolean penalty, FoulRules.Card card,
                                   boolean victimTeamHasBall, float timeLeft) {
        return enabled && !penalty && card != FoulRules.Card.RED && victimTeamHasBall
                && timeLeft > Constants.ADVANTAGE_MIN_TIME_LEFT;
    }

    /** Seguimiento de una ventaja en curso. */
    public static final class Tracker {
        private float elapsed;
        private int control;
        private int lost;

        public void begin() {
            elapsed = 0f;
            control = 0;
            lost = 0;
        }

        public float elapsed() {
            return elapsed;
        }

        /**
         * Un tick de ventaja.
         *
         * @param victimHas  solo el equipo perjudicado tiene la pelota al alcance
         * @param rivalHas   solo el equipo infractor tiene la pelota al alcance (si están los dos, es una disputa: no cuenta para nadie)
         * @param victimShot el perjudicado remató en esta ventana
         * @param timeUp     se acaba el tiempo del período: no hay más ventana
         */
        public Verdict update(float dt, boolean victimHas, boolean rivalHas, boolean victimShot, boolean timeUp) {
            elapsed += dt;
            if (victimShot) {
                return Verdict.REALIZED;
            }
            if (timeUp) {
                return Verdict.CALL_BACK;
            }
            if (victimHas) {
                control++;
                lost = 0;
            } else if (rivalHas) {
                lost++;
                if (lost >= Constants.ADVANTAGE_LOST_TICKS) {
                    return Verdict.CALL_BACK;
                }
            }
            if (control >= Constants.ADVANTAGE_EARLY_TICKS) {
                return Verdict.REALIZED;
            }
            if (elapsed >= Constants.ADVANTAGE_SECONDS) {
                return control >= Constants.ADVANTAGE_MIN_CONTROL_TICKS ? Verdict.REALIZED : Verdict.CALL_BACK;
            }
            return Verdict.PENDING;
        }
    }
}
