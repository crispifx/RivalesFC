package com.rivalesfc.game.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.rivalesfc.game.GameMode;
import com.rivalesfc.game.RivalesFCGame;
import com.rivalesfc.game.Settings;
import com.rivalesfc.game.net.NetClient;
import com.rivalesfc.game.net.NetHost;
import com.rivalesfc.game.net.NetInfo;
import com.rivalesfc.game.net.Protocol;

import java.io.IOException;

/**
 * Lobby previo al partido (secc. 1.1 "Lobby de sala previo al partido" de la
 * propuesta, versión mínima local sin red todavía): permite elegir el modo
 * de juego —1 jugador (contra una IA de soporte) o 2 jugadores (pantalla
 * dividida local, como en la Etapa 1 original)— antes de entrar a
 * {@link MatchScreen}. Se dibuja a mano con {@code ShapeRenderer}/{@code BitmapFont},
 * igual que el resto de la interfaz del proyecto (no se usa Scene2D en
 * ningún otro lado, así que no vale la pena sumar esa dependencia acá).
 *
 * Controles: flechas o W/S para elegir, ENTER o ESPACIO para confirmar.
 */
public class LobbyScreen implements Screen {

    private final RivalesFCGame game;
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();
    private final OrthographicCamera cam = new OrthographicCamera();

    private int screenWidth = 1280;
    private int screenHeight = 800;

    /**
     * Filas navegables: 0 = 1 jugador, 1 = 2 jugadores, 2 = crear partida online (host),
     * 3 = unirse a partida online (IP), 4 = dificultad, 5 = duración, 6 = volumen.
     */
    private int row = 0;
    private static final int ROWS = 7;
    private static final int ROW_HOST = 2;
    private static final int ROW_JOIN = 3;
    private static final int ROW_OPTIONS = 4;

    private final String localIp = NetInfo.localAddress();
    private String joinIp = Settings.lastHostIp;
    private String status = "";

    public LobbyScreen(RivalesFCGame game) {
        this.game = game;
    }

    @Override
    public void show() {
        // Solo para escribir la IP: acepta dígitos, punto y dos puntos cuando está elegida la fila "unirse".
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean keyTyped(char c) {
                if (row == ROW_JOIN && (Character.isDigit(c) || c == '.' || c == ':') && joinIp.length() < 21) {
                    joinIp += c;
                    status = "";
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public void render(float delta) {
        handleInput();

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glViewport(0, 0, screenWidth, screenHeight);
        Gdx.gl.glClearColor(0.05f, 0.09f, 0.06f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        cam.setToOrtho(false, screenWidth, screenHeight);
        cam.update();
        shapes.setProjectionMatrix(cam.combined);
        batch.setProjectionMatrix(cam.combined);

        drawBackgroundStripes();
        drawTitle();
        drawOptionCards();
        drawFooterHint();
    }

    private void handleInput() {
        boolean up = Gdx.input.isKeyJustPressed(Input.Keys.UP) || Gdx.input.isKeyJustPressed(Input.Keys.W);
        boolean down = Gdx.input.isKeyJustPressed(Input.Keys.DOWN) || Gdx.input.isKeyJustPressed(Input.Keys.S);
        if (up) row = (row + ROWS - 1) % ROWS;
        if (down) row = (row + 1) % ROWS;
        if (row == ROW_JOIN && Gdx.input.isKeyJustPressed(Input.Keys.BACKSPACE) && !joinIp.isEmpty()) {
            joinIp = joinIp.substring(0, joinIp.length() - 1);
        }

        int dir = 0;
        if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT) || Gdx.input.isKeyJustPressed(Input.Keys.A)) dir = -1;
        if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT) || Gdx.input.isKeyJustPressed(Input.Keys.D)) dir = 1;
        if (dir != 0) {
            adjust(row, dir);
        }

        boolean confirm = Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.isKeyJustPressed(Input.Keys.SPACE);
        GameMode start = null;
        if (confirm && row == 0) start = GameMode.ONE_PLAYER;
        if (confirm && row == 1) start = GameMode.TWO_PLAYERS;
        // Atajos directos: 1 o 2 elige y confirma de una (no con la fila de IP: ahí los números se escriben).
        if (row != ROW_JOIN && Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) start = GameMode.ONE_PLAYER;
        if (row != ROW_JOIN && Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) start = GameMode.TWO_PLAYERS;
        // Con Enter sobre una fila de opciones no arranca: cicla el valor.
        if (confirm && row >= ROW_OPTIONS) {
            adjust(row, 1);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            if (row == ROW_HOST) startOnlineHost();
            if (row == ROW_JOIN) startOnlineClient();
        }

        if (start != null) {
            Settings.save();
            game.setScreen(new MatchScreen(game, start));
        }
    }

    private void startOnlineHost() {
        NetHost host = new NetHost();
        try {
            host.start(Protocol.DEFAULT_PORT);
        } catch (IOException e) {
            status = "No se pudo abrir el puerto " + Protocol.DEFAULT_PORT + " (¿ya hay otro juego abierto?)";
            return;
        }
        Settings.save();
        game.setScreen(new MatchScreen(game, GameMode.ONLINE_HOST, host, null, localIp));
    }

    private void startOnlineClient() {
        String text = joinIp.trim();
        if (text.isEmpty()) {
            status = "Escribí la IP del host (la muestra su lobby).";
            return;
        }
        String ip = text;
        int port = Protocol.DEFAULT_PORT;
        int colon = text.indexOf(':');
        try {
            if (colon >= 0) {
                ip = text.substring(0, colon);
                port = Integer.parseInt(text.substring(colon + 1));
            }
            NetClient client = new NetClient();
            client.connect(ip, port);
            Settings.lastHostIp = text;
            Settings.save();
            game.setScreen(new MatchScreen(game, GameMode.ONLINE_CLIENT, null, client, text));
        } catch (IOException | RuntimeException e) {
            status = "IP o puerto inválido: " + text;
        }
    }

    /** Cambia el valor de una fila de opciones (dir = -1 / +1) y lo guarda. */
    private void adjust(int r, int dir) {
        if (r == 4) {
            int n = Settings.Difficulty.values().length;
            Settings.difficulty = Settings.Difficulty.values()[(Settings.difficulty.ordinal() + dir + n) % n];
        } else if (r == 5) {
            int idx = 0;
            for (int i = 0; i < Settings.HALF_MINUTES_OPTIONS.length; i++) {
                if (Settings.HALF_MINUTES_OPTIONS[i] == Settings.halfMinutes) idx = i;
            }
            int n = Settings.HALF_MINUTES_OPTIONS.length;
            Settings.halfMinutes = Settings.HALF_MINUTES_OPTIONS[(idx + dir + n) % n];
        } else if (r == 6) {
            Settings.muted = false;
            Settings.volume = Math.max(0f, Math.min(1f, Math.round((Settings.volume + dir * 0.1f) * 10f) / 10f));
        } else {
            return;
        }
        Settings.save();
    }

    private void drawBackgroundStripes() {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Color light = new Color(0.08f, 0.12f, 0.09f, 1f);
        Color dark = new Color(0.06f, 0.10f, 0.07f, 1f);
        int stripeW = 80;
        for (int x = 0; x * stripeW < screenWidth; x++) {
            shapes.setColor(x % 2 == 0 ? light : dark);
            shapes.rect(x * stripeW, 0, stripeW, screenHeight);
        }
        shapes.end();
    }

    private void drawTitle() {
        batch.begin();
        font.getData().setScale(3.2f);
        GlyphLayout title = new GlyphLayout(font, "RIVALES F.C.");
        font.setColor(0f, 0f, 0f, 0.55f);
        font.draw(batch, title, screenWidth / 2f - title.width / 2f + 3f, screenHeight * 0.82f - 3f);
        font.setColor(0.95f, 0.85f, 0.2f, 1f);
        font.draw(batch, title, screenWidth / 2f - title.width / 2f, screenHeight * 0.82f);

        font.getData().setScale(1.1f);
        GlyphLayout subtitle = new GlyphLayout(font, "MK4 — elegí cómo jugar");
        font.setColor(0.85f, 0.9f, 0.85f, 1f);
        font.draw(batch, subtitle, screenWidth / 2f - subtitle.width / 2f, screenHeight * 0.82f - 46f);
        font.getData().setScale(1f);
        batch.end();
    }

    private void drawOptionCards() {
        float cardW = Math.min(520f, screenWidth * 0.6f);
        float cardH = 70f;
        float rowH = 38f;
        float gap = 8f;
        float x = screenWidth / 2f - cardW / 2f;
        float y = screenHeight * 0.70f;

        y -= cardH;
        drawOptionCard(x, y, cardW, cardH, "1 JUGADOR", "Pantalla completa: vos (WASD) contra la IA", row == 0);
        y -= cardH + gap;
        drawOptionCard(x, y, cardW, cardH, "2 JUGADORES", "Pantalla dividida local: WASD vs. Flechas", row == 1);

        y -= 12f + rowH;
        drawSettingRow(x, y, cardW, rowH, "ONLINE: CREAR PARTIDA", "ENTER  -  tu IP: " + localIp, row == ROW_HOST, false);
        y -= rowH + gap;
        drawSettingRow(x, y, cardW, rowH, "ONLINE: UNIRSE (IP del host)",
                joinIp + (row == ROW_JOIN ? "_" : ""), row == ROW_JOIN, false);

        y -= 12f + rowH;
        drawSettingRow(x, y, cardW, rowH, "DIFICULTAD (IA y arqueros)", Settings.difficulty.label, row == 4);
        y -= rowH + gap;
        drawSettingRow(x, y, cardW, rowH, "DURACION DE CADA TIEMPO", Settings.halfMinutes + " min", row == 5);
        y -= rowH + gap;
        String vol = Settings.muted ? "SILENCIO" : Math.round(Settings.volume * 100) + "%";
        drawSettingRow(x, y, cardW, rowH, "VOLUMEN", vol, row == 6);

        if (!status.isEmpty()) {
            batch.begin();
            font.getData().setScale(1f);
            GlyphLayout sl = new GlyphLayout(font, status);
            font.setColor(1f, 0.45f, 0.4f, 1f);
            font.draw(batch, sl, screenWidth / 2f - sl.width / 2f, 76f);
            batch.end();
        }
    }

    private void drawSettingRow(float x, float y, float w, float h, String label, String value, boolean isSelected) {
        drawSettingRow(x, y, w, h, label, value, isSelected, true);
    }

    private void drawSettingRow(float x, float y, float w, float h, String label, String value, boolean isSelected, boolean showArrows) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(isSelected ? new Color(0.16f, 0.32f, 0.18f, 1f) : new Color(0.10f, 0.11f, 0.13f, 0.92f));
        shapes.rect(x, y, w, h);
        shapes.end();
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(isSelected ? new Color(0.95f, 0.85f, 0.2f, 1f) : new Color(0.4f, 0.4f, 0.45f, 1f));
        shapes.rect(x, y, w, h);
        shapes.end();

        batch.begin();
        font.getData().setScale(0.95f);
        font.setColor(isSelected ? Color.WHITE : new Color(0.8f, 0.8f, 0.82f, 1f));
        font.draw(batch, label, x + 16f, y + h / 2f + 7f);
        GlyphLayout v = new GlyphLayout(font, (isSelected && showArrows ? "<  " : "") + value + (isSelected && showArrows ? "  >" : ""));
        font.setColor(isSelected ? new Color(0.98f, 0.9f, 0.35f, 1f) : Color.WHITE);
        font.draw(batch, v, x + w - v.width - 16f, y + h / 2f + 7f);
        font.getData().setScale(1f);
        batch.end();
    }

    private void drawOptionCard(float x, float y, float w, float h, String label, String desc, boolean isSelected) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(isSelected ? new Color(0.16f, 0.32f, 0.18f, 1f) : new Color(0.10f, 0.11f, 0.13f, 0.92f));
        shapes.rect(x, y, w, h);
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(isSelected ? new Color(0.95f, 0.85f, 0.2f, 1f) : new Color(0.4f, 0.4f, 0.45f, 1f));
        shapes.rect(x, y, w, h);
        if (isSelected) {
            shapes.rect(x + 3, y + 3, w - 6, h - 6);
        }
        shapes.end();

        batch.begin();
        font.getData().setScale(1.4f);
        GlyphLayout labelLayout = new GlyphLayout(font, label);
        font.setColor(isSelected ? new Color(0.98f, 0.9f, 0.35f, 1f) : Color.WHITE);
        font.draw(batch, labelLayout, x + w / 2f - labelLayout.width / 2f, y + h - 16f);

        font.getData().setScale(0.95f);
        GlyphLayout descLayout = new GlyphLayout(font, desc);
        font.setColor(0.85f, 0.85f, 0.85f, 1f);
        font.draw(batch, descLayout, x + w / 2f - descLayout.width / 2f, y + h - 46f);

        if (isSelected) {
            font.getData().setScale(1.4f);
            font.setColor(0.95f, 0.85f, 0.2f, 1f);
            font.draw(batch, ">", x + 14f, y + h / 2f + 8f);
        }
        font.getData().setScale(1f);
        batch.end();
    }

    private void drawFooterHint() {
        batch.begin();
        font.getData().setScale(1f);
        String hint = "W-S / Flechas: elegir   -   A-D / Izq-Der: cambiar opción   -   ENTER: confirmar   -   1 / 2: arranca directo";
        GlyphLayout layout = new GlyphLayout(font, hint);
        font.setColor(0.8f, 0.8f, 0.8f, 0.9f);
        font.draw(batch, layout, screenWidth / 2f - layout.width / 2f, 40f);
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
    }
}
