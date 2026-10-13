package com.rivalesfc.game.gfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;

/**
 * Fuente del juego (MK7).
 *
 * Antes se usaba {@code new BitmapFont()}: la Arial de 15 px que trae LibGDX, que al
 * ampliarla se ve dentada/borrosa y no tiene varios símbolos (por eso en el lobby
 * aparecía un cuadradito en "MK6 — elegí"). Ahora se genera en runtime con FreeType a
 * partir de {@code assets/font.ttf} (Barlow Condensed Bold, licencia OFL) a un tamaño
 * grande, con mipmaps y filtro lineal, y se reduce al dibujar: queda nítida.
 *
 * Como la fuente se genera a {@link #BASE_SIZE} px, todo {@code setScale(x)} del código
 * existente debe multiplicarse por {@link #K} (así {@code setScale(1f)} sigue
 * significando "tamaño normal", unos {@link #TARGET_SIZE} px).
 */
public final class Fonts {

    public static final int BASE_SIZE = 64;
    public static final float TARGET_SIZE = 18f;

    /** Factor a aplicar a cada escala. Vale 1 si hubo que usar la fuente de respaldo. */
    public static float K = TARGET_SIZE / BASE_SIZE;

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
            return font;
        } catch (Throwable t) {
            // Sin FreeType / sin font.ttf: se usa la fuente por defecto, pero suavizada.
            Gdx.app.error("Fonts", "No se pudo generar la fuente (" + t + "); uso la de respaldo");
            BitmapFont font = new BitmapFont();
            font.getRegion().getTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            font.setUseIntegerPositions(false);
            K = 1f;
            return font;
        }
    }
}
