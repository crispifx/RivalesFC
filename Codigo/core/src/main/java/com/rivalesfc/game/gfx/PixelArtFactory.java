package com.rivalesfc.game.gfx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.MathUtils;

/**
 * Genera texturas con estética pixel-art dibujando directamente sobre
 * {@link Pixmap} en tiempo de ejecución. La Etapa 1 original no traía arte
 * (todo se dibujaba con {@code ShapeRenderer}); esta fábrica permite subir
 * el nivel visual (jugadores con forma humana, pasto a rayas, tribuna con
 * "cabecitas" de público) sin necesitar internet ni archivos .png externos,
 * algo importante porque este entorno de trabajo no tiene acceso a assets.
 *
 * Todas las texturas quedan en filtro NEAREST para conservar el look
 * "bloque de píxeles" al escalarlas.
 */
public final class PixelArtFactory {

    private PixelArtFactory() {
    }

    private static Texture toTexture(Pixmap pm) {
        Texture tex = new Texture(pm);
        tex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pm.dispose();
        return tex;
    }

    /**
     * Sprite humano de 20x32 px, con sombreado simple (medio cuerpo más oscuro
     * para dar sensación de volumen), ojos, mangas y guantes. Reemplaza la
     * versión anterior (16x28, totalmente plana, sin sombreado ni rasgos
     * faciales) para que el personaje se lea mejor a la distancia y no parezca
     * un bloque de colores sólidos.
     */
    public static Texture buildPersonTexture(Color jersey, Color shorts, boolean goalkeeper) {
        int w = 20;
        int h = 32;
        Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        pm.setColor(0f, 0f, 0f, 0f);
        pm.fill();

        Color outline = new Color(0.06f, 0.05f, 0.04f, 1f);
        Color skin = new Color(0.90f, 0.70f, 0.52f, 1f);
        Color skinShade = shade(skin, 0.85f);
        Color hair = new Color(0.18f, 0.11f, 0.06f, 1f);
        Color shoe = new Color(0.09f, 0.09f, 0.09f, 1f);
        Color sock = Color.WHITE;
        Color sockShade = shade(sock, 0.85f);
        Color jerseyShade = shade(jersey, 0.72f);
        Color shortsShade = shade(shorts, 0.75f);
        Color glove = goalkeeper ? new Color(0.94f, 0.80f, 0.16f, 1f) : skin;

        // NOTA de convención de ejes: en Pixmap y=0 es la fila de ARRIBA de la
        // imagen (crece hacia abajo) — la cabeza va con y chico, los botines
        // con y grande.

        // Silueta/contorno oscuro de base: todo lo demás se pinta encima, así
        // queda un borde de 1px que separa al personaje del pasto.
        pm.setColor(outline);
        pm.fillRectangle(3, 26, 6, 4);
        pm.fillRectangle(11, 26, 6, 4);
        pm.fillRectangle(2, 17, 16, 10);
        pm.fillCircle(10, 5, 6);

        // Botines
        pm.setColor(shoe);
        pm.fillRectangle(3, 27, 6, 3);
        pm.fillRectangle(11, 27, 6, 3);

        // Medias (una pierna en sombra para dar volumen)
        pm.setColor(sock);
        pm.fillRectangle(3, 22, 6, 5);
        pm.setColor(sockShade);
        pm.fillRectangle(11, 22, 6, 5);

        // Short
        pm.setColor(shorts);
        pm.fillRectangle(2, 18, 16, 6);
        pm.setColor(shortsShade);
        pm.fillRectangle(11, 18, 7, 6);

        // Camiseta (torso), con mitad derecha sombreada
        pm.setColor(jersey);
        pm.fillRectangle(2, 8, 16, 10);
        pm.setColor(jerseyShade);
        pm.fillRectangle(11, 8, 7, 10);

        // Mangas
        pm.setColor(Color.WHITE);
        pm.fillRectangle(1, 8, 2, 5);
        pm.fillRectangle(17, 8, 2, 5);

        // Brazos / manos (guantes amarillos si es arquero)
        pm.setColor(glove);
        pm.fillRectangle(0, 9, 2, 5);
        pm.fillRectangle(18, 9, 2, 5);

        // Cuello
        pm.setColor(skin);
        pm.fillRectangle(8, 6, 4, 2);

        // Cabeza, con lateral sombreado
        pm.setColor(skin);
        pm.fillCircle(10, 5, 5);
        pm.setColor(skinShade);
        pm.fillRectangle(13, 1, 3, 9);

        // Ojos (dan personalidad y ayudan a leer hacia dónde "mira" el sprite base)
        pm.setColor(outline);
        pm.fillRectangle(8, 4, 1, 2);
        pm.fillRectangle(11, 4, 1, 2);

        // Pelo
        pm.setColor(hair);
        pm.fillRectangle(4, 0, 12, 3);
        pm.fillRectangle(4, 2, 2, 4);
        pm.fillRectangle(14, 2, 2, 4);

        return toTexture(pm);
    }

    /** Oscurece un color un factor {@code f} (0..1), preservando el alfa. Útil para sombreado simple. */
    private static Color shade(Color c, float f) {
        return new Color(c.r * f, c.g * f, c.b * f, c.a);
    }

    /** Pelota blanca con sombreado de volumen y manchas negras estilo balón de fútbol. */
    public static Texture buildBallTexture() {
        int size = 20;
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pm.setColor(0f, 0f, 0f, 0f);
        pm.fill();

        int cx = size / 2;
        int cy = size / 2;
        int radius = size / 2 - 1;

        pm.setColor(new Color(0.92f, 0.92f, 0.92f, 1f));
        pm.fillCircle(cx, cy, radius);

        // Sombreado simple: un cuarto de la pelota (abajo-derecha) un poco más oscuro,
        // para que no se vea como un círculo plano.
        Color shadow = new Color(0.82f, 0.82f, 0.82f, 1f);
        for (int y = cy - radius; y <= cy + radius; y++) {
            for (int x = cx - radius; x <= cx + radius; x++) {
                int dx = x - cx;
                int dy = y - cy;
                if (dx * dx + dy * dy <= radius * radius && (dx + dy) > radius / 2) {
                    pm.setColor(shadow);
                    pm.drawPixel(x, y);
                }
            }
        }

        pm.setColor(Color.BLACK);
        pm.fillCircle(cx, cy, 3);
        pm.fillCircle(cx + 6, cy, 1);
        pm.fillCircle(cx - 6, cy, 1);
        pm.fillCircle(cx, cy + 6, 1);
        pm.fillCircle(cx, cy - 6, 1);
        pm.fillCircle(cx + 4, cy + 4, 1);

        return toTexture(pm);
    }

    /**
     * Franja de césped "cortado a rayas" (bandas verticales verde claro/oscuro
     * alternadas), con un leve ruido para que no se vea plana. Se genera una
     * sola vez con el ancho de la cancha completa: se estira verticalmente al
     * dibujarla, así se evita repetir la textura en un loop por cada tile.
     */
    public static Texture buildGrassTexture(int stripeCount, int stripePx) {
        int w = Math.max(1, stripeCount) * stripePx;
        int h = stripePx;
        Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);

        Color light = new Color(0.19f, 0.58f, 0.24f, 1f);
        Color dark = new Color(0.13f, 0.48f, 0.18f, 1f);

        for (int c = 0; c < stripeCount; c++) {
            pm.setColor(c % 2 == 0 ? light : dark);
            pm.fillRectangle(c * stripePx, 0, stripePx, h);
        }

        // Ruido sutil (más denso que antes) para simular textura de pasto real
        // en vez de bandas de color totalmente planas.
        int noisePixels = (w * h) / 14;
        for (int i = 0; i < noisePixels; i++) {
            int x = MathUtils.random(0, w - 1);
            int y = MathUtils.random(0, h - 1);
            boolean brighten = MathUtils.randomBoolean(0.5f);
            pm.setColor(brighten ? 1f : 0f, brighten ? 1f : 0f, brighten ? 1f : 0f, 0.05f);
            pm.drawPixel(x, y);
        }

        return toTexture(pm);
    }

    /**
     * Tira decorativa de tribuna: fondo oscuro con "cabecitas" de público de
     * colores variados, salpicadas al azar (pero con semilla fija para que no
     * titile de forma distinta cada vez que se reconstruye la textura).
     *
     * La versión anterior dibujaba la cabeza (círculo de piel) por DEBAJO del
     * cuerpo (torso) en vez de encima, porque no tuvo en cuenta que en Pixmap
     * "y" crece hacia abajo: quedaba gente de la tribuna con la cabeza a la
     * altura de la panza. Acá la cabeza va con "y" menor (arriba) que el torso.
     */
    public static Texture buildCrowdTexture(int cols, int rows, int tilePx) {
        MathUtils.random.setSeed(20260825L);
        int w = cols * tilePx;
        int h = rows * tilePx;
        Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        pm.setColor(new Color(0.14f, 0.16f, 0.22f, 1f));
        pm.fill();

        Color[] shirts = new Color[]{
                new Color(0.85f, 0.20f, 0.20f, 1f),
                new Color(0.20f, 0.40f, 0.85f, 1f),
                new Color(0.90f, 0.85f, 0.20f, 1f),
                new Color(0.92f, 0.92f, 0.92f, 1f),
                new Color(0.30f, 0.70f, 0.30f, 1f),
                new Color(0.55f, 0.30f, 0.75f, 1f)
        };
        Color[] faceTones = new Color[]{
                new Color(0.90f, 0.75f, 0.60f, 1f),
                new Color(0.65f, 0.48f, 0.35f, 1f),
                new Color(0.40f, 0.28f, 0.20f, 1f)
        };

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (!MathUtils.randomBoolean(0.88f)) {
                    continue;
                }
                Color shirt = shirts[MathUtils.random(shirts.length - 1)];
                Color face = faceTones[MathUtils.random(faceTones.length - 1)];
                int cx = c * tilePx + tilePx / 2;
                int cy = r * tilePx + tilePx / 2;
                int bodyRadius = Math.max(1, tilePx / 3);
                int headRadius = Math.max(1, tilePx / 5);

                // Cabeza arriba (y menor), cuerpo/torso abajo (y mayor).
                pm.setColor(face);
                pm.fillCircle(cx, cy - bodyRadius / 2 - headRadius, headRadius);
                pm.setColor(shirt);
                pm.fillCircle(cx, cy + headRadius / 2, bodyRadius);

                // De vez en cuando, un brazo levantado (hincha festejando).
                if (MathUtils.randomBoolean(0.12f)) {
                    pm.setColor(face);
                    pm.fillRectangle(cx + bodyRadius / 2, cy - headRadius, 1, headRadius + 1);
                }
            }
        }

        return toTexture(pm);
    }
}
