package com.rivalesfc.game.desktop;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.rivalesfc.game.RivalesFCGame;
import com.rivalesfc.game.input.Gamepads;

public class DesktopLauncher {

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Rivales F.C.");

        config.setWindowIcon(Files.FileType.Internal, "icon128.png", "icon64.png", "icon32.png", "icon16.png");
        config.setWindowedMode(1280, 800);
        config.useVsync(true);

        config.setBackBufferConfig(8, 8, 8, 8, 16, 0, 4);
        config.setResizable(true);
        config.setForegroundFPS(60);
        Gamepads.provider = new GlfwGamepads();
        new Lwjgl3Application(new RivalesFCGame(), config);
    }
}
