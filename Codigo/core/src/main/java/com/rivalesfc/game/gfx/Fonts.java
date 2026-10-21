package com.rivalesfc.game.gfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;

public final class Fonts {

    public static final int BASE_SIZE = 64;
    public static final float TARGET_SIZE = 18f;

    public static float K = TARGET_SIZE / BASE_SIZE;

    public static float baseK = K;

    public static void setUiScale(float s) {
        K = baseK * s;
    }

    private static final String CHARS = buildChars();

    private Fonts() {
    }

    private static String buildChars() {
        StringBuilder sb = new StringBuilder();
        for (char c = 32; c < 127; c++) {
            sb.append(c);
        }
        sb.append("áéíóúüñÁÉÍÓÚÜÑ¡¿—–·•°×«»…’‘“”");
        return sb.toString();
    }

    public static BitmapFont create() {
        try {
            FreeTypeFontGenerator gen = new FreeTypeFontGenerator(Gdx.files.internal("font.ttf"));
            FreeTypeFontGenerator.FreeTypeFontParameter p = new FreeTypeFontGenerator.FreeTypeFontParameter();
            p.size = BASE_SIZE;
            p.characters = CHARS;
            p.color = Color.WHITE;
            p.kerning = true;
            p.genMipMaps = true;
            p.minFilter = Texture.TextureFilter.MipMapLinearLinear;
            p.magFilter = Texture.TextureFilter.Linear;
            BitmapFont font = gen.generateFont(p);
            gen.dispose();
            font.setUseIntegerPositions(false);
            K = TARGET_SIZE / BASE_SIZE;
            baseK = K;
            return font;
        } catch (Throwable t) {

            Gdx.app.error("Fonts", "No se pudo generar la fuente (" + t + "); uso la de respaldo");
            BitmapFont font = new BitmapFont();
            font.getRegion().getTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            font.setUseIntegerPositions(false);
            K = 1f;
            baseK = 1f;
            return font;
        }
    }
}
