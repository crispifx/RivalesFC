package com.rivalesfc.game.gfx;

import java.nio.ByteBuffer;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.rivalesfc.game.Constants;

/**
 * Fábrica de texturas del juego (se conserva el nombre por compatibilidad).
 *
 * MK7: ya no es pixel-art de baja resolución. El dibujo lo hace {@link ArtPainter}
 * (alta resolución, antialiasing por supermuestreo) y acá solo se sube a la GPU con
 * filtro trilineal + mipmaps, así que se ve suave a cualquier zoom en vez de con
 * "escalones" (antes: sprites de 20x32 px ampliados con filtro Nearest).
 *
 * Los mapas de bits de la cancha y la tribuna se cachean (son datos puros), así que
 * a partir del segundo partido solo se vuelve a subir la textura.
 */
public final class PixelArtFactory {

    /** Resolución de la textura de la cancha (píxeles por metro). */
    public static final float GROUND_PPM = 32f;

    /** Factor entre un píxel de textura de jugador y su tamaño original (20 px de ancho en la MK6). Sirve para escalar el radio del blur. */
    public static final float PERSON_TEXEL_SCALE = ArtPainter.PERSON_W / 20f;

    /** Altura (m) de la franja de tribuna: el margen menos el cartel publicitario. */
    public static final float CROWD_BAND_HEIGHT = Constants.FIELD_MARGIN - 0.60f;

    private static ArtPainter.Bitmap groundCache;
    private static ArtPainter.Bitmap crowdCache;

    private PixelArtFactory() {
    }

    private static Texture toTexture(ArtPainter.Bitmap bmp) {
        Pixmap pm = new Pixmap(bmp.w, bmp.h, Pixmap.Format.RGBA8888);
        ByteBuffer buf = pm.getPixels();
        int[] src = bmp.argb;
        for (int i = 0; i < src.length; i++) {
            int c = src[i];
            int o = i * 4;
            buf.put(o, (byte) (c >> 16));
            buf.put(o + 1, (byte) (c >> 8));
            buf.put(o + 2, (byte) c);
            buf.put(o + 3, (byte) (c >>> 24));
        }
        Texture tex = new Texture(pm, true); // con mipmaps
        tex.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        tex.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
        pm.dispose();
        return tex;
    }

    private static int argb(Color c) {
        return SoftCanvas.rgb(c.r, c.g, c.b);
    }

    /** Jugador (o arquero con guantes) de frente, 120x192, suave y con contorno. */
    public static Texture buildPersonTexture(Color jersey, Color shorts, boolean goalkeeper) {
        return toTexture(ArtPainter.person(argb(jersey), argb(shorts), goalkeeper));
    }

    /** Pelota con sombreado de volumen y brillo, 160x160. */
    public static Texture buildBallTexture() {
        return toTexture(ArtPainter.ball());
    }

    /** Cancha completa: pasto con rayas y textura, líneas, arcos con red, carteles. Cubre cancha + margen. */
    public static Texture buildGroundTexture() {
        if (groundCache == null) {
            groundCache = ArtPainter.ground(Constants.FIELD_WIDTH, Constants.FIELD_HEIGHT, Constants.FIELD_MARGIN,
                    Constants.GOAL_WIDTH, Constants.GOAL_DEPTH, Constants.PENALTY_BOX_DEPTH,
                    Constants.PENALTY_BOX_HEIGHT, Constants.GOAL_BOX_DEPTH, Constants.GOAL_BOX_HEIGHT, GROUND_PPM);
        }
        return toTexture(groundCache);
    }

    /** Franja de tribuna con la proporción real (ancho de cancha + márgenes por {@link #CROWD_BAND_HEIGHT}). */
    public static Texture buildCrowdTexture() {
        if (crowdCache == null) {
            int w = Math.round((Constants.FIELD_WIDTH + Constants.FIELD_MARGIN * 2f) * GROUND_PPM);
            int h = Math.round(CROWD_BAND_HEIGHT * GROUND_PPM);
            crowdCache = ArtPainter.crowd(w, h);
        }
        return toTexture(crowdCache);
    }

    /** Sombra elíptica suave para jugadores y pelota. */
    public static Texture buildHaloTexture() {
        return toTexture(ArtPainter.halo());
    }

    public static Texture buildShadowTexture() {
        return toTexture(ArtPainter.shadow());
    }

    /** Anillo elíptico que marca al jugador propio. */
    public static Texture buildRingTexture() {
        return toTexture(ArtPainter.ring());
    }
}
