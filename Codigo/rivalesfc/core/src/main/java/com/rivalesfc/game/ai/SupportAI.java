package com.rivalesfc.game.ai;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.rivalesfc.game.Constants;
import com.rivalesfc.game.entities.Ball;
import com.rivalesfc.game.entities.PlayerEntity;
import com.rivalesfc.game.input.PlayerInput;

/**
 * IA de soporte muy simple para el modo 1 jugador (secc. "modo 1 jugador"
 * del lobby): en vez de leer teclado, este objeto le llena un
 * {@link PlayerInput} a un jugador de campo cada tick, igual que haría un
 * humano. Como {@code PlayerEntity.applyInput}/{@code MatchSimulation.handleKick}
 * ya trabajan sobre un {@code PlayerInput} genérico, la IA "engancha" en el
 * mismo lugar que un control de teclado, sin tocar el resto de la simulación
 * (incluido el planchazo, que la IA también puede usar).
 *
 * Comportamiento (a propósito muy simple, para que un jugador humano pueda
 * ganarle con buen manejo, no porque la IA "haga trampa"):
 *   - Si la pelota está lejos: corre derecho hacia ella (con sprint si está
 *     muy lejos).
 *   - Si la pelota está al alcance de patear: apunta al arco rival y patea
 *     con una carga corta de potencia.
 *   - Si un rival humano tiene la pelota muy cerca y la IA no llega a
 *     patearla todavía: de vez en cuando intenta un planchazo, para mostrar
 *     la mecánica también del lado de la IA.
 */
public class SupportAI {

    private final boolean attackingTowardsNegativeX;
    private final Vector2 targetGoal;
    private float kickChargeTimer = 0f;
    private float slideRetryCooldown = 0f;

    /**
     * @param attackingTowardsNegativeX true si este jugador ataca hacia el arco de X negativo
     *                                  (caso del equipo ROJO, panel derecho, en el 2v2 fijo).
     */
    public SupportAI(boolean attackingTowardsNegativeX) {
        this.attackingTowardsNegativeX = attackingTowardsNegativeX;
        float goalX = attackingTowardsNegativeX
                ? -(Constants.FIELD_WIDTH / 2f + Constants.GOAL_DEPTH * 0.4f)
                : (Constants.FIELD_WIDTH / 2f + Constants.GOAL_DEPTH * 0.4f);
        this.targetGoal = new Vector2(goalX, 0f);
    }

    /** Llena {@code out} con la intención de la IA para este tick. Se llama una vez por tick, a 30 Hz. */
    public void update(PlayerInput out, PlayerEntity self, Ball ball, PlayerEntity humanRival, float dt) {
        out.sequence++;
        out.slidePressed = false;

        Vector2 toBall = new Vector2(ball.getPosition()).sub(self.getPosition());
        float dist = toBall.len();

        if (dist > 0.001f) {
            toBall.nor();
        }

        if (self.isNear(ball)) {
            // Al alcance: frena el avance libre y apunta/carga el remate hacia el arco rival.
            Vector2 toGoal = new Vector2(targetGoal).sub(self.getPosition()).nor();
            out.moveX = toGoal.x * 0.2f; // pequeño ajuste de posición mientras carga, no un frenazo total
            out.moveY = toGoal.y * 0.2f;
            out.sprint = false;

            kickChargeTimer += dt;
            boolean shouldRelease = kickChargeTimer >= Constants.KICK_CHARGE_TIME * 0.55f;
            out.kickHeld = !shouldRelease;
            out.kickReleased = shouldRelease;
            if (shouldRelease) {
                kickChargeTimer = 0f;
            }
        } else {
            // Lejos de la pelota: corre directo hacia ella.
            out.moveX = toBall.x;
            out.moveY = toBall.y;
            out.sprint = dist > 3.5f;
            out.kickHeld = false;
            out.kickReleased = false;
            kickChargeTimer = 0f;

            // De tanto en tanto, si el rival humano le "cuida" la pelota muy de cerca
            // y la IA está a un paso de distancia (más lejos que el rango de pateo,
            // pero cerca), intenta un planchazo para tratar de robarla.
            slideRetryCooldown -= dt;
            boolean rivalHasBallNearby = humanRival != null
                    && humanRival.isNear(ball)
                    && dist < Constants.KICK_RANGE + 0.8f;
            if (rivalHasBallNearby && slideRetryCooldown <= 0f && MathUtils.randomBoolean(0.35f)) {
                out.slidePressed = true;
                slideRetryCooldown = Constants.SLIDE_COOLDOWN + 0.5f;
            }
        }

        out.normalizeMove();
    }
}
