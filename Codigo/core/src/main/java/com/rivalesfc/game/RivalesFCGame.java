package com.rivalesfc.game;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.rivalesfc.game.screens.LobbyScreen;

public class RivalesFCGame extends Game {

    public Texture logo;

    @Override
    public void create() {
        Settings.load();
        try {
            logo = new Texture(Gdx.files.internal("logo.png"), true);
            logo.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        } catch (RuntimeException e) {
            logo = null;
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
