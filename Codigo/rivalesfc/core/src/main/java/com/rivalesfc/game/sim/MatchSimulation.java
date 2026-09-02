package com.rivalesfc.game.sim;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.ContactListener;
import com.badlogic.gdx.physics.box2d.Manifold;
import com.badlogic.gdx.physics.box2d.World;
import com.rivalesfc.game.Constants;
import com.rivalesfc.game.GameMode;
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
    private float halfTimeRemaining = Constants.HALF_DURATION_SECONDS;

    // Un gol se detecta dentro de beginContact(), que Box2D dispara EN MEDIO de
    // world.step(). Mover bodies (setTransform) ahí adentro es inseguro y puede
    // corromper el estado del World a mitad de paso. Por eso acá solo se guarda
    // la bandera, y el gol se procesa de verdad recién después de que world.step()
    // termina (ver fixedTick).
    private boolean goalPending = false;
    private boolean pendingLeftTeamScored = false;

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

        boolean playEnabled = phase == Phase.PLAYING;

        if (playEnabled) {
            playerLeft.applyInput(inputLeft, Constants.SIM_STEP);
            handleKick(playerLeft, inputLeft);

            PlayerInput rightInput;
            if (mode == GameMode.ONE_PLAYER) {
                playerRightAI.update(aiGeneratedInput, playerRight, ball, playerLeft, Constants.SIM_STEP);
                rightInput = aiGeneratedInput;
            } else {
                rightInput = inputRight;
            }
            playerRight.applyInput(rightInput, Constants.SIM_STEP);
            handleKick(playerRight, rightInput);

            // Planchazo: si un jugador está en pleno planchazo y alcanza la pelota,
            // se la "gana" con un toque en la dirección del planchazo (una vez por planchazo).
            handleSlideTackle(playerLeft, ball);
            handleSlideTackle(playerRight, ball);
        } else {
            // Fuera de juego (cuenta regresiva / gol / entretiempo / final): todo congelado.
            playerLeft.body.setLinearVelocity(0, 0);
            playerRight.body.setLinearVelocity(0, 0);
            ball.body.setLinearVelocity(0, 0);
            ball.body.setAngularVelocity(0);
            inputLeft.clearTransient();
            inputRight.clearTransient();
        }

        // Arqueros 100% IA: único estado, perseguir la pelota en el eje Y. Se los deja
        // activos incluso durante la cuenta regresiva para que no se vean "congelados a mitad de salto".
        keeperLeft.update(ball.getPosition().y, Constants.SIM_STEP);
        keeperRight.update(ball.getPosition().y, Constants.SIM_STEP);

        world.step(Constants.SIM_STEP, 8, 3);
        tick++;

        // El World ya terminó de resolver este paso: recién ahora es seguro
        // mover bodies (reset de posiciones) si se detectó un gol durante el step.
        if (goalPending) {
            goalPending = false;
            registerGoal(pendingLeftTeamScored);
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
                phase = Phase.KICKOFF;
                phaseTimer = Constants.KICKOFF_FREEZE_SECONDS;
                break;
            case KICKOFF:
                phase = Phase.PLAYING;
                break;
            case HALFTIME:
                halfTimeRemaining = Constants.HALF_DURATION_SECONDS;
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

        keeperLeft.resetToCenter();
        keeperRight.resetToCenter();
    }

    private void handleKick(PlayerEntity player, PlayerInput input) {
        if (input.kickReleased && player.isNear(ball)) {
            Vector2 toBall = new Vector2(ball.getPosition()).sub(player.getPosition());
            if (toBall.len2() < 0.0001f) {
                toBall.set(1f, 0f);
            }
            ball.kick(toBall, player.getKickPower(), 0f);
        }
        input.clearTransient();
    }

    /** Si el jugador está en pleno planchazo y alcanza la pelota, se la "gana" empujándola en esa dirección. */
    private void handleSlideTackle(PlayerEntity player, Ball ball) {
        if (player.canWinBallThisSlide() && player.isNear(ball)) {
            ball.kick(player.getSlideDirection(), Constants.SLIDE_KICK_POWER, 0f);
            player.markSlideBallTouched();
        }
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
            lastGoalMessage = "GOL equipo AZUL";
        } else {
            scoreRight++;
            lastGoalMessage = "GOL equipo ROJO";
        }

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
