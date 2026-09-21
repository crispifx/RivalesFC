package com.rivalesfc.game.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.rivalesfc.game.RivalesFCGame;

/** Punto de entrada para escritorio (Windows/Linux/macOS). */
public class DesktopLauncher {

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Rivales F.C. - Nucleo local (Etapa 1)");
        config.setWindowedMode(1280, 800);
        config.useVsync(true);
        config.setForegroundFPS(60); // el render corre a 60 fps (secc. 2.3); la simulación va aparte a 30 Hz
        new Lwjgl3Application(new RivalesFCGame(), config);
    }
}
