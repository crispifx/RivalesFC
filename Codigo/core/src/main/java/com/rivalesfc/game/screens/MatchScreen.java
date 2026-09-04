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
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer;
import com.rivalesfc.game.Constants;
import com.rivalesfc.game.GameMode;
import com.rivalesfc.game.RivalesFCGame;
import com.rivalesfc.game.entities.Ball;
import com.rivalesfc.game.entities.Field;
import com.rivalesfc.game.entities.GoalkeeperEntity;
import com.rivalesfc.game.entities.PlayerEntity;
import com.rivalesfc.game.gfx.MotionTrail;
import com.rivalesfc.game.gfx.PixelArtFactory;
import com.rivalesfc.game.input.PlayerInput;
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

    public MatchScreen(RivalesFCGame game, GameMode mode) {
        this.game = game;
        this.mode = mode;
        this.sim = new MatchSimulation(mode);
    }

    private final OrthographicCamera camLeft = new OrthographicCamera();
    private final OrthographicCamera camRight = new OrthographicCamera();
    private final OrthographicCamera hudCam = new OrthographicCamera();

    private final ShapeRenderer shapes = new ShapeRenderer();
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();
    private final Box2DDebugRenderer debugRenderer = new Box2DDebugRenderer();
    private boolean showDebug = false;
    private boolean paused = false;

    private int screenWidth = 1280;
    private int screenHeight = 800;

    // --- Arte pixel-art generado en runtime (sin assets externos) ---
    private final Texture texPlayerLeft = PixelArtFactory.buildPersonTexture(Constants.TEAM_LEFT_COLOR, Color.WHITE, false);
    private final Texture texPlayerRight = PixelArtFactory.buildPersonTexture(Constants.TEAM_RIGHT_COLOR, Color.WHITE, false);
    private final Texture texKeeperLeft = PixelArtFactory.buildPersonTexture(new Color(0.10f, 0.10f, 0.10f, 1f), Constants.TEAM_LEFT_COLOR, true);
    private final Texture texKeeperRight = PixelArtFactory.buildPersonTexture(new Color(0.10f, 0.10f, 0.10f, 1f), Constants.TEAM_RIGHT_COLOR, true);
    private final Texture texBall = PixelArtFactory.buildBallTexture();
    private final Texture texGrass = PixelArtFactory.buildGrassTexture((int) Constants.FIELD_WIDTH, 8);
    private final Texture texCrowd = PixelArtFactory.buildCrowdTexture(24, 2, 12);

    private final TextureRegion regionPlayerLeft = new TextureRegion(texPlayerLeft);
    private final TextureRegion regionPlayerRight = new TextureRegion(texPlayerRight);
    private final TextureRegion regionKeeperLeft = new TextureRegion(texKeeperLeft);
    private final TextureRegion regionKeeperRight = new TextureRegion(texKeeperRight);
    private final TextureRegion regionBall = new TextureRegion(texBall);
    private final TextureRegion regionCrowd = new TextureRegion(texCrowd);

    private final MotionTrail trailLeft = new MotionTrail(Constants.TRAIL_MAX_POINTS);
    private final MotionTrail trailRight = new MotionTrail(Constants.TRAIL_MAX_POINTS);

    // --- Animación (bobbing simple según velocidad, sin sprites extra) ---
    private float animTime = 0f;

    // --- Audio sintetizado en runtime (sin assets externos) ---
    private final Sound sfxKickSoft = AudioFactory.kickTone(0.25f);
    private final Sound sfxKickHard = AudioFactory.kickTone(0.9f);
    private final Sound sfxThud = AudioFactory.thudTone();
    private final Sound sfxWhistle = AudioFactory.whistleTone();
    private final Sound sfxGoal = AudioFactory.goalFanfare();
    private final Sound sfxFullTime = AudioFactory.fullTimeTone();
    private MatchSimulation.Phase lastPhase = MatchSimulation.Phase.KICKOFF;

    // --- Screen shake (feedback de gol/planchazo fuerte) ---
    private float shakeTime = 0f;
    private float shakeMagnitude = 0f;

    @Override
    public void show() {
    }

    @Override
    public void render(float delta) {
        pollInput();
        handleGlobalKeys();

        if (!paused) {
            sim.step(delta, inputLeft, inputRight);
            trailLeft.update(sim.getPlayerLeft().getPosition(), sim.getPlayerLeft().body.getLinearVelocity().len());
            trailRight.update(sim.getPlayerRight().getPosition(), sim.getPlayerRight().body.getLinearVelocity().len());
            animTime += delta;
            updateSfxAndFeedback(delta);
        }

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        Gdx.gl.glViewport(0, 0, screenWidth, screenHeight);
        Gdx.gl.glClearColor(0.02f, 0.02f, 0.02f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        int hudHeight = Math.min(Constants.HUD_HEIGHT_PX, screenHeight / 4);
        int paneAreaHeight = Math.max(1, screenHeight - hudHeight);

        int halfWidth = screenWidth / 2;
        int rightWidth = screenWidth - halfWidth;

        updateCamera(camLeft, sim.getPlayerLeft().getPosition(), halfWidth, paneAreaHeight);
        updateCamera(camRight, sim.getPlayerRight().getPosition(), rightWidth, paneAreaHeight);

        // Panel izquierdo: vista del Jugador 1. Su rival se dibuja difuminado.
        renderPane(0, 0, halfWidth, paneAreaHeight, camLeft, true, "JUGADOR 1 - EQUIPO AZUL");

        // Panel derecho: vista del Jugador 2 (humano o IA de soporte, según el modo elegido en el lobby).
        String rightLabel = mode == GameMode.ONE_PLAYER ? "IA - EQUIPO ROJO" : "JUGADOR 2 - EQUIPO ROJO";
        renderPane(halfWidth, 0, rightWidth, paneAreaHeight, camRight, false, rightLabel);

        drawDivider(paneAreaHeight);
        drawSharedHud(paneAreaHeight, hudHeight);

        if (paused) {
            drawPauseOverlay();
        }
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    private void handleGlobalKeys() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.F1)) {
            showDebug = !showDebug;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.P)) {
            paused = !paused;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            restartMatch();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            game.setScreen(new LobbyScreen(game));
        }
    }

    private void restartMatch() {
        sim.dispose();
        sim = new MatchSimulation(mode);
        paused = false;
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
        inputLeft.slidePressed = Gdx.input.isKeyJustPressed(Input.Keys.CONTROL_LEFT);
        boolean spacePressed = Gdx.input.isKeyPressed(Input.Keys.SPACE);
        inputLeft.kickReleased = kickHeldPrevLeft && !spacePressed;
        inputLeft.kickHeld = spacePressed;
        kickHeldPrevLeft = spacePressed;

        if (mode == GameMode.TWO_PLAYERS) {
            inputRight.moveX = 0;
            inputRight.moveY = 0;
            if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) inputRight.moveX -= 1;
            if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) inputRight.moveX += 1;
            if (Gdx.input.isKeyPressed(Input.Keys.UP)) inputRight.moveY += 1;
            if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) inputRight.moveY -= 1;
            inputRight.normalizeMove();
            inputRight.sprint = Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
            inputRight.slidePressed = Gdx.input.isKeyJustPressed(Input.Keys.SHIFT_RIGHT);
            boolean enterPressed = Gdx.input.isKeyPressed(Input.Keys.ENTER);
            inputRight.kickReleased = kickHeldPrevRight && !enterPressed;
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
            if (kickPower > 0.75f) {
                triggerShake(0.15f, 0.06f);
            }
        }
        if (sim.consumeSlideTackleSfxEvent()) {
            playSafe(sfxThud, 0.6f);
            triggerShake(0.12f, 0.05f);
        }

        MatchSimulation.Phase phase = sim.getPhase();
        if (phase != lastPhase) {
            if (phase == MatchSimulation.Phase.KICKOFF) {
                playSafe(sfxWhistle, 0.5f);
            } else if (phase == MatchSimulation.Phase.GOAL_CELEBRATION) {
                playSafe(sfxGoal, 0.6f);
                triggerShake(0.35f, 0.12f);
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
        if (sound != null) {
            sound.play(volume);
        }
    }

    private void triggerShake(float duration, float magnitude) {
        shakeTime = Math.max(shakeTime, duration);
        shakeMagnitude = Math.max(shakeMagnitude, magnitude);
    }

    // ------------------------------------------------------------------
    // Cámaras (una por jugador humano, sigue su propia posición)
    // ------------------------------------------------------------------

    private void updateCamera(OrthographicCamera cam, Vector2 focus, int paneWidthPx, int paneHeightPx) {
        float aspect = paneHeightPx == 0 ? 1f : (float) paneWidthPx / (float) paneHeightPx;
        cam.viewportHeight = Constants.SPLIT_VIEW_HEIGHT;
        cam.viewportWidth = Constants.SPLIT_VIEW_HEIGHT * aspect;

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

    private void renderPane(int x, int y, int w, int h, OrthographicCamera cam, boolean leftIsSelf, String cornerLabel) {
        Gdx.gl.glViewport(x, y, w, h);

        PlayerEntity self = leftIsSelf ? sim.getPlayerLeft() : sim.getPlayerRight();
        PlayerEntity rival = leftIsSelf ? sim.getPlayerRight() : sim.getPlayerLeft();
        TextureRegion selfRegion = leftIsSelf ? regionPlayerLeft : regionPlayerRight;
        TextureRegion rivalRegion = leftIsSelf ? regionPlayerRight : regionPlayerLeft;
        MotionTrail selfTrail = leftIsSelf ? trailLeft : trailRight;
        MotionTrail rivalTrail = leftIsSelf ? trailRight : trailLeft;
        String selfName = leftIsSelf ? Constants.PLAYER_LEFT_NAME : Constants.PLAYER_RIGHT_NAME;
        String rivalName = leftIsSelf ? Constants.PLAYER_RIGHT_NAME : Constants.PLAYER_LEFT_NAME;

        drawField(cam);
        drawGroundShadow(cam, sim.getKeeperLeft().getPosition());
        drawGroundShadow(cam, sim.getKeeperRight().getPosition());
        drawGroundShadow(cam, self.getPosition());
        drawGroundShadow(cam, rival.getPosition());

        drawGoalkeeper(cam, sim.getKeeperLeft(), regionKeeperLeft, Constants.KEEPER_LEFT_NAME);
        drawGoalkeeper(cam, sim.getKeeperRight(), regionKeeperRight, Constants.KEEPER_RIGHT_NAME);

        drawBall(cam);

        // El rival humano se dibuja primero (con su rastro) para quedar "detrás" visualmente.
        rivalTrail.render(shapes(cam), rival.teamColor, Constants.PLAYER_RADIUS * 0.9f);
        drawBlurredRival(cam, rival, rivalRegion, rivalName);

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

        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        batch.setColor(Color.WHITE);
        batch.draw(texGrass, -halfW, -halfH, field.width, field.height);
        batch.end();

        drawCrowdBand(cam, -halfW - Constants.FIELD_MARGIN, halfH + 0.3f,
                field.width + Constants.FIELD_MARGIN * 2f, Constants.FIELD_MARGIN - 0.5f);
        drawCrowdBand(cam, -halfW - Constants.FIELD_MARGIN, -halfH - Constants.FIELD_MARGIN,
                field.width + Constants.FIELD_MARGIN * 2f, Constants.FIELD_MARGIN - 0.5f);

        shapes(cam);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(Color.WHITE);
        shapes.rect(-halfW, -halfH, field.width, field.height);
        shapes.circle(0, 0, 3f, 32);
        shapes.line(0, -halfH, 0, halfH);

        // Áreas grande y chica, a ambos lados de la cancha (solo decorativas).
        float boxH = Constants.PENALTY_BOX_HEIGHT / 2f;
        float smallH = Constants.GOAL_BOX_HEIGHT / 2f;
        shapes.rect(-halfW, -boxH, Constants.PENALTY_BOX_DEPTH, Constants.PENALTY_BOX_HEIGHT);
        shapes.rect(halfW - Constants.PENALTY_BOX_DEPTH, -boxH, Constants.PENALTY_BOX_DEPTH, Constants.PENALTY_BOX_HEIGHT);
        shapes.rect(-halfW, -smallH, Constants.GOAL_BOX_DEPTH, Constants.GOAL_BOX_HEIGHT);
        shapes.rect(halfW - Constants.GOAL_BOX_DEPTH, -smallH, Constants.GOAL_BOX_DEPTH, Constants.GOAL_BOX_HEIGHT);

        // Marcas de penal.
        shapes.circle(-halfW + Constants.PENALTY_BOX_DEPTH - 0.9f, 0, 0.08f, 8);
        shapes.circle(halfW - Constants.PENALTY_BOX_DEPTH + 0.9f, 0, 0.08f, 8);

        // Arcos.
        float goalHalf = Constants.GOAL_WIDTH / 2f;
        shapes.rect(-halfW - Constants.GOAL_DEPTH, -goalHalf, Constants.GOAL_DEPTH, Constants.GOAL_WIDTH);
        shapes.rect(halfW, -goalHalf, Constants.GOAL_DEPTH, Constants.GOAL_WIDTH);

        // Arcos de esquina.
        shapes.arc(-halfW, halfH, 1f, 180f, 90f);
        shapes.arc(halfW, halfH, 1f, 90f, 90f);
        shapes.arc(-halfW, -halfH, 1f, 270f, 90f);
        shapes.arc(halfW, -halfH, 1f, 0f, 90f);

        shapes.setColor(1f, 1f, 1f, 0.55f);
        drawNetGrid(-halfW - Constants.GOAL_DEPTH, -goalHalf, -halfW, goalHalf, 4, 3);
        drawNetGrid(halfW, -goalHalf, halfW + Constants.GOAL_DEPTH, goalHalf, 4, 3);
        shapes.end();
    }

    private void drawNetGrid(float x0, float y0, float x1, float y1, int cols, int rows) {
        for (int c = 0; c <= cols; c++) {
            float fx = MathUtils.lerp(x0, x1, c / (float) cols);
            shapes.line(fx, y0, fx, y1);
        }
        for (int r = 0; r <= rows; r++) {
            float fy = MathUtils.lerp(y0, y1, r / (float) rows);
            shapes.line(x0, fy, x1, fy);
        }
    }

    private void drawCrowdBand(OrthographicCamera cam, float x, float y, float worldWidth, float worldHeight) {
        if (worldWidth <= 0f || worldHeight <= 0f) {
            return;
        }
        int tiles = 14;
        float tileW = worldWidth / tiles;
        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        batch.setColor(Color.WHITE);
        for (int i = 0; i < tiles; i++) {
            batch.draw(regionCrowd, x + i * tileW, y, tileW, worldHeight);
        }
        batch.end();
    }

    private void drawGroundShadow(OrthographicCamera cam, Vector2 pos) {
        shapes(cam);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.28f);
        shapes.ellipse(pos.x - Constants.PLAYER_RADIUS * 0.9f, pos.y - Constants.PLAYER_RADIUS * 0.85f,
                Constants.PLAYER_RADIUS * 1.8f, Constants.PLAYER_RADIUS * 0.9f);
        shapes.end();
    }

    private void drawBall(OrthographicCamera cam) {
        Ball ball = sim.getBall();
        Vector2 pos = ball.getPosition();
        float size = Constants.BALL_RADIUS * 2.1f;
        float angleDeg = ball.body.getAngle() * MathUtils.radiansToDegrees;

        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        batch.setColor(Color.WHITE);
        batch.draw(regionBall, pos.x - size / 2f, pos.y - size / 2f, size / 2f, size / 2f, size, size, 1f, 1f, angleDeg);
        batch.end();
    }

    /** Arquero 100% IA: siempre nítido (no es "el rival humano", es parte fija del campo). Nunca se tira al piso. */
    private void drawGoalkeeper(OrthographicCamera cam, GoalkeeperEntity keeper, TextureRegion region, String name) {
        drawCharacterSprite(cam, region, keeper.getPosition(), 0f, 1f, false, Math.abs(keeper.body.getLinearVelocity().y));
        drawNameTag(cam, keeper.getPosition(), name, keeper.color);
    }

    /**
     * Dibuja al jugador rival "difuminado": varias copias translúcidas de tamaño
     * creciente, simulando un desenfoque barato sin necesidad de un pipeline de
     * shaders/FrameBuffer (fuera del alcance actual), ahora aplicado sobre el
     * sprite pixel-art en vez de un círculo liso.
     */
    private void drawBlurredRival(OrthographicCamera cam, PlayerEntity rival, TextureRegion region, String name) {
        Vector2 pos = rival.getPosition();
        float vx = rival.body.getLinearVelocity().x;
        boolean sliding = rival.isSliding();
        int layers = 5;

        if (sliding) {
            drawSlideDust(cam, pos);
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

    private void drawSelfPlayer(OrthographicCamera cam, PlayerEntity self, TextureRegion region, String name) {
        Vector2 pos = self.getPosition();
        float vx = self.body.getLinearVelocity().x;
        boolean sliding = self.isSliding();

        if (sliding) {
            drawSlideDust(cam, pos);
        }

        // Anillo blanco bajo los pies: remarca cuál es el personaje propio.
        shapes(cam);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(Color.WHITE);
        shapes.circle(pos.x, pos.y - Constants.PLAYER_RADIUS * 0.7f, Constants.PLAYER_RADIUS * 0.95f, 20);
        shapes.end();

        drawCharacterSprite(cam, region, pos, vx, 1f, sliding, self.body.getLinearVelocity().len());
        drawNameTag(cam, pos, name, self.teamColor);

        float power = self.getKickPower();
        if (power > 0f) {
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(0.1f, 0.1f, 0.1f, 0.6f);
            shapes.rect(pos.x - 0.62f, pos.y + 1.05f, 1.24f, 0.20f);
            shapes.setColor(1f, 0.82f, 0.15f, 1f);
            shapes.rect(pos.x - 0.6f, pos.y + 1.07f, 1.2f * power, 0.16f);
            shapes.end();
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
                pos.x - originX, pos.y - originY + bobY,
                originX, originY,
                spriteWidth, spriteHeight * squash,
                facing, 1f,
                rotation);
    }

    private void drawNameTag(OrthographicCamera cam, Vector2 pos, String name, Color color) {
        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        font.getData().setScale(0.024f);
        GlyphLayout layout = new GlyphLayout(font, name);
        float textY = pos.y + Constants.SPRITE_HEIGHT_METERS * 0.85f + layout.height;

        font.setColor(0f, 0f, 0f, 0.85f);
        font.draw(batch, layout, pos.x - layout.width / 2f + 0.02f, textY - 0.02f);
        font.setColor(color);
        font.draw(batch, layout, pos.x - layout.width / 2f, textY);
        font.getData().setScale(1f);
        batch.end();
    }

    // ------------------------------------------------------------------
    // Overlays por panel (cuenta regresiva, gol, entretiempo, final)
    // ------------------------------------------------------------------

    private void drawPaneOverlay(int paneWidthPx, int paneHeightPx, String cornerLabel) {
        hudCam.setToOrtho(false, paneWidthPx, paneHeightPx);
        hudCam.update();
        batch.setProjectionMatrix(hudCam.combined);

        batch.begin();
        font.getData().setScale(0.9f);
        font.setColor(1f, 1f, 1f, 0.7f);
        font.draw(batch, cornerLabel, 10, 22);
        font.getData().setScale(1f);

        MatchSimulation.Phase phase = sim.getPhase();
        if (phase == MatchSimulation.Phase.KICKOFF) {
            drawBigCenteredText(paneWidthPx, paneHeightPx, kickoffCountdownText(), Color.WHITE, 2.6f);
            // Recordatorio de controles solo en el saque inicial del primer tiempo (sim.getHalf()==1),
            // para no repetirlo en cada reinicio de jugada y no tapar la acción una vez arrancado el partido.
            if (sim.getHalf() == 1 && sim.getScoreLeft() == 0 && sim.getScoreRight() == 0) {
                drawControlsHint(paneWidthPx, paneHeightPx, cornerLabel.startsWith("JUGADOR 1"));
            }
        } else if (phase == MatchSimulation.Phase.GOAL_CELEBRATION) {
            String msg = sim.getLastGoalMessage() != null ? sim.getLastGoalMessage() : "GOOOL";
            drawBigCenteredText(paneWidthPx, paneHeightPx, msg, new Color(1f, 0.85f, 0.2f, 1f), 2.4f);
        } else if (phase == MatchSimulation.Phase.HALFTIME) {
            drawBigCenteredText(paneWidthPx, paneHeightPx, "ENTRETIEMPO", Color.WHITE, 2.2f);
        } else if (phase == MatchSimulation.Phase.FULL_TIME) {
            drawBigCenteredText(paneWidthPx, paneHeightPx, "FIN DEL PARTIDO", Color.WHITE, 2.0f);
            String result = matchResultText();
            font.getData().setScale(1.2f);
            GlyphLayout resultLayout = new GlyphLayout(font, result);
            font.setColor(0.95f, 0.9f, 0.25f, 1f);
            font.draw(batch, resultLayout, paneWidthPx / 2f - resultLayout.width / 2f, paneHeightPx / 2f - 26f);

            font.getData().setScale(0.9f);
            String possession = "POSESION  AZUL " + sim.getPossessionPercentLeft() + "%  -  "
                    + (100 - sim.getPossessionPercentLeft()) + "% ROJO";
            GlyphLayout possLayout = new GlyphLayout(font, possession);
            font.setColor(0.85f, 0.85f, 0.9f, 1f);
            font.draw(batch, possLayout, paneWidthPx / 2f - possLayout.width / 2f, paneHeightPx / 2f - 46f);

            font.getData().setScale(0.8f);
            GlyphLayout hint = new GlyphLayout(font, "Presioná R para reiniciar");
            font.setColor(0.85f, 0.85f, 0.85f, 1f);
            font.draw(batch, hint, paneWidthPx / 2f - hint.width / 2f, paneHeightPx / 2f - 78f);
            font.getData().setScale(1f);
        }
        batch.end();
    }

    /** Recordatorio breve de controles, mostrado solo durante el saque inicial del partido (tutorial mínimo in-context). */
    private void drawControlsHint(int paneWidthPx, int paneHeightPx, boolean isPlayerOne) {
        String hint = isPlayerOne
                ? "WASD mover | SHIFT correr | ESPACIO cargar/patear | CTRL planchazo"
                : "Flechas mover | CTRL correr | ENTER cargar/patear | SHIFT planchazo";
        font.getData().setScale(0.85f);
        GlyphLayout layout = new GlyphLayout(font, hint);
        font.setColor(0f, 0f, 0f, 0.6f);
        font.draw(batch, layout, paneWidthPx / 2f - layout.width / 2f + 1f, 64f);
        font.setColor(1f, 1f, 1f, 0.9f);
        font.draw(batch, layout, paneWidthPx / 2f - layout.width / 2f, 65f);
        font.getData().setScale(1f);
    }

    private void drawBigCenteredText(int paneWidthPx, int paneHeightPx, String text, Color color, float scale) {
        font.getData().setScale(scale);
        GlyphLayout layout = new GlyphLayout(font, text);
        font.setColor(0f, 0f, 0f, 0.55f);
        font.draw(batch, layout, paneWidthPx / 2f - layout.width / 2f + 2f, paneHeightPx / 2f + layout.height / 2f + 38f);
        font.setColor(color);
        font.draw(batch, layout, paneWidthPx / 2f - layout.width / 2f, paneHeightPx / 2f + layout.height / 2f + 40f);
        font.getData().setScale(1f);
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
        return (sl > sr ? "GANA AZUL " : "GANA ROJO ") + sl + " - " + sr;
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
        font.getData().setScale(1.1f);
        GlyphLayout phaseLayout = new GlyphLayout(font, phaseLabel);
        font.setColor(0.95f, 0.8f, 0.25f, 1f);
        font.draw(batch, phaseLayout, centerX - phaseLayout.width / 2f, hudHeight - 6f);

        font.getData().setScale(0.9f);
        GlyphLayout matchLayout = new GlyphLayout(font, Constants.MATCH_TITLE);
        font.setColor(0.85f, 0.85f, 0.9f, 1f);
        font.draw(batch, matchLayout, centerX - matchLayout.width / 2f, panelY - 2f);

        String timeText = "TIME: " + formatTime(sim.getHalfTimeRemaining());
        font.getData().setScale(1.05f);
        font.setColor(Color.WHITE);
        font.draw(batch, timeText, clockCx + 26f, hudHeight / 2f + 8f);

        drawTeamScore(centerX, panelY, panelH);

        font.getData().setScale(1f);
        batch.end();

        drawPossessionBar(centerX, panelY);
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
        shapes.setColor(Constants.TEAM_LEFT_COLOR);
        shapes.rect(x, y, leftW, barH);
        shapes.setColor(Constants.TEAM_RIGHT_COLOR);
        shapes.rect(x + leftW, y, barW - leftW, barH);
        shapes.end();
    }

    private void drawTeamScore(float centerX, float panelY, float panelH) {
        font.getData().setScale(1.4f);
        String left = "BLUE";
        String mid = "  " + sim.getScoreLeft() + " - " + sim.getScoreRight() + "  ";
        String right = "RED";

        GlyphLayout leftLayout = new GlyphLayout(font, left);
        GlyphLayout midLayout = new GlyphLayout(font, mid);
        GlyphLayout rightLayout = new GlyphLayout(font, right);

        float totalWidth = leftLayout.width + midLayout.width + rightLayout.width;
        float startX = centerX - totalWidth / 2f;
        float scoreY = panelY + panelH / 2f + midLayout.height / 2f;

        font.setColor(Constants.TEAM_LEFT_COLOR);
        font.draw(batch, leftLayout, startX, scoreY);
        font.setColor(Color.WHITE);
        font.draw(batch, midLayout, startX + leftLayout.width, scoreY);
        font.setColor(Constants.TEAM_RIGHT_COLOR);
        font.draw(batch, rightLayout, startX + leftLayout.width + midLayout.width, scoreY);
    }

    private String phaseLabel() {
        switch (sim.getPhase()) {
            case HALFTIME:
                return "HALFTIME";
            case FULL_TIME:
                return "FULL TIME";
            default:
                return sim.getHalf() == 1 ? "FIRST HALF" : "SECOND HALF";
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

    private void drawPauseOverlay() {
        Gdx.gl.glViewport(0, 0, screenWidth, screenHeight);
        hudCam.setToOrtho(false, screenWidth, screenHeight);
        hudCam.update();

        shapes.setProjectionMatrix(hudCam.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.55f);
        shapes.rect(0, 0, screenWidth, screenHeight);
        shapes.end();

        batch.setProjectionMatrix(hudCam.combined);
        batch.begin();
        font.getData().setScale(2.4f);
        GlyphLayout layout = new GlyphLayout(font, "PAUSA");
        font.setColor(Color.WHITE);
        font.draw(batch, layout, screenWidth / 2f - layout.width / 2f, screenHeight / 2f + layout.height / 2f);
        font.getData().setScale(0.9f);
        GlyphLayout hint = new GlyphLayout(font, "Presioná P para continuar");
        font.setColor(0.85f, 0.85f, 0.85f, 1f);
        font.draw(batch, hint, screenWidth / 2f - hint.width / 2f, screenHeight / 2f - 24f);
        font.getData().setScale(1f);
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
    }

    @Override
    public void dispose() {
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
        texGrass.dispose();
        texCrowd.dispose();

        if (sfxKickSoft != null) sfxKickSoft.dispose();
        if (sfxKickHard != null) sfxKickHard.dispose();
        if (sfxThud != null) sfxThud.dispose();
        if (sfxWhistle != null) sfxWhistle.dispose();
        if (sfxGoal != null) sfxGoal.dispose();
        if (sfxFullTime != null) sfxFullTime.dispose();
    }
}
