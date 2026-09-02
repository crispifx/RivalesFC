package com.rivalesfc.game;

import com.badlogic.gdx.Game;
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

    @Override
    public void create() {
        setScreen(new LobbyScreen(this));
    }
}
