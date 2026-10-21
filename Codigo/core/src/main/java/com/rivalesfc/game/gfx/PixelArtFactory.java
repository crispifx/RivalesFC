package com.rivalesfc.game.gfx;

import java.nio.ByteBuffer;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.rivalesfc.game.Constants;

public final class PixelArtFactory {

    public static final float GROUND_PPM = 32f;

    public static final float PERSON_TEXEL_SCALE = ArtPainter.PERSON_W / 20f;

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
        Texture tex = new Texture(pm, true);
        tex.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        tex.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
        pm.dispose();
        return tex;
    }

    private static int argb(Color c) {
        return SoftCanvas.rgb(c.r, c.g, c.b);
    }

    public static Texture buildPersonTexture(Color jersey, Color shorts, boolean goalkeeper) {
        return toTexture(ArtPainter.person(argb(jersey), argb(shorts), goalkeeper));
    }

    public static Texture buildBallTexture() {
        return toTexture(ArtPainter.ball());
    }

    public static Texture buildGroundTexture() {
        if (groundCache == null) {
            groundCache = ArtPainter.ground(Constants.FIELD_WIDTH, Constants.FIELD_HEIGHT, Constants.FIELD_MARGIN,
                    Constants.GOAL_WIDTH, Constants.GOAL_DEPTH, Constants.PENALTY_BOX_DEPTH,
                    Constants.PENALTY_BOX_HEIGHT, Constants.GOAL_BOX_DEPTH, Constants.GOAL_BOX_HEIGHT, GROUND_PPM);
        }
        return toTexture(groundCache);
    }

    public static Texture buildCrowdTexture() {
        if (crowdCache == null) {
            int w = Math.round((Constants.FIELD_WIDTH + Constants.FIELD_MARGIN * 2f) * GROUND_PPM);
            int h = Math.round(CROWD_BAND_HEIGHT * GROUND_PPM);
            crowdCache = ArtPainter.crowd(w, h);
        }
        return toTexture(crowdCache);
    }

    public static Texture buildHaloTexture() {
        return toTexture(ArtPainter.halo());
    }

    public static Texture buildShadowTexture() {
        return toTexture(ArtPainter.shadow());
    }

    public static Texture buildRingTexture() {
        return toTexture(ArtPainter.ring());
    }
}
