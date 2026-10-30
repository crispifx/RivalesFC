package com.rivalesfc.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

/**
 * Opciones del jugador (MK4). Se eligen en el lobby y se guardan con
 * {@link Preferences} de libGDX (un archivo chico en la carpeta del usuario),
 * así que sobreviven entre sesiones. Es estado global a propósito: la
 * simulación, la IA y la pantalla las leen sin tener que pasarlas por parámetro.
 */
public final class Settings {

    private Settings() {
    }

    /** Dificultad: afecta a la IA de soporte (modo 1 jugador) y a los dos arqueros. */
    public enum Difficulty {
        //        etiqueta  mov.   sprint  jitter tiro   arquero: vel.  reflejo dive%  error
        EASY("FACIL",       0.80f,  8.0f,   1.40f, 11f,   0.85f, 1.50f, 0.50f, 1.20f),
        NORMAL("NORMAL",    0.92f,  3.5f,   0.70f, 14f,   1.00f, 1.00f, 0.75f, 0.60f),
        HARD("DIFICIL",     1.00f,  2.5f,   0.25f, 17f,   1.10f, 0.70f, 0.92f, 0.25f);

        public final String label;
        /** Escala del eje de movimiento de la IA (1 = a fondo). */
        public final float aiMoveScale;
        /** Distancia a la pelota a partir de la cual la IA usa sprint. */
        public final float aiSprintDist;
        /** Error de puntería de la IA, en metros sobre la línea de gol. */
        public final float aiAimJitter;
        /** Distancia al arco rival desde la que la IA se anima a rematar. */
        public final float aiShootRange;
        public final float gkSpeedScale;
        /** Multiplicador del tiempo de reacción del arquero (menor = más rápido). */
        public final float gkReactionScale;
        /** Probabilidad de que el arquero decida tirarse en un remate al arco. */
        public final float gkDiveChance;
        /** Error (metros) con el que el arquero estima adónde va la pelota. */
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
    /** Volumen maestro 0..1 (pasos de 0.1). */
    public static float volume = 0.8f;
    public static boolean muted = false;
    /** MK8: faltas y tarjetas activadas (se elige en Ajustes). */
    public static boolean foulsEnabled = true;
    /** MK8: ley de la ventaja (si el equipo perjudicado conserva la pelota, se sigue jugando). Solo cuenta con faltas activadas. */
    public static boolean advantageEnabled = true;
    /** MK8: en el 2v2 la roja expulsa hasta el final del partido (en vez de 25 s). Nunca deja a un equipo sin jugadores de campo. */
    public static boolean red2v2Permanent = false;
    /** Última IP de host escrita en el lobby para unirse online. */
    public static String lastHostIp = "";
    /** MK6: nombre del jugador (se muestra sobre el personaje y en los carteles) y equipos elegidos (índices de {@link Team#ALL}). */
    public static String playerName = "JUGADOR";
    /** MK8: último modo elegido en la pantalla de preparación (índice de SetupScreen.MODES). */
    public static int modeIndex = 0;
    public static int teamIndex = 0;
    public static int rivalTeamIndex = 1;

    private static final String PREFS = "rivalesfc_settings";

    public static float halfDurationSeconds() {
        return halfMinutes * 60f;
    }

    /** Volumen efectivo para reproducir un sonido con {@code base} de volumen propio. */
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
            foulsEnabled = p.getBoolean("foulsEnabled", true);
            advantageEnabled = p.getBoolean("advantageEnabled", true);
            red2v2Permanent = p.getBoolean("red2v2Permanent", false);
            lastHostIp = p.getString("lastHostIp", "");
            playerName = com.rivalesfc.game.net.Protocol.cleanName(p.getString("playerName", "JUGADOR"));
            if (playerName.isEmpty()) playerName = "JUGADOR";
            modeIndex = Math.max(0, Math.min(4, p.getInteger("modeIndex", 0)));
            teamIndex = Math.floorMod(p.getInteger("teamIndex", 0), Team.ALL.length);
            rivalTeamIndex = Math.floorMod(p.getInteger("rivalTeamIndex", 1), Team.ALL.length);
        } catch (Exception e) {
            // Sin preferencias legibles: se queda con los valores por defecto.
        }
    }

    public static void save() {
        try {
            Preferences p = Gdx.app.getPreferences(PREFS);
            p.putInteger("difficulty", difficulty.ordinal());
            p.putInteger("halfMinutes", halfMinutes);
            p.putFloat("volume", volume);
            p.putBoolean("muted", muted);
            p.putBoolean("foulsEnabled", foulsEnabled);
            p.putBoolean("advantageEnabled", advantageEnabled);
            p.putBoolean("red2v2Permanent", red2v2Permanent);
            p.putString("lastHostIp", lastHostIp);
            p.putString("playerName", playerName);
            p.putInteger("modeIndex", modeIndex);
            p.putInteger("teamIndex", teamIndex);
            p.putInteger("rivalTeamIndex", rivalTeamIndex);
            p.flush();
        } catch (Exception e) {
            // No es crítico si no se puede guardar.
        }
    }
}
