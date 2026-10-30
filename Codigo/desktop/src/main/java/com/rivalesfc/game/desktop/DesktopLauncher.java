package com.rivalesfc.game.desktop;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.rivalesfc.game.RivalesFCGame;

/** Punto de entrada para escritorio (Windows/Linux/macOS). */
public class DesktopLauncher {

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Rivales F.C.");
        // MK6: icono de la ventana (los PNG viven en assets/, que está en el classpath de core).
        config.setWindowIcon(Files.FileType.Internal, "icon128.png", "icon64.png", "icon32.png", "icon16.png");
        config.setWindowedMode(1280, 800);
        config.useVsync(true);
        // MK7: antialiasing por multisampling (4x) para que círculos, anillos y bordes del HUD no se vean escalonados.
        config.setBackBufferConfig(8, 8, 8, 8, 16, 0, 4);
        config.setResizable(true);
        config.setForegroundFPS(60); // el render corre a 60 fps (secc. 2.3); la simulación va aparte a 30 Hz
        new Lwjgl3Application(new RivalesFCGame(), config);
    }
}
