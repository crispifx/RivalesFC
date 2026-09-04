package com.rivalesfc.game.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
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

    private GameMode selected = GameMode.ONE_PLAYER;

    public LobbyScreen(RivalesFCGame game) {
        this.game = game;
    }

    @Override
    public void show() {
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
        if (up || down) {
            selected = selected == GameMode.ONE_PLAYER ? GameMode.TWO_PLAYERS : GameMode.ONE_PLAYER;
        }

        boolean confirm = Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.isKeyJustPressed(Input.Keys.SPACE);
        // Atajos directos: 1 o 2 elige y confirma de una.
        boolean pressedOne = Gdx.input.isKeyJustPressed(Input.Keys.NUM_1);
        boolean pressedTwo = Gdx.input.isKeyJustPressed(Input.Keys.NUM_2);

        if (pressedOne) {
            selected = GameMode.ONE_PLAYER;
            confirm = true;
        } else if (pressedTwo) {
            selected = GameMode.TWO_PLAYERS;
            confirm = true;
        }

        if (confirm) {
            game.setScreen(new MatchScreen(game, selected));
        }
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
        GlyphLayout subtitle = new GlyphLayout(font, "Núcleo local — elegí cómo jugar");
        font.setColor(0.85f, 0.9f, 0.85f, 1f);
        font.draw(batch, subtitle, screenWidth / 2f - subtitle.width / 2f, screenHeight * 0.82f - 46f);
        font.getData().setScale(1f);
        batch.end();
    }

    private void drawOptionCards() {
        float cardW = Math.min(520f, screenWidth * 0.6f);
        float cardH = 110f;
        float gap = 26f;
        float totalH = cardH * 2 + gap;
        float startY = screenHeight / 2f + totalH / 2f - cardH;
        float x = screenWidth / 2f - cardW / 2f;

        drawOptionCard(x, startY, cardW, cardH,
                "1 JUGADOR",
                "Vos (WASD) contra una IA de soporte",
                selected == GameMode.ONE_PLAYER);

        drawOptionCard(x, startY - cardH - gap, cardW, cardH,
                "2 JUGADORES",
                "Pantalla dividida local: WASD vs. Flechas",
                selected == GameMode.TWO_PLAYERS);
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
        font.getData().setScale(1.6f);
        GlyphLayout labelLayout = new GlyphLayout(font, label);
        font.setColor(isSelected ? new Color(0.98f, 0.9f, 0.35f, 1f) : Color.WHITE);
        font.draw(batch, labelLayout, x + w / 2f - labelLayout.width / 2f, y + h - 22f);

        font.getData().setScale(0.95f);
        GlyphLayout descLayout = new GlyphLayout(font, desc);
        font.setColor(0.85f, 0.85f, 0.85f, 1f);
        font.draw(batch, descLayout, x + w / 2f - descLayout.width / 2f, y + h - 60f);

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
        String hint = "Flechas / W-S para elegir  -  ENTER o ESPACIO para confirmar  -  1 / 2 elige directo";
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
