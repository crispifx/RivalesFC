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
import com.rivalesfc.game.sim.MatchStats;
import com.rivalesfc.game.entities.Ball;
import com.rivalesfc.game.entities.Field;
import com.rivalesfc.game.entities.GoalkeeperEntity;
import com.rivalesfc.game.entities.PlayerEntity;
import com.rivalesfc.game.gfx.MotionTrail;
import com.rivalesfc.game.gfx.Fonts;
import com.rivalesfc.game.gfx.PixelArtFactory;
import com.rivalesfc.game.input.Gamepads;
import com.rivalesfc.game.input.PlayerInput;
import com.rivalesfc.game.net.NetClient;
import com.rivalesfc.game.net.NetHost;
import com.rivalesfc.game.net.Protocol;
import com.rivalesfc.game.net.Snapshot;
import com.rivalesfc.game.net.SnapshotInterpolator;
import com.rivalesfc.game.sim.MatchSimulation;
import com.rivalesfc.game.audio.AudioFactory;
import com.badlogic.gdx.audio.Sound;

public class MatchScreen implements Screen {

    private final RivalesFCGame game;
    private final GameMode mode;

    private MatchSimulation sim;

    private final PlayerInput inputLeft = new PlayerInput();
    private final PlayerInput inputRight = new PlayerInput();

    private final PlayerInput inputP3 = new PlayerInput();
    private final PlayerInput inputP4 = new PlayerInput();

    private final boolean[] kickPrev = new boolean[4];

    private final NetHost netHost;
    private final NetClient netClient;
    private final String netAddressText;
    private final Snapshot netSnap = new Snapshot();
    private final SnapshotInterpolator netInterp = new SnapshotInterpolator();
    private int netSeq = 0;
    private float netSendAccum = 0f;
    private boolean hostHadClient = false;

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
        configureSim();
    }

    private final OrthographicCamera camLeft = new OrthographicCamera();
    private final OrthographicCamera camRight = new OrthographicCamera();
    private final OrthographicCamera hudCam = new OrthographicCamera();
    private final Vector2 focusBlend = new Vector2();

    private final Vector2 rBall = new Vector2();
    private final Vector2 rPlayerL = new Vector2();
    private final Vector2 rPlayerR = new Vector2();
    private final Vector2 rKeeperL = new Vector2();
    private final Vector2 rKeeperR = new Vector2();
    private final Vector2 rMateL = new Vector2();
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

    private Texture texPlayerLeft = PixelArtFactory.buildPersonTexture(MatchSetup.leftTeam.color, MatchSetup.leftTeam.shorts, false);
    private Texture texPlayerRight = PixelArtFactory.buildPersonTexture(MatchSetup.rightTeam.color, MatchSetup.rightTeam.shorts, false);
    private Texture texKeeperLeft = PixelArtFactory.buildPersonTexture(new Color(0.10f, 0.10f, 0.10f, 1f), MatchSetup.leftTeam.color, true);
    private Texture texKeeperRight = PixelArtFactory.buildPersonTexture(new Color(0.10f, 0.10f, 0.10f, 1f), MatchSetup.rightTeam.color, true);
    private final Texture texBall = PixelArtFactory.buildBallTexture();

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

    private final ShaderProgram blurShader = BlurShader.create();

    private TextureRegion blurRegionLeft = paddedRegion(texPlayerLeft);
    private TextureRegion blurRegionRight = paddedRegion(texPlayerRight);
    private static final float BLUR_PAD = 0.25f;

    private final MotionTrail trailLeft = new MotionTrail(Constants.TRAIL_MAX_POINTS);
    private final MotionTrail trailRight = new MotionTrail(Constants.TRAIL_MAX_POINTS);

    private float animTime = 0f;

    private String appliedIdentityKey = "";

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

    private final Sound sfxKickSoft = AudioFactory.kickTone(0.25f);
    private final Sound sfxKickHard = AudioFactory.kickTone(0.9f);
    private final Sound sfxThud = AudioFactory.thudTone();
    private final Sound sfxWhistle = AudioFactory.whistleTone();
    private final Sound sfxGoal = AudioFactory.goalFanfare();
    private final Sound sfxFullTime = AudioFactory.fullTimeTone();
    private final Sound sfxCheer = AudioFactory.crowdCheer();
    private final Sound sfxDive = AudioFactory.diveWhoosh();
    private final Sound sfxSave = AudioFactory.saveTone();
    private boolean keeperLeftWasDiving = false;
    private boolean keeperRightWasDiving = false;
    private float saveFlashTime = 0f;

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

    private final Vector2 replayFocus = new Vector2();
    private boolean replayFocusInit = false;
    private MatchSimulation.Phase lastPhase = MatchSimulation.Phase.KICKOFF;

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
        delta = Math.min(delta, Constants.MAX_FRAME_DELTA);
        uiScale = MathUtils.clamp(screenHeight / 800f, 0.8f, 2.2f);
        Fonts.setUiScale(uiScale);
        pollNetIdentity();
        pollInput();
        updateRightHumans();
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
        if (mode.isOnline() || mode == GameMode.ONE_PLAYER || mode.isTeamMode()) {

            boolean isHost = mode != GameMode.ONLINE_CLIENT;
            OrthographicCamera cam = isHost ? camLeft : camRight;
            Vector2 selfFocus = isHost ? focusLeft : focusRight;
            boolean coop = mode.isTeamMode();
            if (coop && !replaying) {

                selfFocus = tmpA.set(rPlayerL).add(rMateL).scl(0.5f);
                if (mode == GameMode.FOUR_PLAYERS) {

                    selfFocus = tmpA.set(rPlayerL).add(rMateL).add(rPlayerR).add(rMateR).scl(0.25f);
                }
            }
            if (!replaying) {

                focusBlend.set(selfFocus).lerp(rBall, coop ? Constants.TEAM_VIEW_BALL_BIAS : Constants.FULL_VIEW_BALL_BIAS);
                smoothInitFull = smoothTo(smoothFocusFull, focusBlend, smoothInitFull, delta);
                selfFocus = smoothFocusFull;
            }
            updateCamera(cam, selfFocus, screenWidth, paneAreaHeight, coop ? Constants.TEAM_VIEW_HEIGHT : Constants.FULL_VIEW_HEIGHT);
            String fullLabel = mode == GameMode.ONLINE_HOST ? "VOS - EQUIPO " + MatchSetup.leftTeam.name + " (HOST)"
                    : mode == GameMode.ONLINE_CLIENT ? "VOS - EQUIPO " + MatchSetup.rightTeam.name
                    : mode == GameMode.FOUR_PLAYERS ? "EQUIPO " + MatchSetup.leftTeam.name + "  vs  EQUIPO " + MatchSetup.rightTeam.name
                    : coop ? "JUGADOR 1 + JUGADOR 2 - EQUIPO " + MatchSetup.leftTeam.name + "  vs  IA"
                    : "VOS - EQUIPO " + MatchSetup.leftTeam.name + "  vs  IA";
            renderPane(0, 0, screenWidth, paneAreaHeight, cam, isHost, fullLabel, mode == GameMode.ONE_PLAYER || coop);
        } else {
            if (!replaying) {

                tmpA.set(focusLeft).lerp(rBall, Constants.SPLIT_VIEW_BALL_BIAS);
                tmpB.set(focusRight).lerp(rBall, Constants.SPLIT_VIEW_BALL_BIAS);
                smoothInitL = smoothTo(smoothFocusL, tmpA, smoothInitL, delta);
                smoothInitR = smoothTo(smoothFocusR, tmpB, smoothInitR, delta);
                focusLeft = smoothFocusL;
                focusRight = smoothFocusR;
            }
            updateCamera(camLeft, focusLeft, halfWidth, paneAreaHeight);
            updateCamera(camRight, focusRight, rightWidth, paneAreaHeight);

            renderPane(0, 0, halfWidth, paneAreaHeight, camLeft, true, "JUGADOR 1 - EQUIPO " + MatchSetup.leftTeam.name, false);

            renderPane(halfWidth, 0, rightWidth, paneAreaHeight, camRight, false, "JUGADOR 2 - EQUIPO " + MatchSetup.rightTeam.name, false);

            drawDivider(paneAreaHeight);
        }
        drawSharedHud(paneAreaHeight, hudHeight);
        drawMiniMap(paneAreaHeight);
        Fonts.setUiScale(1f);
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

        boolean padStart = Gamepads.anyStartJust();
        if (padStart && sim.getPhase() == MatchSimulation.Phase.FULL_TIME && !mode.isOnline()) {

            closeNetwork();
            game.setScreen(new LobbyScreen(game));
            return;
        }
        if ((Gdx.input.isKeyJustPressed(Input.Keys.P) || padStart) && !mode.isOnline()) {
            paused = !paused;
            if (!paused) {

                inputLeft.clearTransient();
                inputRight.clearTransient();
                inputP3.clearTransient();
                inputP4.clearTransient();
            }
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            if (!mode.isOnline()) {

                if (paused || sim.getPhase() == MatchSimulation.Phase.FULL_TIME) {
                    restartMatch();
                }
            } else if (mode == GameMode.ONLINE_HOST && netHost.hasClient()
                    && sim.getPhase() == MatchSimulation.Phase.FULL_TIME) {
                restartMatch();
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

            if (escArmedTime > 0f || paused || sim.getPhase() == MatchSimulation.Phase.FULL_TIME) {
                closeNetwork();
                game.setScreen(new LobbyScreen(game));
            } else {
                escArmedTime = 2.5f;
            }
        }
    }

    private boolean updateNetwork(float delta) {
        if (mode == GameMode.ONLINE_HOST) {
            netHost.poll();
            if (!netHost.hasClient()) {
                inputRight.moveX = 0f;
                inputRight.moveY = 0f;
                inputRight.sprint = false;
                inputRight.kickHeld = false;
                inputRight.clearTransient();
                return false;
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

            if (netInterp.sample(System.nanoTime(), netSnap)) {
                sim.applySnapshot(netSnap);
            }
            netClient.sendInput(inputRight);
            inputRight.clearTransient();
            return true;
        }
        return true;
    }

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
        netSnap.tick = netSeq++;
        netHost.sendSnapshot(netSnap);
    }

    private void closeNetwork() {
        if (netHost != null) netHost.close();
        if (netClient != null) netClient.close();
    }

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
        configureSim();
        paused = false;
        goalLog.clear();
        saveFlashTime = 0f;
        clearConfetti();
        keeperLeftWasDiving = false;
        keeperRightWasDiving = false;
        trailLeft.clear();
        trailRight.clear();
    }

    private int padOfSlot(int slot) {
        if (mode == GameMode.FOUR_PLAYERS) {
            return slot == 0 ? 2 : slot == 1 ? 3 : slot == 2 ? 0 : 1;
        }
        return slot < 2 ? slot : -1;
    }

    private void applyDevices(PlayerInput in, int slot, float kx, float ky, boolean kSprint, boolean kSlideJust, boolean kKick) {
        Gamepads.State g = Gamepads.state(padOfSlot(slot));
        float x = kx;
        float y = ky;
        if (g.present) {
            if (Math.abs(g.moveX) > Math.abs(x)) x = g.moveX;
            if (Math.abs(g.moveY) > Math.abs(y)) y = g.moveY;
        }
        in.moveX = x;
        in.moveY = y;
        in.normalizeMove();
        in.sprint = kSprint || (g.present && g.sprint);

        if (kSlideJust || (g.present && g.slideJust)) in.slidePressed = true;
        boolean kick = kKick || (g.present && g.kick);
        if (kickPrev[slot] && !kick) in.kickReleased = true;
        in.kickHeld = kick;
        kickPrev[slot] = kick;
    }

    private void pollInput() {
        Gamepads.poll();

        float k1x = 0f;
        float k1y = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) k1x -= 1;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) k1x += 1;
        if (Gdx.input.isKeyPressed(Input.Keys.W)) k1y += 1;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) k1y -= 1;
        applyDevices(inputLeft, 0, k1x, k1y,
                Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT),
                Gdx.input.isKeyJustPressed(Input.Keys.CONTROL_LEFT),
                Gdx.input.isKeyPressed(Input.Keys.SPACE));

        if (mode == GameMode.ONLINE_CLIENT) {

            inputRight.moveX = inputLeft.moveX;
            inputRight.moveY = inputLeft.moveY;
            inputRight.sprint = inputLeft.sprint;
            inputRight.kickHeld = inputLeft.kickHeld;
            inputRight.kickReleased |= inputLeft.kickReleased;
            inputRight.slidePressed |= inputLeft.slidePressed;
            inputLeft.clearTransient();
        }

        if (mode == GameMode.TWO_PLAYERS || mode.isTeamMode()) {

            float k2x = 0f;
            float k2y = 0f;
            if (Gdx.input.isKeyPressed(Input.Keys.LEFT) || Gdx.input.isKeyPressed(Input.Keys.J)) k2x -= 1;
            if (Gdx.input.isKeyPressed(Input.Keys.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.L)) k2x += 1;
            if (Gdx.input.isKeyPressed(Input.Keys.UP) || Gdx.input.isKeyPressed(Input.Keys.I)) k2y += 1;
            if (Gdx.input.isKeyPressed(Input.Keys.DOWN) || Gdx.input.isKeyPressed(Input.Keys.K)) k2y -= 1;
            applyDevices(inputRight, 1, k2x, k2y,
                    Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT) || Gdx.input.isKeyPressed(Input.Keys.N),
                    Gdx.input.isKeyJustPressed(Input.Keys.SHIFT_RIGHT) || Gdx.input.isKeyJustPressed(Input.Keys.PERIOD),
                    Gdx.input.isKeyPressed(Input.Keys.ENTER) || Gdx.input.isKeyPressed(Input.Keys.COMMA));
        }
        if (mode == GameMode.FOUR_PLAYERS) {

            applyDevices(inputP3, 2, 0f, 0f, false, false, false);
            applyDevices(inputP4, 3, 0f, 0f, false, false, false);
        }

    }

    private void configureSim() {
        if (mode == GameMode.FOUR_PLAYERS) {
            Gamepads.poll();
            lastRedHumanKey = -1;
            updateRightHumans();
        }
    }

    private int lastRedHumanKey = -1;

    private void updateRightHumans() {
        if (mode != GameMode.FOUR_PLAYERS) {
            return;
        }
        boolean h3 = Gamepads.has(padOfSlot(2));
        boolean h4 = Gamepads.has(padOfSlot(3));
        int key = (h3 ? 1 : 0) | (h4 ? 2 : 0);
        sim.setRightHumans(h3, inputP3, h4, inputP4);
        if (key != lastRedHumanKey) {
            lastRedHumanKey = key;
            MatchSetup.rightName = h3 ? "JUGADOR 3" : "CPU 3";
            MatchSetup.rightMateName = h4 ? "JUGADOR 4" : "CPU 4";
        }
    }

    private PlayerInput inputOf(PlayerEntity p) {
        if (p == sim.getPlayerLeft()) return inputLeft;
        if (sim.hasMates()) {
            if (p == sim.getMateLeft()) return inputRight;
            if (p == sim.getPlayerRight() && mode == GameMode.FOUR_PLAYERS) return inputP3;
            if (p == sim.getMateRight() && mode == GameMode.FOUR_PLAYERS) return inputP4;
        }
        return inputRight;
    }

    private Color humanTint(PlayerEntity p) {
        if (!sim.hasMates()) return null;
        if (p == sim.getMateLeft()) return TINT_J2;
        if (mode == GameMode.FOUR_PLAYERS) {
            if (p == sim.getPlayerRight()) return TINT_J3;
            if (p == sim.getMateRight()) return TINT_J4;
        }
        return null;
    }

    private static final Color TINT_J2 = new Color(0.45f, 0.92f, 1f, 1f);
    private static final Color TINT_J3 = new Color(1f, 0.62f, 0.2f, 1f);
    private static final Color TINT_J4 = new Color(0.82f, 0.55f, 1f, 1f);

    private void updateSfxAndFeedback(float delta) {
        Float kickPower = sim.consumeKickSfxEvent();
        if (kickPower != null) {
            playSafe(kickPower > 0.55f ? sfxKickHard : sfxKickSoft, 0.55f);
            if (kickPower > 0.75f) {
                triggerShake(0.15f, 0.06f);
            }
        }
        if (sim.consumeSlideTackleSfxEvent()) {
            playSafe(sfxThud, 0.6f);
            triggerShake(0.12f, 0.05f);
        }

        if (sim.consumeSaveEvent()) {
            playSafe(sfxSave, 0.7f);
            triggerShake(0.14f, 0.05f);
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
            } else if (phase == MatchSimulation.Phase.FULL_TIME) {
                playSafe(sfxFullTime, 0.5f);
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

        if (shakeTime > 0f) {
            float falloff = shakeTime / 0.35f;
            camX += MathUtils.random(-1f, 1f) * shakeMagnitude * falloff;
            camY += MathUtils.random(-1f, 1f) * shakeMagnitude * falloff;
        }

        cam.position.set(camX, camY, 0);
        cam.update();
    }

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
        drawGroundShadow(cam, rpos(self));
        drawGroundShadow(cam, rpos(rival));
        if (sim.hasMates()) {
            drawGroundShadow(cam, rMateL);
            drawGroundShadow(cam, rMateR);
        }

        drawGoalkeeper(cam, sim.getKeeperLeft(), regionKeeperLeft, Constants.KEEPER_LEFT_NAME);
        drawGoalkeeper(cam, sim.getKeeperRight(), regionKeeperRight, Constants.KEEPER_RIGHT_NAME);

        drawBall(cam);

        rivalTrail.render(shapes(cam), rival.teamColor, Constants.PLAYER_RADIUS * 0.9f);
        if (sharpRival && mode == GameMode.FOUR_PLAYERS && sim.isRightHuman(0)) {
            drawSelfPlayer(cam, rival, rivalRegion, rivalName);
        } else if (sharpRival) {
            drawSharpRival(cam, rival, rivalRegion, rivalName);
        } else {
            drawBlurredRival(cam, rival, rivalRegion, rivalName);
        }

        if (sim.hasMates()) {

            if (mode == GameMode.FOUR_PLAYERS && sim.isRightHuman(1)) {
                drawSelfPlayer(cam, sim.getMateRight(), regionPlayerRight, MatchSetup.rightMateName);
            } else {
                drawSharpRival(cam, sim.getMateRight(), regionPlayerRight, MatchSetup.rightMateName);
            }
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

    private void drawField(OrthographicCamera cam) {
        Field field = sim.getField();
        float halfW = field.width / 2f;
        float halfH = field.height / 2f;
        float gx = halfW + Constants.FIELD_MARGIN;
        float gy = halfH + Constants.FIELD_MARGIN;

        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        batch.setColor(Color.WHITE);
        batch.draw(texGround, -gx, -gy, gx * 2f, gy * 2f);
        batch.end();

        float bandH = PixelArtFactory.CROWD_BAND_HEIGHT;
        drawCrowdBand(cam, -gx, halfH + 0.60f, gx * 2f, bandH, false);
        drawCrowdBand(cam, -gx, -halfH - 0.60f - bandH, gx * 2f, bandH, true);
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
        float size = Constants.BALL_VISUAL_DIAMETER;
        float angleDeg = ballRollDeg + ball.body.getAngle() * MathUtils.radiansToDegrees;
        Vector2 vel = ball.body.getLinearVelocity();
        float speed = vel.len();

        batch.setProjectionMatrix(cam.combined);
        batch.begin();

        float haloS = size * 2.1f;
        batch.setColor(1f, 1f, 1f, 0.55f);
        batch.draw(texHalo, pos.x - haloS / 2f, pos.y - haloS / 2f, haloS, haloS);

        float sw = size * 1.5f;
        batch.setColor(1f, 1f, 1f, 0.8f);
        batch.draw(texShadow, pos.x - sw / 2f + size * 0.12f, pos.y - sw * 0.42f / 2f - size * 0.22f, sw, sw * 0.62f);

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

    private void drawGoalkeeper(OrthographicCamera cam, GoalkeeperEntity keeper, TextureRegion region, String name) {
        float dive = Math.abs(keeper.getVisualDive());
        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        batch.setColor(Color.WHITE);

        drawSpriteInBatch(region, rpos(keeper), 0f, 1f, false,
                Math.abs(keeper.body.getLinearVelocity().y), dive * 0.35f, 1f + dive * 0.32f);
        batch.end();
        drawNameTag(cam, rpos(keeper), name, keeper.color);
    }

    private void drawSharpRival(OrthographicCamera cam, PlayerEntity rival, TextureRegion region, String name) {
        Vector2 pos = rpos(rival);
        if (rival.isSliding()) {
            drawSlideDust(cam, pos);
        }
        drawCharacterSprite(cam, region, pos, rival.body.getLinearVelocity().x, 1f, rival.isSliding(),
                rival.body.getLinearVelocity().len());
        drawNameTag(cam, pos, name, rival.teamColor);
    }

    private void drawBlurredRival(OrthographicCamera cam, PlayerEntity rival, TextureRegion region, String name) {
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
        Vector2 pos = rpos(self);
        float vx = self.body.getLinearVelocity().x;
        boolean sliding = self.isSliding();

        if (sliding) {
            drawSlideDust(cam, pos);
        }

        float ringW = Constants.PLAYER_RADIUS * 3.2f;
        float ringH = ringW * 0.5f;
        float feetY = pos.y - Constants.SPRITE_HEIGHT_METERS * 0.18f - 0.02f;
        batch.setProjectionMatrix(cam.combined);
        batch.begin();

        Color tint = humanTint(self);
        if (self.isNear(sim.getBall()) && sim.getPhase() == MatchSimulation.Phase.PLAYING) {
            batch.setColor(1f, 0.88f, 0.25f, 1f);
        } else if (tint != null) {
            batch.setColor(tint.r, tint.g, tint.b, 0.95f);
        } else {
            batch.setColor(1f, 1f, 1f, 0.95f);
        }
        batch.draw(texRing, pos.x - ringW / 2f, feetY - ringH / 2f, ringW, ringH);
        batch.setColor(Color.WHITE);
        batch.end();

        drawCharacterSprite(cam, region, pos, vx, 1f, sliding, self.body.getLinearVelocity().len());
        drawNameTag(cam, pos, name, self.teamColor);

        if (sim.getPhase() == MatchSimulation.Phase.KICKOFF) {
            float bob = 0.18f * (float) Math.sin(animTime * 7f);
            float ay = pos.y + Constants.SPRITE_HEIGHT_METERS * 0.85f + 0.95f + bob;
            shapes(cam);
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(0f, 0f, 0f, 0.6f);
            shapes.triangle(pos.x - 0.46f, ay + 0.04f, pos.x + 0.46f, ay + 0.04f, pos.x, ay - 0.56f);
            if (tint != null) {
                shapes.setColor(tint);
            } else {
                shapes.setColor(1f, 0.88f, 0.25f, 1f);
            }
            shapes.triangle(pos.x - 0.38f, ay + 0.1f, pos.x + 0.38f, ay + 0.1f, pos.x, ay - 0.46f);
            shapes.end();
        }

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

            float bw = 1.7f;
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(0.05f, 0.05f, 0.05f, 0.75f);
            shapes.rect(pos.x - bw / 2f - 0.04f, pos.y + 1.03f, bw + 0.08f, 0.30f);
            shapes.setColor(1f, 0.85f - 0.55f * power, 0.1f, 1f);
            shapes.rect(pos.x - bw / 2f, pos.y + 1.07f, bw * power, 0.22f);
            shapes.end();

            if (!sliding && self.isNear(sim.getBall())) {
                boolean isLeft = self == sim.getPlayerLeft() || (sim.hasMates() && self == sim.getMateLeft());
                PlayerInput in = inputOf(self);
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

    private void drawSpriteInBatch(TextureRegion region, Vector2 pos, float vx, float scale, boolean sliding) {
        drawSpriteInBatch(region, pos, vx, scale, sliding, 0f);
    }

    private void drawSpriteInBatch(TextureRegion region, Vector2 pos, float vx, float scale, boolean sliding, float moveSpeed) {
        drawSpriteInBatch(region, pos, vx, scale, sliding, moveSpeed, 0f, 1f);
    }

    private void drawSpriteInBatch(TextureRegion region, Vector2 pos, float vx, float scale, boolean sliding,
                                   float moveSpeed, float lift, float stretchY) {
        float spriteHeight = Constants.SPRITE_HEIGHT_METERS * scale;
        float aspect = (float) region.getRegionHeight() / (float) region.getRegionWidth();
        float spriteWidth = spriteHeight / aspect;
        float originX = spriteWidth / 2f;
        float originY = spriteHeight * 0.18f;
        float facing = vx < -0.05f ? -1f : 1f;

        float rotation = sliding ? -62f * facing : 0f;

        float bobY = 0f;
        float squash = 1f;
        if (!sliding && moveSpeed > 0.3f) {
            float strideHz = 2.2f + moveSpeed * 0.35f;
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
        font.getData().setScale((0.024f) * Fonts.baseK);
        GlyphLayout layout = new GlyphLayout(font, name);
        float textY = pos.y + Constants.SPRITE_HEIGHT_METERS * 0.85f + layout.height;

        font.setColor(0f, 0f, 0f, 0.85f);
        font.draw(batch, layout, pos.x - layout.width / 2f + 0.02f, textY - 0.02f);
        font.setColor(color);
        font.draw(batch, layout, pos.x - layout.width / 2f, textY);
        font.getData().setScale((1f) * Fonts.K);
        batch.end();
    }

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

        MatchSimulation.Phase phase = sim.getPhase();
        if (phase == MatchSimulation.Phase.KICKOFF) {
            drawBigCenteredText(paneWidthPx, paneHeightPx, kickoffCountdownText(), Color.WHITE, 2.6f);
            if (sim.getHalf() == 3) {

                drawCentered("TIEMPO EXTRA - GOL DE ORO", paneWidthPx / 2f, paneHeightPx / 2f - 10f, 1.2f,
                        new Color(1f, 0.85f, 0.2f, 1f));
                font.getData().setScale((1f) * Fonts.K);
            }

            if (sim.getHalf() == 1 && sim.getScoreLeft() == 0 && sim.getScoreRight() == 0) {
                if (mode.isTeamMode()) {
                    drawHintLine(paneWidthPx, teamHintLine(0), 0f);
                    drawHintLine(paneWidthPx, teamHintLine(1), 22f * uiScale);
                    drawHintLine(paneWidthPx, teamHintLine(2), 44f * uiScale);
                } else {
                    drawControlsHint(paneWidthPx, paneHeightPx, !cornerLabel.startsWith("JUGADOR 2"), 0f);
                }
            }
        } else if (phase == MatchSimulation.Phase.GOAL_CELEBRATION) {
            String msg = sim.getLastGoalMessage() != null ? sim.getLastGoalMessage() : "GOOOL";
            drawBigCenteredText(paneWidthPx, paneHeightPx, msg, new Color(1f, 0.85f, 0.2f, 1f), 2.0f);
        } else if (phase == MatchSimulation.Phase.HALFTIME) {
            drawBigCenteredText(paneWidthPx, paneHeightPx, "ENTRETIEMPO", Color.WHITE, 2.2f);
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

        batch.end();
    }

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

    private float miniMapWidth() {
        return Constants.MINIMAP_WIDTH_PX * uiScale;
    }

    private float miniMapHeight() {
        float worldW = Constants.FIELD_WIDTH + Constants.GOAL_DEPTH * 2f;
        return miniMapWidth() * Constants.FIELD_HEIGHT / worldW;
    }

    private void drawBallPointer(int w, int h) {
        OrthographicCamera cam = overlayCam;
        if (cam == null) {
            return;
        }
        float nx = (rBall.x - cam.position.x) / cam.viewportWidth + 0.5f;
        float ny = (rBall.y - cam.position.y) / cam.viewportHeight + 0.5f;
        if (nx > 0.04f && nx < 0.96f && ny > 0.05f && ny < 0.95f) {
            return;
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

        shapes.setColor(1f, 1f, 1f, 0.55f);
        if (mode.isOnline() || mode == GameMode.ONE_PLAYER || mode.isTeamMode()) {
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

        shapes.setColor(0.1f, 0.1f, 0.1f, 1f);
        shapes.circle(cx + rKeeperL.x * sc, cy + rKeeperL.y * sc, dot * 0.75f, 10);
        shapes.circle(cx + rKeeperR.x * sc, cy + rKeeperR.y * sc, dot * 0.75f, 10);

        Color lc = MatchSetup.leftTeam.color, rc = MatchSetup.rightTeam.color;
        shapes.setColor(1f, 1f, 1f, 1f);
        shapes.circle(cx + rPlayerL.x * sc, cy + rPlayerL.y * sc, dot + 1.5f, 12);
        shapes.circle(cx + rPlayerR.x * sc, cy + rPlayerR.y * sc, dot + 1.5f, 12);
        shapes.setColor(lc);
        shapes.circle(cx + rPlayerL.x * sc, cy + rPlayerL.y * sc, dot, 12);
        shapes.setColor(rc);
        shapes.circle(cx + rPlayerR.x * sc, cy + rPlayerR.y * sc, dot, 12);
        if (sim.hasMates()) {
            shapes.setColor(1f, 1f, 1f, 1f);
            shapes.circle(cx + rMateL.x * sc, cy + rMateL.y * sc, dot + 1.5f, 12);
            shapes.circle(cx + rMateR.x * sc, cy + rMateR.y * sc, dot + 1.5f, 12);
            shapes.setColor(lc);
            shapes.circle(cx + rMateL.x * sc, cy + rMateL.y * sc, dot, 12);
            shapes.setColor(rc);
            shapes.circle(cx + rMateR.x * sc, cy + rMateR.y * sc, dot, 12);
            shapes.setColor(TINT_J2);
            shapes.circle(cx + rMateL.x * sc, cy + rMateL.y * sc, dot * 0.35f, 8);
            if (mode == GameMode.FOUR_PLAYERS) {

                if (sim.isRightHuman(0)) {
                    shapes.setColor(TINT_J3);
                    shapes.circle(cx + rPlayerR.x * sc, cy + rPlayerR.y * sc, dot * 0.35f, 8);
                }
                if (sim.isRightHuman(1)) {
                    shapes.setColor(TINT_J4);
                    shapes.circle(cx + rMateR.x * sc, cy + rMateR.y * sc, dot * 0.35f, 8);
                }
            }
        }

        shapes.setColor(0f, 0f, 0f, 1f);
        shapes.circle(cx + rBall.x * sc, cy + rBall.y * sc, dot * 0.8f + 1.5f, 10);
        shapes.setColor(1f, 1f, 0.85f, 1f);
        shapes.circle(cx + rBall.x * sc, cy + rBall.y * sc, dot * 0.8f, 10);
        shapes.end();

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

    private void drawCamRect(OrthographicCamera cam, float cx, float cy, float sc) {
        float w = cam.viewportWidth * sc, h = cam.viewportHeight * sc;
        shapes.rect(cx + cam.position.x * sc - w / 2f, cy + cam.position.y * sc - h / 2f, w, h);
    }

    private void drawControlsHint(int paneWidthPx, int paneHeightPx, boolean isPlayerOne, float extraY) {
        String hint = isPlayerOne
                ? "WASD mover | SHIFT correr | ESPACIO cargar/patear | CTRL planchazo"
                : "Flechas mover | CTRL correr | ENTER cargar/patear | SHIFT planchazo";
        drawHintLine(paneWidthPx, hint, extraY);
    }

    private String teamHintLine(int line) {
        if (line == 0) {
            return "J1 (azul):  WASD mover | SHIFT correr | ESPACIO cargar/patear | CTRL planchazo";
        }
        if (line == 1) {
            return "J2 (celeste):  Flechas o IJKL mover | CTRL der. o N correr | ENTER o , cargar/patear | SHIFT der. o . planchazo";
        }
        int pads = Gamepads.count();
        if (mode == GameMode.FOUR_PLAYERS) {
            return "J3 (naranja) y J4 (violeta): con mando (A patear, X planchazo, B correr). Conectados: " + pads
                    + (pads >= 2 ? "" : " - los que falten los juega la CPU");
        }
        return "Mando: stick o cruceta mover | A cargar/patear | X planchazo | B correr | START pausa"
                + (pads > 0 ? "  (" + pads + " conectado" + (pads > 1 ? "s" : "") + ")" : "");
    }

    private void drawHintLine(int paneWidthPx, String hint, float extraY) {
        font.getData().setScale((0.85f) * Fonts.K);
        GlyphLayout layout = new GlyphLayout(font, hint);
        font.setColor(0f, 0f, 0f, 0.6f);
        float hintY = miniMapHeight() + 24f * uiScale + extraY;
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

        if (game.logo != null) {
            float ls = hudHeight - 8f;
            batch.setColor(Color.WHITE);
            batch.draw(game.logo, screenWidth - ls - 12f, 4f, ls, ls);
        }

        font.getData().setScale((1f) * Fonts.K);
        batch.end();

        drawPossessionBar(centerX, panelY);
    }

    private void updateGoalLog() {
        int sl = sim.getScoreLeft();
        int sr = sim.getScoreRight();
        if (sl + sr < goalLogLeft + goalLogRight) {
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

    private void drawFullTimePanel() {
        Gdx.gl.glViewport(0, 0, screenWidth, screenHeight);
        hudCam.setToOrtho(false, screenWidth, screenHeight);
        hudCam.update();
        shapes.setProjectionMatrix(hudCam.combined);

        float k = Math.min(1f, (screenHeight - 30f) / 560f);
        float pw = Math.min(760f, screenWidth - 40f);
        float ph = 560f * k;
        float px = screenWidth / 2f - pw / 2f;
        float py = screenHeight / 2f - ph / 2f;

        int sl = sim.getScoreLeft();
        int sr = sim.getScoreRight();
        MatchStats st = sim.getStats();
        float[] ratings = st.ratings(sl, sr);
        int mvp = st.mvpIndex(sl, sr);
        String leftLabel = sim.hasMates() ? "EQUIPO " + MatchSetup.leftTeam.name : MatchSetup.leftName;
        String rightLabel = sim.hasMates() ? "EQUIPO " + MatchSetup.rightTeam.name : MatchSetup.rightName;
        String[] names = {leftLabel, Constants.KEEPER_LEFT_NAME,
                rightLabel, Constants.KEEPER_RIGHT_NAME};
        String[] roles = {"CAMPO", "ARQUERO", "CAMPO", "ARQUERO"};

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.72f);
        shapes.rect(0, 0, screenWidth, screenHeight);
        shapes.setColor(0.08f, 0.09f, 0.13f, 0.98f);
        shapes.rect(px, py, pw, ph);

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
                {"ATAJADAS", String.valueOf(st.saves[0]), String.valueOf(st.saves[1])}
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
        String fullTimeHint = "R reiniciar   -   ESC menú principal" + (Gamepads.count() > 0 ? "   -   START (mando) menú" : "");
        if (mode == GameMode.ONLINE_HOST) fullTimeHint = "R revancha   -   ESC menú principal";
        if (mode == GameMode.ONLINE_CLIENT) fullTimeHint = "Esperando revancha del host   -   ESC menú principal";
        drawCentered(fullTimeHint, cx, py + 30f * k, 0.9f * k, new Color(0.85f, 0.85f, 0.85f, 1f));
        font.getData().setScale((1f) * Fonts.K);
        batch.end();
    }

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
        if (sfxGoal != null) sfxGoal.dispose();
        if (sfxFullTime != null) sfxFullTime.dispose();
    }
}
