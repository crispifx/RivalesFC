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
import com.rivalesfc.game.sim.FoulRules.Card;

/**
 * Simulación del partido a tick fijo (30 Hz, secc. 2.3 de la propuesta).
 *
 * Modo fijo: **2 vs 2**. Cada equipo tiene exactamente un jugador de campo
 * controlado por un humano local (pantalla dividida, sin red todavía) y un
 * arquero 100% IA. No hay otras configuraciones (1 vs 1, 4 jugadores, etc.)
 * en este modo — eso corresponde a la Etapa 3 de la propuesta cuando se
 * sume la red y el lobby.
 *
 * Sobre la versión original de la Etapa 1, esta clase suma una máquina de
 * estados de **fases de partido** (saque inicial con cuenta regresiva,
 * celebración de gol, entretiempo, tiempo cumplido) para que el juego se
 * sienta como un partido real en vez de resetear la pelota instantáneamente.
 * La física en sí (Box2D, tick fijo) no cambia: sigue siendo el candidato
 * natural a alimentarse con `INPUT_STATE` remoto en la Etapa 2.
 */
public class MatchSimulation implements ContactListener {

    /** Fases de un partido, en el orden en que se van a atravesar durante la partida. */
    public enum Phase {
        /** Cuenta regresiva antes de que la pelota quede habilitada (saque inicial o tras un gol). */
        KICKOFF,
        /** Juego habilitado: física normal, inputs habilitados. */
        PLAYING,
        /** Pelota y jugadores congelados mientras se muestra el cartel de gol. */
        GOAL_CELEBRATION,
        /** MK4: repetición en cámara lenta de la jugada del gol (se puede saltear). */
        REPLAY,
        /** Descanso entre el primer y el segundo tiempo. */
        HALFTIME,
        /** Partido terminado (se cumplieron los dos tiempos). */
        FULL_TIME,
        /**
         * MK8: pausa tras una falta (silbato, cartel y tarjeta). Todo congelado donde ocurrió. Va al final del enum a
         * propósito: el ordinal viaja por red y los anteriores no deben moverse.
         */
        FOUL,
        /** MK8: tiro libre o penal con los jugadores ya acomodados y cuenta regresiva. */
        SET_PIECE
    }

    private final World world;
    private final Field field;
    private final Ball ball;

    private final PlayerEntity playerLeft;
    private final PlayerEntity playerRight;
    /** MK7: segundos jugadores de campo del 2v2 local. Nulos en los demás modos. mateLeft = humano (J2), mateRight = IA. */
    private final PlayerEntity mateLeft;
    private final PlayerEntity mateRight;
    private final GoalkeeperEntity keeperLeft;
    private final GoalkeeperEntity keeperRight;

    /** MK8: los cuatro posibles jugadores de campo por índice: 0 izq., 1 der., 2 compañero izq., 3 compañero der. (null si no hay 2v2). El equipo de un índice es {@code idx & 1}. */
    private final PlayerEntity[] allPlayers = new PlayerEntity[4];

    private final GameMode mode;
    /** No nulo solo en {@link GameMode#ONE_PLAYER}: controla al Jugador 2 (equipo ROJO) en vez del teclado. */
    private final SupportAI playerRightAI;
    private final SupportAI mateRightAI;
    private final PlayerInput aiGeneratedInput = new PlayerInput();
    private final PlayerInput aiGeneratedInput2 = new PlayerInput();
    private final boolean teams2v2;
    private final boolean rightIsAi;
    /** Quién presiona la pelota en el equipo IA del 2v2 (con histéresis para que no se alternen cada tick). */
    private boolean chaserIsMate = false;

    private float accumulator = 0f;
    private int tick = 0;

    private int scoreLeft = 0;
    private int scoreRight = 0;
    private String lastGoalMessage = null;

    private Phase phase = Phase.KICKOFF;
    private float phaseTimer = Constants.KICKOFF_FREEZE_SECONDS;
    private int half = 1;
    /** Duración de cada tiempo, tomada de {@link Settings} al crear el partido. */
    private final float halfDuration = Settings.halfDurationSeconds();
    private float halfTimeRemaining = halfDuration;

    // --- MK4: estadísticas, atajadas y repetición ---
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

    // Un gol se detecta dentro de beginContact(), que Box2D dispara EN MEDIO de
    // world.step(). Mover bodies (setTransform) ahí adentro es inseguro y puede
    // corromper el estado del World a mitad de paso. Por eso acá solo se guarda
    // la bandera, y el gol se procesa de verdad recién después de que world.step()
    // termina (ver fixedTick).
    private boolean goalPending = false;
    private boolean pendingLeftTeamScored = false;

    // --- Eventos "de un solo disparo" para que la capa de presentación (sonido,
    // shake de cámara) reaccione sin acoplar la simulación a libGDX audio/gfx. ---
    private Float pendingKickSfxPower = null;
    private boolean pendingSlideTackleSfx = false;

    // --- Online: contadores acumulados de esos eventos (viajan en el Snapshot) y estado del lado cliente ---
    private int kickEventCount = 0;
    private int slideEventCount = 0;
    private int saveEventCount = 0;
    private float lastKickPower = 0f;
    private boolean remoteInit = false;
    private int remoteKickCount, remoteSlideCount, remoteSaveCount;
    private int remotePossessionLeft = 50;
    private float remoteReplayProgress = 0f;

    // --- Posesión aproximada (para el resumen post-partido): ticks en los que
    // cada equipo tuvo la pelota "controlada" (jugador de campo cerca de ella). ---
    private int possessionTicksLeft = 0;
    private int possessionTicksRight = 0;

    // --- MK7: pelota trabada (red de seguridad) ---
    private final Vector2 stuckAnchor = new Vector2();
    private final Vector2 stuckAnchor2 = new Vector2();
    private int stuckSamples = 0;
    private float stuckTimer = 0f;
    private int unstuckEventCount = 0;
    private boolean pendingUnstuck = false;

    // --- MK8: faltas, tarjetas y tiros libres / penales ---
    private final FoulRules.DisciplineBook book = new FoulRules.DisciplineBook();
    private final FoulRules.Call callTmp = new FoulRules.Call();
    private final float[] preX = new float[4], preY = new float[4], preVX = new float[4], preVY = new float[4];
    private final boolean[] preSliding = new boolean[4], preTouched = new boolean[4], preNearBall = new boolean[4];
    private final int[] foulCooldown = new int[4];
    /** Segundos de juego que le faltan a cada expulsado para volver (0 = en cancha). Online lo copia del snapshot. */
    private final float[] suspendRemaining = new float[4];
    private int foulEventCount = 0;
    private boolean pendingFoulSfx = false;
    private int lastFoulerIdx = -1;
    private Card lastFoulCard = Card.NONE;
    private FoulRules.Type lastFoulType = FoulRules.Type.NONE;
    private boolean lastFoulPenalty = false;
    private int lastFoulSeverity = 0;
    private boolean lastFoulVictimHadBall = false;
    private final Vector2 lastFoulSpot = new Vector2();
    private int setPieceTeam = -1;
    private int setPieceTaker = -1;
    private boolean setPiecePenalty = false;
    private SetPieceLayout pendingLayout = null;
    // MK8: ley de la ventaja
    private final AdvantageRules.Tracker advTracker = new AdvantageRules.Tracker();
    private boolean advActive = false;
    private int advTeam = -1;          // equipo perjudicado (el que se beneficia)
    private int advVictimIdx = -1;
    private int advShotsBase = 0;
    private final Vector2 advSpot = new Vector2();
    private int advantageEventCount = 0;
    private int advantagesPlayed = 0;
    private int advantagesCalledBack = 0;
    private boolean pendingAdvantageSfx = false;
    private int remoteAdvantageCount = 0;
    private float protectTimer = 0f;
    private final Vector2 protectSpot = new Vector2();
    private final PlayerInput neutralInput = new PlayerInput();
    private int remoteFoulCount = 0;

    // --- MK7: interpolación de render. La simulación corre a 30 Hz y se dibuja a 60 fps; sin esto todo se veía a saltos. ---
    // Orden: 0 pelota, 1 jugador izq., 2 jugador der., 3 arquero izq., 4 arquero der. (x,y por cada uno)
    private final float[] prevPos = new float[14];
    private final Vector2 renderTmp = new Vector2();

    public MatchSimulation(GameMode mode) {
        this.mode = mode;
        this.teams2v2 = mode == GameMode.TWO_VS_TWO;
        this.rightIsAi = mode == GameMode.ONE_PLAYER || teams2v2;
        this.playerRightAI = rightIsAi ? new SupportAI(true) : null;
        this.mateRightAI = teams2v2 ? new SupportAI(true) : null;

        world = new World(new Vector2(0, 0), true); // fútbol visto desde arriba: sin gravedad
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

        allPlayers[0] = playerLeft;
        allPlayers[1] = playerRight;
        allPlayers[2] = mateLeft;
        allPlayers[3] = mateRight;

        float halfFieldWidth = Constants.FIELD_WIDTH / 2f;
        float patrolRange = Constants.GOAL_WIDTH / 2f - Constants.PLAYER_RADIUS;
        keeperLeft = new GoalkeeperEntity(world, -halfFieldWidth + Constants.GK_LINE_OFFSET, patrolRange, new Color(MatchSetup.leftColor()));
        keeperRight = new GoalkeeperEntity(world, halfFieldWidth - Constants.GK_LINE_OFFSET, patrolRange, new Color(MatchSetup.rightColor()));

        for (int i = 0; i < ring.length; i++) {
            ring[i] = new ReplayFrame();
        }
    }

    /**
     * Avanza la simulación usando un acumulador de tiempo, de modo que la física
     * corra siempre a pasos fijos de {@link Constants#SIM_STEP} independientemente
     * del framerate de renderizado (60 fps en el cliente, secc. 2.3).
     */
    public void step(float rawDelta, PlayerInput inputLeft, PlayerInput inputRight) {
        if (mode == GameMode.ONLINE_CLIENT) {
            return; // el cliente no simula: su estado llega por applySnapshot()
        }
        // MK7 (fix): un frame colgado (arrastrar la ventana) no debe simular decenas de ticks de golpe.
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

        if (phase == Phase.REPLAY) {
            stepReplay();
            inputLeft.clearTransient();
            inputRight.clearTransient();
            return;
        }

        boolean playEnabled = phase == Phase.PLAYING;

        if (playEnabled) {
            tickDiscipline(Constants.SIM_STEP);
            act(0, inputLeft);

            if (teams2v2) {
                // MK7: el segundo humano (flechas) juega en el mismo equipo que el primero; su input llega en inputRight.
                act(2, inputRight);
            }

            PlayerInput rightInput;
            if (teams2v2) {
                updateAiTeam2v2();
                rightInput = aiGeneratedInput;
            } else if (mode == GameMode.ONE_PLAYER) {
                playerRightAI.update(aiGeneratedInput, playerRight, ball, playerLeft, keeperLeft, Constants.SIM_STEP);
                rightInput = aiGeneratedInput;
            } else {
                rightInput = inputRight;
            }
            act(1, rightInput);
            if (teams2v2) {
                act(3, aiGeneratedInput2);
            }

            // Planchazo: si un jugador está en pleno planchazo y alcanza la pelota,
            // se la "gana" con un toque en la dirección del planchazo (una vez por planchazo).
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
            // Fuera de juego (cuenta regresiva / gol / entretiempo / final): todo congelado.
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
        }

        // Arqueros 100% IA: siguen la pelota (y se tiran a los remates) solo con juego habilitado
        // para estirarse; durante la cuenta regresiva solo la siguen.
        keeperLeft.update(ball.getPosition(), ball.body.getLinearVelocity(), Constants.SIM_STEP, playEnabled);
        keeperRight.update(ball.getPosition(), ball.body.getLinearVelocity(), Constants.SIM_STEP, playEnabled);

        capturePreStep();
        world.step(Constants.SIM_STEP, 8, 3);
        tick++;

        if (playEnabled) {
            recordFrame();
        }

        // El World ya terminó de resolver este paso: recién ahora es seguro
        // mover bodies (reset de posiciones) si se detectó un gol durante el step.
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

        if (phase == Phase.PLAYING && Settings.foulsEnabled) {
            detectFouls();
        }
        if (advActive) {
            tickAdvantage();
        }

        if (phase == Phase.PLAYING) {
            updateStuckWatchdog(Constants.SIM_STEP);
        } else {
            stuckTimer = 0f;
        }

        advancePhase(Constants.SIM_STEP);
    }

    /**
     * MK7: si la pelota no sale de un radio chico durante {@link Constants#BALL_STUCK_SECONDS}
     * (dos jugadores pegándola a un muro, p. ej.), se reubica hacia adentro de la cancha.
     */
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
        // Cada medio período se toma una muestra. Se compara con la de hace BALL_STUCK_SECONDS:
        // si en todo ese tiempo la pelota casi no se movió, está trabada.
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
                // Trabada en medio de la cancha (dos jugadores encima): se la corre 3 m hacia el centro.
                Vector2 toCenter = new Vector2(-bp.x, -bp.y);
                if (toCenter.len2() < 0.01f) {
                    toCenter.set(1f, 0f);
                }
                target.set(bp).mulAdd(toCenter.nor(), 3f);
            }
            ball.reset(target);
            stuckTimer = 0f; // fuerza reinicio de muestras
            unstuckEventCount++;
            pendingUnstuck = true;
            syncPrev();
        }
    }

    /** true una sola vez si la pelota acaba de reubicarse por estar trabada (para sonido/cartel). */
    public boolean consumeUnstuckEvent() {
        boolean v = pendingUnstuck;
        pendingUnstuck = false;
        return v;
    }

    public int getUnstuckEventCount() {
        return unstuckEventCount;
    }


    // ------------------------------------------------------------------
    // MK8: faltas, tarjetas, expulsiones, tiros libres y penales
    // ------------------------------------------------------------------

    /** Un jugador hace su tick: movimiento y pateo, salvo que esté expulsado o quieto por la protección del tiro. */
    private void act(int idx, PlayerInput in) {
        PlayerEntity p = allPlayers[idx];
        if (p.isSuspended()) {
            in.clearTransient();
            return;
        }
        if (isFrozenBySetPiece(idx)) {
            p.body.setLinearVelocity(0f, 0f);
            in.clearTransient();
            p.applyInput(neutralInput, Constants.SIM_STEP); // sigue corriendo sus temporizadores internos
            return;
        }
        p.applyInput(in, Constants.SIM_STEP);
        handleKick(p, in, idx & 1);
    }

    /**
     * Mientras dura la protección del tiro, los rivales del que patea (y en un penal todos los demás) no se
     * pueden mover. Termina sola, o apenas la pelota se mueve del punto.
     */
    private boolean isFrozenBySetPiece(int idx) {
        if (protectTimer <= 0f || setPieceTaker < 0 || idx == setPieceTaker) {
            return false;
        }
        return setPiecePenalty || (idx & 1) != setPieceTeam;
    }

    /** Cada tick de juego: enfriamientos de falta, regreso de expulsados y fin de la protección del tiro. */
    private void tickDiscipline(float dt) {
        for (int i = 0; i < 4; i++) {
            if (foulCooldown[i] > 0) {
                foulCooldown[i]--;
            }
            if (suspendRemaining[i] > 0f && suspendRemaining[i] < Constants.RED_PERMANENT && allPlayers[i] != null) {
                suspendRemaining[i] -= dt;
                if (suspendRemaining[i] <= 0f) {
                    suspendRemaining[i] = 0f;
                    allPlayers[i].setSuspended(false);
                    placeAtKickoff(allPlayers[i], i); // vuelve por su propio campo
                    syncPrev();
                }
            }
        }
        if (protectTimer > 0f) {
            protectTimer -= dt;
            if (ball.getPosition().dst(protectSpot) > 0.6f) {
                protectTimer = 0f; // la pelota ya se movió: el tiro está ejecutado
            }
        }
    }

    /** Guarda posiciones y velocidades de ANTES del paso de física: después del choque la velocidad de cierre ya se perdió. */
    private void capturePreStep() {
        for (int i = 0; i < 4; i++) {
            PlayerEntity p = allPlayers[i];
            if (p == null) {
                continue;
            }
            preX[i] = p.getPosition().x;
            preY[i] = p.getPosition().y;
            preVX[i] = p.body.getLinearVelocity().x;
            preVY[i] = p.body.getLinearVelocity().y;
            preSliding[i] = p.isSliding();
            preTouched[i] = p.hasTouchedBallThisSlide();
            preNearBall[i] = p.isNear(ball);
        }
    }

    /** Busca un contacto punible entre jugadores de equipos distintos (se llama fuera de world.step()). */
    private void detectFouls() {
        for (int a = 0; a < 4; a++) {
            PlayerEntity pa = allPlayers[a];
            if (pa == null || pa.isSuspended() || foulCooldown[a] > 0) {
                continue;
            }
            for (int v = 0; v < 4; v++) {
                PlayerEntity pv = allPlayers[v];
                if (pv == null || pv.isSuspended() || (v & 1) == (a & 1)) {
                    continue;
                }
                if (pa.getPosition().dst(pv.getPosition()) > Constants.FOUL_CONTACT_DIST) {
                    continue;
                }
                FoulRules.judge(preSliding[a], preTouched[a], preSliding[v], preNearBall[a], preNearBall[v],
                        preX[a], preY[a], preVX[a], preVY[a], preX[v], preY[v], preVX[v], preVY[v], callTmp);
                if (callTmp.isFoul()) {
                    registerFoul(a, v, callTmp);
                    return; // una falta por tick
                }
            }
        }
    }

    private void registerFoul(int attIdx, int victIdx, FoulRules.Call call) {
        int attTeam = attIdx & 1;
        PlayerEntity victim = allPlayers[victIdx];
        float fx = victim.getPosition().x;
        float fy = victim.getPosition().y;

        advActive = false; // una falta nueva reemplaza a la ventaja en curso (la anterior ya quedó anotada)

        Card card = book.record(attIdx, call.severity);
        stats.fouls[attTeam]++;
        if (card == Card.YELLOW) {
            stats.yellows[attTeam]++;
        } else if (card == Card.RED) {
            stats.reds[attTeam]++;
        }
        foulCooldown[attIdx] = Math.round(Constants.FOUL_COOLDOWN_SECONDS * Constants.SIM_HZ);

        boolean penalty = FoulRules.inPenaltyArea(attTeam, fx, fy);
        lastFoulerIdx = attIdx;
        lastFoulCard = card;
        lastFoulType = call.type;
        lastFoulPenalty = penalty;
        lastFoulSeverity = call.severity;
        lastFoulVictimHadBall = call.victimHadBall;
        lastFoulSpot.set(fx, fy);

        // MK8: ley de la ventaja. La falta queda anotada (con su tarjeta) pero no se corta el juego si el perjudicado sigue con la pelota.
        if (AdvantageRules.eligible(Settings.advantageEnabled, penalty, card, teamHasBall(1 - attTeam), halfTimeRemaining)) {
            advActive = true;
            advTeam = 1 - attTeam;
            advVictimIdx = victIdx;
            advShotsBase = stats.shots[advTeam];
            advSpot.set(fx, fy);
            advTracker.begin();
            advantageEventCount++;
            pendingAdvantageSfx = true;
            return;
        }

        foulEventCount++;
        pendingFoulSfx = true;

        setPieceTeam = 1 - attTeam;
        setPieceTaker = victIdx;
        setPiecePenalty = penalty;
        pendingLayout = new SetPieceLayout(setPieceTeam, fx, fy, penalty);

        if (card == Card.RED) {
            allPlayers[attIdx].setSuspended(true);
            suspendRemaining[attIdx] = redIsPermanentFor(attIdx) ? Constants.RED_PERMANENT : Constants.RED_SUSPENSION_SECONDS;
        }
        enterFoulPause(card);
    }

    /** Pasa a la pausa de la falta (silbato + cartel). Falta en los últimos segundos: se da tiempo para ejecutar el tiro. */
    private void enterFoulPause(Card card) {
        if (halfTimeRemaining < Constants.SET_PIECE_MIN_TIME) {
            halfTimeRemaining = Constants.SET_PIECE_MIN_TIME;
        }
        lastShotOnTargetTeam = -1;
        pendingSaveTeam = -1;
        phase = Phase.FOUL;
        phaseTimer = card == Card.NONE ? Constants.FOUL_PAUSE_SECONDS : Constants.FOUL_PAUSE_CARD_SECONDS;
    }

    /** ¿Algún jugador de campo (no expulsado) del equipo tiene la pelota al alcance? */
    private boolean teamHasBall(int team) {
        for (int i = team; i < 4; i += 2) {
            PlayerEntity p = allPlayers[i];
            if (p != null && !p.isSuspended() && p.isNear(ball)) {
                return true;
            }
        }
        return false;
    }

    /** MK8: un tick de ventaja en curso. */
    private void tickAdvantage() {
        if (phase != Phase.PLAYING) {
            advActive = false;
            return;
        }
        boolean victimNear = teamHasBall(advTeam);
        boolean rivalNear = teamHasBall(1 - advTeam);
        boolean shot = stats.shots[advTeam] > advShotsBase;
        boolean timeUp = halfTimeRemaining <= Constants.SIM_STEP * 2f;
        AdvantageRules.Verdict v = advTracker.update(Constants.SIM_STEP, victimNear && !rivalNear, rivalNear && !victimNear, shot, timeUp);
        if (v == AdvantageRules.Verdict.REALIZED) {
            advActive = false;
            advantagesPlayed++;
        } else if (v == AdvantageRules.Verdict.CALL_BACK) {
            callBackAdvantage();
        }
    }

    /** La ventaja no se aprovechó: se vuelve atrás y se cobra el tiro libre donde fue la falta. */
    private void callBackAdvantage() {
        advActive = false;
        int taker = advVictimIdx;
        if (taker < 0 || allPlayers[taker] == null || allPlayers[taker].isSuspended()) {
            taker = -1;
            for (int i = advTeam; i < 4; i += 2) {
                if (allPlayers[i] != null && !allPlayers[i].isSuspended()) {
                    taker = i;
                    break;
                }
            }
        }
        if (taker < 0) {
            return; // el equipo no tiene a nadie que patee: sigue el juego
        }
        advantagesCalledBack++;
        foulEventCount++;
        pendingFoulSfx = true;
        setPieceTeam = advTeam;
        setPieceTaker = taker;
        setPiecePenalty = false;
        pendingLayout = new SetPieceLayout(advTeam, advSpot.x, advSpot.y, false);
        enterFoulPause(lastFoulCard);
    }

    /** MK8: ¿la roja de este jugador es hasta el final? Solo en el 2v2 y si su compañero de campo no está ya expulsado para siempre. */
    private boolean redIsPermanentFor(int idx) {
        if (!teams2v2 || !Settings.red2v2Permanent) {
            return false;
        }
        int mate = idx ^ 2; // 0<->2, 1<->3
        return allPlayers[mate] != null && !(allPlayers[mate].isSuspended() && suspendRemaining[mate] >= Constants.RED_PERMANENT)
                && !allPlayers[mate].isSuspended();
    }

    /** Acomoda pelota, el que patea, compañeros y barrera para el tiro (pasa al terminar la pausa de la falta). */
    private void beginSetPiece() {
        SetPieceLayout layout = pendingLayout;
        if (layout == null) {
            return;
        }
        Vector2 tmp = new Vector2();
        ball.reset(new Vector2(layout.ball));
        PlayerEntity taker = allPlayers[setPieceTaker];
        if (taker != null && !taker.isSuspended()) {
            placeAt(taker, layout.takerSpot(tmp));
        }
        int mates = 0, rivals = 0;
        for (int i = 0; i < 4; i++) {
            PlayerEntity p = allPlayers[i];
            if (p == null || p.isSuspended() || i == setPieceTaker) {
                continue;
            }
            if ((i & 1) == setPieceTeam) {
                placeAt(p, layout.mateSpot(mates++, tmp));
            } else {
                placeAt(p, layout.wallSpot(rivals++, tmp));
            }
        }
        if (layout.penalty) {
            (setPieceTeam == 0 ? keeperRight : keeperLeft).resetToCenter(); // el arquero del que cometió la falta, al medio
        }
        ringCount = 0;
        ringHead = 0;
        stuckTimer = 0f;
        pendingLayout = null;
        syncPrev();
    }

    private void placeAt(PlayerEntity p, Vector2 v) {
        p.body.setTransform(v.x, v.y, 0f);
        p.body.setLinearVelocity(0f, 0f);
        p.resetState();
    }

    // --- Lectura para la pantalla (HUD, carteles, sonido) ---

    /** true una sola vez cuando se cobra una falta (para el silbato). */
    public boolean consumeFoulSfxEvent() {
        boolean v = pendingFoulSfx;
        pendingFoulSfx = false;
        return v;
    }

    public int getFoulEventCount() {
        return foulEventCount;
    }

    /** Índice del que cometió la última falta (0 izq., 1 der., 2/3 compañeros), o -1 si aún no hubo. */
    public int getLastFoulerIdx() {
        return lastFoulerIdx;
    }

    public String getLastFoulerName() {
        switch (lastFoulerIdx) {
            case 0: return MatchSetup.leftName;
            case 1: return MatchSetup.rightName;
            case 2: return MatchSetup.leftMateName;
            case 3: return MatchSetup.rightMateName;
            default: return "";
        }
    }

    public Card getLastFoulCard() {
        return lastFoulCard;
    }

    public FoulRules.Type getLastFoulType() {
        return lastFoulType;
    }

    /** Gravedad (1..4) de la última falta cobrada. Solo es fiable donde corre la simulación. */
    public int getLastFoulSeverity() {
        return lastFoulSeverity;
    }

    public boolean didLastFoulVictimHaveBall() {
        return lastFoulVictimHadBall;
    }

    // --- MK8: ventaja y expulsión definitiva (lectura para la pantalla y las pruebas) ---

    /** true mientras hay una ventaja en curso (la falta ya está anotada pero se sigue jugando). */
    public boolean isAdvantageActive() {
        return advActive;
    }

    /** Equipo que se beneficia de la ventaja en curso (0 = AZUL, 1 = ROJO); -1 si no hay. */
    public int getAdvantageTeam() {
        return advActive ? advTeam : -1;
    }

    /** true una sola vez cuando se da una ventaja (para el sonido). */
    public boolean consumeAdvantageSfxEvent() {
        boolean v = pendingAdvantageSfx;
        pendingAdvantageSfx = false;
        return v;
    }

    public int getAdvantagesPlayed() {
        return advantagesPlayed;
    }

    public int getAdvantagesCalledBack() {
        return advantagesCalledBack;
    }

    /** true si una roja en este partido expulsa hasta el final (2v2 con la opción activada). */
    public boolean isRedPermanentMode() {
        return teams2v2 && Settings.red2v2Permanent;
    }

    /** true si el jugador está expulsado hasta el final del partido. */
    public boolean isPermanentlySuspended(int idx) {
        return suspendRemaining[idx] >= Constants.RED_PERMANENT;
    }

    public boolean isLastFoulPenalty() {
        return lastFoulPenalty;
    }

    public Vector2 getLastFoulSpot() {
        return lastFoulSpot;
    }

    /** Equipo que cobra el tiro de la última falta (0 = izquierda, 1 = derecha), o -1. */
    public int getSetPieceTeam() {
        return lastFoulerIdx < 0 ? -1 : 1 - (lastFoulerIdx & 1);
    }

    /** Índice del jugador que cobra el tiro (la víctima de la falta). Online el cliente lo deduce: es el del otro equipo que el infractor. */
    public int getSetPieceTakerIdx() {
        if (lastFoulerIdx < 0) {
            return -1;
        }
        return mode == GameMode.ONLINE_CLIENT ? 1 - (lastFoulerIdx & 1) : setPieceTaker;
    }

    /** Segundos de expulsión que le quedan al jugador {@code idx} (0 = en cancha). */
    public float getSuspendRemaining(int idx) {
        return suspendRemaining[idx];
    }

    /** true si el jugador {@code idx} existe en este modo y está expulsado. */
    public boolean isSuspended(int idx) {
        return allPlayers[idx] != null && allPlayers[idx].isSuspended();
    }

    /** true durante la pausa de la falta y la cuenta del tiro (el reloj del tiempo no corre: ver advancePhase). */
    public boolean isDeadBallFoulPhase() {
        return phase == Phase.FOUL || phase == Phase.SET_PIECE;
    }

    // ------------------------------------------------------------------
    // MK7: interpolación de render
    // ------------------------------------------------------------------

    /**
     * MK7: coordina a los dos jugadores IA del equipo rojo. El más cercano a la pelota la presiona (con histéresis de
     * 2 m para que no se alternen); el otro apoya o cubre. Como rival de referencia toman al jugador azul más cercano
     * a la pelota. Deja el input del primero en aiGeneratedInput y el del segundo en aiGeneratedInput2.
     */
    private void updateAiTeam2v2() {
        Vector2 bp = ball.getPosition();
        float dA = playerRight.getPosition().dst(bp);
        float dB = mateRight.getPosition().dst(bp);
        boolean aOut = playerRight.isSuspended(), bOut = mateRight.isSuspended();
        if (aOut != bOut) {
            chaserIsMate = !bOut; // MK8: si uno está expulsado, el otro siempre va a la pelota (antes se quedaba "apoyando")
        } else if (chaserIsMate) {
            if (dA + 2f < dB) chaserIsMate = false;
        } else if (dB + 2f < dA) {
            chaserIsMate = true;
        }
        boolean lOut = playerLeft.isSuspended(), mOut = mateLeft.isSuspended();
        PlayerEntity rival;
        if (lOut != mOut) {
            rival = lOut ? mateLeft : playerLeft; // MK8: el rival a marcar es el que sigue en cancha
        } else {
            rival = playerLeft.getPosition().dst2(bp) <= mateLeft.getPosition().dst2(bp) ? playerLeft : mateLeft;
        }
        playerRightAI.update(aiGeneratedInput, playerRight, ball, rival, keeperLeft, Constants.SIM_STEP,
                chaserIsMate, bOut ? null : mateRight.getPosition());
        mateRightAI.update(aiGeneratedInput2, mateRight, ball, rival, keeperLeft, Constants.SIM_STEP,
                !chaserIsMate, aOut ? null : playerRight.getPosition());
    }

    private void capturePrev() {
        copyCurrentTo(prevPos);
    }

    /** Hace que la posición "anterior" sea la actual (después de un teletransporte, para no dibujar un barrido). */
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

    /** 0..1: cuánto del próximo tick ya pasó. En el cliente online siempre 1 (ya recibe estados interpolados). */
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
        // Si se movió más de 6 m en un tick fue un teletransporte: no se interpola.
        if (a >= 1f || (cur.x - px) * (cur.x - px) + (cur.y - py) * (cur.y - py) > 36f) {
            return renderTmp.set(cur);
        }
        return renderTmp.set(px + (cur.x - px) * a, py + (cur.y - py) * a);
    }

    /** Posiciones para DIBUJAR (interpoladas). Cada llamada devuelve el mismo Vector2 interno: copiarlo si se guarda. */
    public Vector2 renderBall(Vector2 out) { return out.set(renderOf(0, ball.getPosition())); }
    public Vector2 renderPlayerLeft(Vector2 out) { return out.set(renderOf(1, playerLeft.getPosition())); }
    public Vector2 renderPlayerRight(Vector2 out) { return out.set(renderOf(2, playerRight.getPosition())); }
    public Vector2 renderKeeperLeft(Vector2 out) { return out.set(renderOf(3, keeperLeft.getPosition())); }
    public Vector2 renderKeeperRight(Vector2 out) { return out.set(renderOf(4, keeperRight.getPosition())); }
    public Vector2 renderMateLeft(Vector2 out) { return out.set(renderOf(5, mateLeft.getPosition())); }
    public Vector2 renderMateRight(Vector2 out) { return out.set(renderOf(6, mateRight.getPosition())); }

    // ------------------------------------------------------------------
    // Fases del partido
    // ------------------------------------------------------------------

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
                    // MK6: empate al final del segundo tiempo -> tiempo extra con gol de oro.
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
            return; // solo sale de acá si alguien reinicia el partido desde afuera
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
                    phase = Phase.FULL_TIME; // MK6: gol de oro, se terminó
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
            case FOUL:
                beginSetPiece();
                phase = Phase.SET_PIECE;
                phaseTimer = Constants.SET_PIECE_COUNTDOWN_SECONDS;
                break;
            case SET_PIECE:
                phase = Phase.PLAYING;
                protectTimer = setPiecePenalty ? Constants.PENALTY_PROTECT_SECONDS : Constants.SET_PIECE_PROTECT_SECONDS;
                protectSpot.set(ball.getPosition());
                break;
            default:
                break;
        }
    }

    /** Posición de saque. 0 = jugador izq., 1 = jugador der., 2 = compañero izq. (2v2), 3 = compañero der. (2v2). */
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
        if (p.isSuspended()) {
            return; // MK8: el expulsado sigue afuera hasta cumplir su tiempo
        }
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
        protectTimer = 0f;
        setPieceTaker = -1;
        advActive = false; // un gol o el fin del tiempo cierran la ventaja
        syncPrev();
    }

    // ------------------------------------------------------------------
    // Repetición de gol (MK4): se graba un anillo con los últimos segundos de
    // juego y, tras el festejo, se reproduce en cámara lenta moviendo los mismos
    // bodies (sin hacer world.step, así que la física no interviene).
    // ------------------------------------------------------------------

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

    /** Copia el anillo (en orden cronológico) a un arreglo de repetición. */
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

    /** MK6: copia los colores de equipo actuales de MatchSetup a los cuatro personajes (cuando llega la identidad del rival online). */
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
            phase = Phase.FULL_TIME; // MK6: gol de oro, se terminó
            return;
        }
        phase = Phase.KICKOFF;
        phaseTimer = Constants.KICKOFF_FREEZE_SECONDS;
    }

    /** Saltea la repetición (el reinicio de posiciones lo hace el próximo tick). */
    public void skipReplay() {
        if (phase == Phase.REPLAY) {
            phaseTimer = 0f;
        }
    }

    private void handleKick(PlayerEntity player, PlayerInput input, int team) {
        if (input.kickReleased) {
            float power = player.consumeKickPower();
            // MK5: tirado en el piso (planchazo o recuperación) no se puede patear.
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

    /**
     * Dirección del pateo: del jugador hacia la pelota, mezclada con hacia dónde se mueve el jugador (se puede apuntar).
     * Si se mueve hacia atrás de la pelota (producto escalar <= 0) se ignora. Es pública para que el render dibuje
     * la misma línea de puntería que usa la simulación.
     */
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

    /** Cuenta remates (potencia suficiente y rumbo al arco rival) y si van al arco (trayectoria recta). */
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

    /** Si el jugador está en pleno planchazo y alcanza la pelota, se la "gana" empujándola en esa dirección. */
    private void handleSlideTackle(PlayerEntity player, Ball ball, int team) {
        if (player.canWinBallThisSlide() && player.isSlideReaching(ball)) {
            ball.kick(player.getSlideDirection(), Constants.SLIDE_KICK_POWER, 0f);
            player.markSlideBallTouched();
            pendingSlideTackleSfx = true;
            slideEventCount++;
            stats.tackles[team]++;
        }
    }

    /** Devuelve la potencia del último pateo (para sonido/juice) y limpia el evento. Null si no hubo ninguno este frame. */
    public Float consumeKickSfxEvent() {
        Float v = pendingKickSfxPower;
        pendingKickSfxPower = null;
        return v;
    }

    /** true si hubo un planchazo que "ganó" la pelota este frame (para sonido). Se consume una sola vez. */
    public boolean consumeSlideTackleSfxEvent() {
        boolean v = pendingSlideTackleSfx;
        pendingSlideTackleSfx = false;
        return v;
    }

    /** true si hubo una atajada del arquero este frame (para sonido/cartel). Se consume una sola vez. */
    public boolean consumeSaveEvent() {
        boolean v = pendingSaveSfx;
        pendingSaveSfx = false;
        return v;
    }

    public MatchStats getStats() {
        return stats;
    }

    /** true si el último gol lo hizo el equipo AZUL (izquierda). */
    public boolean isLastGoalByLeft() {
        return lastGoalByLeft;
    }

    /** Porcentaje de posesión aproximado del equipo AZUL (0..100), útil para el resumen post-partido. */
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

        // Atajada: la pelota toca a un arquero cuando venía un remate al arco del equipo contrario.
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
            return; // ya hay un gol anotado para procesar (o ya se está festejando uno)
        }

        if (contact.getFixtureA() == field.leftGoalSensor || contact.getFixtureB() == field.leftGoalSensor) {
            goalPending = true;
            pendingLeftTeamScored = false; // equipo derecho convirtió en el arco izquierdo
        } else if (contact.getFixtureA() == field.rightGoalSensor || contact.getFixtureB() == field.rightGoalSensor) {
            goalPending = true;
            pendingLeftTeamScored = true; // equipo izquierdo convirtió en el arco derecho
        }
    }

    /** Aplica el gol de verdad: contador, mensaje y reset de posiciones. Llamado fuera de world.step(). */
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

        captureReplay();   // antes de reposicionar: el anillo todavía tiene la jugada del gol
        resetKickoffPositions();
        phase = Phase.GOAL_CELEBRATION;
        phaseTimer = Constants.GOAL_CELEBRATION_SECONDS;
    }

    @Override
    public void endContact(Contact contact) {
    }

    @Override
    public void preSolve(Contact contact, Manifold oldManifold) {
        // MK7: entre jugador y pelota el rebote es bajo. Con el 0,65 de la pelota, al correr contra ella salía
        // disparada más rápido que el jugador y no se podía conducir; ahora queda al alcance para patearla.
        short ca = contact.getFixtureA().getFilterData().categoryBits;
        short cb = contact.getFixtureB().getFilterData().categoryBits;
        if ((ca == Constants.CAT_BALL && cb == Constants.CAT_PLAYER)
                || (cb == Constants.CAT_BALL && ca == Constants.CAT_PLAYER)) {
            Body other = ca == Constants.CAT_BALL ? contact.getFixtureB().getBody() : contact.getFixtureA().getBody();
            if (other != keeperLeft.body && other != keeperRight.body) { // los arqueros siguen rebotando como antes
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

    /** MK7: true en el 2v2 local (hay un segundo jugador de campo por equipo). */
    public boolean hasMates() {
        return teams2v2;
    }

    /** MK7: compañero humano del equipo izquierdo (solo 2v2; null en los demás modos). */
    public PlayerEntity getMateLeft() {
        return mateLeft;
    }

    /** MK7: segundo jugador IA del equipo derecho (solo 2v2; null en los demás modos). */
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

    /** Segundos restantes de la fase actual (cuenta regresiva de saque, gol, entretiempo). */
    public float getPhaseTimer() {
        return phaseTimer;
    }

    /** Progreso 0..1 de la repetición (para la barra). */
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

    /** Segundos restantes del tiempo (primero o segundo) que se está jugando actualmente. */
    public float getHalfTimeRemaining() {
        return halfTimeRemaining;
    }

    public int getTick() {
        return tick;
    }

    // ------------------------------------------------------------------
    // Online (Etapa 2): el host genera snapshots; el cliente los aplica.
    // ------------------------------------------------------------------

    /** HOST: vuelca el estado actual del partido en {@code s}. */
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
            s.fouls[i] = (byte) Math.min(127, stats.fouls[i]);
            s.yellows[i] = (byte) Math.min(127, stats.yellows[i]);
            s.reds[i] = (byte) Math.min(127, stats.reds[i]);
            s.suspended[i] = ps[i].isSuspended();
            s.suspendRemaining[i] = suspendRemaining[i];
        }
        s.foulCount = foulEventCount;
        s.advantageTeam = (byte) (advActive ? advTeam : -1);
        s.advantageCount = advantageEventCount;
        s.foulerIdx = (byte) lastFoulerIdx;
        s.foulCard = (byte) lastFoulCard.ordinal();
        s.foulType = (byte) lastFoulType.ordinal();
        s.foulPenalty = lastFoulPenalty;
        s.foulX = lastFoulSpot.x;
        s.foulY = lastFoulSpot.y;
        s.kickCount = kickEventCount;
        s.slideCount = slideEventCount;
        s.saveCount = saveEventCount;
        s.lastKickPower = lastKickPower;
    }

    /** CLIENTE: copia el estado recibido del host sobre esta simulación (que no corre física propia). */
    public void applySnapshot(Snapshot s) {
        // Eventos: se disparan cuando el contador del host supera al último visto. El primer snapshot
        // solo sincroniza los contadores (si no, al unirse a mitad de partido sonaría todo junto).
        if (remoteInit) {
            if (s.kickCount != remoteKickCount) pendingKickSfxPower = s.lastKickPower;
            if (s.slideCount != remoteSlideCount) pendingSlideTackleSfx = true;
            if (s.saveCount != remoteSaveCount) pendingSaveSfx = true;
            if (s.foulCount != remoteFoulCount) pendingFoulSfx = true;
            if (s.advantageCount != remoteAdvantageCount) pendingAdvantageSfx = true;
        }
        remoteAdvantageCount = s.advantageCount;
        advActive = s.advantageTeam >= 0;
        advTeam = s.advantageTeam;
        remoteFoulCount = s.foulCount;
        foulEventCount = s.foulCount;
        lastFoulerIdx = s.foulerIdx;
        Card[] cards = Card.values();
        lastFoulCard = cards[Math.max(0, Math.min(cards.length - 1, s.foulCard))];
        FoulRules.Type[] types = FoulRules.Type.values();
        lastFoulType = types[Math.max(0, Math.min(types.length - 1, s.foulType))];
        lastFoulPenalty = s.foulPenalty;
        lastFoulSpot.set(s.foulX, s.foulY);
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
            stats.fouls[i] = s.fouls[i];
            stats.yellows[i] = s.yellows[i];
            stats.reds[i] = s.reds[i];
            ps[i].setSuspended(s.suspended[i]);
            suspendRemaining[i] = s.suspendRemaining[i];
        }
    }

    public void dispose() {
        world.dispose();
    }
}
