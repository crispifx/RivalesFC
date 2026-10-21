package com.rivalesfc.game.ai;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.rivalesfc.game.Constants;
import com.rivalesfc.game.Settings;
import com.rivalesfc.game.entities.Ball;
import com.rivalesfc.game.entities.GoalkeeperEntity;
import com.rivalesfc.game.entities.PlayerEntity;
import com.rivalesfc.game.input.PlayerInput;

public class SupportAI {

    private enum Action { NONE, SHOOT, PASS }

    private final float attackSign;
    private final float goalX;
    private final float ownGoalX;

    private Action pending = Action.NONE;
    private final Vector2 target = new Vector2();
    private float targetCharge = 0.5f;
    private float kickChargeTimer = 0f;
    private float slideRetryCooldown = 0f;
    private float stuckTimer = 0f;

    public SupportAI(boolean attackingTowardsNegativeX) {
        this.attackSign = attackingTowardsNegativeX ? -1f : 1f;
        float edge = Constants.FIELD_WIDTH / 2f + Constants.GOAL_DEPTH * 0.4f;
        this.goalX = attackSign * edge;
        this.ownGoalX = -attackSign * edge;
    }

    public void update(PlayerInput out, PlayerEntity self, Ball ball, PlayerEntity rival,
                       GoalkeeperEntity oppKeeper, float dt) {
        update(out, self, ball, rival, oppKeeper, dt, false, null);
    }

    public void update(PlayerInput out, PlayerEntity self, Ball ball, PlayerEntity rival,
                       GoalkeeperEntity oppKeeper, float dt, boolean support, Vector2 mate) {
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

            if (support) {
                supportMove(out, sp, bp, mate, ballInOwnHalf, diff);
            } else {
                if (rivalHasBall && ballInOwnHalf && sp.dst(rp) > 2.4f) {
                    defend(out, sp, bp, diff);
                } else {
                    chase(out, sp, bp, distBall, diff);
                }
                trySlide(out, self, ball, rival, distBall, dt);
            }
        }

        out.moveX *= diff.aiMoveScale;
        out.moveY *= diff.aiMoveScale;
        out.normalizeMove();
    }

    private void supportMove(PlayerInput out, Vector2 sp, Vector2 bp, Vector2 mate, boolean ballInOwnHalf,
                             Settings.Difficulty diff) {
        Vector2 spot = new Vector2();
        if (ballInOwnHalf) {

            spot.set(ownGoalX, 0f).lerp(bp, 0.45f);
            spot.y = bp.y * 0.5f;
        } else {

            spot.set(bp.x + attackSign * 5f, bp.y > 0f ? bp.y - 7f : bp.y + 7f);
        }
        if (mate != null && spot.dst(mate) < 3f) {
            Vector2 away = new Vector2(spot).sub(mate);
            if (away.len2() < 0.01f) {
                away.set(0f, 1f);
            }
            spot.set(mate).mulAdd(away.nor(), 3f);
        }
        clampInside(spot);
        Vector2 go = new Vector2(spot).sub(sp);
        float len = go.len();
        if (len > 0.6f) {
            go.nor();
            out.moveX = go.x;
            out.moveY = go.y;
        }
        out.sprint = len > diff.aiSprintDist;
    }

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

    private void chase(PlayerInput out, Vector2 sp, Vector2 bp, float distBall, Settings.Difficulty diff) {

        Vector2 approach = new Vector2(bp);
        if (distBall < 4f) {
            Vector2 toGoal = new Vector2(goalX, 0f).sub(bp).nor();
            approach.mulAdd(toGoal, -0.55f);
        }
        clampInside(approach);
        Vector2 go = new Vector2(approach).sub(sp);
        if (go.len2() > 0.0004f) {
            go.nor();
            out.moveX = go.x;
            out.moveY = go.y;
        }
        out.sprint = distBall > diff.aiSprintDist;
    }

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

        if (alignment > 0.80f && dist < Constants.KICK_RANGE) {

            kickChargeTimer += dt;
            if (kickChargeTimer >= targetCharge * Constants.KICK_CHARGE_TIME) {
                out.kickHeld = false;
                out.kickReleased = true;
                kickChargeTimer = 0f;
                pending = Action.NONE;

                out.moveX = dirT.x;
                out.moveY = dirT.y;
            } else {
                out.kickHeld = true;
                out.moveX = dirT.x * 0.15f;
                out.moveY = dirT.y * 0.15f;
            }
        } else {

            kickChargeTimer = 0f;
            Vector2 stand = new Vector2(bp).mulAdd(dirT, -0.6f);
            clampInside(stand);
            Vector2 go = stand.sub(sp);
            float len = go.len();
            if (len > 0.05f) {
                go.nor().scl(Math.min(1f, len * 2f));
                out.moveX = go.x;
                out.moveY = go.y;
            }

            stuckTimer += dt;
            if (stuckTimer > 1.4f) {
                pending = Action.NONE;
                stuckTimer = 0f;
            }
            return;
        }
        stuckTimer = 0f;
    }

    private static void clampInside(Vector2 p) {
        float mx = Constants.FIELD_WIDTH / 2f - Constants.PLAYER_RADIUS - 0.05f;
        float my = Constants.FIELD_HEIGHT / 2f - Constants.PLAYER_RADIUS - 0.05f;
        p.x = MathUtils.clamp(p.x, -mx, mx);
        p.y = MathUtils.clamp(p.y, -my, my);
    }

    private void decide(Vector2 sp, Vector2 rp, GoalkeeperEntity oppKeeper, Settings.Difficulty diff) {
        float goalDist = Math.abs(goalX - sp.x);
        boolean pressured = sp.dst(rp) < 2.8f;
        float halfH = Constants.FIELD_HEIGHT / 2f - 1f;

        if (goalDist < diff.aiShootRange) {

            float keeperY = oppKeeper != null ? oppKeeper.getPosition().y : 0f;
            float corner = (keeperY >= 0f ? -1f : 1f) * (Constants.GOAL_WIDTH / 2f - 0.8f);
            corner += MathUtils.random(-1f, 1f) * diff.aiAimJitter;
            target.set(goalX, corner);
            targetCharge = MathUtils.random(0.55f, 0.9f);
            pending = Action.SHOOT;
        } else if (pressured) {

            float side = rp.y >= sp.y ? -1f : 1f;
            target.set(sp.x + attackSign * 7f, MathUtils.clamp(sp.y + side * 5f, -halfH, halfH));
            targetCharge = 0.45f;
            pending = Action.PASS;
        } else {

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
