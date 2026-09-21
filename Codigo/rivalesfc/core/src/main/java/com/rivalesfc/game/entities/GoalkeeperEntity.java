package com.rivalesfc.game.entities;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.World;
import com.rivalesfc.game.Constants;
import com.rivalesfc.game.Settings;

/**
 * Arquero 100% IA (nunca recibe input humano). Body cinemático de Box2D que
 * solo se mueve en vertical sobre la línea de su arco.
 *
 * MK4: ahora tiene dos estados —
 *   TRACK: sigue la coordenada Y de la pelota con tiempo de reacción (como en MK2/MK3).
 *   DIVE : "estirada". Si la pelota viaja hacia el arco, la trayectoria (recta) cruza
 *          la boca del arco y el arquero no llega caminando, decide tirarse tras un
 *          reflejo corto, hacia el punto estimado (con un error que depende de la
 *          dificultad), a {@link Constants#GK_DIVE_SPEED}. Después queda en recarga.
 * Como el body es cinemático, seguir siendo imposible empujarlo: solo desvía la
 * pelota por contacto, y la detección de "atajada" la hace MatchSimulation.
 */
public class GoalkeeperEntity {

    public final Body body;
    public final Color color;
    private final float fixedX;
    private final float patrolHalfRange;
    private final float diveHalfRange;
    /** Hacia dónde queda el interior de la cancha desde este arco: +1 (arco izquierdo) o -1 (arco derecho). */
    private final float inward;

    private float trackedBallY = 0f;

    private boolean diving = false;
    private float diveTimer = 0f;
    private float diveCooldown = 0f;
    private float diveTargetY = 0f;
    private float diveSign = 0f;
    /** Cuenta regresiva del reflejo; negativa = todavía no hay remate que evaluar. */
    private float decisionTimer = -1f;
    private float plannedTargetY = 0f;
    private boolean decisionMade = false;

    /** -1..1: signo de la estirada por su progreso (0 = no se está tirando). Lo usa el render. */
    private float visualDive = 0f;

    public GoalkeeperEntity(World world, float fixedX, float patrolHalfRange, Color color) {
        this.fixedX = fixedX;
        this.patrolHalfRange = patrolHalfRange;
        this.diveHalfRange = patrolHalfRange + 0.2f;
        this.inward = fixedX < 0 ? 1f : -1f;
        this.color = color;

        BodyDef bd = new BodyDef();
        bd.type = BodyDef.BodyType.KinematicBody;
        bd.position.set(fixedX, 0);
        bd.fixedRotation = true;
        body = world.createBody(bd);

        CircleShape shape = new CircleShape();
        shape.setRadius(Constants.PLAYER_RADIUS);

        FixtureDef fd = new FixtureDef();
        fd.shape = shape;
        fd.filter.categoryBits = Constants.CAT_PLAYER;

        body.createFixture(fd);
        shape.dispose();
    }

    /**
     * Una vez por tick (30 Hz).
     *
     * @param ballPos posición de la pelota
     * @param ballVel velocidad de la pelota (para anticipar remates)
     * @param canDive false fuera de juego (cuenta regresiva, festejo, etc.)
     */
    public void update(Vector2 ballPos, Vector2 ballVel, float dt, boolean canDive) {
        Settings.Difficulty diff = Settings.difficulty;
        if (diveCooldown > 0f) {
            diveCooldown -= dt;
        }

        if (diving) {
            updateDive(dt);
            return;
        }
        visualDive = 0f;

        // --- TRACK ---
        float desired = MathUtils.clamp(ballPos.y, -patrolHalfRange, patrolHalfRange);
        float lag = MathUtils.clamp(dt / (Constants.GK_REACTION_TIME * diff.gkReactionScale), 0f, 1f);
        trackedBallY = MathUtils.lerp(trackedBallY, desired, lag);
        float diff_y = trackedBallY - body.getPosition().y;
        if (Math.abs(diff_y) < Constants.GK_DEAD_ZONE) {
            body.setLinearVelocity(0, 0);
        } else {
            setVerticalVelocity(Math.signum(diff_y) * Constants.GK_SPEED * diff.gkSpeedScale, dt);
        }

        if (canDive) {
            evaluateShot(ballPos, ballVel, dt, diff);
        } else {
            decisionTimer = -1f;
            decisionMade = false;
        }
    }

    /** ¿Viene un remate al arco que no llego a cubrir caminando? Si sí, tras el reflejo, tirarse. */
    private void evaluateShot(Vector2 ballPos, Vector2 ballVel, float dt, Settings.Difficulty diff) {
        float distIn = (ballPos.x - fixedX) * inward;              // >0: la pelota está delante del arquero
        float speedToGoal = -ballVel.x * inward;                   // >0: se acerca al arco
        boolean incoming = distIn > 0f && distIn < Constants.GK_DIVE_TRIGGER_DIST
                && speedToGoal > Constants.GK_DIVE_MIN_BALL_SPEED;
        if (!incoming || diveCooldown > 0f) {
            decisionTimer = -1f;
            decisionMade = false;
            return;
        }

        float t = distIn / speedToGoal;
        float yPred = ballPos.y + ballVel.y * t;                   // trayectoria recta (sin rebotes)
        boolean onTarget = Math.abs(yPred) < Constants.GOAL_WIDTH / 2f + 0.3f;
        float myY = body.getPosition().y;
        if (!onTarget || Math.abs(yPred - myY) < Constants.GK_DIVE_MIN_MISS) {
            decisionTimer = -1f;
            decisionMade = false;
            return;
        }

        if (decisionTimer < 0f) {
            decisionTimer = Constants.GK_DIVE_REACTION * diff.gkReactionScale;
            decisionMade = false;
        }
        decisionTimer -= dt;
        if (decisionTimer <= 0f && !decisionMade) {
            decisionMade = true;
            if (MathUtils.randomBoolean(diff.gkDiveChance)) {
                plannedTargetY = MathUtils.clamp(yPred + MathUtils.random(-1f, 1f) * diff.gkDiveNoise,
                        -diveHalfRange, diveHalfRange);
                startDive(plannedTargetY);
            } else {
                diveCooldown = 0.8f; // "se durmió": no se tira en este remate
            }
        }
    }

    private void startDive(float targetY) {
        diving = true;
        diveTimer = Constants.GK_DIVE_DURATION;
        diveTargetY = targetY;
        diveSign = Math.signum(targetY - body.getPosition().y);
        if (diveSign == 0f) diveSign = 1f;
        decisionTimer = -1f;
    }

    private void updateDive(float dt) {
        diveTimer -= dt;
        float dy = diveTargetY - body.getPosition().y;
        boolean arrived = Math.abs(dy) < 0.12f || Math.signum(dy) != diveSign;
        if (diveTimer <= 0f || arrived) {
            diving = false;
            diveCooldown = Constants.GK_DIVE_COOLDOWN;
            body.setLinearVelocity(0, 0);
            visualDive = 0f;
            trackedBallY = body.getPosition().y;
            return;
        }
        setVerticalVelocity(diveSign * Constants.GK_DIVE_SPEED, dt);
        float progress = 1f - MathUtils.clamp(diveTimer / Constants.GK_DIVE_DURATION, 0f, 1f);
        visualDive = diveSign * Math.max(0.35f, MathUtils.sin(progress * MathUtils.PI));
    }

    /** Aplica velocidad vertical sin dejar que el body salga del rango permitido del arco. */
    private void setVerticalVelocity(float vy, float dt) {
        float nextY = body.getPosition().y + vy * dt;
        if (nextY > diveHalfRange && vy > 0f) {
            vy = 0f;
        } else if (nextY < -diveHalfRange && vy < 0f) {
            vy = 0f;
        }
        body.setLinearVelocity(0, vy);
    }

    public Vector2 getPosition() {
        return body.getPosition();
    }

    public boolean isDiving() {
        return diving;
    }

    /** -1..1: signo de la estirada por su intensidad actual (0 si no se está tirando). */
    public float getVisualDive() {
        return visualDive;
    }

    /** Solo para la repetición: fuerza el estado visual sin tocar la IA. */
    public void setReplayDive(float value) {
        visualDive = value;
    }

    public void resetToCenter() {
        body.setTransform(fixedX, 0, 0);
        body.setLinearVelocity(0, 0);
        trackedBallY = 0f;
        diving = false;
        diveCooldown = 0f;
        decisionTimer = -1f;
        decisionMade = false;
        visualDive = 0f;
    }
}
