package com.rivalesfc.game.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.rivalesfc.game.RivalesFCGame;
import com.rivalesfc.game.Settings;

/** Ajustes (MK8): dificultad, duración, volumen, faltas y tarjetas, ventaja y roja en 2v2. Se guardan al cambiar. */
public class SettingsScreen extends MenuScreen {

    private static final int DIFF = 0, DUR = 1, VOL = 2, FOULS = 3, ADV = 4, RED2 = 5, BACK = 6, ROWS = 7;
    private int row = 0;

    public SettingsScreen(RivalesFCGame game) {
        super(game);
    }

    private void adjust(int r, int dir) {
        if (r == DIFF) {
            int n = Settings.Difficulty.values().length;
            Settings.difficulty = Settings.Difficulty.values()[(Settings.difficulty.ordinal() + dir + n) % n];
        } else if (r == DUR) {
            int idx = 0;
            for (int i = 0; i < Settings.HALF_MINUTES_OPTIONS.length; i++) {
                if (Settings.HALF_MINUTES_OPTIONS[i] == Settings.halfMinutes) idx = i;
            }
            int n = Settings.HALF_MINUTES_OPTIONS.length;
            Settings.halfMinutes = Settings.HALF_MINUTES_OPTIONS[(idx + dir + n) % n];
        } else if (r == VOL) {
            Settings.muted = false;
            Settings.volume = Math.max(0f, Math.min(1f, Math.round((Settings.volume + dir * 0.1f) * 10f) / 10f));
        } else if (r == FOULS) {
            Settings.foulsEnabled = !Settings.foulsEnabled;
        } else if (r == ADV) {
            Settings.advantageEnabled = !Settings.advantageEnabled;
        } else if (r == RED2) {
            Settings.red2v2Permanent = !Settings.red2v2Permanent;
        } else {
            return;
        }
        Settings.save();
    }

    @Override
    protected void update(float dt, int click) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP) || Gdx.input.isKeyJustPressed(Input.Keys.W)) row = (row + ROWS - 1) % ROWS;
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN) || Gdx.input.isKeyJustPressed(Input.Keys.S)) row = (row + 1) % ROWS;
        int dir = 0;
        if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT) || Gdx.input.isKeyJustPressed(Input.Keys.A)) dir = -1;
        if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT) || Gdx.input.isKeyJustPressed(Input.Keys.D)) dir = 1;
        boolean confirm = Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.isKeyJustPressed(Input.Keys.SPACE);
        if (click >= 0) {
            int r = click % 100;
            row = r;
            if (click >= 200) dir = -1;
            else if (click >= 100) dir = 1;
            else confirm = true;
        }
        if (dir != 0) adjust(row, dir);
        if (confirm) {
            if (row == BACK) game.setScreen(new LobbyScreen(game));
            else adjust(row, 1);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) game.setScreen(new LobbyScreen(game));
    }

    @Override
    protected void draw() {
        drawHeader("Ajustes");
        float w = 640f, h = 48f, x = VW / 2f - w / 2f, y = 560f;
        String vol = Settings.muted ? "SILENCIO" : Math.round(Settings.volume * 100) + "%";
        drawRow(x, y, w, h, "DIFICULTAD (IA y arqueros)", Settings.difficulty.label, row == DIFF, true, DIFF, 200 + DIFF, 100 + DIFF);
        y -= h + 8f;
        drawRow(x, y, w, h, "DURACIÓN DE CADA TIEMPO", Settings.halfMinutes + " min", row == DUR, true, DUR, 200 + DUR, 100 + DUR);
        y -= h + 8f;
        drawRow(x, y, w, h, "VOLUMEN", vol, row == VOL, true, VOL, 200 + VOL, 100 + VOL);
        y -= h + 8f;
        drawRow(x, y, w, h, "FALTAS Y TARJETAS", Settings.foulsEnabled ? "SÍ" : "NO", row == FOULS, true, FOULS, 200 + FOULS, 100 + FOULS);
        y -= h + 8f;
        drawRow(x, y, w, h, "VENTAJA (si el rival conserva la pelota)", Settings.advantageEnabled ? "SÍ" : "NO", row == ADV, true, ADV, 200 + ADV, 100 + ADV);
        y -= h + 8f;
        drawRow(x, y, w, h, "ROJA EN 2 VS 2", Settings.red2v2Permanent ? "HASTA EL FINAL" : "25 s", row == RED2, true, RED2, 200 + RED2, 100 + RED2);
        y -= h + 20f;
        drawRow(x, y, w, h, "VOLVER AL MENÚ", "", row == BACK, false, BACK, 0, 0);

        String help = "";
        if (row == FOULS) {
            help = Settings.foulsEnabled
                    ? "Barridas sin tocar la pelota y choques violentos: tiro libre, penal en el área, amarillas y rojas (expulsión de "
                      + Math.round(com.rivalesfc.game.Constants.RED_SUSPENSION_SECONDS) + " s)."
                    : "Sin faltas: el juego es libre, como en la MK7.";
        } else if (row == ADV) {
            help = "Si te hacen falta pero seguís con la pelota, el juego sigue unos segundos: si la perdés, se cobra la falta. No hay ventaja en penales ni rojas.";
        } else if (row == RED2) {
            help = Settings.red2v2Permanent
                    ? "En el 2v2 la roja expulsa hasta el final del partido (el último jugador de campo nunca queda afuera para siempre)."
                    : "En el 2v2 la roja expulsa 25 s, como en el 1 vs 1.";
        } else if (row == DIFF) {
            help = "Afecta a la IA rival y a los dos arqueros.";
        }
        text(help, VW / 2f, 140f, 0.95f, new Color(0.9f, 0.9f, 0.7f, 1f), 0);
        drawFooter("Flechas: elegir y cambiar   -   ENTER: confirmar   -   ESC: volver");
    }
}
