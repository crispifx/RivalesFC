package com.rivalesfc.game.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.rivalesfc.game.RivalesFCGame;
import com.rivalesfc.game.Settings;

/**
 * Menú principal (MK8). En la MK7 el lobby era una sola pantalla con modos, nombre, equipos, online y ajustes
 * apretados; ahora se reparte en tres: este menú, {@link SetupScreen} (modo, nombre y equipos) y
 * {@link SettingsScreen} (dificultad, duración, volumen y faltas).
 */
public class LobbyScreen extends MenuScreen {

    private static final String[] ITEMS = {"JUGAR", "AJUSTES", "SALIR"};
    private static final String[] DESC = {
            "Elegí el modo, tu nombre y los equipos",
            "Dificultad, duración, volumen y faltas",
            "Cerrar el juego"};
    private int row = 0;

    public LobbyScreen(RivalesFCGame game) {
        super(game);
    }

    @Override
    protected void update(float dt, int click) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP) || Gdx.input.isKeyJustPressed(Input.Keys.W)) row = (row + 2) % 3;
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN) || Gdx.input.isKeyJustPressed(Input.Keys.S)) row = (row + 1) % 3;
        boolean go = Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.isKeyJustPressed(Input.Keys.SPACE);
        if (click >= 0 && click < 3) {
            row = click;
            go = true;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) { row = 0; go = true; }
        if (go) {
            if (row == 0) game.setScreen(new SetupScreen(game));
            else if (row == 1) game.setScreen(new SettingsScreen(game));
            else Gdx.app.exit();
        }
    }

    @Override
    protected void draw() {
        drawHeader("Menú principal");
        float w = 480f, h = 78f, x = VW / 2f - w / 2f;
        float y = 470f;
        for (int i = 0; i < ITEMS.length; i++) {
            boolean sel = i == row;
            rect(x, y, w, h, sel ? PANEL_SEL : PANEL);
            border(x, y, w, h, sel ? GOLD : new Color(0.4f, 0.4f, 0.45f, 1f));
            if (sel) border(x + 3, y + 3, w - 6, h - 6, GOLD);
            text(ITEMS[i], VW / 2f, y + h - 14f, 1.8f, sel ? GOLD : Color.WHITE, 0);
            text(DESC[i], VW / 2f, y + 26f, 0.95f, GREY, 0);
            hot(x, y, w, h, i);
            y -= h + 18f;
        }
        text("Faltas y tarjetas: " + (Settings.foulsEnabled ? "SÍ" : "NO") + "   -   Dificultad: " + Settings.difficulty.label
                + "   -   " + Settings.halfMinutes + " min por tiempo", VW / 2f, 150f, 0.95f, GREY, 0);
        drawFooter("Flechas: elegir   -   ENTER o clic: confirmar   -   1: ir directo a JUGAR");
    }
}
