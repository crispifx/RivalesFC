package com.rivalesfc.game.sim;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.ContactListener;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.Manifold;
import com.badlogic.gdx.physics.box2d.World;
import com.rivalesfc.game.Constants;
import com.rivalesfc.game.GameMode;
import com.rivalesfc.game.Settings;
import com.rivalesfc.game.ai.SupportAI;
import com.rivalesfc.game.entities.Ball;
import com.rivalesfc.game.entities.Field;
import com.rivalesfc.game.entities.GoalkeeperEntity;
import com.rivalesfc.game.entities.PlayerEntity;
import com.rivalesfc.game.input.PlayerInput;

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
        FULL_TIME
    }

    private final World world;
    private final Field field;
    private final Ball ball;

    private final PlayerEntity playerLeft;
    private final PlayerEntity playerRight;
    private final GoalkeeperEntity keeperLeft;
    private final GoalkeeperEntity keeperRight;

    private final GameMode mode;
    /** No nulo solo en {@link GameMode#ONE_PLAYER}: controla al Jugador 2 (equipo ROJO) en vez del teclado. */
    private final SupportAI playerRightAI;
    private final PlayerInput aiGeneratedInput = new PlayerInput();

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

    // --- Posesión aproximada (para el resumen post-partido): ticks en los que
    // cada equipo tuvo la pelota "controlada" (jugador de campo cerca de ella). ---
    private int possessionTicksLeft = 0;
    private int possessionTicksRight = 0;

    public MatchSimulation(GameMode mode) {
        this.mode = mode;
        this.playerRightAI = mode == GameMode.ONE_PLAYER ? new SupportAI(true) : null;

        world = new World(new Vector2(0, 0), true); // fútbol visto desde arriba: sin gravedad
        world.setContactListener(this);

        field = new Field(world);
        ball = new Ball(world, new Vector2(0, 0));

        playerLeft = new PlayerEntity(world, new Vector2(Constants.KICKOFF_LEFT_X, 0), Constants.TEAM_LEFT_COLOR);
        playerRight = new PlayerEntity(world, new Vector2(Constants.KICKOFF_RIGHT_X, 0), Constants.TEAM_RIGHT_COLOR);
        playerRight.humanControlled = mode == GameMode.TWO_PLAYERS;

        float halfFieldWidth = Constants.FIELD_WIDTH / 2f;
        float patrolRange = Constants.GOAL_WIDTH / 2f - Constants.PLAYER_RADIUS;
        keeperLeft = new GoalkeeperEntity(world, -halfFieldWidth + Constants.GK_LINE_OFFSET, patrolRange, Constants.TEAM_LEFT_COLOR);
        keeperRight = new GoalkeeperEntity(world, halfFieldWidth - Constants.GK_LINE_OFFSET, patrolRange, Constants.TEAM_RIGHT_COLOR);

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
        accumulator += rawDelta;
        while (accumulator >= Constants.SIM_STEP) {
            fixedTick(inputLeft, inputRight);
            accumulator -= Constants.SIM_STEP;
        }
    }

    private void fixedTick(PlayerInput inputLeft, PlayerInput inputRight) {
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
            playerLeft.applyInput(inputLeft, Constants.SIM_STEP);
            handleKick(playerLeft, inputLeft, 0);

            PlayerInput rightInput;
            if (mode == GameMode.ONE_PLAYER) {
                playerRightAI.update(aiGeneratedInput, playerRight, ball, playerLeft, keeperLeft, Constants.SIM_STEP);
                rightInput = aiGeneratedInput;
            } else {
                rightInput = inputRight;
            }
            playerRight.applyInput(rightInput, Constants.SIM_STEP);
            handleKick(playerRight, rightInput, 1);

            // Planchazo: si un jugador está en pleno planchazo y alcanza la pelota,
            // se la "gana" con un toque en la dirección del planchazo (una vez por planchazo).
            handleSlideTackle(playerLeft, ball, 0);
            handleSlideTackle(playerRight, ball, 1);

            if (playerLeft.isNear(ball)) {
                possessionTicksLeft++;
            } else if (playerRight.isNear(ball)) {
                possessionTicksRight++;
            }
        } else {
            // Fuera de juego (cuenta regresiva / gol / entretiempo / final): todo congelado.
            playerLeft.body.setLinearVelocity(0, 0);
            playerRight.body.setLinearVelocity(0, 0);
            ball.body.setLinearVelocity(0, 0);
            ball.body.setAngularVelocity(0);
            inputLeft.clearTransient();
            inputRight.clearTransient();
        }

        // Arqueros 100% IA: siguen la pelota (y se tiran a los remates) solo con juego habilitado
        // para estirarse; durante la cuenta regresiva solo la siguen.
        keeperLeft.update(ball.getPosition(), ball.body.getLinearVelocity(), Constants.SIM_STEP, playEnabled);
        keeperRight.update(ball.getPosition(), ball.body.getLinearVelocity(), Constants.SIM_STEP, playEnabled);

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
            lastShotOnTargetTeam = -1;
            pendingSaveTeam = -1;
        }

        advancePhase(Constants.SIM_STEP);
    }

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

    private void resetKickoffPositions() {
        ball.reset(new Vector2(0, 0));

        playerLeft.body.setTransform(Constants.KICKOFF_LEFT_X, 0, 0);
        playerLeft.body.setLinearVelocity(0, 0);
        playerRight.body.setTransform(Constants.KICKOFF_RIGHT_X, 0, 0);
        playerRight.body.setLinearVelocity(0, 0);

        playerLeft.resetState();
        playerRight.resetState();

        keeperLeft.resetToCenter();
        keeperRight.resetToCenter();
        ringCount = 0;
        ringHead = 0;
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
            applyFrame(replayFrames[idx]);
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
        keeperLeft.body.setTransform(keeperLeft.body.getPosition().x, f.kly, 0);
        keeperLeft.body.setLinearVelocity(0, f.klvy);
        keeperLeft.setReplayDive(f.klDive);
        keeperRight.body.setTransform(keeperRight.body.getPosition().x, f.kry, 0);
        keeperRight.body.setLinearVelocity(0, f.krvy);
        keeperRight.setReplayDive(f.krDive);
    }

    private void endReplay() {
        resetKickoffPositions();
        replayFrames = null;
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
            if (player.isNear(ball)) {
                Vector2 toBall = new Vector2(ball.getPosition()).sub(player.getPosition());
                if (toBall.len2() < 0.0001f) {
                    toBall.set(team == 0 ? 1f : -1f, 0f);
                }
                ball.kick(toBall, power, 0f);
                pendingKickSfxPower = power;
                registerShot(team, toBall, power);
            }
        }
        input.clearTransient();
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
        if (player.canWinBallThisSlide() && player.isNear(ball)) {
            ball.kick(player.getSlideDirection(), Constants.SLIDE_KICK_POWER, 0f);
            player.markSlideBallTouched();
            pendingSlideTackleSfx = true;
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
            lastGoalMessage = "GOL de " + Constants.PLAYER_LEFT_NAME + " (AZUL)";
        } else {
            scoreRight++;
            lastGoalMessage = "GOL de " + Constants.PLAYER_RIGHT_NAME + " (ROJO)";
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

    public void dispose() {
        world.dispose();
    }
}
