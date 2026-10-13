package com.rivalesfc.game;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.rivalesfc.game.screens.LobbyScreen;

/**
 * Clase principal de "Rivales F.C.".
 *
 * Modo actual (local, sin red, secc. 2.5 de la propuesta): arranca en un
 * lobby mínimo (secc. 1.1, "Lobby de sala previo al partido") donde se
 * elige el modo de juego (1 jugador contra IA de soporte, o 2 jugadores en
 * pantalla dividida) y recién ahí entra a la pantalla de partido. La etapa
 * siguiente reemplazará el modo 2 jugadores local por un cliente conectado
 * en red, reutilizando el mismo lobby para elegir sala en vez de modo.
 */
public class RivalesFCGame extends Game {

    /** MK6: logo del club (assets/logo.png), compartido por el lobby y el HUD del partido. Puede ser null si falta el archivo. */
    public Texture logo;

    @Override
    public void create() {
        Settings.load();
        try {
            logo = new Texture(Gdx.files.internal("logo.png"), true); // MK7: con mipmaps, se reduce sin dentado
            logo.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        } catch (RuntimeException e) {
            logo = null; // sin logo el juego funciona igual
        }
        setScreen(new LobbyScreen(this));
    }

    @Override
    public void dispose() {
        super.dispose();
        if (logo != null) {
            logo.dispose();
        }
    }
}
