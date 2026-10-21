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

public class PlayerEntity {

    public final Body body;
    public final Color teamColor;
    public boolean humanControlled = true;

    private float kickChargeTime = 0f;

    private final Vector2 lastMoveDir = new Vector2(1f, 0f);
    private final Vector2 slideDirection = new Vector2();
    private boolean sliding = false;
    private float slideTimer = 0f;
    private float slideCooldown = 0f;

    private float recoveryTimer = 0f;

    private boolean slideBallTouched = false;

    public PlayerEntity(World world, Vector2 startPosition, Color teamColor) {
        this.teamColor = teamColor;

        BodyDef bd = new BodyDef();
        bd.type = BodyDef.BodyType.DynamicBody;
        bd.position.set(startPosition);
        bd.linearDamping = Constants.PLAYER_LINEAR_DAMPING;
        bd.fixedRotation = true;

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

    public void applyInput(PlayerInput input, float dt) {
        updateSlideTimers(dt);

        if (sliding) {
            kickChargeTime = 0f;
            return;
        }

        if (recoveryTimer > 0f) {

            recoveryTimer -= dt;
            kickChargeTime = 0f;
            return;
        }

        Vector2 dir = new Vector2(input.moveX, input.moveY);
        if (dir.len2() > 0f) {
            dir.nor();
            lastMoveDir.set(dir);

            float force = Constants.PLAYER_ACCEL_FORCE * (input.sprint ? Constants.PLAYER_SPRINT_ACCEL_MULT : 1f);
            body.applyForceToCenter(dir.scl(force), true);
        }

        float maxSpeed = input.sprint ? Constants.PLAYER_SPRINT_SPEED : Constants.PLAYER_MAX_SPEED;
        Vector2 v = body.getLinearVelocity();
        float speed = v.len();
        if (speed > maxSpeed) {

            float target = Math.max(maxSpeed, speed * 0.90f);
            v.nor().scl(target);
            body.setLinearVelocity(v);
        }

        if (input.kickHeld) {
            kickChargeTime = Math.min(Constants.KICK_CHARGE_TIME, kickChargeTime + dt);
        } else if (!input.kickReleased) {

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
                recoveryTimer = Constants.SLIDE_RECOVERY_TIME;

                Vector2 v = body.getLinearVelocity();
                body.setLinearVelocity(v.x * Constants.SLIDE_RECOVERY_BRAKE, v.y * Constants.SLIDE_RECOVERY_BRAKE);
            }
        } else if (slideCooldown > 0f) {
            slideCooldown -= dt;
        }
    }

    public float consumeKickPower() {
        float p = getKickPower();
        kickChargeTime = 0f;
        return p;
    }

    public void resetState() {
        sliding = false;
        slideTimer = 0f;
        slideCooldown = 0f;
        recoveryTimer = 0f;
        kickChargeTime = 0f;
        slideBallTouched = false;
    }

    public void setReplaySliding(boolean value) {
        sliding = value;
    }

    public void setDisplayKickPower(float power) {
        kickChargeTime = Math.max(0f, Math.min(1f, power)) * Constants.KICK_CHARGE_TIME;
    }

    public float getKickPower() {
        return kickChargeTime / Constants.KICK_CHARGE_TIME;
    }

    public boolean isRecovering() {
        return recoveryTimer > 0f;
    }

    public boolean isSlideReaching(Ball ball) {
        return getPosition().dst(ball.getPosition()) <= Constants.SLIDE_REACH + Constants.BALL_RADIUS;
    }

    public boolean isNear(Ball ball) {
        float dist = getPosition().dst(ball.getPosition());
        return dist <= Constants.KICK_RANGE + Constants.BALL_RADIUS;
    }

    public boolean isSliding() {
        return sliding;
    }

    public float getSlideProgress() {
        return sliding ? 1f - MathUtils.clamp(slideTimer / Constants.SLIDE_DURATION, 0f, 1f) : 0f;
    }

    public float getSlideCooldownFraction() {
        return MathUtils.clamp(slideCooldown / Constants.SLIDE_COOLDOWN, 0f, 1f);
    }

    public Vector2 getSlideDirection() {
        return slideDirection;
    }

    public boolean canWinBallThisSlide() {
        return sliding && !slideBallTouched;
    }

    public void markSlideBallTouched() {
        slideBallTouched = true;
    }
}
