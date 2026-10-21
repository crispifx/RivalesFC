package com.rivalesfc.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

public final class Settings {

    private Settings() {
    }

    public enum Difficulty {

        EASY("FACIL",       0.80f,  8.0f,   1.40f, 11f,   0.85f, 1.50f, 0.50f, 1.20f),
        NORMAL("NORMAL",    0.92f,  3.5f,   0.70f, 14f,   1.00f, 1.00f, 0.75f, 0.60f),
        HARD("DIFICIL",     1.00f,  2.5f,   0.25f, 17f,   1.10f, 0.70f, 0.92f, 0.25f);

        public final String label;

        public final float aiMoveScale;

        public final float aiSprintDist;

        public final float aiAimJitter;

        public final float aiShootRange;
        public final float gkSpeedScale;

        public final float gkReactionScale;

        public final float gkDiveChance;

        public final float gkDiveNoise;

        Difficulty(String label, float aiMoveScale, float aiSprintDist, float aiAimJitter, float aiShootRange,
                   float gkSpeedScale, float gkReactionScale, float gkDiveChance, float gkDiveNoise) {
            this.label = label;
            this.aiMoveScale = aiMoveScale;
            this.aiSprintDist = aiSprintDist;
            this.aiAimJitter = aiAimJitter;
            this.aiShootRange = aiShootRange;
            this.gkSpeedScale = gkSpeedScale;
            this.gkReactionScale = gkReactionScale;
            this.gkDiveChance = gkDiveChance;
            this.gkDiveNoise = gkDiveNoise;
        }
    }

    public static final int[] HALF_MINUTES_OPTIONS = {1, 2, 3, 5};

    public static Difficulty difficulty = Difficulty.NORMAL;
    public static int halfMinutes = 3;

    public static float volume = 0.8f;
    public static boolean muted = false;

    public static String lastHostIp = "";

    public static String playerName = "JUGADOR";
    public static int teamIndex = 0;
    public static int rivalTeamIndex = 1;

    private static final String PREFS = "rivalesfc_settings";

    public static float halfDurationSeconds() {
        return halfMinutes * 60f;
    }

    public static float sfx(float base) {
        return muted ? 0f : base * volume;
    }

    public static void load() {
        try {
            Preferences p = Gdx.app.getPreferences(PREFS);
            int d = p.getInteger("difficulty", Difficulty.NORMAL.ordinal());
            difficulty = Difficulty.values()[Math.max(0, Math.min(Difficulty.values().length - 1, d))];
            halfMinutes = p.getInteger("halfMinutes", 3);
            boolean valid = false;
            for (int m : HALF_MINUTES_OPTIONS) {
                if (m == halfMinutes) valid = true;
            }
            if (!valid) halfMinutes = 3;
            volume = Math.max(0f, Math.min(1f, p.getFloat("volume", 0.8f)));
            muted = p.getBoolean("muted", false);
            lastHostIp = p.getString("lastHostIp", "");
            playerName = com.rivalesfc.game.net.Protocol.cleanName(p.getString("playerName", "JUGADOR"));
            if (playerName.isEmpty()) playerName = "JUGADOR";
            teamIndex = Math.floorMod(p.getInteger("teamIndex", 0), Team.ALL.length);
            rivalTeamIndex = Math.floorMod(p.getInteger("rivalTeamIndex", 1), Team.ALL.length);
        } catch (Exception e) {

        }
    }

    public static void save() {
        try {
            Preferences p = Gdx.app.getPreferences(PREFS);
            p.putInteger("difficulty", difficulty.ordinal());
            p.putInteger("halfMinutes", halfMinutes);
            p.putFloat("volume", volume);
            p.putBoolean("muted", muted);
            p.putString("lastHostIp", lastHostIp);
            p.putString("playerName", playerName);
            p.putInteger("teamIndex", teamIndex);
            p.putInteger("rivalTeamIndex", rivalTeamIndex);
            p.flush();
        } catch (Exception e) {

        }
    }
}
