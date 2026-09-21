package com.rivalesfc.game.ai;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.rivalesfc.game.Constants;
import com.rivalesfc.game.Settings;
import com.rivalesfc.game.entities.Ball;
import com.rivalesfc.game.entities.GoalkeeperEntity;
import com.rivalesfc.game.entities.PlayerEntity;
import com.rivalesfc.game.input.PlayerInput;

/**
 * IA de soporte (modo 1 jugador). Llena un {@link PlayerInput} igual que lo haría
 * un teclado, así que movimiento, pateo y planchazo siguen teniendo una única
 * implementación.
 *
 * MK4: pasa de "correr a la pelota y patear" a una máquina de estados chica:
 *   DEFEND — si el rival tiene la pelota en la mitad propia, se planta del lado del
 *            arco propio (entre la pelota y el arco) y, si se le acerca, intenta barrida.
 *   CHASE  — va a buscar la pelota, aproximándose desde atrás respecto de la dirección
 *            en la que quiere patear (así el pateo sale hacia donde apunta).
 *   CARRY  — con la pelota al alcance decide entre:
 *              SHOOT: remate al rincón más alejado del arquero rival (con error según dificultad),
 *              PASS : toque suave hacia un espacio libre (esquivando al rival si lo tiene encima).
 *
 * Aclaración honesta: en el 2v2 fijo cada equipo tiene un solo jugador de campo, así que el
 * "pase" de la IA es un toque a espacio libre y no un pase a un compañero. Los pases a
 * compañeros llegan con más jugadores por equipo (Etapa 3).
 *
 * La dirección del pateo en este juego es "desde el jugador hacia la pelota", por eso la IA
 * se acomoda detrás de la pelota, sobre la línea hacia su objetivo, antes de cargar el pateo.
 */
public class SupportAI {

    private enum Action { NONE, SHOOT, PASS }

    private final float attackSign;   // +1 ataca hacia X+, -1 hacia X-
    private final float goalX;
    private final float ownGoalX;

    private Action pending = Action.NONE;
    private final Vector2 target = new Vector2();
    private float targetCharge = 0.5f;
    private float kickChargeTimer = 0f;
    private float slideRetryCooldown = 0f;
    private float stuckTimer = 0f;

    /**
     * @param attackingTowardsNegativeX true si este jugador ataca hacia el arco de X negativo
     *                                  (equipo ROJO, panel derecho).
     */
    public SupportAI(boolean attackingTowardsNegativeX) {
        this.attackSign = attackingTowardsNegativeX ? -1f : 1f;
        float edge = Constants.FIELD_WIDTH / 2f + Constants.GOAL_DEPTH * 0.4f;
        this.goalX = attackSign * edge;
        this.ownGoalX = -attackSign * edge;
    }

    /** Se llama una vez por tick (30 Hz). */
    public void update(PlayerInput out, PlayerEntity self, Ball ball, PlayerEntity rival,
                       GoalkeeperEntity oppKeeper, float dt) {
        Settings.Difficulty diff = Settings.difficulty;
        out.sequence++;
        out.slidePressed = false;
        out.kickHeld = false;
        out.kickReleased = false;
        out.sprint = false;
        out.moveX = 0f;
        out.moveY = 0f;

        Vector2 sp = self.getPosition();
        Vector2 bp = ball.getPosition();
        Vector2 rp = rival.getPosition();
        float distBall = sp.dst(bp);
        slideRetryCooldown -= dt;

        boolean ballInOwnHalf = bp.x * attackSign < 0f;
        boolean rivalHasBall = rival.isNear(ball);

        if (self.isNear(ball)) {
            carry(out, self, ball, rival, oppKeeper, dt, diff);
        } else {
            pending = Action.NONE;
            kickChargeTimer = 0f;

            if (rivalHasBall && ballInOwnHalf && sp.dst(rp) > 2.4f) {
                defend(out, sp, bp, diff);
            } else {
                chase(out, sp, bp, distBall, diff);
            }
            trySlide(out, self, ball, rival, distBall, dt);
        }

        // Anti-atasco: si está pegada a un borde sin avanzar, no se queda "empujando" la pared.
        out.moveX *= diff.aiMoveScale;
        out.moveY *= diff.aiMoveScale;
        out.normalizeMove();
    }

    // ---------------------------------------------------------------- DEFEND
    private void defend(PlayerInput out, Vector2 sp, Vector2 bp, Settings.Difficulty diff) {
        Vector2 toOwnGoal = new Vector2(ownGoalX, 0f).sub(bp).nor();
        Vector2 post = new Vector2(bp).mulAdd(toOwnGoal, 2.4f);
        Vector2 go = new Vector2(post).sub(sp);
        float len = go.len();
        if (len > 0.25f) {
            go.nor();
            out.moveX = go.x;
            out.moveY = go.y;
        }
        out.sprint = len > diff.aiSprintDist;
    }

    // ----------------------------------------------------------------- CHASE
    private void chase(PlayerInput out, Vector2 sp, Vector2 bp, float distBall, Settings.Difficulty diff) {
        // Cerca de la pelota se acerca desde atrás, sobre la línea hacia el arco rival.
        Vector2 approach = new Vector2(bp);
        if (distBall < 4f) {
            Vector2 toGoal = new Vector2(goalX, 0f).sub(bp).nor();
            approach.mulAdd(toGoal, -0.55f);
        }
        Vector2 go = new Vector2(approach).sub(sp);
        if (go.len2() > 0.0004f) {
            go.nor();
            out.moveX = go.x;
            out.moveY = go.y;
        }
        out.sprint = distBall > diff.aiSprintDist;
    }

    // -------------------------------------------------------- CARRY (SHOOT/PASS)
    private void carry(PlayerInput out, PlayerEntity self, Ball ball, PlayerEntity rival,
                       GoalkeeperEntity oppKeeper, float dt, Settings.Difficulty diff) {
        Vector2 sp = self.getPosition();
        Vector2 bp = ball.getPosition();
        Vector2 rp = rival.getPosition();

        if (pending == Action.NONE) {
            decide(sp, rp, oppKeeper, diff);
        }

        Vector2 dirT = new Vector2(target).sub(bp);
        if (dirT.len2() < 0.0001f) {
            dirT.set(attackSign, 0f);
        }
        dirT.nor();

        Vector2 kickDir = new Vector2(bp).sub(sp);
        float dist = kickDir.len();
        if (dist < 0.0001f) {
            kickDir.set(dirT);
            dist = 0.0001f;
        }
        kickDir.nor();
        float alignment = kickDir.dot(dirT);

        if (alignment > 0.94f && dist < Constants.KICK_RANGE) {
            // Bien parada detrás de la pelota: carga el remate/toque y lo suelta.
            kickChargeTimer += dt;
            if (kickChargeTimer >= targetCharge * Constants.KICK_CHARGE_TIME) {
                out.kickHeld = false;
                out.kickReleased = true;
                kickChargeTimer = 0f;
                pending = Action.NONE;
            } else {
                out.kickHeld = true;
            }
            out.moveX = dirT.x * 0.15f;
            out.moveY = dirT.y * 0.15f;
        } else {
            // Se acomoda detrás de la pelota, sobre la línea hacia el objetivo.
            kickChargeTimer = 0f;
            Vector2 stand = new Vector2(bp).mulAdd(dirT, -0.6f);
            Vector2 go = stand.sub(sp);
            float len = go.len();
            if (len > 0.05f) {
                go.nor().scl(Math.min(1f, len * 2f));
                out.moveX = go.x;
                out.moveY = go.y;
            }
            // Si se pasó de largo o lleva mucho acomodándose, replantea la jugada.
            stuckTimer += dt;
            if (stuckTimer > 1.4f) {
                pending = Action.NONE;
                stuckTimer = 0f;
            }
            return;
        }
        stuckTimer = 0f;
    }

    private void decide(Vector2 sp, Vector2 rp, GoalkeeperEntity oppKeeper, Settings.Difficulty diff) {
        float goalDist = Math.abs(goalX - sp.x);
        boolean pressured = sp.dst(rp) < 2.8f;
        float halfH = Constants.FIELD_HEIGHT / 2f - 1f;

        if (goalDist < diff.aiShootRange) {
            // SHOOT: al rincón más lejano del arquero, con error según dificultad.
            float keeperY = oppKeeper != null ? oppKeeper.getPosition().y : 0f;
            float corner = (keeperY >= 0f ? -1f : 1f) * (Constants.GOAL_WIDTH / 2f - 0.8f);
            corner += MathUtils.random(-1f, 1f) * diff.aiAimJitter;
            target.set(goalX, corner);
            targetCharge = MathUtils.random(0.55f, 0.9f);
            pending = Action.SHOOT;
        } else if (pressured) {
            // PASS esquivando: toque fuerte-ish hacia el lado contrario del rival, ganando campo.
            float side = rp.y >= sp.y ? -1f : 1f;
            target.set(sp.x + attackSign * 7f, MathUtils.clamp(sp.y + side * 5f, -halfH, halfH));
            targetCharge = 0.45f;
            pending = Action.PASS;
        } else {
            // PASS de avance: toque suave hacia el arco rival, tirando a la franja central.
            target.set(sp.x + attackSign * 6f, MathUtils.clamp(sp.y * 0.5f, -halfH, halfH));
            targetCharge = 0.28f;
            pending = Action.PASS;
        }
    }

    private void trySlide(PlayerInput out, PlayerEntity self, Ball ball, PlayerEntity rival,
                          float distBall, float dt) {
        boolean rivalHasBallNearby = rival.isNear(ball) && distBall < Constants.KICK_RANGE + 0.9f;
        if (rivalHasBallNearby && slideRetryCooldown <= 0f && MathUtils.randomBoolean(0.35f)) {
            out.slidePressed = true;
            slideRetryCooldown = Constants.SLIDE_COOLDOWN + 0.5f;
        }
    }
}
