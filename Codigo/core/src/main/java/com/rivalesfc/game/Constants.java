package com.rivalesfc.game;

import com.badlogic.gdx.graphics.Color;

public final class Constants {

    private Constants() {
    }

    public static final float SIM_HZ = 30f;
    public static final float SIM_STEP = 1f / SIM_HZ;

    public static final float PPM = 32f;

    public static final float FIELD_WIDTH = 44f;
    public static final float FIELD_HEIGHT = 28f;
    public static final float FIELD_MARGIN = 4f;
    public static final float GOAL_WIDTH = 6f;
    public static final float GOAL_DEPTH = 1.5f;

    public static final float FIELD_CORNER_CUT = 1.2f;
    public static final float PENALTY_BOX_DEPTH = 5f;
    public static final float PENALTY_BOX_HEIGHT = 16f;
    public static final float GOAL_BOX_DEPTH = 2f;
    public static final float GOAL_BOX_HEIGHT = 8f;

    public static final float PLAYER_RADIUS = 0.5f;
    public static final float PLAYER_MAX_SPEED = 7.5f;
    public static final float PLAYER_SPRINT_SPEED = 10.5f;
    public static final float PLAYER_ACCEL_FORCE = 45f;

    public static final float PLAYER_SPRINT_ACCEL_MULT = 1.5f;
    public static final float PLAYER_LINEAR_DAMPING = 6f;

    public static final float BALL_RADIUS = 0.22f;
    public static final float BALL_DENSITY = 0.5f;
    public static final float BALL_RESTITUTION = 0.65f;
    public static final float BALL_LINEAR_DAMPING = 0.6f;
    public static final float BALL_FRICTION = 0.2f;

    public static final float KICK_RANGE = 1.05f;
    public static final float PASS_IMPULSE_MIN = 1.1f;
    public static final float SHOT_IMPULSE_MAX = 3.8f;
    public static final float KICK_CHARGE_TIME = 0.75f;
    public static final float AIM_ASSIST = 1.2f;

    public static final float SLIDE_SPEED = 8.0f;
    public static final float SLIDE_DURATION = 0.30f;
    public static final float SLIDE_COOLDOWN = 1.8f;
    public static final float SLIDE_RECOVERY_TIME = 0.35f;
    public static final float SLIDE_REACH = 0.8f;
    public static final float SLIDE_RECOVERY_BRAKE = 0.35f;
    public static final float SLIDE_KICK_POWER = 0.22f;

    public static final float GK_SPEED = 4.2f;
    public static final float GK_LINE_OFFSET = 0.6f;
    public static final float GK_REACTION_TIME = 0.30f;
    public static final float GK_DEAD_ZONE = 0.15f;

    public static final float GK_DIVE_SPEED = 9.0f;
    public static final float GK_DIVE_DURATION = 0.45f;
    public static final float GK_DIVE_COOLDOWN = 1.8f;
    public static final float GK_DIVE_REACTION = 0.10f;
    public static final float GK_DIVE_MIN_BALL_SPEED = 6f;
    public static final float GK_DIVE_TRIGGER_DIST = 16f;
    public static final float GK_DIVE_MIN_MISS = 0.7f;

    public static final float PLAYER_BALL_RESTITUTION = 0.12f;

    public static final float BALL_STUCK_SECONDS = 5f;
    public static final float BALL_STUCK_RADIUS = 2.0f;

    public static final float BALL_UNSTUCK_INSET = 3.5f;

    public static final float REPLAY_SECONDS = 3.5f;
    public static final float REPLAY_SPEED = 0.6f;

    public static final float SHOT_MIN_POWER = 0.45f;

    public static final float KICKOFF_LEFT_X = -9f;
    public static final float KICKOFF_RIGHT_X = 9f;
    public static final Color TEAM_LEFT_COLOR = new Color(0.20f, 0.45f, 0.95f, 1f);
    public static final Color TEAM_RIGHT_COLOR = new Color(0.90f, 0.25f, 0.25f, 1f);

    public static final String PLAYER_LEFT_NAME = "ALEJO";
    public static final String KEEPER_LEFT_NAME = "MARTIN";
    public static final String PLAYER_RIGHT_NAME = "TOBIAS";
    public static final String KEEPER_RIGHT_NAME = "JERIEL";

    public static final float SPLIT_VIEW_HEIGHT = 20f;
    public static final float SPLIT_VIEW_BALL_BIAS = 0.35f;

    public static final float FULL_VIEW_HEIGHT = 20f;
    public static final float FULL_VIEW_BALL_BIAS = 0.3f;
    public static final float CAM_FOLLOW_RATE = 9f;
    public static final float RIVAL_BLUR_ALPHA = 0.30f;

    public static final float KICKOFF_2V2_FRONT_X = 7f;
    public static final float KICKOFF_2V2_BACK_X = 12f;
    public static final float KICKOFF_2V2_SPREAD_Y = 3.5f;

    public static final float TEAM_VIEW_HEIGHT = 24f;
    public static final float TEAM_VIEW_BALL_BIAS = 0.45f;

    public static final float BALL_VISUAL_DIAMETER = 0.78f;

    public static final float MAX_FRAME_DELTA = 0.1f;

    public static final float MINIMAP_WIDTH_PX = 210f;

    public static final int HUD_HEIGHT_PX = 108;
    public static final String MATCH_TITLE = "MATCH: Rivales F.C.";

    public static final float HALF_DURATION_SECONDS = 180f;
    public static final float OVERTIME_SECONDS = 60f;
    public static final float KICKOFF_FREEZE_SECONDS = 3f;
    public static final float GOAL_CELEBRATION_SECONDS = 2.0f;
    public static final float HALFTIME_BREAK_SECONDS = 4f;

    public static final int TRAIL_MAX_POINTS = 10;
    public static final float TRAIL_MIN_DIST = 0.18f;
    public static final float TRAIL_SPEED_THRESHOLD = 4.0f;

    public static final float SPRITE_HEIGHT_METERS = PLAYER_RADIUS * 3.4f;

    public static final short CAT_BOUNDARY = 0x0001;
    public static final short CAT_PLAYER = 0x0002;
    public static final short CAT_BALL = 0x0004;
    public static final short CAT_GOAL_SENSOR = 0x0008;
}
