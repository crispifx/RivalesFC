package com.rivalesfc.game;

import com.badlogic.gdx.graphics.Color;

/**
 * Constantes globales del proyecto "Rivales F.C.".
 *
 * Modo actual (local, sin red): pantalla dividida 2v2 fijo — un jugador
 * humano por equipo (controlado localmente, cada uno en su mitad de
 * pantalla) más un arquero 100% IA por equipo. Estas constantes ya se
 * escriben pensando en la etapa de red (secc. 2.3 de la propuesta): tick de
 * simulación fijo a 30 Hz, unidades en metros (convención de Box2D) con un
 * factor PPM (pixels por metro) para el renderizado.
 *
 * Esta versión ("Etapa 1 mejorada") suma presentación estilo arcade
 * pixel-art (inspirada en una referencia visual): reloj y marcador
 * compartidos, nombres sobre los jugadores, rastro de movimiento a alta
 * velocidad, tiempos de partido con entretiempo y cuenta regresiva de
 * saque, sin tocar el núcleo de física/red que ya estaba pensado para las
 * etapas siguientes.
 */
public final class Constants {

    private Constants() {
    }

    // --- Simulación / red (secc. 2.3) ---
    /** Tick rate de la simulación física, igual al que tendrá el host en la etapa de red. */
    public static final float SIM_HZ = 30f;
    public static final float SIM_STEP = 1f / SIM_HZ;

    // --- Render ---
    public static final float PPM = 32f; // pixels por metro

    // --- Cancha (en metros) ---
    public static final float FIELD_WIDTH = 44f;   // ancho reglamentario reducido
    public static final float FIELD_HEIGHT = 28f;  // alto reglamentario reducido
    public static final float FIELD_MARGIN = 4f;   // margen visual alrededor de la cancha
    public static final float GOAL_WIDTH = 6f;     // ancho del arco (eje Y)
    public static final float GOAL_DEPTH = 1.5f;   // profundidad del arco (eje X)

    // --- Áreas (solo dibujo, decorativas) ---
    public static final float PENALTY_BOX_DEPTH = 5f;
    public static final float PENALTY_BOX_HEIGHT = 16f;
    public static final float GOAL_BOX_DEPTH = 2f;
    public static final float GOAL_BOX_HEIGHT = 8f;

    // --- Jugador de campo ---
    public static final float PLAYER_RADIUS = 0.5f;
    public static final float PLAYER_MAX_SPEED = 7.5f;   // m/s caminando
    public static final float PLAYER_SPRINT_SPEED = 10.5f; // m/s con sprint
    public static final float PLAYER_ACCEL_FORCE = 45f;   // fuerza aplicada para acelerar
    public static final float PLAYER_LINEAR_DAMPING = 6f; // frena al soltar teclas

    // --- Pelota ---
    public static final float BALL_RADIUS = 0.22f;
    public static final float BALL_DENSITY = 0.5f;
    public static final float BALL_RESTITUTION = 0.65f;
    public static final float BALL_LINEAR_DAMPING = 0.6f;
    public static final float BALL_FRICTION = 0.2f;

    // --- Pase / remate (potencia variable, secc. 1.2) ---
    public static final float KICK_RANGE = 0.9f;          // distancia máxima jugador-pelota para patear
    public static final float PASS_IMPULSE_MIN = 3f;
    public static final float SHOT_IMPULSE_MAX = 16f;
    public static final float KICK_CHARGE_TIME = 1.0f;     // segundos para cargar potencia máxima

    // --- Planchazo / barrida (nuevo) ---
    public static final float SLIDE_SPEED = 9.5f;         // m/s durante el planchazo (más rápido que el sprint)
    public static final float SLIDE_DURATION = 0.35f;      // segundos que dura el impulso del planchazo
    public static final float SLIDE_COOLDOWN = 1.0f;       // segundos de recarga antes de poder repetirlo
    public static final float SLIDE_RECOVERY_BRAKE = 0.35f; // frenado aplicado a la velocidad al terminar el planchazo
    public static final float SLIDE_KICK_POWER = 0.5f;     // potencia (0..1) del toque que le da a la pelota si la alcanza

    // --- Arquero (100% IA, secc. 1.1 "arquero automático") ---
    // Único estado de IA: seguir la coordenada Y de la pelota, sin salir de la boca del arco.
    public static final float GK_SPEED = 4.2f;        // m/s, movimiento exclusivamente vertical (antes 6.5: infranqueable)
    public static final float GK_LINE_OFFSET = 0.6f;  // distancia hacia adentro de la cancha desde el fondo
    public static final float GK_REACTION_TIME = 0.30f; // segundos de "reflejo" simulado antes de reaccionar al movimiento de la pelota
    public static final float GK_DEAD_ZONE = 0.15f;     // margen sin corregir, evita micro-ajustes robóticos

    // --- Equipos y saque (2v2 fijo: 1 humano + 1 arquero IA por equipo) ---
    public static final float KICKOFF_LEFT_X = -9f;
    public static final float KICKOFF_RIGHT_X = 9f;
    public static final Color TEAM_LEFT_COLOR = new Color(0.20f, 0.45f, 0.95f, 1f);  // azul
    public static final Color TEAM_RIGHT_COLOR = new Color(0.90f, 0.25f, 0.25f, 1f); // rojo

    // --- Nombres mostrados en pantalla (estética "cartel sobre el jugador" de la referencia) ---
    public static final String PLAYER_LEFT_NAME = "ALEJO";
    public static final String KEEPER_LEFT_NAME = "MARTIN";
    public static final String PLAYER_RIGHT_NAME = "TOBIAS";
    public static final String KEEPER_RIGHT_NAME = "JERIEL";

    // --- Pantalla dividida ---
    public static final float SPLIT_VIEW_HEIGHT = 16f;   // metros visibles verticalmente por cámara
    public static final float RIVAL_BLUR_ALPHA = 0.30f;  // opacidad base del jugador rival difuminado

    // --- Barra superior compartida (reloj + marcador + título del partido) ---
    public static final int HUD_HEIGHT_PX = 108;
    public static final String MATCH_TITLE = "MATCH: Rivales F.C. (Local 2v2)";

    // --- Tiempos de partido ---
    public static final float HALF_DURATION_SECONDS = 180f;   // 3:00 por tiempo
    public static final float KICKOFF_FREEZE_SECONDS = 3f;    // cuenta regresiva antes de jugar
    public static final float GOAL_CELEBRATION_SECONDS = 2.5f;
    public static final float HALFTIME_BREAK_SECONDS = 4f;

    // --- Rastro de movimiento a alta velocidad (estética de la referencia) ---
    public static final int TRAIL_MAX_POINTS = 10;
    public static final float TRAIL_MIN_DIST = 0.18f;        // metros entre puntos del rastro
    public static final float TRAIL_SPEED_THRESHOLD = 4.0f;  // m/s a partir del cual aparece el rastro

    // --- Arte pixel procedural (generado en runtime, sin assets externos) ---
    public static final float SPRITE_HEIGHT_METERS = PLAYER_RADIUS * 3.4f;

    // --- Categorías de colisión Box2D ---
    public static final short CAT_BOUNDARY = 0x0001;
    public static final short CAT_PLAYER = 0x0002;
    public static final short CAT_BALL = 0x0004;
    public static final short CAT_GOAL_SENSOR = 0x0008;
}
