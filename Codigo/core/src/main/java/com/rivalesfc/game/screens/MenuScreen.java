package com.rivalesfc.game.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.rivalesfc.game.RivalesFCGame;
import com.rivalesfc.game.gfx.Fonts;

import java.util.ArrayList;
import java.util.List;

/**
 * MK8: base de las pantallas de menú (principal, preparación del partido, ajustes).
 * Dibuja en un lienzo virtual de 1280x800 que se escala a la ventana (con barras si la proporción no coincide),
 * así los menús se ven bien a cualquier resolución (en la MK7 el lobby no escalaba).
 * Las zonas clicables se registran al dibujar ({@link #hot}) y se atienden en el cuadro siguiente.
 */
abstract class MenuScreen implements Screen {

    static final float VW = 1280f;
    static final float VH = 800f;

    protected static final Color GOLD = new Color(0.98f, 0.88f, 0.30f, 1f);
    protected static final Color PANEL = new Color(0.10f, 0.11f, 0.13f, 0.94f);
    protected static final Color PANEL_SEL = new Color(0.16f, 0.32f, 0.18f, 1f);
    protected static final Color GREY = new Color(0.78f, 0.80f, 0.84f, 1f);

    protected final RivalesFCGame game;
    protected final ShapeRenderer shapes = new ShapeRenderer();
    protected final SpriteBatch batch = new SpriteBatch();
    protected final BitmapFont font = Fonts.create();
    private final OrthographicCamera cam = new OrthographicCamera();

    private int sw = 1280, sh = 800;
    private float scale = 1f, offX = 0f, offY = 0f;
    protected float time = 0f;
    /** Se pidió ocultar la pantalla (cambio a otra) y los recursos ya se liberaron o se liberan al terminar el cuadro. */
    private boolean hidden = false;
    private boolean inRender = false;
    private boolean disposed = false;

    /** Zonas clicables del cuadro anterior: {x, y, w, h, id}. */
    private List<float[]> hot = new ArrayList<>();
    private List<float[]> hotNext = new ArrayList<>();

    protected MenuScreen(RivalesFCGame game) {
        this.game = game;
    }

    /** Entrada y lógica (se llama antes de dibujar). {@code clickId} es la zona clicada o -1. */
    protected abstract void update(float dt, int clickId);

    protected abstract void draw();

    @Override
    public void show() {
        Fonts.setUiScale(1f);
    }

    @Override
    public void render(float delta) {
        time += Math.min(delta, 0.1f);
        updateViewport();
        inRender = true;
        update(delta, pollClick());
        inRender = false;
        if (hidden) {
            // El update cambió de pantalla (setScreen llama a hide() acá adentro): esta ya no se dibuja y se libera ahora.
            // Antes hide() liberaba al instante y el render seguía dibujando con el SpriteBatch/fuente destruidos: se cerraba la ventana.
            dispose();
            return;
        }

        hot = hotNext;
        hotNext = new ArrayList<>();

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glViewport(0, 0, sw, sh);
        Gdx.gl.glClearColor(0.03f, 0.05f, 0.04f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glViewport(Math.round(offX), Math.round(offY), Math.round(VW * scale), Math.round(VH * scale));
        cam.setToOrtho(false, VW, VH);
        cam.update();
        shapes.setProjectionMatrix(cam.combined);
        batch.setProjectionMatrix(cam.combined);

        drawStripes();
        draw();
    }

    private void updateViewport() {
        scale = Math.min(sw / VW, sh / VH);
        offX = (sw - VW * scale) / 2f;
        offY = (sh - VH * scale) / 2f;
    }

    private int pollClick() {
        if (!Gdx.input.justTouched()) {
            return -1;
        }
        float vx = (Gdx.input.getX() - offX) / scale;
        float vy = (sh - Gdx.input.getY() - offY) / scale;
        for (int i = hot.size() - 1; i >= 0; i--) {
            float[] r = hot.get(i);
            if (vx >= r[0] && vx <= r[0] + r[2] && vy >= r[1] && vy <= r[1] + r[3]) {
                return (int) r[4];
            }
        }
        return -1;
    }

    /** Registra una zona clicable para el próximo cuadro. */
    protected void hot(float x, float y, float w, float h, int id) {
        hotNext.add(new float[]{x, y, w, h, id});
    }

    // ------------------------------------------------------------------ dibujo

    private void drawStripes() {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int x = 0; x * 80 < VW; x++) {
            shapes.setColor(x % 2 == 0 ? 0.08f : 0.06f, x % 2 == 0 ? 0.12f : 0.10f, x % 2 == 0 ? 0.09f : 0.07f, 1f);
            shapes.rect(x * 80, 0, 80, VH);
        }
        shapes.end();
    }

    protected void rect(float x, float y, float w, float h, Color c) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(c);
        shapes.rect(x, y, w, h);
        shapes.end();
    }

    protected void border(float x, float y, float w, float h, Color c) {
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(c);
        shapes.rect(x, y, w, h);
        shapes.end();
    }

    /** Texto con {@code topY} como borde superior. align: -1 izquierda, 0 centro, 1 derecha respecto de x. */
    protected void text(String s, float x, float topY, float size, Color c, int align) {
        batch.begin();
        font.getData().setScale(size * Fonts.K);
        GlyphLayout gl = new GlyphLayout(font, s);
        float dx = align == 0 ? -gl.width / 2f : align > 0 ? -gl.width : 0f;
        font.setColor(0f, 0f, 0f, 0.5f * c.a);
        font.draw(batch, gl, x + dx + 1.5f, topY - 1.5f);
        font.setColor(c);
        font.draw(batch, gl, x + dx, topY);
        font.getData().setScale(Fonts.K);
        batch.end();
    }

    protected float textWidth(String s, float size) {
        font.getData().setScale(size * Fonts.K);
        float w = new GlyphLayout(font, s).width;
        font.getData().setScale(Fonts.K);
        return w;
    }

    /** Cabecera: franja negra con el logo (o el título en texto) y un subtítulo. Ocupa los 150 px de arriba. */
    protected void drawHeader(String subtitle) {
        float bandH = 150f;
        float y0 = VH - bandH;
        rect(0, y0, VW, bandH, Color.BLACK);
        rect(0, y0 - 3f, VW, 3f, new Color(0.95f, 0.78f, 0.15f, 1f));
        if (game.logo != null) {
            batch.begin();
            batch.setColor(Color.WHITE);
            batch.draw(game.logo, VW / 2f - bandH / 2f, y0, bandH, bandH);
            batch.end();
        } else {
            text("RIVALES F.C.", VW / 2f, VH - 40f, 3.2f, GOLD, 0);
        }
        text(subtitle, 28f, y0 + 40f, 1.3f, new Color(0.85f, 0.9f, 0.85f, 1f), -1);
        text("MK8", VW - 28f, y0 + 40f, 1.3f, GOLD, 1);
    }

    protected void drawFooter(String hint) {
        text(hint, VW / 2f, 38f, 1f, new Color(0.8f, 0.8f, 0.8f, 0.9f), 0);
    }

    /** Fila de ajuste: etiqueta a la izquierda, valor a la derecha (con flechas si está elegida). Registra clic en la fila y en cada flecha. */
    protected void drawRow(float x, float y, float w, float h, String label, String value, boolean sel, boolean arrows,
                           int idRow, int idLeft, int idRight) {
        rect(x, y, w, h, sel ? PANEL_SEL : PANEL);
        border(x, y, w, h, sel ? GOLD : new Color(0.4f, 0.4f, 0.45f, 1f));
        float ty = y + h / 2f + 8f;
        text(label, x + 16f, ty, 1.0f, sel ? Color.WHITE : GREY, -1);
        boolean showArrows = arrows && sel;
        text((showArrows ? "<   " : "") + value + (showArrows ? "   >" : ""), x + w - 16f, ty, 1.05f, sel ? GOLD : Color.WHITE, 1);
        hot(x, y, w, h, idRow);
        if (arrows) {
            hot(x + w - 190f, y, 95f, h, idLeft);
            hot(x + w - 95f, y, 95f, h, idRight);
        }
    }

    /** Camiseta del equipo (cuerpo + short) como cuadro de color. */
    protected void drawKit(float x, float y, float w, float h, com.rivalesfc.game.Team t) {
        rect(x, y, w, h, new Color(0f, 0f, 0f, 0.35f));
        rect(x + w * 0.18f, y + h * 0.34f, w * 0.64f, h * 0.58f, t.color);
        rect(x + w * 0.28f, y + h * 0.08f, w * 0.44f, h * 0.28f, t.shorts);
        border(x, y, w, h, Color.WHITE);
    }

    @Override
    public void resize(int width, int height) {
        sw = Math.max(2, width);
        sh = Math.max(2, height);
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
        // Las pantallas de menú no se reutilizan: se liberan la fuente y los renderers, pero NUNCA en medio de su propio render().
        hidden = true;
        if (!inRender) {
            dispose();
        }
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }
}
