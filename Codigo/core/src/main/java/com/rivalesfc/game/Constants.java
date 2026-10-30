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
    /** MK7 (fix): las esquinas son ochavadas (corte a 45°). Con esquinas en ángulo recto la pelota quedaba trabada y no había forma de sacarla (el pateo siempre empuja del jugador hacia la pelota). */
    public static final float FIELD_CORNER_CUT = 1.2f;
    public static final float PENALTY_BOX_DEPTH = 5f;
    public static final float PENALTY_BOX_HEIGHT = 16f;
    public static final float GOAL_BOX_DEPTH = 2f;
    public static final float GOAL_BOX_HEIGHT = 8f;

    // --- Jugador de campo ---
    public static final float PLAYER_RADIUS = 0.5f;
    public static final float PLAYER_MAX_SPEED = 7.5f;   // m/s caminando
    public static final float PLAYER_SPRINT_SPEED = 10.5f; // m/s con sprint
    public static final float PLAYER_ACCEL_FORCE = 45f;   // fuerza aplicada para acelerar
    /** MK7 (fix): con la fuerza de 45 N y el damping de 6 la velocidad terminal era ~8 m/s, así que el sprint (10,5) casi no servía. Al correr la fuerza se multiplica por esto. */
    public static final float PLAYER_SPRINT_ACCEL_MULT = 1.5f;
    public static final float PLAYER_LINEAR_DAMPING = 6f; // frena al soltar teclas

    // --- Pelota ---
    public static final float BALL_RADIUS = 0.22f;
    public static final float BALL_DENSITY = 0.5f;
    public static final float BALL_RESTITUTION = 0.65f;
    public static final float BALL_LINEAR_DAMPING = 0.6f;
    public static final float BALL_FRICTION = 0.2f;

    // --- Pase / remate (potencia variable, secc. 1.2) ---
    public static final float KICK_RANGE = 1.05f;         // MK5: antes 0,9; pateo más permisivo (distancia máxima jugador-pelota)
    public static final float PASS_IMPULSE_MIN = 1.1f;   // MK4: antes 3 (~39 m/s, ya pegado al tope de Box2D)
    public static final float SHOT_IMPULSE_MAX = 3.8f;   // MK5 BUFF: antes 2,9 (~38 m/s); ahora ~50 m/s, por debajo del tope de Box2D (~60 m/s)
    public static final float KICK_CHARGE_TIME = 0.75f;    // MK5 BUFF: antes 1,0; se llega a la potencia máxima más rápido
    public static final float AIM_ASSIST = 1.2f;           // MK5: cuánto pesa la dirección en que te movés al patear (0 = solo hacia donde está la pelota)

    // --- Planchazo / barrida (nuevo) ---
    public static final float SLIDE_SPEED = 8.0f;         // MK5 NERF: antes 9,5; durante el planchazo (aún más rápido que caminar, pero ya no más que el sprint)
    public static final float SLIDE_DURATION = 0.30f;      // MK5 NERF: antes 0,35; el alcance total baja de ~3,3 m a ~2,4 m
    public static final float SLIDE_COOLDOWN = 1.8f;       // MK5 NERF: antes 1,0; no se puede spamear
    public static final float SLIDE_RECOVERY_TIME = 0.35f; // MK5 NERF: segundos que el jugador queda en el piso sin poder moverse ni patear tras el planchazo
    public static final float SLIDE_REACH = 0.8f;          // MK5 NERF: alcance del planchazo a la pelota (antes usaba KICK_RANGE = 0,9)
    public static final float SLIDE_RECOVERY_BRAKE = 0.35f; // frenado aplicado a la velocidad al terminar el planchazo
    public static final float SLIDE_KICK_POWER = 0.22f;     // MK5 NERF: antes 0,35; el toque que le da a la pelota es más flojo

    // --- Arquero (100% IA, secc. 1.1 "arquero automático") ---
    // Único estado de IA: seguir la coordenada Y de la pelota, sin salir de la boca del arco.
    public static final float GK_SPEED = 4.2f;        // m/s, movimiento exclusivamente vertical (antes 6.5: infranqueable)
    public static final float GK_LINE_OFFSET = 0.6f;  // distancia hacia adentro de la cancha desde el fondo
    public static final float GK_REACTION_TIME = 0.30f; // segundos de "reflejo" simulado antes de reaccionar al movimiento de la pelota
    public static final float GK_DEAD_ZONE = 0.15f;     // margen sin corregir, evita micro-ajustes robóticos


    // --- MK4: estirada del arquero ---
    public static final float GK_DIVE_SPEED = 9.0f;          // m/s durante la estirada
    public static final float GK_DIVE_DURATION = 0.45f;      // segundos máximos de la estirada
    public static final float GK_DIVE_COOLDOWN = 1.8f;       // recarga tras estirarse
    public static final float GK_DIVE_REACTION = 0.10f;      // reflejo antes de tirarse (se escala por dificultad)
    public static final float GK_DIVE_MIN_BALL_SPEED = 6f;   // velocidad mínima hacia el arco para considerar un remate
    public static final float GK_DIVE_TRIGGER_DIST = 16f;    // distancia en X a la que empieza a evaluar
    public static final float GK_DIVE_MIN_MISS = 0.7f;       // si la pelota ya pasa cerca, no hace falta tirarse

    /** MK7: rebote del contacto jugador-pelota (la pelota contra muros y arqueros sigue rebotando como antes). */
    public static final float PLAYER_BALL_RESTITUTION = 0.12f;

    // --- MK7: pelota trabada ---
    /** Si en este tiempo la pelota no se aleja más de {@link #BALL_STUCK_RADIUS} de donde estaba, se reubica (red de seguridad: dos jugadores pegando la pelota a un muro). */
    public static final float BALL_STUCK_SECONDS = 5f;
    public static final float BALL_STUCK_RADIUS = 2.0f;
    /** Al reubicarla, la pelota queda al menos a esta distancia de cada muro. */
    public static final float BALL_UNSTUCK_INSET = 3.5f;

    // --- MK4: repetición de gol ---
    public static final float REPLAY_SECONDS = 3.5f;         // cuánto del juego previo al gol se repite
    public static final float REPLAY_SPEED = 0.6f;           // 1 = tiempo real, <1 = cámara lenta

    // --- MK4: goles / estadísticas ---
    public static final float SHOT_MIN_POWER = 0.45f;        // potencia mínima para contar como remate

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
    public static final float SPLIT_VIEW_HEIGHT = 20f;   // MK7: antes 16; cada panel mostraba solo ~15 m de ancho de una cancha de 44 m y la pelota se perdía de vista
    public static final float SPLIT_VIEW_BALL_BIAS = 0.35f; // MK7: la cámara de cada panel también se inclina hacia la pelota
    // --- MK5: pantalla completa (1 jugador y online) ---
    public static final float FULL_VIEW_HEIGHT = 20f;    // metros visibles en vertical; con un solo panel conviene ver más cancha
    public static final float FULL_VIEW_BALL_BIAS = 0.3f; // 0 = cámara solo sobre el jugador, 1 = solo sobre la pelota
    public static final float CAM_FOLLOW_RATE = 9f;      // MK7: suavizado de la cámara (1/s); más alto = más pegada al foco
    public static final float RIVAL_BLUR_ALPHA = 0.30f;  // opacidad base del jugador rival difuminado

    // --- MK7: 2 vs 2 local ---
    public static final float KICKOFF_2V2_FRONT_X = 7f;
    public static final float KICKOFF_2V2_BACK_X = 12f;
    public static final float KICKOFF_2V2_SPREAD_Y = 3.5f;
    /** Alto de la vista (m) en 2v2: casi toda la cancha a la vez, porque hay cuatro jugadores. */
    public static final float TEAM_VIEW_HEIGHT = 24f;
    public static final float TEAM_VIEW_BALL_BIAS = 0.45f;

    // --- MK7: visibilidad ---
    /** Diámetro con que se DIBUJA la pelota (m). La física sigue con {@link #BALL_RADIUS}: la real (0,44 m) medía ~16 px en pantalla y no se seguía bien. */
    public static final float BALL_VISUAL_DIAMETER = 0.78f;
    /** Tope de un frame (s) para la simulación: si la ventana se traba (arrastrarla, minimizar) no se corren decenas de ticks de golpe. */
    public static final float MAX_FRAME_DELTA = 0.1f;
    /** Ancho (px, a 800 px de alto de ventana) del minimapa. */
    public static final float MINIMAP_WIDTH_PX = 210f;

    // --- Barra superior compartida (reloj + marcador + título del partido) ---
    public static final int HUD_HEIGHT_PX = 108;
    public static final String MATCH_TITLE = "MATCH: Rivales F.C.";

    // --- Tiempos de partido ---
    public static final float HALF_DURATION_SECONDS = 180f;   // 3:00 por tiempo
    public static final float OVERTIME_SECONDS = 60f;        // MK6: tiempo extra con gol de oro si hay empate
    public static final float KICKOFF_FREEZE_SECONDS = 3f;    // cuenta regresiva antes de jugar
    public static final float GOAL_CELEBRATION_SECONDS = 2.0f;
    public static final float HALFTIME_BREAK_SECONDS = 4f;

    // --- Rastro de movimiento a alta velocidad (estética de la referencia) ---
    public static final int TRAIL_MAX_POINTS = 10;
    public static final float TRAIL_MIN_DIST = 0.18f;        // metros entre puntos del rastro
    public static final float TRAIL_SPEED_THRESHOLD = 4.0f;  // m/s a partir del cual aparece el rastro

    // --- Arte pixel procedural (generado en runtime, sin assets externos) ---
    public static final float SPRITE_HEIGHT_METERS = PLAYER_RADIUS * 3.4f;


    // --- MK8: sistema de faltas ---
    /** Distancia entre centros por debajo de la cual dos jugadores se consideran en contacto (2 radios + margen). */
    public static final float FOUL_CONTACT_DIST = PLAYER_RADIUS * 2f + 0.15f;
    /** Velocidad (m/s) con la que hay que chocar a un rival, sin jugar la pelota, para que sea falta de carga. */
    public static final float CHARGE_FOUL_SPEED = 8.5f;
    /** La carga solo es falta si el que choca viene al menos esto más rápido que el chocado (choque de frente = nadie). */
    public static final float CHARGE_FOUL_MARGIN = 2.0f;
    /** Velocidad de cierre (m/s) a partir de la cual un contacto suma gravedad por \"entrada fuerte\". */
    public static final float FOUL_HARD_CLOSING_SPEED = 9.5f;
    /** Para \"por detrás\": el chocado se aleja del que entra al menos a esta velocidad (m/s). */
    public static final float FOUL_BEHIND_VICTIM_SPEED = 2.0f;
    /** Tiempo mínimo entre dos faltas del mismo jugador (s): un solo choque no se cobra dos veces. */
    public static final float FOUL_COOLDOWN_SECONDS = 1.5f;
    /** Gravedad mínima para amarilla directa / roja directa (ver FoulRules). */
    public static final int FOUL_YELLOW_SEVERITY = 3;
    public static final int FOUL_RED_SEVERITY = 4;
    /** Cada N faltas del mismo jugador, amarilla por reincidencia. */
    public static final int FOUL_REPEAT_YELLOW_EVERY = 3;
    /** Con roja el jugador sale este tiempo (s de juego) y vuelve por su campo. */
    public static final float RED_SUSPENSION_SECONDS = 25f;
    /** Pausa de la falta (silbato + cartel + tarjeta) antes de acomodar el tiro. */
    public static final float FOUL_PAUSE_SECONDS = 1.6f;
    public static final float FOUL_PAUSE_CARD_SECONDS = 2.4f;
    /** Cuenta regresiva del tiro libre / penal con los jugadores ya acomodados. */
    public static final float SET_PIECE_COUNTDOWN_SECONDS = 2.5f;
    /** Distancia de la barrera al balón en un tiro libre (m). */
    public static final float FREE_KICK_WALL_DIST = 5.5f;
    /** Tras el silbato, los rivales del que patea quedan quietos este tiempo (o hasta que la pelota se mueva). */
    public static final float SET_PIECE_PROTECT_SECONDS = 2.0f;
    public static final float PENALTY_PROTECT_SECONDS = 3.0f;
    /** Distancia del punto de penal a la línea de gol; coincide con el punto que dibuja ArtPainter. */
    public static final float PENALTY_SPOT_DIST = PENALTY_BOX_DEPTH - 0.9f;
    /** Si falta menos que esto al cobrar la falta, se da este tiempo para ejecutar el tiro. */
    public static final float SET_PIECE_MIN_TIME = 4f;
    /** La pelota de un tiro libre no se pone más cerca que esto de los bordes (m). */
    public static final float SET_PIECE_EDGE_MARGIN_X = 1.6f;
    public static final float SET_PIECE_EDGE_MARGIN_Y = 1.4f;

    // --- MK8: ley de la ventaja ---
    /** Cuánto dura como máximo la ventaja antes de decidir si se cobra la falta (s). */
    public static final float ADVANTAGE_SECONDS = 2.5f;
    /** Ticks seguidos (30/s) con la pelota controlada por el equipo perjudicado para dar la ventaja por buena antes de tiempo (~1,3 s). */
    public static final int ADVANTAGE_EARLY_TICKS = 40;
    /** Ticks de control mínimos para que, al vencer el tiempo, la ventaja cuente como aprovechada (~0,6 s). */
    public static final int ADVANTAGE_MIN_CONTROL_TICKS = 18;
    /** Ticks seguidos con la pelota en poder del infractor para volver atrás y cobrar la falta (~0,2 s). */
    public static final int ADVANTAGE_LOST_TICKS = 6;
    /** Con menos tiempo restante que esto (s) no se da ventaja: se cobra la falta directamente. */
    public static final float ADVANTAGE_MIN_TIME_LEFT = ADVANTAGE_SECONDS + 0.5f;
    /** Marca de expulsión definitiva (en lugar de un tiempo). */
    public static final float RED_PERMANENT = 1.0e9f;

    /** MK8: la IA deja de esprintar a esta distancia de un rival (a >= CHARGE_FOUL_SPEED un choque es falta de carga). */
    public static final float AI_FOUL_CAUTION_DIST = 4.5f;
    /** MK8: alcance máximo (m, desde el centro del jugador) al que la IA se anima a barrer a la pelota. */
    public static final float AI_SLIDE_MAX_BALL_DIST = 2.5f;
    /** MK8: probabilidad POR TICK (30/s) con que la IA barre cuando el rival se interpone y es probable que sea falta (~15 % por encuentro). */
    public static final float AI_RISKY_SLIDE_CHANCE = 0.010f;
    /** MK8: a qué distancia pasada la pelota (del lado opuesto al rival que la lleva) se acomoda la IA para tackear limpio. */
    public static final float AI_FLANK_DIST = 0.9f;

    // --- Categorías de colisión Box2D ---
    public static final short CAT_BOUNDARY = 0x0001;
    public static final short CAT_PLAYER = 0x0002;
    public static final short CAT_BALL = 0x0004;
    public static final short CAT_GOAL_SENSOR = 0x0008;
}
