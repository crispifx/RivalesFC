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
import com.rivalesfc.game.input.PlayerInput;

/**
 * Un jugador en cancha. En la Etapa 1 solo existe el jugador controlado por el
 * humano (sin IA de soporte todavía, sin equipos); esta clase ya deja lugar
 * para diferenciar humano/IA vía el flag {@code humanControlled}, que en la
 * Etapa 3 (2 vs 2, secc. 2.1) decidirá si el movimiento viene de teclado/red o
 * de la máquina de estados de IA descripta en la sección 1.2 de la propuesta.
 */
public class PlayerEntity {

    public final Body body;
    public final Color teamColor;
    public boolean humanControlled = true;

    /** Potencia acumulada mientras se mantiene presionado el botón de pateo (0..1). */
    private float kickChargeTime = 0f;

    // --- Planchazo / barrida ---
    /** Última dirección de movimiento no nula, usada como dirección del planchazo si el jugador está quieto. */
    private final Vector2 lastMoveDir = new Vector2(1f, 0f);
    private final Vector2 slideDirection = new Vector2();
    private boolean sliding = false;
    private float slideTimer = 0f;
    private float slideCooldown = 0f;
    /** Evita que un mismo planchazo "toque" la pelota varias veces (una vez por planchazo). */
    private boolean slideBallTouched = false;

    public PlayerEntity(World world, Vector2 startPosition, Color teamColor) {
        this.teamColor = teamColor;

        BodyDef bd = new BodyDef();
        bd.type = BodyDef.BodyType.DynamicBody;
        bd.position.set(startPosition);
        bd.linearDamping = Constants.PLAYER_LINEAR_DAMPING;
        bd.fixedRotation = true; // el sprite del jugador no debe "rodar"

        body = world.createBody(bd);

        CircleShape shape = new CircleShape();
        shape.setRadius(Constants.PLAYER_RADIUS);

        FixtureDef fd = new FixtureDef();
        fd.shape = shape;
        fd.density = 1.2f;
        fd.friction = 0.3f;
        fd.restitution = 0.05f;
        fd.filter.categoryBits = Constants.CAT_PLAYER;

        body.createFixture(fd);
        shape.dispose();
    }

    public Vector2 getPosition() {
        return body.getPosition();
    }

    /**
     * Aplica el movimiento correspondiente a un tick de simulación (30 Hz, secc. 2.3).
     * Usa fuerza (no velocidad directa) para que el movimiento se sienta con inercia,
     * y limita la velocidad máxima según haya o no sprint.
     *
     * Si el jugador está en medio de un planchazo, este método NO lee el input de
     * movimiento (no se puede re-dirigir en pleno planchazo): solo actualiza los
     * temporizadores de la barrida.
     */
    public void applyInput(PlayerInput input, float dt) {
        updateSlideTimers(dt);

        if (sliding) {
            kickChargeTime = 0f; // no se puede cargar un remate mientras se está tirado al piso
            return;
        }

        Vector2 dir = new Vector2(input.moveX, input.moveY);
        if (dir.len2() > 0f) {
            dir.nor();
            lastMoveDir.set(dir);
            body.applyForceToCenter(
                    dir.scl(Constants.PLAYER_ACCEL_FORCE),
                    true);
        }

        float maxSpeed = input.sprint ? Constants.PLAYER_SPRINT_SPEED : Constants.PLAYER_MAX_SPEED;
        Vector2 v = body.getLinearVelocity();
        if (v.len() > maxSpeed) {
            v.nor().scl(maxSpeed);
            body.setLinearVelocity(v);
        }

        if (input.kickHeld) {
            kickChargeTime = Math.min(Constants.KICK_CHARGE_TIME, kickChargeTime + dt);
        } else {
            kickChargeTime = 0f;
        }

        if (input.slidePressed && slideCooldown <= 0f) {
            startSlide();
        }
    }

    private void startSlide() {
        sliding = true;
        slideBallTouched = false;
        slideTimer = Constants.SLIDE_DURATION;
        slideDirection.set(lastMoveDir);
        if (slideDirection.len2() < 0.0001f) {
            slideDirection.set(1f, 0f);
        }
        slideDirection.nor();
        body.setLinearVelocity(slideDirection.x * Constants.SLIDE_SPEED, slideDirection.y * Constants.SLIDE_SPEED);
    }

    private void updateSlideTimers(float dt) {
        if (sliding) {
            slideTimer -= dt;
            body.setLinearVelocity(slideDirection.x * Constants.SLIDE_SPEED, slideDirection.y * Constants.SLIDE_SPEED);
            if (slideTimer <= 0f) {
                sliding = false;
                slideCooldown = Constants.SLIDE_COOLDOWN;
                // Frena un poco al levantarse, para que no siga "patinando" a máxima velocidad.
                Vector2 v = body.getLinearVelocity();
                body.setLinearVelocity(v.x * Constants.SLIDE_RECOVERY_BRAKE, v.y * Constants.SLIDE_RECOVERY_BRAKE);
            }
        } else if (slideCooldown > 0f) {
            slideCooldown -= dt;
        }
    }

    /** Potencia de pateo actual, en 0..1, según cuánto tiempo se sostuvo el botón. */
    public float getKickPower() {
        return kickChargeTime / Constants.KICK_CHARGE_TIME;
    }

    public boolean isNear(Ball ball) {
        float dist = getPosition().dst(ball.getPosition());
        return dist <= Constants.KICK_RANGE + Constants.BALL_RADIUS;
    }

    public boolean isSliding() {
        return sliding;
    }

    /** Fracción 0..1 de cuánto falta del planchazo actual (0 = recién arrancó, 1 = terminando). Útil para animarlo. */
    public float getSlideProgress() {
        return sliding ? 1f - MathUtils.clamp(slideTimer / Constants.SLIDE_DURATION, 0f, 1f) : 0f;
    }

    public Vector2 getSlideDirection() {
        return slideDirection;
    }

    /** true si este planchazo todavía no le "ganó" la pelota a nadie (para aplicar el efecto una sola vez). */
    public boolean canWinBallThisSlide() {
        return sliding && !slideBallTouched;
    }

    public void markSlideBallTouched() {
        slideBallTouched = true;
    }
}
