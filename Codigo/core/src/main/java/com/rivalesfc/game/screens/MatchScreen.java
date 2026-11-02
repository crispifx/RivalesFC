package com.rivalesfc.game.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer;
import com.rivalesfc.game.Constants;
import com.rivalesfc.game.GameMode;
import com.rivalesfc.game.MatchSetup;
import com.rivalesfc.game.RivalesFCGame;
import com.rivalesfc.game.Settings;
import com.rivalesfc.game.gfx.BlurShader;
import com.rivalesfc.game.sim.FoulRules;
import com.rivalesfc.game.sim.MatchStats;
import com.rivalesfc.game.entities.Ball;
import com.rivalesfc.game.entities.Field;
import com.rivalesfc.game.entities.GoalkeeperEntity;
import com.rivalesfc.game.entities.PlayerEntity;
import com.rivalesfc.game.gfx.MotionTrail;
import com.rivalesfc.game.gfx.NetRipple;
import com.rivalesfc.game.gfx.Particles;
import com.rivalesfc.game.gfx.Fonts;
import com.rivalesfc.game.gfx.PixelArtFactory;
import com.rivalesfc.game.input.PlayerInput;
import com.rivalesfc.game.net.NetClient;
import com.rivalesfc.game.net.NetHost;
import com.rivalesfc.game.net.Protocol;
import com.rivalesfc.game.net.Snapshot;
import com.rivalesfc.game.net.SnapshotInterpolator;
import com.rivalesfc.game.sim.MatchSimulation;
import com.rivalesfc.game.audio.AudioFactory;
import com.badlogic.gdx.audio.Sound;

/**
 * Pantalla de partido — modo local, pantalla dividida. El lobby previo
 * ({@link LobbyScreen}) define si el panel derecho lo maneja un segundo
 * humano o una IA de soporte (modo 1 jugador). Cada mitad de la pantalla es
 * la vista de "su" jugador: la cámara de esa mitad lo sigue, y dentro de esa
 * vista el jugador rival (humano o IA) se dibuja **difuminado**, para que
 * cada uno identifique de un vistazo cuál personaje es el suyo. Los dos
 * arqueros son 100% IA y se ven nítidos en ambas mitades.
 *
 * Sobre la Etapa 1 original, esta pantalla suma la presentación estilo
 * arcade pixel-art inspirada en una referencia visual: una barra superior
 * COMPARTIDA (no duplicada por panel) con reloj, marcador "BLUE X - Y RED"
 * y título del partido; sprites de jugadores generados en runtime con
 * {@link PixelArtFactory} en lugar de círculos lisos; carteles con el
 * nombre de cada jugador; un rastro de movimiento que aparece al
 * sprintar; un planchazo/barrida; cancha con áreas, redes y tribuna
 * decorativa; y una máquina de estados de partido (cuenta regresiva de
 * saque, cartel de gol, entretiempo, fin del partido con reinicio).
 *
 * Controles:
 *   Jugador 1 (equipo AZUL, panel izquierdo)
 *     WASD          -> mover
 *     Shift izq.    -> sprint
 *     Ctrl izq.     -> planchazo/barrida
 *     Espacio (mantener y soltar) -> cargar y patear
 *
 *   Jugador 2 (equipo ROJO, panel derecho) — solo en modo 2 jugadores
 *     Flechas       -> mover
 *     Ctrl derecho  -> sprint
 *     Shift derecho -> planchazo/barrida
 *     Enter (mantener y soltar) -> cargar y patear
 *
 *   TAB -> saltear la repetición de gol (MK4)
 *   M   -> silenciar / activar sonido (MK4)
 *   F1 -> alternar visualización de debug de Box2D
 *   P  -> pausa
 *   R  -> reiniciar el partido
 */
public class MatchScreen implements Screen {

    private final RivalesFCGame game;
    private final GameMode mode;

    private MatchSimulation sim;

    private final PlayerInput inputLeft = new PlayerInput();
    private final PlayerInput inputRight = new PlayerInput();
    private boolean kickHeldPrevLeft = false;
    private boolean kickHeldPrevRight = false;

    // --- Online 1v1: el host simula y manda snapshots; el cliente manda su input y dibuja lo recibido ---
    private final NetHost netHost;
    private final NetClient netClient;
    private final String netAddressText;
    private final Snapshot netSnap = new Snapshot();
    private final SnapshotInterpolator netInterp = new SnapshotInterpolator(); // MK5: suaviza al cliente
    private int netSeq = 0;
    private float netSendAccum = 0f;
    private boolean hostHadClient = false;

    /** MK6: registro de goles para el HUD ("AZUL 1:23"); se arma mirando los cambios del marcador (sirve también en el cliente online). */
    private final java.util.ArrayList<String> goalLog = new java.util.ArrayList<>();
    private int goalLogLeft = 0;
    private int goalLogRight = 0;

    public MatchScreen(RivalesFCGame game, GameMode mode) {
        this(game, mode, null, null, "");
    }

    public MatchScreen(RivalesFCGame game, GameMode mode, NetHost netHost, NetClient netClient, String netAddressText) {
        this.game = game;
        this.mode = mode;
        this.netHost = netHost;
        this.netClient = netClient;
        this.netAddressText = netAddressText;
        this.sim = new MatchSimulation(mode);
    }

    private final OrthographicCamera camLeft = new OrthographicCamera();
    private final OrthographicCamera camRight = new OrthographicCamera();
    private final OrthographicCamera hudCam = new OrthographicCamera();
    private final Vector2 focusBlend = new Vector2();

    // --- MK7: visibilidad ---
    private final Vector2 rBall = new Vector2();
    private final Vector2 rPlayerL = new Vector2();
    private final Vector2 rPlayerR = new Vector2();
    private final Vector2 rKeeperL = new Vector2();
    private final Vector2 rKeeperR = new Vector2();
    private final Vector2 rMateL = new Vector2();   // MK7: 2v2 local
    private final Vector2 rMateR = new Vector2();
    private final Vector2 smoothFocusL = new Vector2();
    private final Vector2 smoothFocusR = new Vector2();
    private final Vector2 smoothFocusFull = new Vector2();
    private boolean smoothInitL = false, smoothInitR = false, smoothInitFull = false;
    private final Vector2 tmpA = new Vector2();
    private final Vector2 tmpB = new Vector2();
    private float ballRollDeg = 0f;
    private float uiScale = 1f;
    private float unstuckToast = 0f;
    private float escArmedTime = 0f;
    private OrthographicCamera overlayCam;
    private boolean overlaySelfIsLeft = true;

    private final ShapeRenderer shapes = new ShapeRenderer();
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = Fonts.create();
    private final Box2DDebugRenderer debugRenderer = new Box2DDebugRenderer();
    private boolean showDebug = false;
    private boolean paused = false;

    private int screenWidth = 1280;
    private int screenHeight = 800;

    // --- Arte generado en runtime en alta resolución y con antialiasing (MK7; sin assets externos) ---
    private Texture texPlayerLeft = PixelArtFactory.buildPersonTexture(MatchSetup.leftTeam.color, MatchSetup.leftTeam.shorts, false);
    private Texture texPlayerRight = PixelArtFactory.buildPersonTexture(MatchSetup.rightTeam.color, MatchSetup.rightTeam.shorts, false);
    private Texture texKeeperLeft = PixelArtFactory.buildPersonTexture(new Color(0.10f, 0.10f, 0.10f, 1f), MatchSetup.leftTeam.color, true);
    private Texture texKeeperRight = PixelArtFactory.buildPersonTexture(new Color(0.10f, 0.10f, 0.10f, 1f), MatchSetup.rightTeam.color, true);
    private final Texture texBall = PixelArtFactory.buildBallTexture();
    /** MK7: cancha completa (pasto, líneas, arcos con red y carteles) en una sola textura de 32 px/m. */
    private final Texture texGround = PixelArtFactory.buildGroundTexture();
    private final Texture texCrowd = PixelArtFactory.buildCrowdTexture();
    private final Texture texShadow = PixelArtFactory.buildShadowTexture();
    private final Texture texRing = PixelArtFactory.buildRingTexture();
    private final Texture texHalo = PixelArtFactory.buildHaloTexture();

    private TextureRegion regionPlayerLeft = new TextureRegion(texPlayerLeft);
    private TextureRegion regionPlayerRight = new TextureRegion(texPlayerRight);
    private TextureRegion regionKeeperLeft = new TextureRegion(texKeeperLeft);
    private TextureRegion regionKeeperRight = new TextureRegion(texKeeperRight);
    private final TextureRegion regionBall = new TextureRegion(texBall);
    private final TextureRegion regionCrowd = new TextureRegion(texCrowd);

    // --- MK4: desenfoque real por shader (con respaldo por capas si no compila) ---
    private final ShaderProgram blurShader = BlurShader.create();
    /** Región del sprite rival con UVs extendidas (borde transparente) para que el blur no se recorte. */
    private TextureRegion blurRegionLeft = paddedRegion(texPlayerLeft);
    private TextureRegion blurRegionRight = paddedRegion(texPlayerRight);
    private static final float BLUR_PAD = 0.25f;

    private final NetRipple netRipple = new NetRipple(); // MK9: red que se sacude con el gol
    private final Particles particles = new Particles(); // MK9: polvo, chispas
    private final MotionTrail trailLeft = new MotionTrail(Constants.TRAIL_MAX_POINTS);
    private final MotionTrail trailRight = new MotionTrail(Constants.TRAIL_MAX_POINTS);

    // --- Animación (bobbing simple según velocidad, sin sprites extra) ---
    private float animTime = 0f;

    // --- MK6: identidad del rival online (equipo + nombre) ---
    private String appliedIdentityKey = "";

    /** MK6: aplica el equipo/nombre del rival cuando llegan por red y regenera los sprites con los colores nuevos. */
    private void pollNetIdentity() {
        Protocol.Identity id = null;
        if (mode == GameMode.ONLINE_HOST && netHost != null) id = netHost.getClientIdentity();
        else if (mode == GameMode.ONLINE_CLIENT && netClient != null) id = netClient.getHostIdentity();
        if (id == null) return;
        String key = id.team + "|" + id.name;
        if (key.equals(appliedIdentityKey)) return;
        appliedIdentityKey = key;
        String own = MatchSetup.ownName();
        if (mode == GameMode.ONLINE_HOST) {
            MatchSetup.setOnline(Settings.teamIndex, own, id.team, id.name);
        } else {
            MatchSetup.setOnline(id.team, id.name, Settings.teamIndex, own);
        }
        rebuildTeamArt();
    }

    private void rebuildTeamArt() {
        texPlayerLeft.dispose();
        texPlayerRight.dispose();
        texKeeperLeft.dispose();
        texKeeperRight.dispose();
        texPlayerLeft = PixelArtFactory.buildPersonTexture(MatchSetup.leftTeam.color, MatchSetup.leftTeam.shorts, false);
        texPlayerRight = PixelArtFactory.buildPersonTexture(MatchSetup.rightTeam.color, MatchSetup.rightTeam.shorts, false);
        texKeeperLeft = PixelArtFactory.buildPersonTexture(new Color(0.10f, 0.10f, 0.10f, 1f), MatchSetup.leftTeam.color, true);
        texKeeperRight = PixelArtFactory.buildPersonTexture(new Color(0.10f, 0.10f, 0.10f, 1f), MatchSetup.rightTeam.color, true);
        regionPlayerLeft = new TextureRegion(texPlayerLeft);
        regionPlayerRight = new TextureRegion(texPlayerRight);
        regionKeeperLeft = new TextureRegion(texKeeperLeft);
        regionKeeperRight = new TextureRegion(texKeeperRight);
        blurRegionLeft = paddedRegion(texPlayerLeft);
        blurRegionRight = paddedRegion(texPlayerRight);
        sim.refreshTeamColors();
    }

    // --- Audio sintetizado en runtime (sin assets externos) ---
    private final Sound sfxKickSoft = AudioFactory.kickTone(0.25f);
    private final Sound sfxKickHard = AudioFactory.kickTone(0.9f);
    private final Sound sfxThud = AudioFactory.thudTone();
    private final Sound sfxWhistle = AudioFactory.whistleTone();
    private final Sound sfxAdvantage = AudioFactory.advantageTone(); // MK8
    private final Sound sfxGoal = AudioFactory.goalFanfare();
    private final Sound sfxFullTime = AudioFactory.fullTimeTone();
    private final Sound sfxCheer = AudioFactory.crowdCheer();
    private final Sound sfxDive = AudioFactory.diveWhoosh();
    private final Sound sfxSave = AudioFactory.saveTone();
    private boolean keeperLeftWasDiving = false;
    private boolean keeperRightWasDiving = false;
    private float saveFlashTime = 0f;

    // --- MK4: confeti de gol (coordenadas normalizadas 0..1 dentro de cada panel) ---
    private static final int CONFETTI_MAX = 120;
    private final float[] cfX = new float[CONFETTI_MAX];
    private final float[] cfY = new float[CONFETTI_MAX];
    private final float[] cfVX = new float[CONFETTI_MAX];
    private final float[] cfVY = new float[CONFETTI_MAX];
    private final float[] cfRot = new float[CONFETTI_MAX];
    private final float[] cfSpin = new float[CONFETTI_MAX];
    private final float[] cfSize = new float[CONFETTI_MAX];
    private final float[] cfLife = new float[CONFETTI_MAX];
    private final Color[] cfColor = new Color[CONFETTI_MAX];

    // --- MK4: cámara de repetición (ambos paneles siguen la pelota) ---
    private final Vector2 replayFocus = new Vector2();
    private boolean replayFocusInit = false;
    private MatchSimulation.Phase lastPhase = MatchSimulation.Phase.KICKOFF;

    // --- Screen shake (feedback de gol/planchazo fuerte) ---
    private float shakeTime = 0f;
    private float shakeMagnitude = 0f;

    private static TextureRegion paddedRegion(Texture t) {
        TextureRegion r = new TextureRegion(t);
        r.setRegion(-BLUR_PAD, -BLUR_PAD, 1f + BLUR_PAD, 1f + BLUR_PAD);
        return r;
    }

    @Override
    public void show() {
    }

    @Override
    public void render(float delta) {
        delta = Math.min(delta, Constants.MAX_FRAME_DELTA); // MK7: un frame colgado no debe saltar el juego
        uiScale = MathUtils.clamp(screenHeight / 800f, 0.8f, 2.2f);
        Fonts.setUiScale(uiScale);
        pollNetIdentity();
        pollInput();
        handleGlobalKeys();
        boolean simRuns = updateNetwork(delta);

        if (!paused && simRuns) {
            sim.step(delta, inputLeft, inputRight);
            refreshRenderPositions();
            trailLeft.update(rPlayerL, sim.getPlayerLeft().body.getLinearVelocity().len());
            trailRight.update(rPlayerR, sim.getPlayerRight().body.getLinearVelocity().len());
            Vector2 bv = sim.getBall().body.getLinearVelocity();
            float bsp = bv.len();
            ballRollDeg -= (Math.abs(bv.x) >= Math.abs(bv.y) ? Math.signum(bv.x) : Math.signum(bv.y))
                    * bsp * delta / Constants.BALL_RADIUS * MathUtils.radiansToDegrees;
            if (unstuckToast > 0f) {
                unstuckToast -= delta;
            }
            if (sim.consumeUnstuckEvent()) {
                unstuckToast = 2.2f;
                if (!Settings.muted) {
                    sfxWhistle.play(0.6f);
                }
            }
            animTime += delta;
            updateSfxAndFeedback(delta);
            updateConfetti(delta);
            particles.update(delta);
            netRipple.update(delta);
        }
        sendSnapshotIfDue(delta);
        if (paused || !simRuns) {
            refreshRenderPositions();
        }
        if (escArmedTime > 0f) {
            escArmedTime -= delta;
        }

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        Gdx.gl.glViewport(0, 0, screenWidth, screenHeight);
        Gdx.gl.glClearColor(0.02f, 0.02f, 0.02f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        int hudHeight = Math.min(Math.round(Constants.HUD_HEIGHT_PX * uiScale), screenHeight / 4);
        int paneAreaHeight = Math.max(1, screenHeight - hudHeight);

        int halfWidth = screenWidth / 2;
        int rightWidth = screenWidth - halfWidth;

        boolean replaying = sim.getPhase() == MatchSimulation.Phase.REPLAY;
        Vector2 focusLeft = rPlayerL;
        Vector2 focusRight = rPlayerR;
        if (replaying) {
            Vector2 bp = rBall;
            if (!replayFocusInit) {
                replayFocus.set(bp);
                replayFocusInit = true;
            }
            replayFocus.lerp(bp, 1f - (float) Math.exp(-8f * delta));
            focusLeft = replayFocus;
            focusRight = replayFocus;
        } else {
            replayFocusInit = false;
        }
        if (mode.isOnline() || mode == GameMode.ONE_PLAYER || mode == GameMode.TWO_VS_TWO) {
            // MK5: un solo panel a pantalla completa con la vista propia (online: host = AZUL, cliente = ROJO;
            // 1 jugador: vos sos el AZUL y la IA es el rival). Solo el 2 jugadores local divide la pantalla.
            boolean isHost = mode != GameMode.ONLINE_CLIENT;
            OrthographicCamera cam = isHost ? camLeft : camRight;
            Vector2 selfFocus = isHost ? focusLeft : focusRight;
            boolean coop = mode == GameMode.TWO_VS_TWO;
            if (coop && !replaying) {
                // MK7 2v2: la cámara compartida apunta al punto medio de los dos compañeros.
                boolean outA = sim.getPlayerLeft().isSuspended(), outB = sim.getMateLeft().isSuspended();
                if (outA != outB) {
                    selfFocus = tmpA.set(outA ? rMateL : rPlayerL); // MK8: un expulsado no cuenta (su cuerpo está inactivo)
                } else {
                    selfFocus = tmpA.set(rPlayerL).add(rMateL).scl(0.5f);
                }
            }
            if (!replaying) {
                // La cámara sigue mayormente al jugador pero se inclina hacia la pelota (30 %; 45 % en 2v2), así no se pierde la jugada.
                focusBlend.set(selfFocus).lerp(rBall, coop ? Constants.TEAM_VIEW_BALL_BIAS : Constants.FULL_VIEW_BALL_BIAS);
                smoothInitFull = smoothTo(smoothFocusFull, focusBlend, smoothInitFull, delta);
                selfFocus = smoothFocusFull;
            }
            updateCamera(cam, selfFocus, screenWidth, paneAreaHeight, coop ? Constants.TEAM_VIEW_HEIGHT : Constants.FULL_VIEW_HEIGHT);
            String fullLabel = mode == GameMode.ONLINE_HOST ? "VOS - EQUIPO " + MatchSetup.leftTeam.name + " (HOST)"
                    : mode == GameMode.ONLINE_CLIENT ? "VOS - EQUIPO " + MatchSetup.rightTeam.name
                    : coop ? "JUGADOR 1 + JUGADOR 2 - EQUIPO " + MatchSetup.leftTeam.name + "  vs  IA"
                    : "VOS - EQUIPO " + MatchSetup.leftTeam.name + "  vs  IA";
            renderPane(0, 0, screenWidth, paneAreaHeight, cam, isHost, fullLabel, mode == GameMode.ONE_PLAYER || coop);
        } else {
            if (!replaying) {
                // MK7: cada panel se inclina hacia la pelota y se suaviza (antes seguía solo al jugador y la pelota se perdía).
                tmpA.set(focusLeft).lerp(rBall, Constants.SPLIT_VIEW_BALL_BIAS);
                tmpB.set(focusRight).lerp(rBall, Constants.SPLIT_VIEW_BALL_BIAS);
                smoothInitL = smoothTo(smoothFocusL, tmpA, smoothInitL, delta);
                smoothInitR = smoothTo(smoothFocusR, tmpB, smoothInitR, delta);
                focusLeft = smoothFocusL;
                focusRight = smoothFocusR;
            }
            updateCamera(camLeft, focusLeft, halfWidth, paneAreaHeight);
            updateCamera(camRight, focusRight, rightWidth, paneAreaHeight);

            // Panel izquierdo: vista del Jugador 1. Su rival se dibuja difuminado.
            renderPane(0, 0, halfWidth, paneAreaHeight, camLeft, true, "JUGADOR 1 - EQUIPO " + MatchSetup.leftTeam.name, false);

            // Panel derecho: vista del Jugador 2 (humano o IA de soporte, según el modo elegido en el lobby).
            renderPane(halfWidth, 0, rightWidth, paneAreaHeight, camRight, false, "JUGADOR 2 - EQUIPO " + MatchSetup.rightTeam.name, false);

            drawDivider(paneAreaHeight);
        }
        drawSharedHud(paneAreaHeight, hudHeight);
        drawMiniMap(paneAreaHeight);
        Fonts.setUiScale(1f); // los paneles centrales (red, pausa, final) tienen layout fijo pensado para escala 1
        if (mode.isOnline()) {
            drawNetStatus();
        }

        if (sim.getPhase() == MatchSimulation.Phase.FULL_TIME) {
            drawFullTimePanel();
        }

        if (paused) {
            drawPauseOverlay();
        }
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    /** MK7: copia las posiciones interpoladas (suaves a 60 fps) de la simulación a 30 Hz. */
    private void refreshRenderPositions() {
        sim.renderBall(rBall);
        sim.renderPlayerLeft(rPlayerL);
        sim.renderPlayerRight(rPlayerR);
        sim.renderKeeperLeft(rKeeperL);
        sim.renderKeeperRight(rKeeperR);
        if (sim.hasMates()) {
            sim.renderMateLeft(rMateL);
            sim.renderMateRight(rMateR);
        }
    }

    private Vector2 rpos(Object e) {
        if (e == sim.getBall()) return rBall;
        if (e == sim.getPlayerLeft()) return rPlayerL;
        if (e == sim.getPlayerRight()) return rPlayerR;
        if (e == sim.getKeeperLeft()) return rKeeperL;
        if (sim.hasMates() && e == sim.getMateLeft()) return rMateL;
        if (sim.hasMates() && e == sim.getMateRight()) return rMateR;
        return rKeeperR;
    }

    /** Suaviza el foco de cámara; si el salto es grande (saque, repetición) corta directo. Devuelve true (ya inicializado). */
    private boolean smoothTo(Vector2 state, Vector2 target, boolean init, float delta) {
        if (!init || state.dst2(target) > 100f) {
            state.set(target);
        } else {
            state.lerp(target, 1f - (float) Math.exp(-Constants.CAM_FOLLOW_RATE * delta));
        }
        return true;
    }

    private void handleGlobalKeys() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.F1)) {
            showDebug = !showDebug;
        }
        // Online no hay pausa ni reinicio: pausar al host congelaría al rival.
        if (Gdx.input.isKeyJustPressed(Input.Keys.P) && !mode.isOnline()) {
            paused = !paused;
            if (!paused) {
                // MK7 (fix): se descartan teclas "pegadas" acumuladas durante la pausa (un patadón al reanudar).
                inputLeft.clearTransient();
                inputRight.clearTransient();
            }
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            if (!mode.isOnline()) {
                // MK7 (fix): R está pegada a WASD y reiniciaba el partido sin querer. Ahora solo en pausa o al final.
                if (paused || sim.getPhase() == MatchSimulation.Phase.FULL_TIME) {
                    restartMatch();
                }
            } else if (mode == GameMode.ONLINE_HOST && netHost.hasClient()
                    && sim.getPhase() == MatchSimulation.Phase.FULL_TIME) {
                restartMatch(); // MK5: revancha online; el cliente recibe el partido nuevo por snapshots
            }
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.TAB) && !paused) {
            sim.skipReplay();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.M)) {
            Settings.muted = !Settings.muted;
            Settings.save();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            // MK7 (fix): ESC salía al instante y se perdía el partido. Ahora hay que apretarlo dos veces (o en pausa / al final).
            if (escArmedTime > 0f || paused || sim.getPhase() == MatchSimulation.Phase.FULL_TIME) {
                closeNetwork();
                game.setScreen(new LobbyScreen(game));
            } else {
                escArmedTime = 2.5f;
            }
        }
    }

    // ------------------------------------------------------------------
    // Online 1v1
    // ------------------------------------------------------------------

    /**
     * Atiende la red una vez por frame. Devuelve false si la simulación NO debe avanzar
     * (host esperando rival). El cliente nunca simula: MatchSimulation.step() lo ignora y solo aplica snapshots.
     */
    private boolean updateNetwork(float delta) {
        if (mode == GameMode.ONLINE_HOST) {
            netHost.poll();
            if (!netHost.hasClient()) {
                inputRight.moveX = 0f;
                inputRight.moveY = 0f;
                inputRight.sprint = false;
                inputRight.kickHeld = false;
                inputRight.clearTransient();
                return false; // sin rival la pelota no se mueve
            }
            hostHadClient = true;
            netHost.drainInto(inputRight);
            return true;
        }
        if (mode == GameMode.ONLINE_CLIENT) {
            netClient.poll();
            if (netClient.takeSnapshot(netSnap)) {
                netInterp.push(netSnap, System.nanoTime());
            }
            // MK5: se dibuja el estado de hace ~70 ms mezclando los dos snapshots que lo rodean (60 fps parejos).
            if (netInterp.sample(System.nanoTime(), netSnap)) {
                sim.applySnapshot(netSnap);
            }
            netClient.sendInput(inputRight);
            inputRight.clearTransient(); // los eventos se mandaron una vez; no se repiten
            return true; // sim.step() no hace nada en el cliente, pero rastros, sonido y confeti sí deben correr
        }
        return true;
    }

    /** HOST: manda el estado ~30 veces por segundo (por tiempo, no por tick: en la repetición el tick no avanza). */
    private void sendSnapshotIfDue(float delta) {
        if (mode != GameMode.ONLINE_HOST || !netHost.hasClient()) {
            return;
        }
        netSendAccum += delta;
        if (netSendAccum < Constants.SIM_STEP) {
            return;
        }
        netSendAccum = Math.min(netSendAccum - Constants.SIM_STEP, Constants.SIM_STEP);
        sim.fillSnapshot(netSnap);
        netSnap.tick = netSeq++; // el cliente descarta paquetes con número menor o igual al último
        netHost.sendSnapshot(netSnap);
    }

    private void closeNetwork() {
        if (netHost != null) netHost.close();
        if (netClient != null) netClient.close();
    }

    /** Cartel de estado de la conexión (esperando rival, conectando, conexión perdida). */
    private void drawNetStatus() {
        String title = null;
        String line = null;
        if (mode == GameMode.ONLINE_HOST && !netHost.hasClient()) {
            if (hostHadClient) {
                title = "EL RIVAL SE DESCONECTO";
                line = "Esperando que vuelva a entrar...  (" + netAddressText + ")";
            } else {
                title = "ESPERANDO AL RIVAL";
                line = "Que elija UNIRSE e ingrese:  " + netAddressText;
            }
        } else if (mode == GameMode.ONLINE_CLIENT) {
            NetClient.State st = netClient.getState();
            if (st == NetClient.State.CONNECTING) {
                title = "CONECTANDO...";
                line = netAddressText;
            } else if (st == NetClient.State.FAILED) {
                title = "NO SE PUDO CONECTAR";
                line = "Revisa la IP y que el host ya haya creado la partida";
            } else if (st == NetClient.State.DISCONNECTED) {
                title = "SE PERDIO LA CONEXION";
                line = "El host cerro la partida o dejo de responder";
            }
        }
        if (title == null) {
            drawPingBadge();
            return;
        }
        Gdx.gl.glViewport(0, 0, screenWidth, screenHeight);
        hudCam.setToOrtho(false, screenWidth, screenHeight);
        hudCam.update();
        shapes.setProjectionMatrix(hudCam.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.6f);
        shapes.rect(0, 0, screenWidth, screenHeight);
        shapes.end();

        batch.setProjectionMatrix(hudCam.combined);
        batch.begin();
        float cx = screenWidth / 2f;
        float y = screenHeight / 2f + 40f;
        drawCentered(title, cx, y, 2.4f, Color.WHITE);
        drawCentered(line, cx, y - 56f, 1.2f, new Color(0.9f, 0.9f, 0.6f, 1f));
        drawCentered("ESC  volver al menu", cx, y - 100f, 1.0f, new Color(0.8f, 0.8f, 0.82f, 1f));
        font.getData().setScale((1f) * Fonts.K);
        batch.end();
    }

    /** MK5: ping en la esquina inferior izquierda (verde < 60 ms, amarillo < 120, rojo más). */
    private void drawPingBadge() {
        int ping = mode == GameMode.ONLINE_CLIENT ? netClient.getPingMs() : netHost.getClientPingMs();
        if (ping < 0) {
            return;
        }
        Color c = ping < 60 ? new Color(0.45f, 0.95f, 0.5f, 1f)
                : ping < 120 ? new Color(0.98f, 0.85f, 0.3f, 1f)
                : new Color(0.95f, 0.35f, 0.3f, 1f);
        Gdx.gl.glViewport(0, 0, screenWidth, screenHeight);
        hudCam.setToOrtho(false, screenWidth, screenHeight);
        hudCam.update();
        shapes.setProjectionMatrix(hudCam.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.45f);
        shapes.rect(6f, 4f, 136f, 26f);
        shapes.setColor(c);
        shapes.circle(18f, 17f, 4f, 12);
        shapes.end();
        batch.setProjectionMatrix(hudCam.combined);
        batch.begin();
        font.getData().setScale((1.0f) * Fonts.K);
        font.setColor(c);
        font.draw(batch, "PING " + ping + " ms", 28f, 24f);
        font.setColor(Color.WHITE);
        batch.end();
    }

    private void restartMatch() {
        sim.dispose();
        sim = new MatchSimulation(mode);
        paused = false;
        goalLog.clear();
        saveFlashTime = 0f;
        clearConfetti();
        particles.clear();
        netRipple.clear();
        keeperLeftWasDiving = false;
        keeperRightWasDiving = false;
        trailLeft.clear();
        trailRight.clear();
    }

    private void pollInput() {
        inputLeft.moveX = 0;
        inputLeft.moveY = 0;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) inputLeft.moveX -= 1;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) inputLeft.moveX += 1;
        if (Gdx.input.isKeyPressed(Input.Keys.W)) inputLeft.moveY += 1;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) inputLeft.moveY -= 1;
        inputLeft.normalizeMove();
        inputLeft.sprint = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT);
        // MK4 (fix): los eventos de un solo disparo son "pegajosos" (OR): el juego renderiza a 60 fps pero
        // simula a 30 Hz, así que en la mitad de los frames no corre ningún tick. Si el evento se pisaba
        // en cada frame, la mitad de los planchazos/pateos se perdía. MatchSimulation los limpia al usarlos.
        if (Gdx.input.isKeyJustPressed(Input.Keys.CONTROL_LEFT)) inputLeft.slidePressed = true;
        boolean spacePressed = Gdx.input.isKeyPressed(Input.Keys.SPACE);
        if (kickHeldPrevLeft && !spacePressed) inputLeft.kickReleased = true;
        inputLeft.kickHeld = spacePressed;
        kickHeldPrevLeft = spacePressed;

        if (mode == GameMode.ONLINE_CLIENT) {
            // El cliente controla al equipo ROJO con el mismo esquema (WASD / Shift / Ctrl / Espacio).
            inputRight.moveX = inputLeft.moveX;
            inputRight.moveY = inputLeft.moveY;
            inputRight.sprint = inputLeft.sprint;
            inputRight.kickHeld = inputLeft.kickHeld;
            inputRight.kickReleased |= inputLeft.kickReleased;
            inputRight.slidePressed |= inputLeft.slidePressed;
            inputLeft.clearTransient();
        }

        if (mode == GameMode.TWO_PLAYERS || mode == GameMode.TWO_VS_TWO) {
            // En 2v2 este segundo teclado (flechas) mueve al COMPAÑERO de equipo; en 2 jugadores, al rival.
            inputRight.moveX = 0;
            inputRight.moveY = 0;
            if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) inputRight.moveX -= 1;
            if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) inputRight.moveX += 1;
            if (Gdx.input.isKeyPressed(Input.Keys.UP)) inputRight.moveY += 1;
            if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) inputRight.moveY -= 1;
            inputRight.normalizeMove();
            inputRight.sprint = Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
            if (Gdx.input.isKeyJustPressed(Input.Keys.SHIFT_RIGHT)) inputRight.slidePressed = true;
            boolean enterPressed = Gdx.input.isKeyPressed(Input.Keys.ENTER);
            if (kickHeldPrevRight && !enterPressed) inputRight.kickReleased = true;
            inputRight.kickHeld = enterPressed;
            kickHeldPrevRight = enterPressed;
        }
        // En modo 1 jugador, inputRight ni se lee: MatchSimulation ignora este
        // parámetro y usa su IA de soporte interna para el Jugador 2.
    }

    // ------------------------------------------------------------------
    // Sonido y feedback (screen shake) — reacciona a eventos "de un solo
    // disparo" que expone MatchSimulation, sin acoplar la física al audio.
    // ------------------------------------------------------------------

    private void updateSfxAndFeedback(float delta) {
        Float kickPower = sim.consumeKickSfxEvent();
        if (kickPower != null) {
            playSafe(kickPower > 0.55f ? sfxKickHard : sfxKickSoft, 0.55f);
            Vector2 kv = sim.getBall().body.getLinearVelocity();
            float kl = kv.len();
            if (kl > 0.01f) {
                particles.kick(rBall.x, rBall.y, kv.x / kl, kv.y / kl, kickPower);
            }
            if (kickPower > 0.75f) {
                triggerShake(0.15f, 0.06f);
            }
        }
        if (sim.consumeSlideTackleSfxEvent()) {
            playSafe(sfxThud, 0.6f);
            triggerShake(0.12f, 0.05f);
            particles.dust(rBall.x, rBall.y, 0.8f);
        }
        if (sim.consumeAdvantageSfxEvent()) {
            playSafe(sfxAdvantage, 0.6f); // MK8: ventaja: aviso suave, sin silbato
        }
        if (sim.consumeFoulSfxEvent()) {
            // MK8: falta: silbato + golpe; con tarjeta roja, además un sacudón.
            playSafe(sfxWhistle, 0.75f);
            playSafe(sfxThud, 0.45f);
            triggerShake(sim.getLastFoulCard() == FoulRules.Card.RED ? 0.3f : 0.15f, 0.06f);
        }

        if (sim.consumeSaveEvent()) {
            playSafe(sfxSave, 0.7f);
            triggerShake(0.14f, 0.05f);
            particles.save(rBall.x, rBall.y);
            saveFlashTime = 1.3f;
        }
        boolean dl = sim.getKeeperLeft().isDiving();
        boolean dr = sim.getKeeperRight().isDiving();
        if ((dl && !keeperLeftWasDiving) || (dr && !keeperRightWasDiving)) {
            playSafe(sfxDive, 0.5f);
        }
        keeperLeftWasDiving = dl;
        keeperRightWasDiving = dr;
        if (saveFlashTime > 0f) {
            saveFlashTime = Math.max(0f, saveFlashTime - delta);
        }

        MatchSimulation.Phase phase = sim.getPhase();
        if (phase != lastPhase) {
            if (phase == MatchSimulation.Phase.KICKOFF) {
                playSafe(sfxWhistle, 0.5f);
            } else if (phase == MatchSimulation.Phase.GOAL_CELEBRATION) {
                playSafe(sfxGoal, 0.6f);
                playSafe(sfxCheer, 0.8f);
                triggerShake(0.35f, 0.12f);
                spawnConfetti(sim.isLastGoalByLeft() ? MatchSetup.leftColor() : MatchSetup.rightColor());
                netRipple.start(sim.isLastGoalByLeft()); // el equipo izquierdo ataca hacia X+
            } else if (phase == MatchSimulation.Phase.FULL_TIME) {
                playSafe(sfxFullTime, 0.5f);
            } else if (phase == MatchSimulation.Phase.PLAYING && lastPhase == MatchSimulation.Phase.SET_PIECE) {
                playSafe(sfxWhistle, 0.45f); // MK8: silbato corto para ejecutar el tiro
            }
            lastPhase = phase;
        }

        if (shakeTime > 0f) {
            shakeTime = Math.max(0f, shakeTime - delta);
        }
    }

    private void playSafe(Sound sound, float volume) {
        float v = Settings.sfx(volume);
        if (sound != null && v > 0f) {
            sound.play(v);
        }
    }

    // ------------------------------------------------------------------
    // Confeti (MK4): partículas 2D dibujadas sobre cada panel al hacer un gol
    // ------------------------------------------------------------------

    private void spawnConfetti(Color teamColor) {
        Color[] palette = {teamColor, new Color(1f, 0.85f, 0.2f, 1f), Color.WHITE, new Color(teamColor).lerp(Color.WHITE, 0.5f)};
        for (int i = 0; i < CONFETTI_MAX; i++) {
            cfX[i] = MathUtils.random(0.05f, 0.95f);
            cfY[i] = MathUtils.random(1.0f, 1.35f);
            cfVX[i] = MathUtils.random(-0.06f, 0.06f);
            cfVY[i] = MathUtils.random(-0.42f, -0.18f);
            cfRot[i] = MathUtils.random(0f, 360f);
            cfSpin[i] = MathUtils.random(-360f, 360f);
            cfSize[i] = MathUtils.random(4f, 8f);
            cfLife[i] = MathUtils.random(2.2f, 3.6f);
            cfColor[i] = palette[MathUtils.random(palette.length - 1)];
        }
    }

    private void clearConfetti() {
        for (int i = 0; i < CONFETTI_MAX; i++) {
            cfLife[i] = 0f;
        }
    }

    private void updateConfetti(float delta) {
        for (int i = 0; i < CONFETTI_MAX; i++) {
            if (cfLife[i] <= 0f) {
                continue;
            }
            cfLife[i] -= delta;
            cfX[i] += (cfVX[i] + MathUtils.sin(animTime * 3f + i) * 0.03f) * delta;
            cfY[i] += cfVY[i] * delta;
            cfRot[i] += cfSpin[i] * delta;
        }
    }

    private void drawConfetti(int paneWidthPx, int paneHeightPx) {
        shapes.setProjectionMatrix(hudCam.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < CONFETTI_MAX; i++) {
            if (cfLife[i] <= 0f || cfColor[i] == null) {
                continue;
            }
            float a = Math.min(1f, cfLife[i]);
            shapes.setColor(cfColor[i].r, cfColor[i].g, cfColor[i].b, a);
            float px = cfX[i] * paneWidthPx;
            float py = cfY[i] * paneHeightPx;
            shapes.rect(px, py, cfSize[i] / 2f, cfSize[i] / 2f, cfSize[i], cfSize[i] * 0.6f, 1f, 1f, cfRot[i]);
        }
        shapes.end();
    }

    private void triggerShake(float duration, float magnitude) {
        shakeTime = Math.max(shakeTime, duration);
        shakeMagnitude = Math.max(shakeMagnitude, magnitude);
    }

    // ------------------------------------------------------------------
    // Cámaras (una por jugador humano, sigue su propia posición)
    // ------------------------------------------------------------------

    private void updateCamera(OrthographicCamera cam, Vector2 focus, int paneWidthPx, int paneHeightPx) {
        updateCamera(cam, focus, paneWidthPx, paneHeightPx, Constants.SPLIT_VIEW_HEIGHT);
    }

    private void updateCamera(OrthographicCamera cam, Vector2 focus, int paneWidthPx, int paneHeightPx, float viewHeight) {
        float aspect = paneHeightPx == 0 ? 1f : (float) paneWidthPx / (float) paneHeightPx;
        cam.viewportHeight = viewHeight;
        cam.viewportWidth = viewHeight * aspect;

        float halfFieldW = Constants.FIELD_WIDTH / 2f + Constants.FIELD_MARGIN;
        float halfFieldH = Constants.FIELD_HEIGHT / 2f + Constants.FIELD_MARGIN;

        float camX = (cam.viewportWidth >= halfFieldW * 2f)
                ? 0f
                : MathUtils.clamp(focus.x, -halfFieldW + cam.viewportWidth / 2f, halfFieldW - cam.viewportWidth / 2f);
        float camY = (cam.viewportHeight >= halfFieldH * 2f)
                ? 0f
                : MathUtils.clamp(focus.y, -halfFieldH + cam.viewportHeight / 2f, halfFieldH - cam.viewportHeight / 2f);

        // Screen shake: sacude ambos paneles brevemente en golpes fuertes/goles,
        // con la intensidad decayendo a lo largo de shakeTime.
        if (shakeTime > 0f) {
            float falloff = shakeTime / 0.35f;
            camX += MathUtils.random(-1f, 1f) * shakeMagnitude * falloff;
            camY += MathUtils.random(-1f, 1f) * shakeMagnitude * falloff;
        }

        cam.position.set(camX, camY, 0);
        cam.update();
    }

    // ------------------------------------------------------------------
    // Render de cada mitad de pantalla
    // ------------------------------------------------------------------

    private void renderPane(int x, int y, int w, int h, OrthographicCamera cam, boolean leftIsSelf, String cornerLabel,
                            boolean sharpRival) {
        Gdx.gl.glViewport(x, y, w, h);

        PlayerEntity self = leftIsSelf ? sim.getPlayerLeft() : sim.getPlayerRight();
        PlayerEntity rival = leftIsSelf ? sim.getPlayerRight() : sim.getPlayerLeft();
        TextureRegion selfRegion = leftIsSelf ? regionPlayerLeft : regionPlayerRight;
        TextureRegion rivalRegion = leftIsSelf ? regionPlayerRight : regionPlayerLeft;
        MotionTrail selfTrail = leftIsSelf ? trailLeft : trailRight;
        MotionTrail rivalTrail = leftIsSelf ? trailRight : trailLeft;
        String selfName = leftIsSelf ? MatchSetup.leftName : MatchSetup.rightName;
        String rivalName = leftIsSelf ? MatchSetup.rightName : MatchSetup.leftName;

        overlayCam = cam;
        overlaySelfIsLeft = leftIsSelf;
        drawField(cam);
        drawGroundShadow(cam, rKeeperL);
        drawGroundShadow(cam, rKeeperR);
        if (!self.isSuspended()) drawGroundShadow(cam, rpos(self));
        if (!rival.isSuspended()) drawGroundShadow(cam, rpos(rival));
        if (sim.hasMates()) {
            if (!sim.getMateLeft().isSuspended()) drawGroundShadow(cam, rMateL);
            if (!sim.getMateRight().isSuspended()) drawGroundShadow(cam, rMateR);
        }

        drawGoalkeeper(cam, sim.getKeeperLeft(), regionKeeperLeft, Constants.KEEPER_LEFT_NAME);
        drawGoalkeeper(cam, sim.getKeeperRight(), regionKeeperRight, Constants.KEEPER_RIGHT_NAME);

        drawSetPieceMarker(cam);
        drawBall(cam);
        particles.render(shapes(cam));

        // El rival humano se dibuja primero (con su rastro) para quedar "detrás" visualmente.
        rivalTrail.render(shapes(cam), rival.teamColor, Constants.PLAYER_RADIUS * 0.9f);
        if (sharpRival) {
            drawSharpRival(cam, rival, rivalRegion, rivalName); // MK5: contra la IA no hace falta difuminarla
        } else {
            drawBlurredRival(cam, rival, rivalRegion, rivalName);
        }

        if (sim.hasMates()) {
            // MK7 2v2: segundo rival IA (nítido) y compañero humano (con su propio anillo y barra de potencia)
            drawSharpRival(cam, sim.getMateRight(), regionPlayerRight, MatchSetup.rightMateName);
            drawSelfPlayer(cam, sim.getMateLeft(), regionPlayerLeft, MatchSetup.leftMateName);
        }
        selfTrail.render(shapes(cam), self.teamColor, Constants.PLAYER_RADIUS * 0.9f);
        drawSelfPlayer(cam, self, selfRegion, selfName);

        if (showDebug) {
            debugRenderer.render(sim.getWorld(), cam.combined);
        }

        drawPaneOverlay(w, h, cornerLabel);
    }

    private ShapeRenderer shapes(OrthographicCamera cam) {
        shapes.setProjectionMatrix(cam.combined);
        return shapes;
    }

    // ------------------------------------------------------------------
    // Cancha: pasto a rayas, líneas, áreas, arcos con red y tribuna
    // ------------------------------------------------------------------

    private void drawField(OrthographicCamera cam) {
        Field field = sim.getField();
        float halfW = field.width / 2f;
        float halfH = field.height / 2f;
        float gx = halfW + Constants.FIELD_MARGIN;
        float gy = halfH + Constants.FIELD_MARGIN;

        // MK7: una sola textura con pasto, líneas (antialiasadas), arcos con red y carteles.
        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        batch.setColor(Color.WHITE);
        batch.draw(texGround, -gx, -gy, gx * 2f, gy * 2f);
        batch.end();

        // Tribunas detrás de los carteles (la de abajo va espejada: el fondo siempre queda lejos de la cancha).
        float bandH = PixelArtFactory.CROWD_BAND_HEIGHT;
        drawCrowdBand(cam, -gx, halfH + 0.60f, gx * 2f, bandH, false);
        drawCrowdBand(cam, -gx, -halfH - 0.60f - bandH, gx * 2f, bandH, true);
        netRipple.render(shapes(cam), halfW);
    }

    private void drawCrowdBand(OrthographicCamera cam, float x, float y, float worldWidth, float worldHeight, boolean flipY) {
        if (worldWidth <= 0f || worldHeight <= 0f) {
            return;
        }
        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        batch.setColor(Color.WHITE);
        if (flipY) {
            batch.draw(regionCrowd, x, y + worldHeight, worldWidth, -worldHeight);
        } else {
            batch.draw(regionCrowd, x, y, worldWidth, worldHeight);
        }
        batch.end();
    }

    private void drawGroundShadow(OrthographicCamera cam, Vector2 pos) {
        // MK7: sombra suave (textura con degradé) en vez de una elipse plana. Se centra en los pies del sprite.
        float w = Constants.PLAYER_RADIUS * 3.0f;
        float h = w * 0.42f;
        float feetY = pos.y - Constants.SPRITE_HEIGHT_METERS * 0.18f - 0.02f;
        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        batch.setColor(Color.WHITE);
        batch.draw(texShadow, pos.x - w / 2f, feetY - h / 2f, w, h);
        batch.end();
    }

    private void drawBall(OrthographicCamera cam) {
        Ball ball = sim.getBall();
        Vector2 pos = rBall;
        float size = Constants.BALL_VISUAL_DIAMETER; // MK7: se dibuja ~1,8x más grande que su física para poder seguirla
        float angleDeg = ballRollDeg + ball.body.getAngle() * MathUtils.radiansToDegrees;
        Vector2 vel = ball.body.getLinearVelocity();
        float speed = vel.len();

        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        // Halo claro detrás: la pelota resalta sobre el pasto y las líneas blancas.
        float haloS = size * 2.1f;
        batch.setColor(1f, 1f, 1f, 0.55f);
        batch.draw(texHalo, pos.x - haloS / 2f, pos.y - haloS / 2f, haloS, haloS);
        // Sombra suave corrida hacia abajo-derecha
        float sw = size * 1.5f;
        batch.setColor(1f, 1f, 1f, 0.8f);
        batch.draw(texShadow, pos.x - sw / 2f + size * 0.12f, pos.y - sw * 0.42f / 2f - size * 0.22f, sw, sw * 0.62f);
        // Estela de la pelota rápida (copias que se desvanecen detrás)
        if (speed > 9f) {
            for (int k = 4; k >= 1; k--) {
                float a = 0.30f * (1f - k / 5f);
                float sz = size * (1f - 0.08f * k);
                float ox = pos.x - vel.x * 0.014f * k;
                float oy = pos.y - vel.y * 0.014f * k;
                batch.setColor(1f, 1f, 1f, a);
                batch.draw(regionBall, ox - sz / 2f, oy - sz / 2f, sz / 2f, sz / 2f, sz, sz, 1f, 1f, angleDeg);
            }
        }
        batch.setColor(Color.WHITE);
        batch.draw(regionBall, pos.x - size / 2f, pos.y - size / 2f, size / 2f, size / 2f, size, size, 1f, 1f, angleDeg);
        batch.end();
    }

    /** Arquero 100% IA: siempre nítido (no es "el rival humano", es parte fija del campo). Nunca se tira al piso. */
    private void drawGoalkeeper(OrthographicCamera cam, GoalkeeperEntity keeper, TextureRegion region, String name) {
        float dive = Math.abs(keeper.getVisualDive());
        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        batch.setColor(Color.WHITE);
        // Estirada (MK4): el arquero "salta" y se estira en la dirección del movimiento.
        drawSpriteInBatch(region, rpos(keeper), 0f, 1f, false,
                Math.abs(keeper.body.getLinearVelocity().y), dive * 0.35f, 1f + dive * 0.32f);
        batch.end();
        drawNameTag(cam, rpos(keeper), name, keeper.color);
    }

    /**
     * Dibuja al jugador rival "difuminado": varias copias translúcidas de tamaño
     * creciente, simulando un desenfoque barato sin necesidad de un pipeline de
     * shaders/FrameBuffer (fuera del alcance actual), ahora aplicado sobre el
     * sprite pixel-art en vez de un círculo liso.
     */
    /** MK5: rival nítido (sin difuminado ni anillo), para el modo 1 jugador a pantalla completa. */
    private void drawSharpRival(OrthographicCamera cam, PlayerEntity rival, TextureRegion region, String name) {
        if (rival.isSuspended()) {
            return; // MK8: expulsado
        }
        Vector2 pos = rpos(rival);
        if (rival.isSliding()) {
            drawSlideDust(cam, pos);
        }
        drawCharacterSprite(cam, region, pos, rival.body.getLinearVelocity().x, 1f, rival.isSliding(),
                rival.body.getLinearVelocity().len());
        drawNameTag(cam, pos, name, rival.teamColor);
    }

    private void drawBlurredRival(OrthographicCamera cam, PlayerEntity rival, TextureRegion region, String name) {
        if (rival.isSuspended()) {
            return; // MK8: expulsado
        }
        Vector2 pos = rpos(rival);
        float vx = rival.body.getLinearVelocity().x;
        boolean sliding = rival.isSliding();
        int layers = 5;

        if (sliding) {
            drawSlideDust(cam, pos);
        }

        if (blurShader != null) {
            drawShaderBlurredRival(cam, rival, region == regionPlayerRight ? blurRegionRight : blurRegionLeft);
            drawNameTag(cam, pos, name, new Color(rival.teamColor.r, rival.teamColor.g, rival.teamColor.b, 0.65f));
            return;
        }

        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        float speed = rival.body.getLinearVelocity().len();
        for (int i = layers; i >= 1; i--) {
            float t = i / (float) layers;
            float alpha = Constants.RIVAL_BLUR_ALPHA * (1f - t * 0.7f);
            float scale = 1f + t * 0.5f;
            batch.setColor(1f, 1f, 1f, alpha);
            drawSpriteInBatch(region, pos, vx, scale, sliding, speed);
        }
        batch.setColor(1f, 1f, 1f, Constants.RIVAL_BLUR_ALPHA + 0.15f);
        drawSpriteInBatch(region, pos, vx, 1f, sliding, speed);
        batch.end();

        drawNameTag(cam, pos, name, new Color(rival.teamColor.r, rival.teamColor.g, rival.teamColor.b, 0.65f));
    }

    /**
     * MK4: desenfoque REAL por shader (gaussiano 5x5) sobre el sprite del rival. Se dibuja el sprite
     * con UVs extendidas (un margen transparente alrededor) para que el difuminado no se corte
     * en el borde del sprite. El radio crece un poco con la velocidad (efecto de movimiento).
     */
    private void drawShaderBlurredRival(OrthographicCamera cam, PlayerEntity rival, TextureRegion padded) {
        Vector2 pos = rpos(rival);
        Vector2 vel = rival.body.getLinearVelocity();
        boolean sliding = rival.isSliding();
        float speed = vel.len();

        float spriteH = Constants.SPRITE_HEIGHT_METERS;
        float aspect = (float) padded.getRegionHeight() / (float) padded.getRegionWidth();
        float spriteW = spriteH / aspect;
        float padW = spriteW * (1f + 2f * BLUR_PAD);
        float padH = spriteH * (1f + 2f * BLUR_PAD);
        float pivotX = padW / 2f;
        float pivotY = spriteH * BLUR_PAD + spriteH * 0.18f;
        float facing = vel.x < -0.05f ? -1f : 1f;
        float rotation = sliding ? -62f * facing : 0f;

        float bobY = 0f;
        if (!sliding && speed > 0.3f) {
            float strideHz = 2.2f + speed * 0.35f;
            bobY = Math.abs(MathUtils.sin(animTime * strideHz * MathUtils.PI2)) * 0.06f;
        }

        batch.setProjectionMatrix(cam.combined);
        batch.setShader(blurShader);
        batch.begin();
        blurShader.setUniformf("u_texel", 1f / padded.getTexture().getWidth(), 1f / padded.getTexture().getHeight());
        blurShader.setUniformf("u_radius", (1.4f + Math.min(speed, 10f) * 0.12f) * PixelArtFactory.PERSON_TEXEL_SCALE);
        batch.setColor(1f, 1f, 1f, 0.85f);
        batch.draw(padded,
                pos.x - pivotX, pos.y - pivotY + bobY,
                pivotX, pivotY,
                padW, padH,
                facing, 1f,
                rotation);
        batch.end();
        batch.setShader(null);
    }

    private void drawSelfPlayer(OrthographicCamera cam, PlayerEntity self, TextureRegion region, String name) {
        if (self.isSuspended()) {
            return; // MK8: expulsado (el cuerpo está desactivado; el HUD muestra cuánto falta para que vuelva)
        }
        Vector2 pos = rpos(self);
        float vx = self.body.getLinearVelocity().x;
        boolean sliding = self.isSliding();

        if (sliding) {
            drawSlideDust(cam, pos);
        }

        // Anillo blanco bajo los pies: remarca cuál es el personaje propio.
        // MK7: anillo suave (textura) en lugar de un círculo de 1 px.
        float ringW = Constants.PLAYER_RADIUS * 3.2f;
        float ringH = ringW * 0.5f;
        float feetY = pos.y - Constants.SPRITE_HEIGHT_METERS * 0.18f - 0.02f;
        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        // MK7: el anillo se pone amarillo cuando tu jugador tiene la pelota al alcance (se ve de un vistazo quién la tiene).
        boolean isMate = sim.hasMates() && self == sim.getMateLeft();
        if (self.isNear(sim.getBall()) && sim.getPhase() == MatchSimulation.Phase.PLAYING) {
            batch.setColor(1f, 0.88f, 0.25f, 1f);
        } else if (isMate) {
            batch.setColor(0.45f, 0.92f, 1f, 0.95f); // 2v2: el compañero (J2) lleva el anillo celeste, J1 blanco
        } else {
            batch.setColor(1f, 1f, 1f, 0.95f);
        }
        batch.draw(texRing, pos.x - ringW / 2f, feetY - ringH / 2f, ringW, ringH);
        batch.setColor(Color.WHITE);
        batch.end();

        drawCharacterSprite(cam, region, pos, vx, 1f, sliding, self.body.getLinearVelocity().len());
        drawNameTag(cam, pos, name, self.teamColor);

        // MK7: durante la cuenta regresiva del saque, una flecha que rebota marca cuál es tu jugador.
        // MK8: en un tiro libre / penal la flecha marca solo a quien lo cobra.
        if (sim.getPhase() == MatchSimulation.Phase.KICKOFF
                || (sim.getPhase() == MatchSimulation.Phase.SET_PIECE && sim.getSetPieceTakerIdx() == idxOf(self))) {
            float bob = 0.18f * (float) Math.sin(animTime * 7f);
            float ay = pos.y + Constants.SPRITE_HEIGHT_METERS * 0.85f + 0.95f + bob;
            shapes(cam);
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(0f, 0f, 0f, 0.6f);
            shapes.triangle(pos.x - 0.46f, ay + 0.04f, pos.x + 0.46f, ay + 0.04f, pos.x, ay - 0.56f);
            if (isMate) {
                shapes.setColor(0.45f, 0.92f, 1f, 1f);
            } else {
                shapes.setColor(1f, 0.88f, 0.25f, 1f);
            }
            shapes.triangle(pos.x - 0.38f, ay + 0.1f, pos.x + 0.38f, ay + 0.1f, pos.x, ay - 0.46f);
            shapes.end();
        }

        // MK6: barrita fina de recarga del planchazo (en el cliente online no se sincroniza, así que ahí no se muestra).
        float cd = self.getSlideCooldownFraction();
        if (cd > 0f && mode != GameMode.ONLINE_CLIENT) {
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(0.1f, 0.1f, 0.1f, 0.5f);
            shapes.rect(pos.x - 0.45f, pos.y - 1.0f, 0.9f, 0.1f);
            shapes.setColor(0.9f, 0.45f, 0.3f, 1f);
            shapes.rect(pos.x - 0.45f, pos.y - 1.0f, 0.9f * (1f - cd), 0.1f);
            shapes.end();
        }

        float power = self.getKickPower();
        if (power > 0f) {
            shapes(cam);
            // Barra más grande, con color según la potencia (amarillo -> rojo).
            float bw = 1.7f;
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(0.05f, 0.05f, 0.05f, 0.75f);
            shapes.rect(pos.x - bw / 2f - 0.04f, pos.y + 1.03f, bw + 0.08f, 0.30f);
            shapes.setColor(1f, 0.85f - 0.55f * power, 0.1f, 1f);
            shapes.rect(pos.x - bw / 2f, pos.y + 1.07f, bw * power, 0.22f);
            shapes.end();

            // MK7: línea de puntería. Muestra hacia dónde saldrá la pelota (misma cuenta que usa la simulación,
            // incluida la corrección por la dirección en que te movés).
            if (!sliding && self.isNear(sim.getBall())) {
                boolean isLeft = self == sim.getPlayerLeft() || isMate;
                PlayerInput in = self == sim.getPlayerLeft() ? inputLeft : inputRight; // el compañero se maneja con inputRight
                Vector2 dir = MatchSimulation.kickDirection(pos, rBall, in, isLeft ? 0 : 1, new Vector2());
                float len = 2.5f + 9f * power;
                Gdx.gl.glEnable(GL20.GL_BLEND);
                shapes.begin(ShapeRenderer.ShapeType.Filled);
                for (float d = 0.6f; d < len; d += 0.55f) {
                    float fade = 1f - d / (len + 0.5f);
                    shapes.setColor(1f, 0.95f - 0.6f * power, 0.2f, 0.25f + 0.7f * fade);
                    shapes.circle(rBall.x + dir.x * d, rBall.y + dir.y * d, 0.10f + 0.06f * fade, 8);
                }
                shapes.end();
            }
        }
    }

    /** Nube de polvo simple detrás de un jugador en pleno planchazo. */
    private void drawSlideDust(OrthographicCamera cam, Vector2 pos) {
        shapes(cam);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.85f, 0.82f, 0.72f, 0.4f);
        shapes.ellipse(pos.x - Constants.PLAYER_RADIUS * 1.5f, pos.y - Constants.PLAYER_RADIUS * 1.05f,
                Constants.PLAYER_RADIUS * 3f, Constants.PLAYER_RADIUS * 1.3f);
        shapes.setColor(0.85f, 0.82f, 0.72f, 0.22f);
        shapes.ellipse(pos.x - Constants.PLAYER_RADIUS * 2.1f, pos.y - Constants.PLAYER_RADIUS * 0.95f,
                Constants.PLAYER_RADIUS * 2.2f, Constants.PLAYER_RADIUS * 1f);
        shapes.end();
    }

    private void drawCharacterSprite(OrthographicCamera cam, TextureRegion region, Vector2 pos, float vx, float scale, boolean sliding, float moveSpeed) {
        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        batch.setColor(Color.WHITE);
        drawSpriteInBatch(region, pos, vx, scale, sliding, moveSpeed);
        batch.end();
    }

    /** Debe llamarse entre {@code batch.begin()}/{@code batch.end()} ya abiertos por quien invoque. */
    private void drawSpriteInBatch(TextureRegion region, Vector2 pos, float vx, float scale, boolean sliding) {
        drawSpriteInBatch(region, pos, vx, scale, sliding, 0f);
    }

    /**
     * @param moveSpeed magnitud de la velocidad actual (m/s), usada para animar un
     *                  "bobbing" de carrera (sube y baja + ligero squash) proporcional
     *                  al ritmo de zancada. Reemplaza el sprite estático de la Etapa 1
     *                  mejorada sin necesitar frames de animación adicionales.
     */
    private void drawSpriteInBatch(TextureRegion region, Vector2 pos, float vx, float scale, boolean sliding, float moveSpeed) {
        drawSpriteInBatch(region, pos, vx, scale, sliding, moveSpeed, 0f, 1f);
    }

    /** @param lift elevación extra (m) y @param stretchY estiramiento vertical, usados por la estirada del arquero. */
    private void drawSpriteInBatch(TextureRegion region, Vector2 pos, float vx, float scale, boolean sliding,
                                   float moveSpeed, float lift, float stretchY) {
        float spriteHeight = Constants.SPRITE_HEIGHT_METERS * scale;
        float aspect = (float) region.getRegionHeight() / (float) region.getRegionWidth();
        float spriteWidth = spriteHeight / aspect;
        float originX = spriteWidth / 2f;
        float originY = spriteHeight * 0.18f;
        float facing = vx < -0.05f ? -1f : 1f;

        // Mientras se desliza, se rota el sprite para simular al jugador tirado
        // "planchando" en el piso, en vez de mostrarlo corriendo de pie.
        float rotation = sliding ? -62f * facing : 0f;

        float bobY = 0f;
        float squash = 1f;
        if (!sliding && moveSpeed > 0.3f) {
            float strideHz = 2.2f + moveSpeed * 0.35f; // cadencia de zancada según velocidad
            float phase = animTime * strideHz * MathUtils.PI2;
            bobY = Math.abs(MathUtils.sin(phase)) * 0.06f;
            squash = 1f - Math.abs(MathUtils.sin(phase)) * 0.04f;
        }

        batch.draw(region,
                pos.x - originX, pos.y - originY + bobY + lift,
                originX, originY,
                spriteWidth, spriteHeight * squash * stretchY,
                facing, 1f,
                rotation);
    }

    private void drawNameTag(OrthographicCamera cam, Vector2 pos, String name, Color color) {
        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        font.getData().setScale((0.024f) * Fonts.baseK); // en el mundo: no escala con la UI
        GlyphLayout layout = new GlyphLayout(font, name);
        float textY = pos.y + Constants.SPRITE_HEIGHT_METERS * 0.85f + layout.height;

        font.setColor(0f, 0f, 0f, 0.85f);
        font.draw(batch, layout, pos.x - layout.width / 2f + 0.02f, textY - 0.02f);
        font.setColor(color);
        font.draw(batch, layout, pos.x - layout.width / 2f, textY);
        font.getData().setScale((1f) * Fonts.K);
        batch.end();
    }

    // ------------------------------------------------------------------
    // Overlays por panel (cuenta regresiva, gol, entretiempo, final)
    // ------------------------------------------------------------------

    private void drawPaneOverlay(int paneWidthPx, int paneHeightPx, String cornerLabel) {
        hudCam.setToOrtho(false, paneWidthPx, paneHeightPx);
        hudCam.update();
        batch.setProjectionMatrix(hudCam.combined);

        MatchSimulation.Phase curPhase = sim.getPhase();
        if (curPhase == MatchSimulation.Phase.REPLAY) {
            drawReplayBars(paneWidthPx, paneHeightPx);
        }
        drawConfetti(paneWidthPx, paneHeightPx);
        drawBallPointer(paneWidthPx, paneHeightPx);

        batch.begin();
        font.getData().setScale((0.9f) * Fonts.K);
        font.setColor(1f, 1f, 1f, 0.7f);
        font.draw(batch, cornerLabel, 10, 22);
        font.getData().setScale((1f) * Fonts.K);

        if (saveFlashTime > 0f && curPhase == MatchSimulation.Phase.PLAYING) {
            float a = Math.min(1f, saveFlashTime * 2f);
            font.getData().setScale((1.8f) * Fonts.K);
            GlyphLayout sl = new GlyphLayout(font, "¡ATAJADA!");
            font.setColor(0f, 0f, 0f, 0.55f * a);
            font.draw(batch, sl, paneWidthPx / 2f - sl.width / 2f + 2f, paneHeightPx * 0.78f - 2f);
            font.setColor(0.35f, 0.9f, 1f, a);
            font.draw(batch, sl, paneWidthPx / 2f - sl.width / 2f, paneHeightPx * 0.78f);
            font.getData().setScale((1f) * Fonts.K);
        }

        if (sim.isAdvantageActive() && curPhase == MatchSimulation.Phase.PLAYING) {
            // MK8: ventaja: la falta ya está anotada pero sigue el juego
            boolean on = ((int) (animTime * 4f)) % 2 == 0;
            int advTeam = sim.getAdvantageTeam();
            Color tc = advTeam == 0 ? new Color(0.45f, 0.7f, 1f, 1f) : new Color(1f, 0.5f, 0.45f, 1f);
            tc.a = on ? 1f : 0.75f;
            drawCentered("¡VENTAJA!", paneWidthPx / 2f + 2f, paneHeightPx * 0.70f - 2f, 1.7f, new Color(0f, 0f, 0f, 0.55f));
            drawCentered("¡VENTAJA!", paneWidthPx / 2f, paneHeightPx * 0.70f, 1.7f, tc);
            String sub = sim.getLastFoulCard() == FoulRules.Card.YELLOW ? "SIGUE EL JUEGO - TARJETA AMARILLA" : "SIGUE EL JUEGO";
            drawCentered(sub, paneWidthPx / 2f, paneHeightPx * 0.70f - 52f, 0.95f, new Color(1f, 1f, 1f, 0.9f));
        }

        MatchSimulation.Phase phase = sim.getPhase();
        if (phase == MatchSimulation.Phase.KICKOFF) {
            drawBigCenteredText(paneWidthPx, paneHeightPx, kickoffCountdownText(), Color.WHITE, 2.6f);
            if (sim.getHalf() == 3) {
                // MK6: tiempo extra, el primero que convierte gana.
                drawCentered("TIEMPO EXTRA - GOL DE ORO", paneWidthPx / 2f, paneHeightPx / 2f - 10f, 1.2f,
                        new Color(1f, 0.85f, 0.2f, 1f));
                font.getData().setScale((1f) * Fonts.K);
            }
            // Recordatorio de controles solo en el saque inicial del primer tiempo (sim.getHalf()==1),
            // para no repetirlo en cada reinicio de jugada y no tapar la acción una vez arrancado el partido.
            if (sim.getHalf() == 1 && sim.getScoreLeft() == 0 && sim.getScoreRight() == 0) {
                if (mode == GameMode.TWO_VS_TWO) {
                    drawControlsHint(paneWidthPx, paneHeightPx, true, 0f);
                    drawControlsHint(paneWidthPx, paneHeightPx, false, 22f * uiScale);
                } else {
                    drawControlsHint(paneWidthPx, paneHeightPx, !cornerLabel.startsWith("JUGADOR 2"), 0f);
                }
            }
        } else if (phase == MatchSimulation.Phase.GOAL_CELEBRATION) {
            String msg = sim.getLastGoalMessage() != null ? sim.getLastGoalMessage() : "GOOOL";
            drawBigCenteredText(paneWidthPx, paneHeightPx, msg, new Color(1f, 0.85f, 0.2f, 1f), 2.0f);
        } else if (phase == MatchSimulation.Phase.HALFTIME) {
            drawBigCenteredText(paneWidthPx, paneHeightPx, "ENTRETIEMPO", Color.WHITE, 2.2f);
        } else if (phase == MatchSimulation.Phase.FOUL) {
            drawFoulBanner(paneWidthPx, paneHeightPx);
        } else if (phase == MatchSimulation.Phase.SET_PIECE) {
            drawSetPieceBanner(paneWidthPx, paneHeightPx);
        } else if (phase == MatchSimulation.Phase.REPLAY) {
            boolean blink = ((int) (animTime * 2f)) % 2 == 0;
            font.getData().setScale((1.5f) * Fonts.K);
            GlyphLayout rl = new GlyphLayout(font, "REPLAY");
            font.setColor(0.95f, 0.25f, 0.25f, blink ? 1f : 0.45f);
            font.draw(batch, rl, paneWidthPx / 2f - rl.width / 2f, paneHeightPx - paneHeightPx * 0.08f - 12f);
            font.getData().setScale((0.8f) * Fonts.K);
            GlyphLayout tl = new GlyphLayout(font, "TAB para saltear");
            font.setColor(1f, 1f, 1f, 0.75f);
            font.draw(batch, tl, paneWidthPx / 2f - tl.width / 2f, paneHeightPx * 0.08f - 8f + 22f);
            font.getData().setScale((1f) * Fonts.K);
        }
        // FULL_TIME: el resumen se dibuja a pantalla completa en drawFullTimePanel().
        batch.end();
    }

    /** Barras negras arriba y abajo (formato "cine") + barra de progreso durante la repetición. */
    private void drawReplayBars(int paneWidthPx, int paneHeightPx) {
        float bar = paneHeightPx * 0.08f;
        shapes.setProjectionMatrix(hudCam.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.85f);
        shapes.rect(0, paneHeightPx - bar, paneWidthPx, bar);
        shapes.rect(0, 0, paneWidthPx, bar);
        shapes.setColor(0.95f, 0.25f, 0.25f, 0.9f);
        shapes.rect(0, bar, paneWidthPx * sim.getReplayProgress(), 3f);
        shapes.end();
    }

    /** Recordatorio breve de controles, mostrado solo durante el saque inicial del partido (tutorial mínimo in-context). */
    // ------------------------------------------------------------------
    // MK7: ayudas de visibilidad
    // ------------------------------------------------------------------

    private float miniMapWidth() {
        return Constants.MINIMAP_WIDTH_PX * uiScale;
    }

    private float miniMapHeight() {
        float worldW = Constants.FIELD_WIDTH + Constants.GOAL_DEPTH * 2f;
        return miniMapWidth() * Constants.FIELD_HEIGHT / worldW;
    }

    /** Flecha en el borde del panel apuntando a la pelota cuando queda fuera de la vista, con la distancia en metros. */
    private void drawBallPointer(int w, int h) {
        OrthographicCamera cam = overlayCam;
        if (cam == null) {
            return;
        }
        float nx = (rBall.x - cam.position.x) / cam.viewportWidth + 0.5f;
        float ny = (rBall.y - cam.position.y) / cam.viewportHeight + 0.5f;
        if (nx > 0.04f && nx < 0.96f && ny > 0.05f && ny < 0.95f) {
            return; // se ve: no hace falta
        }
        float inset = 34f * uiScale;
        float cx = w / 2f, cy = h / 2f;
        float dx = (nx - 0.5f) * w, dy = (ny - 0.5f) * h;
        float tx = dx == 0f ? 1e9f : (dx > 0 ? (w / 2f - inset) / dx : (-w / 2f + inset) / dx);
        float ty = dy == 0f ? 1e9f : (dy > 0 ? (h / 2f - inset) / dy : (-h / 2f + inset) / dy);
        float t = Math.min(tx, ty);
        float px = cx + dx * t, py = cy + dy * t;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        float ux = dx / len, uy = dy / len;
        float r = 15f * uiScale;

        shapes.setProjectionMatrix(hudCam.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.65f);
        shapes.triangle(px + ux * (r + 4f), py + uy * (r + 4f),
                px - ux * (r - 2f) - uy * (r + 3f), py - uy * (r - 2f) + ux * (r + 3f),
                px - ux * (r - 2f) + uy * (r + 3f), py - uy * (r - 2f) - ux * (r + 3f));
        shapes.setColor(1f, 0.85f, 0.15f, 0.95f);
        shapes.triangle(px + ux * r, py + uy * r,
                px - ux * (r - 4f) - uy * r * 0.8f, py - uy * (r - 4f) + ux * r * 0.8f,
                px - ux * (r - 4f) + uy * r * 0.8f, py - uy * (r - 4f) - ux * r * 0.8f);
        shapes.end();

        Vector2 selfPos = overlaySelfIsLeft ? rPlayerL : rPlayerR;
        String txt = Math.round(selfPos.dst(rBall)) + " m";
        batch.setProjectionMatrix(hudCam.combined);
        batch.begin();
        font.getData().setScale(0.8f * Fonts.K);
        GlyphLayout gl = new GlyphLayout(font, txt);
        float lx = MathUtils.clamp(px - ux * (r + 22f * uiScale) - gl.width / 2f, 4f, w - gl.width - 4f);
        float ly = MathUtils.clamp(py - uy * (r + 14f * uiScale) + gl.height / 2f, gl.height + 4f, h - 4f);
        font.setColor(0f, 0f, 0f, 0.8f);
        font.draw(batch, gl, lx + 1f, ly - 1f);
        font.setColor(1f, 0.92f, 0.4f, 1f);
        font.draw(batch, gl, lx, ly);
        font.getData().setScale(Fonts.K);
        batch.end();
    }

    /** Minimapa centrado abajo: cancha, jugadores, arqueros, pelota y el recuadro que ve cada cámara. Más carteles de aviso. */
    private void drawMiniMap(int paneAreaHeight) {
        if (sim.getPhase() == MatchSimulation.Phase.FULL_TIME) {
            return;
        }
        Gdx.gl.glViewport(0, 0, screenWidth, screenHeight);
        hudCam.setToOrtho(false, screenWidth, screenHeight);
        hudCam.update();
        shapes.setProjectionMatrix(hudCam.combined);

        float mw = miniMapWidth(), mh = miniMapHeight();
        float x0 = (screenWidth - mw) / 2f, y0 = 8f * uiScale;
        float worldW = Constants.FIELD_WIDTH + Constants.GOAL_DEPTH * 2f;
        float sc = mw / worldW;
        float cx = x0 + mw / 2f, cy = y0 + mh / 2f;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.03f, 0.18f, 0.06f, 0.62f);
        shapes.rect(x0, y0, mw, mh);
        // arcos
        shapes.setColor(0.9f, 0.9f, 0.9f, 0.55f);
        float gh = Constants.GOAL_WIDTH * sc;
        shapes.rect(x0, cy - gh / 2f, Constants.GOAL_DEPTH * sc, gh);
        shapes.rect(x0 + mw - Constants.GOAL_DEPTH * sc, cy - gh / 2f, Constants.GOAL_DEPTH * sc, gh);
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(1f, 1f, 1f, 0.7f);
        float fx0 = x0 + Constants.GOAL_DEPTH * sc, fw = Constants.FIELD_WIDTH * sc;
        shapes.rect(fx0, y0, fw, mh);
        shapes.line(cx, y0, cx, y0 + mh);
        shapes.circle(cx, cy, 5f * sc, 16);
        // recuadros de cámara
        shapes.setColor(1f, 1f, 1f, 0.55f);
        if (mode.isOnline() || mode == GameMode.ONE_PLAYER || mode == GameMode.TWO_VS_TWO) {
            drawCamRect((mode != GameMode.ONLINE_CLIENT) ? camLeft : camRight, cx, cy, sc);
        } else {
            shapes.setColor(MatchSetup.leftTeam.color.r, MatchSetup.leftTeam.color.g, MatchSetup.leftTeam.color.b, 0.9f);
            drawCamRect(camLeft, cx, cy, sc);
            shapes.setColor(MatchSetup.rightTeam.color.r, MatchSetup.rightTeam.color.g, MatchSetup.rightTeam.color.b, 0.9f);
            drawCamRect(camRight, cx, cy, sc);
        }
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        float dot = 4.2f * uiScale;
        // arqueros
        shapes.setColor(0.1f, 0.1f, 0.1f, 1f);
        shapes.circle(cx + rKeeperL.x * sc, cy + rKeeperL.y * sc, dot * 0.75f, 10);
        shapes.circle(cx + rKeeperR.x * sc, cy + rKeeperR.y * sc, dot * 0.75f, 10);
        // jugadores (borde blanco); MK8: los expulsados no se dibujan
        Color lc = MatchSetup.leftTeam.color, rc = MatchSetup.rightTeam.color;
        miniDot(cx, cy, sc, rPlayerL, dot, lc, sim.getPlayerLeft().isSuspended());
        miniDot(cx, cy, sc, rPlayerR, dot, rc, sim.getPlayerRight().isSuspended());
        if (sim.hasMates()) {
            miniDot(cx, cy, sc, rMateL, dot, lc, sim.getMateLeft().isSuspended());
            miniDot(cx, cy, sc, rMateR, dot, rc, sim.getMateRight().isSuspended());
            if (!sim.getMateLeft().isSuspended()) {
                shapes.setColor(0.45f, 0.92f, 1f, 1f); // punto celeste = J2
                shapes.circle(cx + rMateL.x * sc, cy + rMateL.y * sc, dot * 0.35f, 8);
            }
        }
        // pelota
        shapes.setColor(0f, 0f, 0f, 1f);
        shapes.circle(cx + rBall.x * sc, cy + rBall.y * sc, dot * 0.8f + 1.5f, 10);
        shapes.setColor(1f, 1f, 0.85f, 1f);
        shapes.circle(cx + rBall.x * sc, cy + rBall.y * sc, dot * 0.8f, 10);
        shapes.end();

        // carteles (aviso de pelota reubicada / confirmar salida)
        String msg = null;
        if (escArmedTime > 0f) {
            msg = "ESC otra vez para salir (se pierde el partido)";
        } else if (unstuckToast > 0f) {
            msg = "Pelota trabada: la reubicamos";
        }
        if (msg != null) {
            batch.setProjectionMatrix(hudCam.combined);
            batch.begin();
            font.getData().setScale(1.15f * Fonts.K);
            GlyphLayout gl = new GlyphLayout(font, msg);
            float ty = paneAreaHeight - 22f * uiScale;
            font.setColor(0f, 0f, 0f, 0.75f);
            font.draw(batch, gl, screenWidth / 2f - gl.width / 2f + 2f, ty - 2f);
            font.setColor(1f, 0.9f, 0.3f, 1f);
            font.draw(batch, gl, screenWidth / 2f - gl.width / 2f, ty);
            font.getData().setScale(Fonts.K);
            batch.end();
        }
    }

    // ------------------------------------------------------------------
    // MK8: faltas, tarjetas, tiros libres y penales (carteles, marcador de pelota y HUD)
    // ------------------------------------------------------------------

    /** Índice de jugador (0 izq., 1 der., 2 compañero izq., 3 compañero der.), igual que MatchSimulation. */
    private int idxOf(PlayerEntity p) {
        if (p == sim.getPlayerLeft()) return 0;
        if (p == sim.getPlayerRight()) return 1;
        if (sim.hasMates() && p == sim.getMateLeft()) return 2;
        return 3;
    }

    private void miniDot(float cx, float cy, float sc, Vector2 pos, float dot, Color c, boolean hidden) {
        if (hidden) {
            return;
        }
        shapes.setColor(1f, 1f, 1f, 1f);
        shapes.circle(cx + pos.x * sc, cy + pos.y * sc, dot + 1.5f, 12);
        shapes.setColor(c);
        shapes.circle(cx + pos.x * sc, cy + pos.y * sc, dot, 12);
    }

    private static Color cardColor(FoulRules.Card card) {
        return card == FoulRules.Card.RED ? new Color(0.92f, 0.12f, 0.12f, 1f) : new Color(1f, 0.85f, 0.1f, 1f);
    }

    /** Tarjeta (rectángulo con borde) centrada en (cx, cy). Usa ShapeRenderer: el batch no debe estar activo. */
    private void drawCardIcon(float cx, float cy, float w, float h, FoulRules.Card card) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.5f);
        shapes.rect(cx - w / 2f + 2f, cy - h / 2f - 2f, w, h);
        shapes.setColor(cardColor(card));
        shapes.rect(cx - w / 2f, cy - h / 2f, w, h);
        shapes.end();
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(1f, 1f, 1f, 0.9f);
        shapes.rect(cx - w / 2f, cy - h / 2f, w, h);
        shapes.end();
    }

    private String teamNameOfIdx(int idx) {
        return (idx & 1) == 0 ? MatchSetup.leftTeam.name : MatchSetup.rightTeam.name;
    }

    private Color teamColorOfIdx(int idx) {
        return (idx & 1) == 0 ? MatchSetup.leftColor() : MatchSetup.rightColor();
    }

    /** Cartel de la pausa tras la falta. Se llama con el batch activo (lo corta un momento para dibujar la tarjeta). */
    private void drawFoulBanner(int w, int h) {
        float cx = w / 2f;
        float cy = h / 2f;
        int idx = sim.getLastFoulerIdx();
        FoulRules.Card card = sim.getLastFoulCard();
        String detail = sim.getLastFoulType() == FoulRules.Type.SLIDE ? "Barrida sin tocar la pelota" : "Carga violenta";

        drawCentered("¡FALTA!", cx + 2f, cy + 88f - 2f, 2.4f, new Color(0f, 0f, 0f, 0.55f));
        drawCentered("¡FALTA!", cx, cy + 88f, 2.4f, Color.WHITE);
        drawCentered(sim.getLastFoulerName() + " (" + teamNameOfIdx(idx) + ")", cx, cy + 40f, 1.25f, teamColorOfIdx(idx));
        drawCentered(detail + (sim.isLastFoulPenalty() ? "  -  ¡EN EL ÁREA: PENAL!" : ""), cx, cy + 12f, 0.95f,
                sim.isLastFoulPenalty() ? new Color(1f, 0.55f, 0.25f, 1f) : new Color(0.9f, 0.9f, 0.9f, 1f));
        if (card != FoulRules.Card.NONE) {
            batch.end();
            shapes.setProjectionMatrix(hudCam.combined);
            float bob = 3f * (float) Math.sin(animTime * 6f);
            drawCardIcon(cx, cy - 34f + bob, 34f, 48f, card);
            batch.begin();
            String label = card == FoulRules.Card.RED
                    ? (sim.isPermanentlySuspended(sim.getLastFoulerIdx()) ? "TARJETA ROJA - EXPULSADO DEL PARTIDO"
                    : "TARJETA ROJA - EXPULSADO " + Math.round(Constants.RED_SUSPENSION_SECONDS) + " s")
                    : "TARJETA AMARILLA";
            drawCentered(label, cx, cy - 74f, 1.15f, cardColor(card));
        }
    }

    /** Cartel del tiro libre / penal durante la cuenta regresiva. */
    private void drawSetPieceBanner(int w, int h) {
        float cx = w / 2f;
        float cy = h / 2f;
        int team = sim.getSetPieceTeam();
        String teamName = team == 0 ? MatchSetup.leftTeam.name : MatchSetup.rightTeam.name;
        Color tc = team == 0 ? MatchSetup.leftColor() : MatchSetup.rightColor();
        String title = sim.isLastFoulPenalty() ? "¡PENAL!" : "TIRO LIBRE";
        drawCentered(title, cx + 2f, cy + 88f - 2f, 2.4f, new Color(0f, 0f, 0f, 0.55f));
        drawCentered(title, cx, cy + 88f, 2.4f, new Color(1f, 0.85f, 0.2f, 1f));
        drawCentered("Cobra " + teamName, cx, cy + 40f, 1.2f, tc);
        drawCentered(kickoffCountdownText(), cx, cy + 4f, 2.6f, Color.WHITE);
        if (sim.getSetPieceTakerIdx() >= 0 && !mode.isOnline() && mode != GameMode.TWO_PLAYERS) {
            boolean mine = sim.getSetPieceTakerIdx() == 0 || sim.getSetPieceTakerIdx() == 2;
            if (mine) {
                drawCentered("Te toca patear: mantené ESPACIO y soltá", cx, cy - 36f, 0.95f, new Color(0.9f, 0.95f, 0.9f, 1f));
            }
        } else if (mode.isOnline()) {
            boolean mine = (sim.getSetPieceTakerIdx() == 0) == (mode == GameMode.ONLINE_HOST);
            if (mine) {
                drawCentered("Te toca patear: mantené ESPACIO y soltá", cx, cy - 36f, 0.95f, new Color(0.9f, 0.95f, 0.9f, 1f));
            }
        }
    }

    /** Anillo pulsante en la cancha: donde fue la falta y, después, donde se cobra el tiro. */
    private void drawSetPieceMarker(OrthographicCamera cam) {
        MatchSimulation.Phase ph = sim.getPhase();
        if (ph != MatchSimulation.Phase.FOUL && ph != MatchSimulation.Phase.SET_PIECE) {
            return;
        }
        boolean foul = ph == MatchSimulation.Phase.FOUL;
        Vector2 p = foul ? sim.getLastFoulSpot() : rBall;
        Color c;
        if (foul) {
            c = sim.getLastFoulCard() == FoulRules.Card.NONE ? Color.WHITE : cardColor(sim.getLastFoulCard());
        } else {
            c = sim.getSetPieceTeam() == 0 ? MatchSetup.leftColor() : MatchSetup.rightColor();
        }
        float pulse = 1f + 0.14f * (float) Math.sin(animTime * 8f);
        float size = (foul ? 3.2f : 2.6f) * pulse;
        float h = size * 0.5f;
        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        batch.setColor(c.r, c.g, c.b, 0.95f);
        batch.draw(texRing, p.x - size / 2f, p.y - Constants.SPRITE_HEIGHT_METERS * 0.18f - h / 2f, size, h);
        batch.setColor(Color.WHITE);
        batch.end();
    }

    /**
     * Faltas y tarjetas de cada equipo a los costados del marcador y, si hay un expulsado, cuánto falta para que vuelva.
     * Si no hay lugar (ventana angosta) se usa la versión corta o se omite.
     */
    private void drawDiscipline(float centerX, float panelW, int hudHeight) {
        if (!Settings.foulsEnabled) {
            return;
        }
        MatchStats st = sim.getStats();
        float baseY = hudHeight * 0.5f;
        float iconW = 10f, iconH = 14f;
        float leftLimit = 180f;                       // el reloj ocupa hasta aquí
        float rightLimit = screenWidth - (hudHeight - 8f) - 20f; // el logo ocupa desde aquí
        for (int t = 0; t < 2; t++) {
            int f = st.fouls[t], y = st.yellows[t], r = st.reds[t];
            font.getData().setScale(0.8f * Fonts.K);
            GlyphLayout lf = new GlyphLayout(font, "FALTAS " + f);
            GlyphLayout lfShort = new GlyphLayout(font, "F " + f);
            GlyphLayout ly = new GlyphLayout(font, String.valueOf(y));
            GlyphLayout lr = new GlyphLayout(font, String.valueOf(r));
            float cardsW = (iconW + 4f + ly.width) + 12f + (iconW + 4f + lr.width);
            float avail = t == 0 ? (centerX - panelW / 2f - 14f) - leftLimit : rightLimit - (centerX + panelW / 2f + 14f);
            GlyphLayout lab = lf.width + 12f + cardsW <= avail ? lf : lfShort;
            float total = lab.width + 12f + cardsW;
            if (total > avail) {
                continue; // no entra
            }
            float x0 = t == 0 ? centerX - panelW / 2f - 14f - total : centerX + panelW / 2f + 14f;
            float xi = x0 + lab.width + 12f;

            shapes.setProjectionMatrix(hudCam.combined);
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(cardColor(FoulRules.Card.YELLOW));
            shapes.rect(xi, baseY - iconH / 2f - 4f, iconW, iconH);
            shapes.setColor(cardColor(FoulRules.Card.RED));
            shapes.rect(xi + iconW + 4f + ly.width + 12f, baseY - iconH / 2f - 4f, iconW, iconH);
            shapes.end();

            batch.setProjectionMatrix(hudCam.combined);
            batch.begin();
            Color tc = t == 0 ? MatchSetup.leftColor() : MatchSetup.rightColor();
            font.setColor(tc);
            font.draw(batch, lab, x0, baseY + 6f);
            font.setColor(Color.WHITE);
            font.draw(batch, ly, xi + iconW + 4f, baseY + 6f);
            font.draw(batch, lr, xi + iconW + 4f + ly.width + 12f + iconW + 4f, baseY + 6f);

            // Expulsado: tiempo que falta para que vuelva.
            float rem = 0f;
            for (int i = t; i < 4; i += 2) {
                if (sim.isSuspended(i)) {
                    rem = Math.max(rem, sim.getSuspendRemaining(i));
                }
            }
            if (rem > 0f) {
                boolean on = ((int) (animTime * 2f)) % 2 == 0;
                String txt = rem >= Constants.RED_PERMANENT ? "EXPULSADO" : "EXPULSADO " + MathUtils.ceil(rem) + " s";
                GlyphLayout le = new GlyphLayout(font, txt);
                font.setColor(0.95f, 0.3f, 0.25f, on ? 1f : 0.65f);
                font.draw(batch, le, t == 0 ? x0 + total - le.width : x0, baseY - 20f);
            }
            font.getData().setScale(Fonts.K);
            batch.end();
        }
    }

    private void drawCamRect(OrthographicCamera cam, float cx, float cy, float sc) {
        float w = cam.viewportWidth * sc, h = cam.viewportHeight * sc;
        shapes.rect(cx + cam.position.x * sc - w / 2f, cy + cam.position.y * sc - h / 2f, w, h);
    }

    private void drawControlsHint(int paneWidthPx, int paneHeightPx, boolean isPlayerOne, float extraY) {
        String hint = isPlayerOne
                ? (mode == GameMode.TWO_VS_TWO ? "J1 (azul):  " : "") + "WASD mover | SHIFT correr | ESPACIO cargar/patear | CTRL planchazo"
                : (mode == GameMode.TWO_VS_TWO ? "J2 (celeste):  " : "") + "Flechas mover | CTRL correr | ENTER cargar/patear | SHIFT planchazo";
        font.getData().setScale((0.85f) * Fonts.K);
        GlyphLayout layout = new GlyphLayout(font, hint);
        font.setColor(0f, 0f, 0f, 0.6f);
        float hintY = miniMapHeight() + 24f * uiScale + extraY; // MK7: encima del minimapa
        font.draw(batch, layout, paneWidthPx / 2f - layout.width / 2f + 1f, hintY - 1f);
        font.setColor(1f, 1f, 1f, 0.9f);
        font.draw(batch, layout, paneWidthPx / 2f - layout.width / 2f, hintY);
        font.getData().setScale((1f) * Fonts.K);
    }

    private void drawBigCenteredText(int paneWidthPx, int paneHeightPx, String text, Color color, float scale) {
        font.getData().setScale((scale) * Fonts.K);
        GlyphLayout layout = new GlyphLayout(font, text);
        font.setColor(0f, 0f, 0f, 0.55f);
        font.draw(batch, layout, paneWidthPx / 2f - layout.width / 2f + 2f, paneHeightPx / 2f + layout.height / 2f + 38f);
        font.setColor(color);
        font.draw(batch, layout, paneWidthPx / 2f - layout.width / 2f, paneHeightPx / 2f + layout.height / 2f + 40f);
        font.getData().setScale((1f) * Fonts.K);
    }

    private String kickoffCountdownText() {
        int n = MathUtils.ceil(sim.getPhaseTimer());
        return n <= 0 ? "¡VAMOS!" : String.valueOf(n);
    }

    private String matchResultText() {
        int sl = sim.getScoreLeft();
        int sr = sim.getScoreRight();
        if (sl == sr) {
            return "EMPATE " + sl + " - " + sr;
        }
        return (sl > sr ? "GANA " + MatchSetup.leftTeam.name + " " : "GANA " + MatchSetup.rightTeam.name + " ") + sl + " - " + sr;
    }

    // ------------------------------------------------------------------
    // Barra superior compartida: reloj, marcador y título del partido
    // ------------------------------------------------------------------

    private void drawSharedHud(int hudY, int hudHeight) {
        Gdx.gl.glViewport(0, hudY, screenWidth, hudHeight);
        hudCam.setToOrtho(false, screenWidth, hudHeight);
        hudCam.update();

        shapes.setProjectionMatrix(hudCam.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.05f, 0.06f, 0.09f, 0.95f);
        shapes.rect(0, 0, screenWidth, hudHeight);
        shapes.end();

        float centerX = screenWidth / 2f;
        float panelW = Math.min(460f, screenWidth * 0.42f);
        float panelH = hudHeight * 0.60f;
        float panelX = centerX - panelW / 2f;
        float panelY = hudHeight * 0.10f;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.11f, 0.12f, 0.16f, 0.95f);
        shapes.rect(panelX, panelY, panelW, panelH);
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(0.55f, 0.55f, 0.62f, 1f);
        shapes.rect(panelX, panelY, panelW, panelH);
        shapes.end();

        // Reloj estilizado a la izquierda.
        float clockCx = 44f;
        float clockCy = hudHeight / 2f;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.85f, 0.65f, 0.15f, 1f);
        shapes.circle(clockCx, clockCy, 16f, 24);
        shapes.end();
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(Color.BLACK);
        shapes.circle(clockCx, clockCy, 16f, 24);
        float angle = (sim.getHalfTimeRemaining() % 60f) / 60f * MathUtils.PI2;
        shapes.line(clockCx, clockCy,
                clockCx + MathUtils.cos(angle + MathUtils.PI / 2f) * 10f,
                clockCy + MathUtils.sin(angle + MathUtils.PI / 2f) * 10f);
        shapes.end();

        batch.setProjectionMatrix(hudCam.combined);
        batch.begin();

        String phaseLabel = phaseLabel();
        font.getData().setScale((1.1f) * Fonts.K);
        GlyphLayout phaseLayout = new GlyphLayout(font, phaseLabel);
        font.setColor(0.95f, 0.8f, 0.25f, 1f);
        font.draw(batch, phaseLayout, centerX - phaseLayout.width / 2f, hudHeight - 6f);

        font.getData().setScale((0.9f) * Fonts.K);
        GlyphLayout matchLayout = new GlyphLayout(font, Constants.MATCH_TITLE);
        font.setColor(0.85f, 0.85f, 0.9f, 1f);
        font.draw(batch, matchLayout, centerX - matchLayout.width / 2f, panelY - 2f);

        String timeText = "TIME: " + formatTime(sim.getHalfTimeRemaining());
        font.getData().setScale((1.05f) * Fonts.K);
        // MK6: en los últimos 10 s de juego el reloj se pone rojo y titila.
        boolean lastSeconds = sim.getPhase() == MatchSimulation.Phase.PLAYING && sim.getHalfTimeRemaining() <= 10f;
        if (lastSeconds) {
            boolean on = ((int) (animTime * 3f)) % 2 == 0;
            font.setColor(0.95f, 0.3f, 0.25f, on ? 1f : 0.55f);
        } else {
            font.setColor(Color.WHITE);
        }
        font.draw(batch, timeText, clockCx + 26f, hudHeight / 2f + 8f);

        drawTeamScore(centerX, panelY, panelH);
        updateGoalLog();
        font.getData().setScale((0.7f) * Fonts.K);
        int first = Math.max(0, goalLog.size() - 3);
        for (int i = first; i < goalLog.size(); i++) {
            String g = goalLog.get(i);
            font.setColor(g.startsWith("L") ? MatchSetup.leftColor() : MatchSetup.rightColor());
            font.draw(batch, g.substring(1), 12f, hudHeight - 8f - (i - first) * 15f);
        }

        // MK6: logo del club a la derecha de la barra superior.
        if (game.logo != null) {
            float ls = hudHeight - 8f;
            batch.setColor(Color.WHITE);
            batch.draw(game.logo, screenWidth - ls - 12f, 4f, ls, ls);
        }

        font.getData().setScale((1f) * Fonts.K);
        batch.end();

        drawPossessionBar(centerX, panelY);
        drawDiscipline(centerX, panelW, hudHeight);
    }

    /** MK6: compara el marcador con el último visto y anota quién convirtió y cuándo (tiempo de partido transcurrido). */
    private void updateGoalLog() {
        int sl = sim.getScoreLeft();
        int sr = sim.getScoreRight();
        if (sl + sr < goalLogLeft + goalLogRight) { // partido nuevo (revancha / reinicio)
            goalLog.clear();
        } else if (sl + sr > goalLogLeft + goalLogRight) {
            float dur = Settings.halfDurationSeconds();
            int half = sim.getHalf();
            float elapsed = half == 1 ? dur - sim.getHalfTimeRemaining()
                    : half == 2 ? dur + (dur - sim.getHalfTimeRemaining())
                    : 2f * dur + (Constants.OVERTIME_SECONDS - sim.getHalfTimeRemaining());
            int secs = Math.max(0, (int) elapsed);
            String when = String.format("%d:%02d", secs / 60, secs % 60);
            if (sl > goalLogLeft) goalLog.add("L" + MatchSetup.leftTeam.name + " " + when);
            if (sr > goalLogRight) goalLog.add("R" + MatchSetup.rightTeam.name + " " + when);
        }
        goalLogLeft = sl;
        goalLogRight = sr;
    }

    /** Barrita fina de posesión (azul vs. rojo) debajo del marcador — noción simple de "quién domina el partido". */
    private void drawPossessionBar(float centerX, float panelY) {
        float barW = 200f;
        float barH = 6f;
        float x = centerX - barW / 2f;
        float y = panelY - 14f;
        int leftPct = sim.getPossessionPercentLeft();
        float leftW = barW * leftPct / 100f;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(MatchSetup.leftColor());
        shapes.rect(x, y, leftW, barH);
        shapes.setColor(MatchSetup.rightColor());
        shapes.rect(x + leftW, y, barW - leftW, barH);
        shapes.end();

        // MK6: porcentaje a cada lado de la barra.
        batch.begin();
        font.getData().setScale((0.7f) * Fonts.K);
        GlyphLayout ll = new GlyphLayout(font, leftPct + "%");
        GlyphLayout rl = new GlyphLayout(font, (100 - leftPct) + "%");
        font.setColor(MatchSetup.leftColor());
        font.draw(batch, ll, x - ll.width - 8f, y + barH + 4f);
        font.setColor(MatchSetup.rightColor());
        font.draw(batch, rl, x + barW + 8f, y + barH + 4f);
        font.getData().setScale((1f) * Fonts.K);
        font.setColor(Color.WHITE);
        batch.end();
    }

    private void drawTeamScore(float centerX, float panelY, float panelH) {
        font.getData().setScale((1.4f) * Fonts.K);
        String left = MatchSetup.leftTeam.name;
        String mid = "  " + sim.getScoreLeft() + " - " + sim.getScoreRight() + "  ";
        String right = MatchSetup.rightTeam.name;

        GlyphLayout leftLayout = new GlyphLayout(font, left);
        GlyphLayout midLayout = new GlyphLayout(font, mid);
        GlyphLayout rightLayout = new GlyphLayout(font, right);

        float totalWidth = leftLayout.width + midLayout.width + rightLayout.width;
        float startX = centerX - totalWidth / 2f;
        float scoreY = panelY + panelH / 2f + midLayout.height / 2f;

        font.setColor(MatchSetup.leftColor());
        font.draw(batch, leftLayout, startX, scoreY);
        font.setColor(Color.WHITE);
        font.draw(batch, midLayout, startX + leftLayout.width, scoreY);
        font.setColor(MatchSetup.rightColor());
        font.draw(batch, rightLayout, startX + leftLayout.width + midLayout.width, scoreY);
    }

    private String phaseLabel() {
        switch (sim.getPhase()) {
            case HALFTIME:
                return "HALFTIME";
            case FULL_TIME:
                return "FULL TIME";
            case REPLAY:
                return "REPLAY";
            case FOUL:
                return "FOUL";
            case SET_PIECE:
                return sim.isLastFoulPenalty() ? "PENALTY" : "FREE KICK";
            default:
                return sim.getHalf() == 1 ? "FIRST HALF" : sim.getHalf() == 2 ? "SECOND HALF" : "GOLDEN GOAL";
        }
    }

    private String formatTime(float seconds) {
        int total = Math.max(0, MathUtils.ceil(seconds));
        int minutes = total / 60;
        int secs = total % 60;
        return String.format("%02d:%02d", minutes, secs);
    }

    private void drawDivider(int paneAreaHeight) {
        Gdx.gl.glViewport(0, 0, screenWidth, paneAreaHeight);
        hudCam.setToOrtho(false, screenWidth, paneAreaHeight);
        hudCam.update();
        shapes.setProjectionMatrix(hudCam.combined);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(Color.WHITE);
        shapes.rect(screenWidth / 2f - 1f, 0, 2f, paneAreaHeight);
        shapes.end();
    }

    /** MK4: pantalla final a pantalla completa — resultado, estadísticas por equipo y mejor jugador. */
    private void drawFullTimePanel() {
        Gdx.gl.glViewport(0, 0, screenWidth, screenHeight);
        hudCam.setToOrtho(false, screenWidth, screenHeight);
        hudCam.update();
        shapes.setProjectionMatrix(hudCam.combined);

        float k = Math.min(1f, (screenHeight - 30f) / 620f);
        float pw = Math.min(760f, screenWidth - 40f);
        float ph = 620f * k;
        float px = screenWidth / 2f - pw / 2f;
        float py = screenHeight / 2f - ph / 2f;

        int sl = sim.getScoreLeft();
        int sr = sim.getScoreRight();
        MatchStats st = sim.getStats();
        float[] ratings = st.ratings(sl, sr);
        int mvp = st.mvpIndex(sl, sr);
        String[] names = {MatchSetup.leftName, Constants.KEEPER_LEFT_NAME,
                MatchSetup.rightName, Constants.KEEPER_RIGHT_NAME};
        String[] roles = {"CAMPO", "ARQUERO", "CAMPO", "ARQUERO"};

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.72f);
        shapes.rect(0, 0, screenWidth, screenHeight);
        shapes.setColor(0.08f, 0.09f, 0.13f, 0.98f);
        shapes.rect(px, py, pw, ph);
        // Banda del MVP.
        float mvpBandH = 46f * k;
        float mvpBandY = py + 44f * k;
        shapes.setColor(0.35f, 0.28f, 0.05f, 1f);
        shapes.rect(px + 16f, mvpBandY, pw - 32f, mvpBandH);
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(0.55f, 0.55f, 0.62f, 1f);
        shapes.rect(px, py, pw, ph);
        shapes.setColor(0.95f, 0.8f, 0.2f, 1f);
        shapes.rect(px + 16f, mvpBandY, pw - 32f, mvpBandH);
        shapes.end();

        batch.setProjectionMatrix(hudCam.combined);
        batch.begin();
        float top = py + ph;
        float cx = screenWidth / 2f;

        drawCentered("FIN DEL PARTIDO", cx, top - 14f * k, 1.9f * k, new Color(0.95f, 0.85f, 0.25f, 1f));
        drawCentered(matchResultText(), cx, top - 58f * k, 1.3f * k, Color.WHITE);

        float labelX = px + 50f;
        float blueX = px + pw * 0.62f;
        float redX = px + pw * 0.85f;
        float y = top - 100f * k;
        float row = 26f * k;

        drawCentered(MatchSetup.leftTeam.name, blueX, y, 1.1f * k, MatchSetup.leftColor());
        drawCentered(MatchSetup.rightTeam.name, redX, y, 1.1f * k, MatchSetup.rightColor());
        y -= row;
        int poss = sim.getPossessionPercentLeft();
        String[][] rows = {
                {"GOLES", String.valueOf(sl), String.valueOf(sr)},
                {"POSESION", poss + "%", (100 - poss) + "%"},
                {"REMATES", String.valueOf(st.shots[0]), String.valueOf(st.shots[1])},
                {"AL ARCO", String.valueOf(st.shotsOnTarget[0]), String.valueOf(st.shotsOnTarget[1])},
                {"RECUPERACIONES", String.valueOf(st.tackles[0]), String.valueOf(st.tackles[1])},
                {"ATAJADAS", String.valueOf(st.saves[0]), String.valueOf(st.saves[1])},
                {"FALTAS", String.valueOf(st.fouls[0]), String.valueOf(st.fouls[1])},
                {"AMARILLAS / ROJAS", st.yellows[0] + " / " + st.reds[0], st.yellows[1] + " / " + st.reds[1]}
        };
        for (String[] r : rows) {
            font.getData().setScale((1.0f * k) * Fonts.K);
            font.setColor(0.8f, 0.82f, 0.9f, 1f);
            font.draw(batch, r[0], labelX, y);
            drawCentered(r[1], blueX, y, 1.0f * k, Color.WHITE);
            drawCentered(r[2], redX, y, 1.0f * k, Color.WHITE);
            y -= row;
        }

        y -= 10f * k;
        drawCentered("NOTAS DEL PARTIDO", cx, y, 1.0f * k, new Color(0.7f, 0.75f, 0.85f, 1f));
        y -= row;
        for (int i = 0; i < 4; i++) {
            Color c = i < 2 ? MatchSetup.leftColor() : MatchSetup.rightColor();
            font.getData().setScale((1.0f * k) * Fonts.K);
            font.setColor(c);
            font.draw(batch, names[i] + "  (" + roles[i] + ")", labelX, y);
            drawCentered(String.format("%.1f", ratings[i]) + (i == mvp ? "  *" : ""), redX, y, 1.0f * k,
                    i == mvp ? new Color(1f, 0.85f, 0.2f, 1f) : Color.WHITE);
            y -= row * 0.92f;
        }

        drawCentered("MEJOR JUGADOR: " + names[mvp] + "  -  " + String.format("%.1f", ratings[mvp]),
                cx, mvpBandY + mvpBandH - 12f * k, 1.3f * k, new Color(1f, 0.9f, 0.3f, 1f));
        String fullTimeHint = "R reiniciar   -   ESC menú principal";
        if (mode == GameMode.ONLINE_HOST) fullTimeHint = "R revancha   -   ESC menú principal";
        if (mode == GameMode.ONLINE_CLIENT) fullTimeHint = "Esperando revancha del host   -   ESC menú principal";
        drawCentered(fullTimeHint, cx, py + 30f * k, 0.9f * k, new Color(0.85f, 0.85f, 0.85f, 1f));
        font.getData().setScale((1f) * Fonts.K);
        batch.end();
    }

    /** Texto centrado horizontalmente en {@code cx}, con {@code topY} como borde superior del texto. Debe estar dentro de batch.begin(). */
    private void drawCentered(String text, float cx, float topY, float scale, Color color) {
        font.getData().setScale((scale) * Fonts.K);
        GlyphLayout gl = new GlyphLayout(font, text);
        font.setColor(color);
        font.draw(batch, gl, cx - gl.width / 2f, topY);
        font.getData().setScale((1f) * Fonts.K);
    }

    private void drawPauseOverlay() {
        Gdx.gl.glViewport(0, 0, screenWidth, screenHeight);
        hudCam.setToOrtho(false, screenWidth, screenHeight);
        hudCam.update();

        shapes.setProjectionMatrix(hudCam.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.6f);
        shapes.rect(0, 0, screenWidth, screenHeight);
        shapes.end();

        batch.setProjectionMatrix(hudCam.combined);
        batch.begin();
        float cx = screenWidth / 2f;
        float y = screenHeight / 2f + 110f;
        drawCentered("PAUSA", cx, y, 2.6f, Color.WHITE);
        String[] lines = {
                "P  continuar",
                "R  reiniciar el partido",
                "M  sonido: " + (Settings.muted ? "NO" : "SI") + "  (volumen " + Math.round(Settings.volume * 100) + "%)",
                "F1  ver cuerpos de Box2D (debug)",
                "ESC  volver al menú principal"
        };
        y -= 60f;
        for (String line : lines) {
            drawCentered(line, cx, y, 1.1f, new Color(0.88f, 0.88f, 0.9f, 1f));
            y -= 30f;
        }
        font.getData().setScale((1f) * Fonts.K);
        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        screenWidth = Math.max(2, width);
        screenHeight = Math.max(1, height);
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
        Fonts.setUiScale(1f);
        closeNetwork();
    }

    @Override
    public void dispose() {
        closeNetwork();
        shapes.dispose();
        batch.dispose();
        font.dispose();
        debugRenderer.dispose();
        sim.dispose();

        texPlayerLeft.dispose();
        texPlayerRight.dispose();
        texKeeperLeft.dispose();
        texKeeperRight.dispose();
        texBall.dispose();
        texGround.dispose();
        texCrowd.dispose();
        texShadow.dispose();
        texRing.dispose();
        texHalo.dispose();
        if (blurShader != null) blurShader.dispose();
        if (sfxCheer != null) sfxCheer.dispose();
        if (sfxDive != null) sfxDive.dispose();
        if (sfxSave != null) sfxSave.dispose();

        if (sfxKickSoft != null) sfxKickSoft.dispose();
        if (sfxKickHard != null) sfxKickHard.dispose();
        if (sfxThud != null) sfxThud.dispose();
        if (sfxWhistle != null) sfxWhistle.dispose();
        if (sfxAdvantage != null) sfxAdvantage.dispose();
        if (sfxGoal != null) sfxGoal.dispose();
        if (sfxFullTime != null) sfxFullTime.dispose();
    }
}
