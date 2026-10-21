package com.rivalesfc.game.sim;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.ContactListener;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.Manifold;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.graphics.Color;
import com.rivalesfc.game.Constants;
import com.rivalesfc.game.GameMode;
import com.rivalesfc.game.MatchSetup;
import com.rivalesfc.game.Settings;
import com.rivalesfc.game.ai.SupportAI;
import com.rivalesfc.game.entities.Ball;
import com.rivalesfc.game.entities.Field;
import com.rivalesfc.game.entities.GoalkeeperEntity;
import com.rivalesfc.game.entities.PlayerEntity;
import com.rivalesfc.game.input.PlayerInput;
import com.rivalesfc.game.net.Snapshot;

public class MatchSimulation implements ContactListener {

    public enum Phase {

        KICKOFF,

        PLAYING,

        GOAL_CELEBRATION,

        REPLAY,

        HALFTIME,

        FULL_TIME
    }

    private final World world;
    private final Field field;
    private final Ball ball;

    private final PlayerEntity playerLeft;
    private final PlayerEntity playerRight;

    private final PlayerEntity mateLeft;
    private final PlayerEntity mateRight;
    private final GoalkeeperEntity keeperLeft;
    private final GoalkeeperEntity keeperRight;

    private final GameMode mode;

    private final SupportAI playerRightAI;
    private final SupportAI mateRightAI;
    private final PlayerInput aiGeneratedInput = new PlayerInput();
    private final PlayerInput aiGeneratedInput2 = new PlayerInput();
    private final boolean teams2v2;
    private final boolean rightIsAi;

    private boolean chaserIsMate = false;

    private boolean human3 = false;
    private boolean human4 = false;
    private PlayerInput input3 = null;
    private PlayerInput input4 = null;

    private float accumulator = 0f;
    private int tick = 0;

    private int scoreLeft = 0;
    private int scoreRight = 0;
    private String lastGoalMessage = null;

    private Phase phase = Phase.KICKOFF;
    private float phaseTimer = Constants.KICKOFF_FREEZE_SECONDS;
    private int half = 1;

    private final float halfDuration = Settings.halfDurationSeconds();
    private float halfTimeRemaining = halfDuration;

    private final MatchStats stats = new MatchStats();
    private int lastShotOnTargetTeam = -1;
    private int lastShotTick = -1000;
    private int pendingSaveTeam = -1;
    private boolean pendingSaveSfx = false;
    private boolean lastGoalByLeft = false;

    private final int ringSize = (int) (Constants.REPLAY_SECONDS * Constants.SIM_HZ) + 1;
    private final ReplayFrame[] ring = new ReplayFrame[ringSize];
    private int ringHead = 0;
    private int ringCount = 0;
    private ReplayFrame[] replayFrames = null;
    private float replayCursor = 0f;

    private boolean goalPending = false;
    private boolean pendingLeftTeamScored = false;

    private Float pendingKickSfxPower = null;
    private boolean pendingSlideTackleSfx = false;

    private int kickEventCount = 0;
    private int slideEventCount = 0;
    private int saveEventCount = 0;
    private float lastKickPower = 0f;
    private boolean remoteInit = false;
    private int remoteKickCount, remoteSlideCount, remoteSaveCount;
    private int remotePossessionLeft = 50;
    private float remoteReplayProgress = 0f;

    private int possessionTicksLeft = 0;
    private int possessionTicksRight = 0;

    private final Vector2 stuckAnchor = new Vector2();
    private final Vector2 stuckAnchor2 = new Vector2();
    private int stuckSamples = 0;
    private float stuckTimer = 0f;
    private int unstuckEventCount = 0;
    private boolean pendingUnstuck = false;

    private final float[] prevPos = new float[14];
    private final Vector2 renderTmp = new Vector2();

    public MatchSimulation(GameMode mode) {
        this.mode = mode;
        this.teams2v2 = mode.isTeamMode();
        this.rightIsAi = mode == GameMode.ONE_PLAYER || teams2v2;
        this.playerRightAI = rightIsAi ? new SupportAI(true) : null;
        this.mateRightAI = teams2v2 ? new SupportAI(true) : null;

        world = new World(new Vector2(0, 0), true);
        world.setContactListener(this);

        field = new Field(world);
        ball = new Ball(world, new Vector2(0, 0));

        playerLeft = new PlayerEntity(world, kickoffSpot(0), new Color(MatchSetup.leftColor()));
        playerRight = new PlayerEntity(world, kickoffSpot(1), new Color(MatchSetup.rightColor()));
        playerRight.humanControlled = !rightIsAi;
        if (teams2v2) {
            mateLeft = new PlayerEntity(world, kickoffSpot(2), new Color(MatchSetup.leftColor()));
            mateRight = new PlayerEntity(world, kickoffSpot(3), new Color(MatchSetup.rightColor()));
            mateLeft.humanControlled = true;
            mateRight.humanControlled = false;
        } else {
            mateLeft = null;
            mateRight = null;
        }

        float halfFieldWidth = Constants.FIELD_WIDTH / 2f;
        float patrolRange = Constants.GOAL_WIDTH / 2f - Constants.PLAYER_RADIUS;
        keeperLeft = new GoalkeeperEntity(world, -halfFieldWidth + Constants.GK_LINE_OFFSET, patrolRange, new Color(MatchSetup.leftColor()));
        keeperRight = new GoalkeeperEntity(world, halfFieldWidth - Constants.GK_LINE_OFFSET, patrolRange, new Color(MatchSetup.rightColor()));

        for (int i = 0; i < ring.length; i++) {
            ring[i] = new ReplayFrame();
        }
    }

    public void step(float rawDelta, PlayerInput inputLeft, PlayerInput inputRight) {
        if (mode == GameMode.ONLINE_CLIENT) {
            return;
        }

        accumulator += Math.min(rawDelta, Constants.MAX_FRAME_DELTA);
        while (accumulator >= Constants.SIM_STEP) {
            fixedTick(inputLeft, inputRight);
            accumulator -= Constants.SIM_STEP;
        }
    }

    private void fixedTick(PlayerInput inputLeft, PlayerInput inputRight) {
        capturePrev();
        inputLeft.sequence = tick;
        inputRight.sequence = tick;
        if (input3 != null) input3.sequence = tick;
        if (input4 != null) input4.sequence = tick;

        if (phase == Phase.REPLAY) {
            stepReplay();
            inputLeft.clearTransient();
            inputRight.clearTransient();
            clearExtraTransients();
            return;
        }

        boolean playEnabled = phase == Phase.PLAYING;

        if (playEnabled) {
            playerLeft.applyInput(inputLeft, Constants.SIM_STEP);
            handleKick(playerLeft, inputLeft, 0);

            if (teams2v2) {

                mateLeft.applyInput(inputRight, Constants.SIM_STEP);
                handleKick(mateLeft, inputRight, 0);
            }

            PlayerInput rightInput;
            if (teams2v2) {
                updateAiTeam2v2();
                rightInput = human3 && input3 != null ? input3 : aiGeneratedInput;
            } else if (mode == GameMode.ONE_PLAYER) {
                playerRightAI.update(aiGeneratedInput, playerRight, ball, playerLeft, keeperLeft, Constants.SIM_STEP);
                rightInput = aiGeneratedInput;
            } else {
                rightInput = inputRight;
            }
            playerRight.applyInput(rightInput, Constants.SIM_STEP);
            handleKick(playerRight, rightInput, 1);
            if (teams2v2) {
                PlayerInput mateRightInput = human4 && input4 != null ? input4 : aiGeneratedInput2;
                mateRight.applyInput(mateRightInput, Constants.SIM_STEP);
                handleKick(mateRight, mateRightInput, 1);
            }

            handleSlideTackle(playerLeft, ball, 0);
            handleSlideTackle(playerRight, ball, 1);
            if (teams2v2) {
                handleSlideTackle(mateLeft, ball, 0);
                handleSlideTackle(mateRight, ball, 1);
            }

            boolean leftNear = playerLeft.isNear(ball) || (teams2v2 && mateLeft.isNear(ball));
            boolean rightNear = playerRight.isNear(ball) || (teams2v2 && mateRight.isNear(ball));
            if (leftNear) {
                possessionTicksLeft++;
            } else if (rightNear) {
                possessionTicksRight++;
            }
        } else {

            playerLeft.body.setLinearVelocity(0, 0);
            playerRight.body.setLinearVelocity(0, 0);
            if (teams2v2) {
                mateLeft.body.setLinearVelocity(0, 0);
                mateRight.body.setLinearVelocity(0, 0);
            }
            ball.body.setLinearVelocity(0, 0);
            ball.body.setAngularVelocity(0);
            inputLeft.clearTransient();
            inputRight.clearTransient();
            clearExtraTransients();
        }

        keeperLeft.update(ball.getPosition(), ball.body.getLinearVelocity(), Constants.SIM_STEP, playEnabled);
        keeperRight.update(ball.getPosition(), ball.body.getLinearVelocity(), Constants.SIM_STEP, playEnabled);

        world.step(Constants.SIM_STEP, 8, 3);
        tick++;

        if (playEnabled) {
            recordFrame();
        }

        if (goalPending) {
            goalPending = false;
            pendingSaveTeam = -1;
            registerGoal(pendingLeftTeamScored);
        } else if (pendingSaveTeam >= 0) {
            stats.saves[pendingSaveTeam]++;
            pendingSaveSfx = true;
            saveEventCount++;
            lastShotOnTargetTeam = -1;
            pendingSaveTeam = -1;
        }

        if (phase == Phase.PLAYING) {
            updateStuckWatchdog(Constants.SIM_STEP);
        } else {
            stuckTimer = 0f;
        }

        advancePhase(Constants.SIM_STEP);
    }

    private void updateStuckWatchdog(float dt) {
        Vector2 bp = ball.getPosition();
        if (stuckTimer <= 0f) {
            stuckSamples = 0;
            stuckAnchor.set(bp);
            stuckAnchor2.set(bp);
            stuckTimer = 0.0001f;
            return;
        }
        stuckTimer += dt;
        if (stuckTimer < Constants.BALL_STUCK_SECONDS / 2f) {
            return;
        }

        boolean stuck = stuckSamples >= 2 && bp.dst(stuckAnchor) < Constants.BALL_STUCK_RADIUS;
        stuckAnchor.set(stuckAnchor2);
        stuckAnchor2.set(bp);
        stuckSamples++;
        stuckTimer = 0.0001f;
        if (!stuck) {
            return;
        }
        {
            float maxX = Constants.FIELD_WIDTH / 2f - Constants.BALL_UNSTUCK_INSET;
            float maxY = Constants.FIELD_HEIGHT / 2f - Constants.BALL_UNSTUCK_INSET;
            Vector2 target = new Vector2(
                    Math.max(-maxX, Math.min(maxX, bp.x)),
                    Math.max(-maxY, Math.min(maxY, bp.y)));
            if (target.dst(bp) < 1.5f) {

                Vector2 toCenter = new Vector2(-bp.x, -bp.y);
                if (toCenter.len2() < 0.01f) {
                    toCenter.set(1f, 0f);
                }
                target.set(bp).mulAdd(toCenter.nor(), 3f);
            }
            ball.reset(target);
            stuckTimer = 0f;
            unstuckEventCount++;
            pendingUnstuck = true;
            syncPrev();
        }
    }

    public boolean consumeUnstuckEvent() {
        boolean v = pendingUnstuck;
        pendingUnstuck = false;
        return v;
    }

    public int getUnstuckEventCount() {
        return unstuckEventCount;
    }

    private void updateAiTeam2v2() {
        boolean ai3 = !(human3 && input3 != null);
        boolean ai4 = !(human4 && input4 != null);
        if (!ai3 && !ai4) {
            return;
        }
        Vector2 bp = ball.getPosition();
        boolean both = ai3 && ai4;
        if (both) {
            float dA = playerRight.getPosition().dst(bp);
            float dB = mateRight.getPosition().dst(bp);
            if (chaserIsMate) {
                if (dA + 2f < dB) chaserIsMate = false;
            } else if (dB + 2f < dA) {
                chaserIsMate = true;
            }
        }
        PlayerEntity rival = playerLeft.getPosition().dst2(bp) <= mateLeft.getPosition().dst2(bp) ? playerLeft : mateLeft;

        if (ai3) {
            playerRightAI.update(aiGeneratedInput, playerRight, ball, rival, keeperLeft, Constants.SIM_STEP,
                    both && chaserIsMate, mateRight.getPosition());
        }
        if (ai4) {
            mateRightAI.update(aiGeneratedInput2, mateRight, ball, rival, keeperLeft, Constants.SIM_STEP,
                    both && !chaserIsMate, playerRight.getPosition());
        }
    }

    public void setRightHumans(boolean p3Human, PlayerInput p3Input, boolean p4Human, PlayerInput p4Input) {
        if (!teams2v2) {
            return;
        }
        this.human3 = p3Human && p3Input != null;
        this.human4 = p4Human && p4Input != null;
        this.input3 = p3Input;
        this.input4 = p4Input;
        playerRight.humanControlled = this.human3;
        mateRight.humanControlled = this.human4;
    }

    public boolean isRightHuman(int which) {
        return which == 0 ? human3 : human4;
    }

    private void clearExtraTransients() {
        if (input3 != null) input3.clearTransient();
        if (input4 != null) input4.clearTransient();
    }

    private void capturePrev() {
        copyCurrentTo(prevPos);
    }

    private void syncPrev() {
        copyCurrentTo(prevPos);
    }

    private void copyCurrentTo(float[] a) {
        a[0] = ball.getPosition().x;         a[1] = ball.getPosition().y;
        a[2] = playerLeft.getPosition().x;   a[3] = playerLeft.getPosition().y;
        a[4] = playerRight.getPosition().x;  a[5] = playerRight.getPosition().y;
        a[6] = keeperLeft.getPosition().x;   a[7] = keeperLeft.getPosition().y;
        a[8] = keeperRight.getPosition().x;  a[9] = keeperRight.getPosition().y;
        if (teams2v2) {
            a[10] = mateLeft.getPosition().x;  a[11] = mateLeft.getPosition().y;
            a[12] = mateRight.getPosition().x; a[13] = mateRight.getPosition().y;
        }
    }

    public float getRenderAlpha() {
        if (mode == GameMode.ONLINE_CLIENT) {
            return 1f;
        }
        return Math.max(0f, Math.min(1f, accumulator / Constants.SIM_STEP));
    }

    private Vector2 renderOf(int idx, Vector2 cur) {
        float a = getRenderAlpha();
        float px = prevPos[idx * 2];
        float py = prevPos[idx * 2 + 1];

        if (a >= 1f || (cur.x - px) * (cur.x - px) + (cur.y - py) * (cur.y - py) > 36f) {
            return renderTmp.set(cur);
        }
        return renderTmp.set(px + (cur.x - px) * a, py + (cur.y - py) * a);
    }

    public Vector2 renderBall(Vector2 out) { return out.set(renderOf(0, ball.getPosition())); }
    public Vector2 renderPlayerLeft(Vector2 out) { return out.set(renderOf(1, playerLeft.getPosition())); }
    public Vector2 renderPlayerRight(Vector2 out) { return out.set(renderOf(2, playerRight.getPosition())); }
    public Vector2 renderKeeperLeft(Vector2 out) { return out.set(renderOf(3, keeperLeft.getPosition())); }
    public Vector2 renderKeeperRight(Vector2 out) { return out.set(renderOf(4, keeperRight.getPosition())); }
    public Vector2 renderMateLeft(Vector2 out) { return out.set(renderOf(5, mateLeft.getPosition())); }
    public Vector2 renderMateRight(Vector2 out) { return out.set(renderOf(6, mateRight.getPosition())); }

    private void advancePhase(float dt) {
        if (phase == Phase.PLAYING) {
            halfTimeRemaining -= dt;
            if (halfTimeRemaining <= 0f) {
                halfTimeRemaining = 0f;
                if (half == 1) {
                    half = 2;
                    resetKickoffPositions();
                    phase = Phase.HALFTIME;
                    phaseTimer = Constants.HALFTIME_BREAK_SECONDS;
                } else if (half == 2 && scoreLeft == scoreRight) {

                    half = 3;
                    halfTimeRemaining = Constants.OVERTIME_SECONDS;
                    resetKickoffPositions();
                    phase = Phase.KICKOFF;
                    phaseTimer = Constants.KICKOFF_FREEZE_SECONDS;
                } else {
                    phase = Phase.FULL_TIME;
                }
            }
            return;
        }

        if (phase == Phase.FULL_TIME) {
            return;
        }

        phaseTimer -= dt;
        if (phaseTimer > 0f) {
            return;
        }

        switch (phase) {
            case GOAL_CELEBRATION:
                if (replayFrames != null && replayFrames.length >= 20) {
                    phase = Phase.REPLAY;
                    replayCursor = 0f;
                    phaseTimer = replayFrames.length * Constants.SIM_STEP / Constants.REPLAY_SPEED;
                } else if (half == 3) {
                    phase = Phase.FULL_TIME;
                } else {
                    phase = Phase.KICKOFF;
                    phaseTimer = Constants.KICKOFF_FREEZE_SECONDS;
                }
                break;
            case KICKOFF:
                phase = Phase.PLAYING;
                break;
            case HALFTIME:
                halfTimeRemaining = halfDuration;
                phase = Phase.KICKOFF;
                phaseTimer = Constants.KICKOFF_FREEZE_SECONDS;
                break;
            default:
                break;
        }
    }

    private Vector2 kickoffSpot(int which) {
        if (!teams2v2) {
            return new Vector2(which == 1 ? Constants.KICKOFF_RIGHT_X : Constants.KICKOFF_LEFT_X, 0f);
        }
        float front = Constants.KICKOFF_2V2_FRONT_X, back = Constants.KICKOFF_2V2_BACK_X, y = Constants.KICKOFF_2V2_SPREAD_Y;
        switch (which) {
            case 0: return new Vector2(-front, y);
            case 1: return new Vector2(front, -y);
            case 2: return new Vector2(-back, -y);
            default: return new Vector2(back, y);
        }
    }

    private void placeAtKickoff(PlayerEntity p, int which) {
        Vector2 k = kickoffSpot(which);
        p.body.setTransform(k.x, k.y, 0);
        p.body.setLinearVelocity(0, 0);
        p.resetState();
    }

    private void resetKickoffPositions() {
        ball.reset(new Vector2(0, 0));

        placeAtKickoff(playerLeft, 0);
        placeAtKickoff(playerRight, 1);
        if (teams2v2) {
            placeAtKickoff(mateLeft, 2);
            placeAtKickoff(mateRight, 3);
        }

        keeperLeft.resetToCenter();
        keeperRight.resetToCenter();
        ringCount = 0;
        ringHead = 0;
        stuckTimer = 0f;
        syncPrev();
    }

    private void recordFrame() {
        ReplayFrame f = ring[ringHead];
        f.ballX = ball.getPosition().x;
        f.ballY = ball.getPosition().y;
        f.ballAngle = ball.body.getAngle();
        f.lx = playerLeft.getPosition().x;
        f.ly = playerLeft.getPosition().y;
        f.lvx = playerLeft.body.getLinearVelocity().x;
        f.lvy = playerLeft.body.getLinearVelocity().y;
        f.lSliding = playerLeft.isSliding();
        f.rx = playerRight.getPosition().x;
        f.ry = playerRight.getPosition().y;
        f.rvx = playerRight.body.getLinearVelocity().x;
        f.rvy = playerRight.body.getLinearVelocity().y;
        f.rSliding = playerRight.isSliding();
        if (teams2v2) {
            f.mlx = mateLeft.getPosition().x;  f.mly = mateLeft.getPosition().y;
            f.mlvx = mateLeft.body.getLinearVelocity().x;  f.mlvy = mateLeft.body.getLinearVelocity().y;
            f.mlSliding = mateLeft.isSliding();
            f.mrx = mateRight.getPosition().x; f.mry = mateRight.getPosition().y;
            f.mrvx = mateRight.body.getLinearVelocity().x; f.mrvy = mateRight.body.getLinearVelocity().y;
            f.mrSliding = mateRight.isSliding();
        }
        f.kly = keeperLeft.getPosition().y;
        f.klvy = keeperLeft.body.getLinearVelocity().y;
        f.klDive = keeperLeft.getVisualDive();
        f.kry = keeperRight.getPosition().y;
        f.krvy = keeperRight.body.getLinearVelocity().y;
        f.krDive = keeperRight.getVisualDive();
        ringHead = (ringHead + 1) % ring.length;
        ringCount = Math.min(ringCount + 1, ring.length);
    }

    private void captureReplay() {
        replayFrames = new ReplayFrame[ringCount];
        int start = (ringHead - ringCount + ring.length) % ring.length;
        for (int i = 0; i < ringCount; i++) {
            ReplayFrame c = new ReplayFrame();
            c.copyFrom(ring[(start + i) % ring.length]);
            replayFrames[i] = c;
        }
    }

    private void stepReplay() {
        if (replayFrames != null && replayFrames.length > 0) {
            int idx = Math.min((int) replayCursor, replayFrames.length - 1);
            boolean first = replayCursor == 0f;
            applyFrame(replayFrames[idx]);
            if (first) {
                syncPrev();
            }
            replayCursor += Constants.REPLAY_SPEED;
        }
        phaseTimer -= Constants.SIM_STEP;
        if (phaseTimer <= 0f) {
            endReplay();
        }
    }

    private void applyFrame(ReplayFrame f) {
        ball.body.setTransform(f.ballX, f.ballY, f.ballAngle);
        playerLeft.body.setTransform(f.lx, f.ly, 0);
        playerLeft.body.setLinearVelocity(f.lvx, f.lvy);
        playerLeft.setReplaySliding(f.lSliding);
        playerRight.body.setTransform(f.rx, f.ry, 0);
        playerRight.body.setLinearVelocity(f.rvx, f.rvy);
        playerRight.setReplaySliding(f.rSliding);
        if (teams2v2) {
            mateLeft.body.setTransform(f.mlx, f.mly, 0);
            mateLeft.body.setLinearVelocity(f.mlvx, f.mlvy);
            mateLeft.setReplaySliding(f.mlSliding);
            mateRight.body.setTransform(f.mrx, f.mry, 0);
            mateRight.body.setLinearVelocity(f.mrvx, f.mrvy);
            mateRight.setReplaySliding(f.mrSliding);
        }
        keeperLeft.body.setTransform(keeperLeft.body.getPosition().x, f.kly, 0);
        keeperLeft.body.setLinearVelocity(0, f.klvy);
        keeperLeft.setReplayDive(f.klDive);
        keeperRight.body.setTransform(keeperRight.body.getPosition().x, f.kry, 0);
        keeperRight.body.setLinearVelocity(0, f.krvy);
        keeperRight.setReplayDive(f.krDive);
    }

    public void refreshTeamColors() {
        playerLeft.teamColor.set(MatchSetup.leftColor());
        playerRight.teamColor.set(MatchSetup.rightColor());
        if (teams2v2) {
            mateLeft.teamColor.set(MatchSetup.leftColor());
            mateRight.teamColor.set(MatchSetup.rightColor());
        }
        keeperLeft.color.set(MatchSetup.leftColor());
        keeperRight.color.set(MatchSetup.rightColor());
    }

    private void endReplay() {
        resetKickoffPositions();
        replayFrames = null;
        if (half == 3) {
            phase = Phase.FULL_TIME;
            return;
        }
        phase = Phase.KICKOFF;
        phaseTimer = Constants.KICKOFF_FREEZE_SECONDS;
    }

    public void skipReplay() {
        if (phase == Phase.REPLAY) {
            phaseTimer = 0f;
        }
    }

    private void handleKick(PlayerEntity player, PlayerInput input, int team) {
        if (input.kickReleased) {
            float power = player.consumeKickPower();

            if (!player.isSliding() && !player.isRecovering() && player.isNear(ball)) {
                Vector2 toBall = kickDirection(player.getPosition(), ball.getPosition(), input, team, new Vector2());
                ball.kick(toBall, power, 0f);
                pendingKickSfxPower = power;
                kickEventCount++;
                lastKickPower = power;
                registerShot(team, toBall, power);
            }
        }
        input.clearTransient();
    }

    public static Vector2 kickDirection(Vector2 playerPos, Vector2 ballPos, PlayerInput input, int team, Vector2 out) {
        out.set(ballPos).sub(playerPos);
        if (out.len2() < 0.0001f) {
            out.set(team == 0 ? 1f : -1f, 0f);
        }
        out.nor();
        float mvLen = (float) Math.sqrt(input.moveX * input.moveX + input.moveY * input.moveY);
        if (mvLen > 0.05f) {
            float mx = input.moveX / mvLen;
            float my = input.moveY / mvLen;
            if (mx * out.x + my * out.y > 0f) {
                float w = Constants.AIM_ASSIST * Math.min(1f, mvLen);
                out.add(mx * w, my * w).nor();
            }
        }
        return out;
    }

    private void registerShot(int team, Vector2 rawDir, float power) {
        if (power < Constants.SHOT_MIN_POWER) {
            return;
        }
        float attack = team == 0 ? 1f : -1f;
        Vector2 dir = new Vector2(rawDir).nor();
        if (dir.x * attack < 0.15f) {
            return;
        }
        stats.shots[team]++;
        float goalLineX = attack * (Constants.FIELD_WIDTH / 2f);
        Vector2 bp = ball.getPosition();
        float t = (goalLineX - bp.x) / dir.x;
        float yAtGoal = bp.y + dir.y * t;
        if (Math.abs(yAtGoal) < Constants.GOAL_WIDTH / 2f) {
            stats.shotsOnTarget[team]++;
            lastShotOnTargetTeam = team;
            lastShotTick = tick;
        }
    }

    private void handleSlideTackle(PlayerEntity player, Ball ball, int team) {
        if (player.canWinBallThisSlide() && player.isSlideReaching(ball)) {
            ball.kick(player.getSlideDirection(), Constants.SLIDE_KICK_POWER, 0f);
            player.markSlideBallTouched();
            pendingSlideTackleSfx = true;
            slideEventCount++;
            stats.tackles[team]++;
        }
    }

    public Float consumeKickSfxEvent() {
        Float v = pendingKickSfxPower;
        pendingKickSfxPower = null;
        return v;
    }

    public boolean consumeSlideTackleSfxEvent() {
        boolean v = pendingSlideTackleSfx;
        pendingSlideTackleSfx = false;
        return v;
    }

    public boolean consumeSaveEvent() {
        boolean v = pendingSaveSfx;
        pendingSaveSfx = false;
        return v;
    }

    public MatchStats getStats() {
        return stats;
    }

    public boolean isLastGoalByLeft() {
        return lastGoalByLeft;
    }

    public int getPossessionPercentLeft() {
        if (mode == GameMode.ONLINE_CLIENT) {
            return remotePossessionLeft;
        }
        int total = possessionTicksLeft + possessionTicksRight;
        if (total == 0) {
            return 50;
        }
        return Math.round(possessionTicksLeft * 100f / total);
    }

    public GameMode getMode() {
        return mode;
    }

    @Override
    public void beginContact(Contact contact) {
        boolean isBall = contact.getFixtureA().getFilterData().categoryBits == Constants.CAT_BALL
                || contact.getFixtureB().getFilterData().categoryBits == Constants.CAT_BALL;
        if (!isBall) {
            return;
        }

        if (phase == Phase.PLAYING && !goalPending) {
            Body other = contact.getFixtureA().getFilterData().categoryBits == Constants.CAT_BALL
                    ? contact.getFixtureB().getBody() : contact.getFixtureA().getBody();
            int keeperTeam = other == keeperLeft.body ? 0 : (other == keeperRight.body ? 1 : -1);
            if (keeperTeam >= 0 && lastShotOnTargetTeam == 1 - keeperTeam
                    && tick - lastShotTick <= 4 * (int) Constants.SIM_HZ) {
                pendingSaveTeam = keeperTeam;
            }
        }

        if (goalPending || phase == Phase.GOAL_CELEBRATION) {
            return;
        }

        if (contact.getFixtureA() == field.leftGoalSensor || contact.getFixtureB() == field.leftGoalSensor) {
            goalPending = true;
            pendingLeftTeamScored = false;
        } else if (contact.getFixtureA() == field.rightGoalSensor || contact.getFixtureB() == field.rightGoalSensor) {
            goalPending = true;
            pendingLeftTeamScored = true;
        }
    }

    private void registerGoal(boolean leftTeamScored) {
        if (leftTeamScored) {
            scoreLeft++;
            lastGoalMessage = "GOL de " + MatchSetup.leftName + " (" + MatchSetup.leftTeam.name + ")";
        } else {
            scoreRight++;
            lastGoalMessage = "GOL de " + MatchSetup.rightName + " (" + MatchSetup.rightTeam.name + ")";
        }
        lastGoalByLeft = leftTeamScored;
        lastShotOnTargetTeam = -1;

        captureReplay();
        resetKickoffPositions();
        phase = Phase.GOAL_CELEBRATION;
        phaseTimer = Constants.GOAL_CELEBRATION_SECONDS;
    }

    @Override
    public void endContact(Contact contact) {
    }

    @Override
    public void preSolve(Contact contact, Manifold oldManifold) {

        short ca = contact.getFixtureA().getFilterData().categoryBits;
        short cb = contact.getFixtureB().getFilterData().categoryBits;
        if ((ca == Constants.CAT_BALL && cb == Constants.CAT_PLAYER)
                || (cb == Constants.CAT_BALL && ca == Constants.CAT_PLAYER)) {
            Body other = ca == Constants.CAT_BALL ? contact.getFixtureB().getBody() : contact.getFixtureA().getBody();
            if (other != keeperLeft.body && other != keeperRight.body) {
                contact.setRestitution(Constants.PLAYER_BALL_RESTITUTION);
            }
        }
    }

    @Override
    public void postSolve(Contact contact, ContactImpulse impulse) {
    }

    public World getWorld() {
        return world;
    }

    public Field getField() {
        return field;
    }

    public Ball getBall() {
        return ball;
    }

    public PlayerEntity getPlayerLeft() {
        return playerLeft;
    }

    public PlayerEntity getPlayerRight() {
        return playerRight;
    }

    public boolean hasMates() {
        return teams2v2;
    }

    public PlayerEntity getMateLeft() {
        return mateLeft;
    }

    public PlayerEntity getMateRight() {
        return mateRight;
    }

    public GoalkeeperEntity getKeeperLeft() {
        return keeperLeft;
    }

    public GoalkeeperEntity getKeeperRight() {
        return keeperRight;
    }

    public int getScoreLeft() {
        return scoreLeft;
    }

    public int getScoreRight() {
        return scoreRight;
    }

    public String getLastGoalMessage() {
        return phase == Phase.GOAL_CELEBRATION ? lastGoalMessage : null;
    }

    public Phase getPhase() {
        return phase;
    }

    public float getPhaseTimer() {
        return phaseTimer;
    }

    public float getReplayProgress() {
        if (mode == GameMode.ONLINE_CLIENT) {
            return remoteReplayProgress;
        }
        if (phase != Phase.REPLAY || replayFrames == null || replayFrames.length == 0) {
            return 0f;
        }
        return Math.min(1f, replayCursor / replayFrames.length);
    }

    public int getHalf() {
        return half;
    }

    public float getHalfTimeRemaining() {
        return halfTimeRemaining;
    }

    public int getTick() {
        return tick;
    }

    public void fillSnapshot(Snapshot s) {
        s.tick = tick;
        s.phase = (byte) phase.ordinal();
        s.half = (byte) half;
        s.scoreLeft = (byte) Math.min(127, scoreLeft);
        s.scoreRight = (byte) Math.min(127, scoreRight);
        s.possessionLeft = (byte) getPossessionPercentLeft();
        s.lastGoalByLeft = lastGoalByLeft;
        s.halfTimeRemaining = halfTimeRemaining;
        s.phaseTimer = phaseTimer;
        s.replayProgress = getReplayProgress();

        s.ballX = ball.getPosition().x;
        s.ballY = ball.getPosition().y;
        s.ballAngle = ball.body.getAngle();
        s.ballVX = ball.body.getLinearVelocity().x;
        s.ballVY = ball.body.getLinearVelocity().y;

        PlayerEntity[] ps = {playerLeft, playerRight};
        GoalkeeperEntity[] ks = {keeperLeft, keeperRight};
        for (int i = 0; i < 2; i++) {
            s.px[i] = ps[i].getPosition().x;
            s.py[i] = ps[i].getPosition().y;
            s.pvx[i] = ps[i].body.getLinearVelocity().x;
            s.pvy[i] = ps[i].body.getLinearVelocity().y;
            s.sliding[i] = ps[i].isSliding();
            s.kickPower[i] = ps[i].getKickPower();
            s.ky[i] = ks[i].getPosition().y;
            s.kvy[i] = ks[i].body.getLinearVelocity().y;
            s.kdive[i] = ks[i].getVisualDive();
            s.kdiving[i] = ks[i].isDiving();
            s.shots[i] = (byte) Math.min(127, stats.shots[i]);
            s.shotsOnTarget[i] = (byte) Math.min(127, stats.shotsOnTarget[i]);
            s.tackles[i] = (byte) Math.min(127, stats.tackles[i]);
            s.saves[i] = (byte) Math.min(127, stats.saves[i]);
        }
        s.kickCount = kickEventCount;
        s.slideCount = slideEventCount;
        s.saveCount = saveEventCount;
        s.lastKickPower = lastKickPower;
    }

    public void applySnapshot(Snapshot s) {

        if (remoteInit) {
            if (s.kickCount != remoteKickCount) pendingKickSfxPower = s.lastKickPower;
            if (s.slideCount != remoteSlideCount) pendingSlideTackleSfx = true;
            if (s.saveCount != remoteSaveCount) pendingSaveSfx = true;
        }
        remoteKickCount = s.kickCount;
        remoteSlideCount = s.slideCount;
        remoteSaveCount = s.saveCount;
        remoteInit = true;

        tick = s.tick;
        Phase[] phases = Phase.values();
        phase = phases[Math.max(0, Math.min(phases.length - 1, s.phase))];
        half = s.half;
        scoreLeft = s.scoreLeft;
        scoreRight = s.scoreRight;
        halfTimeRemaining = s.halfTimeRemaining;
        phaseTimer = s.phaseTimer;
        remoteReplayProgress = s.replayProgress;
        remotePossessionLeft = s.possessionLeft;
        lastGoalByLeft = s.lastGoalByLeft;
        lastGoalMessage = s.lastGoalByLeft
                ? "GOL de " + MatchSetup.leftName + " (" + MatchSetup.leftTeam.name + ")"
                : "GOL de " + MatchSetup.rightName + " (" + MatchSetup.rightTeam.name + ")";

        ball.body.setTransform(s.ballX, s.ballY, s.ballAngle);
        ball.body.setLinearVelocity(s.ballVX, s.ballVY);

        PlayerEntity[] ps = {playerLeft, playerRight};
        GoalkeeperEntity[] ks = {keeperLeft, keeperRight};
        for (int i = 0; i < 2; i++) {
            ps[i].body.setTransform(s.px[i], s.py[i], 0);
            ps[i].body.setLinearVelocity(s.pvx[i], s.pvy[i]);
            ps[i].setReplaySliding(s.sliding[i]);
            ps[i].setDisplayKickPower(s.kickPower[i]);
            ks[i].body.setTransform(ks[i].body.getPosition().x, s.ky[i], 0);
            ks[i].body.setLinearVelocity(0, s.kvy[i]);
            ks[i].setReplayDive(s.kdive[i]);
            ks[i].setRemoteDiving(s.kdiving[i]);
            stats.shots[i] = s.shots[i];
            stats.shotsOnTarget[i] = s.shotsOnTarget[i];
            stats.tackles[i] = s.tackles[i];
            stats.saves[i] = s.saves[i];
        }
    }

    public void dispose() {
        world.dispose();
    }
}
