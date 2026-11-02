package com.rivalesfc.game.gfx;

import static com.rivalesfc.game.gfx.SoftCanvas.capsule;
import static com.rivalesfc.game.gfx.SoftCanvas.circle;
import static com.rivalesfc.game.gfx.SoftCanvas.ellipse;
import static com.rivalesfc.game.gfx.SoftCanvas.hgrad;
import static com.rivalesfc.game.gfx.SoftCanvas.intersect;
import static com.rivalesfc.game.gfx.SoftCanvas.minus;
import static com.rivalesfc.game.gfx.SoftCanvas.polygon;
import static com.rivalesfc.game.gfx.SoftCanvas.radial;
import static com.rivalesfc.game.gfx.SoftCanvas.rgb;
import static com.rivalesfc.game.gfx.SoftCanvas.roundRect;
import static com.rivalesfc.game.gfx.SoftCanvas.shade;
import static com.rivalesfc.game.gfx.SoftCanvas.vgrad;

import java.util.Random;

import com.rivalesfc.game.gfx.SoftCanvas.Paint;
import com.rivalesfc.game.gfx.SoftCanvas.Shape;

/**
 * Arte procedural en alta resolución y con antialiasing (Java puro, sin LibGDX).
 * {@link PixelArtFactory} convierte estos mapas de bits en texturas.
 *
 * Reemplaza los sprites de 20x32 px con bordes duros de la MK6: ahora los
 * jugadores se dibujan a 120x192, la pelota a 160x160, el pasto y las líneas de
 * la cancha a 32 px por metro (las líneas se calculan con distancia analítica,
 * por eso salen nítidas sin escalones) y la tribuna en su proporción real.
 */
public final class ArtPainter {

    private ArtPainter() {
    }

    /** Imagen ARGB (sin premultiplicar), fila 0 = arriba. */
    public static final class Bitmap {
        public final int w;
        public final int h;
        public final int[] argb;

        Bitmap(int w, int h, int[] argb) {
            this.w = w;
            this.h = h;
            this.argb = argb;
        }
    }

    public static final int PERSON_W = 120;
    public static final int PERSON_H = 192;
    public static final int BALL_SIZE = 160;

    private static final int OUTLINE = 0xFF15110F;

    // ------------------------------------------------------------------
    // Jugador / arquero
    // ------------------------------------------------------------------

    /** Jugador de frente, 120x192. Los colores vienen como ARGB. */
    public static Bitmap person(final int jersey, final int shorts, boolean keeper) {
        // MK9: sin variante explícita, se deduce de los colores (mismo equipo = mismo aspecto, equipos distintos = distinto).
        int h = jersey * 31 + shorts * 17 + (keeper ? 7 : 0);
        h ^= h >>> 15;
        return person(jersey, shorts, keeper, Math.abs(h) % 4);
    }

    private static final float[][] SKINS = {{0.94f, 0.75f, 0.58f}, {0.80f, 0.58f, 0.40f}, {0.55f, 0.38f, 0.27f}, {0.97f, 0.82f, 0.70f}};
    private static final float[][] HAIRS = {{0.20f, 0.12f, 0.07f}, {0.07f, 0.06f, 0.06f}, {0.86f, 0.68f, 0.30f}, {0.55f, 0.22f, 0.10f}};

    /** variant 0..3: tono de piel + pelo + diseño de camiseta (0 liso, 1 rayas, 2 franjas, 3 banda). */
    public static Bitmap person(final int jersey, final int shorts, boolean keeper, int variant) {
        final int vi = ((variant % 4) + 4) % 4;
        SoftCanvas c = new SoftCanvas(PERSON_W, PERSON_H, 3);
        final float ow = 2.4f;

        final int skin = rgb(SKINS[vi][0], SKINS[vi][1], SKINS[vi][2]);
        final int skinD = shade(skin, 0.80f);
        final int hair = rgb(HAIRS[vi][0], HAIRS[vi][1], HAIRS[vi][2]);
        final int boot = rgb(0.10f, 0.10f, 0.11f);
        final int sock = keeper ? shade(shorts, 0.85f) : rgb(0.96f, 0.96f, 0.96f);
        final int glove = rgb(0.98f, 0.82f, 0.14f);

        // --- Botines
        for (int side = 0; side < 2; side++) {
            float cx = side == 0 ? 43f : 77f;
            Shape b = ellipse(cx, 180f, 17f, 9f);
            c.fillOutlined(b, vgrad(171f, shade(boot, 1.6f), 189f, boot), ow, OUTLINE);
            c.fill(ellipse(cx + (side == 0 ? -3f : 3f), 177f, 7f, 2.2f), 0x55FFFFFF);
        }

        // --- Medias, con franja del color de la camiseta
        for (int side = 0; side < 2; side++) {
            float cx = side == 0 ? 43f : 77f;
            Shape s = roundRect(cx, 156f, 9.5f, 24f, 5f);
            c.fillOutlined(s, hgrad(cx - 9.5f, sock, cx + 9.5f, shade(sock, 0.80f)), ow, OUTLINE);
            c.fill(intersect(s, roundRect(cx, 145f, 12f, 3f, 1f)), shade(jersey, 0.95f));
        }

        // --- Muslos (piel) debajo del short
        for (int side = 0; side < 2; side++) {
            float cx = side == 0 ? 43f : 77f;
            c.fillOutlined(roundRect(cx, 135f, 9.5f, 12f, 4f),
                    hgrad(cx - 9.5f, skin, cx + 9.5f, skinD), ow, OUTLINE);
        }

        // --- Short: dos piernas + cintura
        for (int side = 0; side < 2; side++) {
            float cx = side == 0 ? 42f : 78f;
            Shape s = roundRect(cx, 119f, 18f, 19f, 8f);
            c.fillOutlined(s, new Paint() {
                @Override
                public int argb(float x, float y) {
                    float t = Math.max(0f, Math.min(1f, (x - 24f) / 72f));
                    float v = Math.max(0f, Math.min(1f, (y - 100f) / 40f));
                    return shade(shorts, 1.12f - 0.34f * t - 0.12f * v);
                }
            }, ow, OUTLINE);
        }
        c.fillOutlined(roundRect(60f, 102f, 33f, 5.5f, 3f), shade(shorts, 0.78f), ow, OUTLINE);

        // --- Cuello
        c.fillOutlined(roundRect(60f, 54f, 8f, 7f, 3f), skinD, ow, OUTLINE);

        // --- Torso (camiseta)
        Shape torso = roundRect(60f, 83f, 33f, 30f, 15f);
        c.fillOutlined(torso, new Paint() {
            @Override
            public int argb(float x, float y) {
                float t = Math.max(0f, Math.min(1f, (x - 27f) / 66f));
                float v = Math.max(0f, Math.min(1f, (y - 56f) / 56f));
                return shade(jersey, 1.14f - 0.36f * t - 0.10f * v);
            }
        }, ow, OUTLINE);
        // franja vertical central, más clara
        c.fill(intersect(torso, roundRect(60f, 83f, 9f, 40f, 0f)), SoftCanvas.withAlpha(0xFFFFFFFF, 0.13f));
        // diseño de camiseta
        int band = SoftCanvas.withAlpha(0xFFFFFFFF, keeper ? 0.12f : 0.30f);
        if (vi == 1) {            // rayas verticales
            for (int k = -2; k <= 2; k += 2) {
                c.fill(intersect(torso, roundRect(60f + k * 9f, 83f, 4.5f, 40f, 0f)), band);
            }
        } else if (vi == 2) {     // franjas horizontales
            c.fill(intersect(torso, roundRect(60f, 80f, 40f, 4f, 0f)), band);
            c.fill(intersect(torso, roundRect(60f, 94f, 40f, 4f, 0f)), band);
        } else if (vi == 3) {     // banda diagonal
            c.fill(intersect(torso, polygon(30f, 70f, 42f, 62f, 92f, 108f, 80f, 116f)), band);
        }
        // dobladillo
        c.fill(intersect(torso, roundRect(60f, 112f, 40f, 3f, 0f)), SoftCanvas.withAlpha(0xFF000000, 0.18f));
        // cuello en V
        c.fillOutlined(polygon(47f, 55f, 73f, 55f, 60f, 73f), rgb(0.97f, 0.97f, 0.97f), 1.6f, OUTLINE);
        c.fill(polygon(52f, 56f, 68f, 56f, 60f, 67f), skinD);
        // escudo
        c.fillOutlined(circle(76f, 78f, 5.2f), rgb(0.97f, 0.97f, 0.97f), 1.4f, OUTLINE);
        c.fill(circle(76f, 78f, 2.6f), shade(jersey, 0.85f));

        // --- Brazos
        for (int side = 0; side < 2; side++) {
            float m = side == 0 ? 1f : -1f;
            float sx = 60f - m * 33f;
            float elbowX = 60f - m * 40f;
            float handX = 60f - m * 42f;
            // manga
            c.fillOutlined(capsule(sx, 66f, elbowX, 88f, 10f, 8.5f),
                    new Paint() {
                        @Override
                        public int argb(float x, float y) {
                            return shade(jersey, 1.0f - 0.2f * Math.abs(x - 60f) / 45f);
                        }
                    }, ow, OUTLINE);
            // puño de la manga
            c.fill(capsule(elbowX + m * 0.3f, 87f, elbowX + m * 0.3f, 89f, 8.6f, 8.6f), shade(shorts, 0.95f));
            // antebrazo
            c.fillOutlined(capsule(elbowX, 92f, handX, 108f, 6.2f, 5.6f), skin, ow, OUTLINE);
            // mano / guante
            if (keeper) {
                c.fillOutlined(circle(handX, 114f, 9.5f), vgrad(104f, shade(glove, 1.1f), 124f, shade(glove, 0.8f)),
                        ow, OUTLINE);
            } else {
                c.fillOutlined(circle(handX, 113f, 6.3f), skin, ow, OUTLINE);
            }
        }

        // --- Orejas
        c.fillOutlined(circle(37f, 34f, 5.2f), skinD, 1.8f, OUTLINE);
        c.fillOutlined(circle(83f, 34f, 5.2f), skinD, 1.8f, OUTLINE);

        // --- Cabeza
        Shape head = ellipse(60f, 32f, 23.5f, 26f);
        c.fillOutlined(head, radial(52f, 22f, 44f, shade(skin, 1.08f), shade(skin, 0.80f)), ow, OUTLINE);

        // --- Pelo
        Shape hairShape = minus(ellipse(60f, 24f, 26f, 21.5f), ellipse(60f, 42f, 19.5f, 21.5f));
        c.fillOutlined(hairShape, vgrad(6f, shade(hair, 1.5f), 30f, hair), 1.8f, OUTLINE);

        // --- Cara
        for (int side = 0; side < 2; side++) {
            float ex = side == 0 ? 51f : 69f;
            c.fill(ellipse(ex, 36f, 3.6f, 4.6f), rgb(0.97f, 0.97f, 0.97f));
            c.fill(ellipse(ex + 0.4f, 36.6f, 2.4f, 3.4f), rgb(0.10f, 0.08f, 0.07f));
            c.fill(circle(ex + 1.2f, 35.2f, 0.9f), 0xFFFFFFFF);
            float bx = side == 0 ? -1f : 1f;
            c.fill(capsule(ex - 5f, 29.5f + bx * 0.6f, ex + 5f, 29.5f - bx * 0.6f, 1.3f, 1.3f), hair);
        }
        c.fill(ellipse(60f, 42f, 2.3f, 1.6f), shade(skin, 0.78f));
        c.fill(capsule(54f, 48f, 60f, 50f, 1.2f, 1.2f), rgb(0.55f, 0.24f, 0.20f));
        c.fill(capsule(60f, 50f, 66f, 48f, 1.2f, 1.2f), rgb(0.55f, 0.24f, 0.20f));

        return new Bitmap(PERSON_W, PERSON_H, c.toArgb(OUTLINE));
    }

    // ------------------------------------------------------------------
    // Pelota
    // ------------------------------------------------------------------

    public static Bitmap ball() {
        final int S = BALL_SIZE;
        SoftCanvas c = new SoftCanvas(S, S, 3);
        final float cx = S / 2f;
        final float cy = S / 2f;
        final float R = 72f;
        final int dark = rgb(0.07f, 0.07f, 0.09f);
        Shape disc = circle(cx, cy, R);

        c.outline(disc, 2.6f, OUTLINE);
        c.fill(disc, radial(cx - 18f, cy - 22f, 105f, rgb(1f, 1f, 1f), rgb(0.66f, 0.68f, 0.72f)));

        // costuras radiales
        for (int k = 0; k < 5; k++) {
            double a = Math.toRadians(-90 + 72 * k);
            float ca = (float) Math.cos(a);
            float sa = (float) Math.sin(a);
            c.fill(intersect(disc, capsule(cx + ca * 18f, cy + sa * 18f, cx + ca * 46f, cy + sa * 46f, 1.5f, 1.5f)),
                    rgb(0.30f, 0.31f, 0.35f));
        }
        // pentágonos
        c.fill(polygon(regularPoly(cx, cy, 22f, -90f, 5)), dark);
        for (int k = 0; k < 5; k++) {
            double a = Math.toRadians(-90 + 72 * k);
            float px = cx + (float) Math.cos(a) * 64f;
            float py = cy + (float) Math.sin(a) * 64f;
            Shape p = polygon(regularPoly(px, py, 21f, -90f + 72 * k + 180f, 5));
            c.fill(intersect(disc, p), dark);
        }
        // sombreado de volumen en el borde
        c.fill(disc, new Paint() {
            @Override
            public int argb(float x, float y) {
                float dx = x - (cx - 14f);
                float dy = y - (cy - 18f);
                float t = (float) Math.sqrt(dx * dx + dy * dy) / 108f;
                float a = 0.42f * Math.max(0f, Math.min(1f, (t - 0.40f) / 0.60f));
                return SoftCanvas.argb(0.02f, 0.03f, 0.08f, a);
            }
        });
        // brillo
        c.fill(ellipse(cx - 22f, cy - 28f, 15f, 9f), radial(cx - 22f, cy - 28f, 16f, 0x99FFFFFF, 0x00FFFFFF));

        return new Bitmap(S, S, c.toArgb(OUTLINE));
    }

    private static float[] regularPoly(float cx, float cy, float r, float startDeg, int n) {
        float[] pts = new float[n * 2];
        for (int i = 0; i < n; i++) {
            double a = Math.toRadians(startDeg + 360.0 * i / n);
            pts[i * 2] = cx + (float) Math.cos(a) * r;
            pts[i * 2 + 1] = cy + (float) Math.sin(a) * r;
        }
        return pts;
    }

    // ------------------------------------------------------------------
    // Sombra y anillo del jugador propio
    // ------------------------------------------------------------------

    /** Sombra elíptica suave, 128x64. */
    /** MK7: halo suave (blanco-amarillento) que se dibuja detrás de la pelota para que resalte sobre el pasto y las líneas. */
    public static Bitmap halo() {
        final int w = 128;
        final int h = 128;
        SoftCanvas c = new SoftCanvas(w, h, 1);
        c.fill(roundRect(w / 2f, h / 2f, w / 2f, h / 2f, 0f), new Paint() {
            @Override
            public int argb(float x, float y) {
                float dx = (x - w / 2f) / (w / 2f);
                float dy = (y - h / 2f) / (h / 2f);
                float t = (float) Math.sqrt(dx * dx + dy * dy);
                if (t >= 1f) {
                    return 0;
                }
                float a = 0.85f * (float) Math.pow(1f - t, 2.2);
                return SoftCanvas.argb(1f, 0.97f, 0.7f, a);
            }
        });
        return new Bitmap(w, h, c.toArgb(0xFF000000));
    }

    public static Bitmap shadow() {
        final int w = 128;
        final int h = 64;
        SoftCanvas c = new SoftCanvas(w, h, 1);
        c.fill(roundRect(w / 2f, h / 2f, w / 2f, h / 2f, 0f), new Paint() {
            @Override
            public int argb(float x, float y) {
                float dx = (x - w / 2f) / (w / 2f);
                float dy = (y - h / 2f) / (h / 2f);
                float t = (float) Math.sqrt(dx * dx + dy * dy);
                if (t >= 1f) {
                    return 0;
                }
                float a = 0.60f * (float) Math.pow(1f - t, 1.5);
                return SoftCanvas.argb(0f, 0f, 0f, a);
            }
        });
        return new Bitmap(w, h, c.toArgb(0xFF000000));
    }

    /** Anillo blanco (elipse) para marcar al jugador propio, 160x80. */
    public static Bitmap ring() {
        final int w = 160;
        final int h = 80;
        SoftCanvas c = new SoftCanvas(w, h, 3);
        Shape outerGlow = minus(ellipse(80f, 40f, 79f, 39f), ellipse(80f, 40f, 66f, 30f));
        c.fill(outerGlow, SoftCanvas.argb(1f, 1f, 1f, 0.22f));
        Shape ring = minus(ellipse(80f, 40f, 74f, 36f), ellipse(80f, 40f, 66f, 30f));
        c.fill(ring, SoftCanvas.argb(1f, 1f, 1f, 0.95f));
        return new Bitmap(w, h, c.toArgb(0xFFFFFFFF));
    }

    // ------------------------------------------------------------------
    // Tribuna
    // ------------------------------------------------------------------

    /** Tribuna con público sentado en filas. {@code w x h} en píxeles (usar la proporción real de la franja). */
    public static Bitmap crowd(int w, int h) {
        SoftCanvas c = new SoftCanvas(w, h, 2);
        Random rnd = new Random(20261006L);

        c.fill(roundRect(w / 2f, h / 2f, w / 2f, h / 2f, 0f),
                vgrad(0f, rgb(0.04f, 0.05f, 0.09f), h, rgb(0.13f, 0.14f, 0.19f)));

        final int[] skins = {
                rgb(0.94f, 0.76f, 0.60f), rgb(0.83f, 0.62f, 0.46f),
                rgb(0.62f, 0.44f, 0.32f), rgb(0.42f, 0.29f, 0.21f)
        };
        final int[] hairs = {
                rgb(0.10f, 0.07f, 0.05f), rgb(0.25f, 0.15f, 0.08f), rgb(0.55f, 0.38f, 0.18f),
                rgb(0.80f, 0.68f, 0.35f), rgb(0.55f, 0.55f, 0.58f)
        };
        final int[] shirts = {
                rgb(0.85f, 0.18f, 0.18f), rgb(0.20f, 0.42f, 0.88f), rgb(0.95f, 0.80f, 0.15f),
                rgb(0.93f, 0.93f, 0.95f), rgb(0.22f, 0.68f, 0.34f), rgb(0.58f, 0.30f, 0.78f),
                rgb(0.95f, 0.50f, 0.15f), rgb(0.12f, 0.12f, 0.14f)
        };

        int rows = 4;
        float rowStep = (h - 28f) / (rows - 1);
        for (int r = 0; r < rows; r++) {
            float y = 15f + r * rowStep;
            float bright = 0.58f + 0.14f * r;
            float step = 17.5f;
            float x = -4f + (r % 2) * 8.5f;
            while (x < w + 12f) {
                float px = x + (rnd.nextFloat() - 0.5f) * 3f;
                float py = y + (rnd.nextFloat() - 0.5f) * 2.5f;
                int shirt = shade(shirts[rnd.nextInt(shirts.length)], bright);
                int skin = shade(skins[rnd.nextInt(skins.length)], bright);
                int hair = shade(hairs[rnd.nextInt(hairs.length)], bright);
                int line = SoftCanvas.argb(0f, 0f, 0f, 0.55f);

                boolean cheer = rnd.nextFloat() < 0.16f;
                if (cheer) {
                    float s = rnd.nextBoolean() ? 1f : -1f;
                    Shape arm = capsule(px + s * 8f, py + 12f, px + s * 11f, py - 5f, 2.4f, 2.2f);
                    c.outline(arm, 1.1f, line);
                    c.fill(arm, skin);
                    c.fill(circle(px + s * 11f, py - 6f, 3.1f), skin);
                }
                Shape body = roundRect(px, py + 12.5f, 9.8f, 9f, 6.5f);
                c.fillOutlined(body, shirt, 1.1f, line);
                if (rnd.nextFloat() < 0.22f) {
                    int scarf = shade(shirts[rnd.nextInt(shirts.length)], bright);
                    c.fill(roundRect(px, py + 7.5f, 6.5f, 2.0f, 1f), scarf);
                }
                Shape head = circle(px, py, 6.4f);
                c.fillOutlined(head, skin, 1.1f, line);
                c.fill(minus(ellipse(px, py - 1.6f, 6.9f, 5.4f), ellipse(px, py + 3.4f, 5.4f, 4.8f)), hair);
                x += step + (rnd.nextFloat() - 0.5f) * 3f;
            }
        }
        // suave oscurecimiento hacia el fondo de la tribuna
        c.fill(roundRect(w / 2f, h / 2f, w / 2f, h / 2f, 0f), vgrad(0f, 0x66000010, h * 0.7f, 0x00000010));
        return new Bitmap(w, h, c.toArgb(0xFF0A0B10));
    }

    // ------------------------------------------------------------------
    // Cancha completa: pasto, líneas, arcos con red y carteles
    // ------------------------------------------------------------------

    /**
     * Textura de toda la zona visible (cancha + margen), a {@code ppm} píxeles por metro.
     * Cubre x en [-(fieldW/2+margin), +(fieldW/2+margin)] e y en [-(fieldH/2+margin), +(fieldH/2+margin)].
     * Las líneas se calculan por distancia analítica (bordes suaves a cualquier zoom).
     */
    public static Bitmap ground(float fieldW, float fieldH, float margin, float goalW, float goalDepth,
                                float penDepth, float penH, float gbDepth, float gbH, float ppm) {
        final float halfW = fieldW / 2f;
        final float halfH = fieldH / 2f;
        final float halfX = halfW + margin;
        final float halfY = halfH + margin;
        final int W = Math.round(halfX * 2f * ppm);
        final int H = Math.round(halfY * 2f * ppm);
        final float lineHW = 0.075f;

        float[] mask = new float[W * H];
        Mask m = new Mask(mask, W, H, halfX, halfY, ppm);

        // contorno, línea central y círculo
        m.rect(-halfW, -halfH, halfW, halfH, lineHW);
        m.seg(0f, -halfH, 0f, halfH, lineHW);
        m.arc(0f, 0f, 3f, 0f, 360f, lineHW);
        m.dot(0f, 0f, 0.20f);
        // áreas (los lados sobre la línea de fondo ya están en el contorno)
        for (int side = -1; side <= 1; side += 2) {
            float gx = side * halfW;
            float px = side * (halfW - penDepth);
            float bx = side * (halfW - gbDepth);
            m.seg(px, -penH / 2f, px, penH / 2f, lineHW);
            m.seg(gx, penH / 2f, px, penH / 2f, lineHW);
            m.seg(gx, -penH / 2f, px, -penH / 2f, lineHW);
            m.seg(bx, -gbH / 2f, bx, gbH / 2f, lineHW);
            m.seg(gx, gbH / 2f, bx, gbH / 2f, lineHW);
            m.seg(gx, -gbH / 2f, bx, -gbH / 2f, lineHW);
            // punto de penal y "medialuna"
            float spotX = side * (halfW - (penDepth - 0.9f));
            m.dot(spotX, 0f, 0.16f);
            float ang = (float) Math.toDegrees(Math.acos(0.9f / 3f));
            if (side < 0) {
                m.arc(spotX, 0f, 3f, -ang, ang, lineHW);
            } else {
                m.arc(spotX, 0f, 3f, 180f - ang, 180f + ang, lineHW);
            }
            // marco del arco
            float bk = side * (halfW + goalDepth);
            m.seg(gx, -goalW / 2f, bk, -goalW / 2f, 0.06f);
            m.seg(bk, -goalW / 2f, bk, goalW / 2f, 0.06f);
            m.seg(gx, goalW / 2f, bk, goalW / 2f, 0.06f);
            m.dot(gx, -goalW / 2f, 0.13f);
            m.dot(gx, goalW / 2f, 0.13f);
        }
        // arcos de córner (hacia adentro de la cancha)
        m.arc(-halfW, halfH, 1f, 270f, 360f, lineHW);
        m.arc(halfW, halfH, 1f, 180f, 270f, lineHW);
        m.arc(-halfW, -halfH, 1f, 0f, 90f, lineHW);
        m.arc(halfW, -halfH, 1f, 90f, 180f, lineHW);

        final float stripeW = fieldW / 10f; // 10 franjas: el cambio de color cae justo en la línea central
        final float[] light = {0.22f, 0.63f, 0.27f};
        final float[] dark = {0.14f, 0.49f, 0.20f};
        final float boardY0 = halfH + 0.25f;
        final float boardY1 = halfH + 0.60f;

        int[] out = new int[W * H];
        for (int j = 0; j < H; j++) {
            float wy = halfY - (j + 0.5f) / ppm;
            float ay = Math.abs(wy);
            for (int i = 0; i < W; i++) {
                float wx = -halfX + (i + 0.5f) / ppm;
                float ax = Math.abs(wx);

                // --- rayas de corte
                float u = (wx + halfW) / stripeW;
                float fl = (float) Math.floor(u);
                float f = u - fl;
                boolean even = (((int) fl) & 1) == 0;
                float edge = Math.min(f, 1f - f) * stripeW * ppm;
                float tone = even ? 1f : 0f;
                tone = 0.5f + (tone - 0.5f) * Math.min(1f, edge / 1.6f);
                float r = dark[0] + (light[0] - dark[0]) * tone;
                float g = dark[1] + (light[1] - dark[1]) * tone;
                float b = dark[2] + (light[2] - dark[2]) * tone;

                // --- textura del pasto: manchas grandes, medias, hebras y grano fino
                float n1 = vnoise(wx * 0.30f, wy * 0.30f, 11);
                float n2 = vnoise(wx * 2.2f, wy * 2.2f, 23);
                float st = vnoise(wx * 20f, wy * 1.4f, 37);
                float gr = hash(i, j, 5);
                float mul = 1f + 0.05f * (n1 - 0.5f) + 0.05f * (n2 - 0.5f) + 0.055f * (st - 0.5f) + 0.05f * (gr - 0.5f);
                r *= mul;
                g *= mul;
                b *= mul;

                boolean inField = ax <= halfW && ay <= halfH;
                boolean inGoal = ax > halfW && ax <= halfW + goalDepth && ay <= goalW / 2f;

                if (!inField) {
                    float dx = Math.max(0f, ax - halfW);
                    float dy = Math.max(0f, ay - halfH);
                    float dd = (float) Math.sqrt(dx * dx + dy * dy);
                    float k = 0.66f - 0.05f * Math.min(1f, dd / margin);
                    r *= k;
                    g *= k;
                    b *= k;
                }

                if (inGoal) {
                    r = 0.05f + 0.04f * mul;
                    g = 0.13f + 0.05f * mul;
                    b = 0.08f + 0.03f * mul;
                    // red: malla de 0,3 m
                    float gx = netDist(wx - halfW * Math.signum(wx), 0.3f);
                    float gy = netDist(wy + goalW / 2f, 0.3f);
                    float dnet = Math.min(gx, gy);
                    float cov = Math.max(0f, Math.min(1f, (0.014f - dnet) * ppm + 0.5f)) * 0.45f;
                    r += (1f - r) * cov;
                    g += (1f - g) * cov;
                    b += (1f - b) * cov;
                } else if (ay >= boardY0 && ay <= halfY && ax <= halfX) {
                    // --- carteles publicitarios (negro con ribete dorado) y fondo oscuro detrás
                    if (ay <= boardY1) {
                        float t = (ay - boardY0) / (boardY1 - boardY0);
                        float lit = 0.06f + 0.05f * (1f - t);
                        r = lit;
                        g = lit + 0.004f;
                        b = lit + 0.015f;
                        if (ay - boardY0 < 0.05f) {
                            r = 0.95f;
                            g = 0.73f;
                            b = 0.15f;
                        } else {
                            float panel = ((wx + halfX) / 4f);
                            float pf = panel - (float) Math.floor(panel);
                            if (Math.min(pf, 1f - pf) * 4f * ppm < 1.2f) {
                                r += 0.10f;
                                g += 0.10f;
                                b += 0.11f;
                            }
                            // detalle: rombo dorado en el centro de cada cartel
                            float cx = Math.abs(pf - 0.5f) * 4f;
                            float cyy = Math.abs(t - 0.5f) * (boardY1 - boardY0);
                            if (cx + cyy * 2.2f < 0.14f) {
                                r = 0.85f;
                                g = 0.66f;
                                b = 0.14f;
                            }
                        }
                    } else {
                        r = 0.04f;
                        g = 0.045f;
                        b = 0.07f;
                    }
                }

                // --- líneas blancas
                float lc = mask[j * W + i];
                if (lc > 0f) {
                    float a = lc * 0.94f;
                    r += (0.98f - r) * a;
                    g += (0.98f - g) * a;
                    b += (0.98f - b) * a;
                }
                out[j * W + i] = rgbPack(r, g, b);
            }
        }
        return new Bitmap(W, H, out);
    }

    private static int rgbPack(float r, float g, float b) {
        int ri = Math.min(255, Math.max(0, Math.round(r * 255f)));
        int gi = Math.min(255, Math.max(0, Math.round(g * 255f)));
        int bi = Math.min(255, Math.max(0, Math.round(b * 255f)));
        return 0xFF000000 | (ri << 16) | (gi << 8) | bi;
    }

    /** Distancia (m) a la línea de malla más cercana en una dimensión. */
    private static float netDist(float v, float spacing) {
        float t = v / spacing;
        float f = t - (float) Math.floor(t);
        return Math.min(f, 1f - f) * spacing;
    }

    private static float hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 982451653;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= (h >>> 16);
        return (h & 0xFFFFFF) / 16777215f;
    }

    private static float vnoise(float x, float y, int seed) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        float fx = x - xi;
        float fy = y - yi;
        fx = fx * fx * (3f - 2f * fx);
        fy = fy * fy * (3f - 2f * fy);
        float a = hash(xi, yi, seed);
        float b = hash(xi + 1, yi, seed);
        float c = hash(xi, yi + 1, seed);
        float d = hash(xi + 1, yi + 1, seed);
        float top = a + (b - a) * fx;
        float bot = c + (d - c) * fx;
        return top + (bot - top) * fy;
    }

    /** Máscara de líneas con cobertura analítica (en metros del mundo → píxeles de la textura). */
    private static final class Mask {
        final float[] mask;
        final int w;
        final int h;
        final float halfX;
        final float halfY;
        final float ppm;

        Mask(float[] mask, int w, int h, float halfX, float halfY, float ppm) {
            this.mask = mask;
            this.w = w;
            this.h = h;
            this.halfX = halfX;
            this.halfY = halfY;
            this.ppm = ppm;
        }

        private void put(int i, int j, float d, float hw) {
            float cov = (hw - d) * ppm + 0.5f;
            if (cov <= 0f) {
                return;
            }
            if (cov > 1f) {
                cov = 1f;
            }
            int o = j * w + i;
            if (cov > mask[o]) {
                mask[o] = cov;
            }
        }

        private int px(float wx) {
            return (int) Math.floor((wx + halfX) * ppm);
        }

        private int py(float wy) {
            return (int) Math.floor((halfY - wy) * ppm);
        }

        void seg(float x1, float y1, float x2, float y2, float hw) {
            int i0 = Math.max(0, px(Math.min(x1, x2) - hw) - 1);
            int i1 = Math.min(w - 1, px(Math.max(x1, x2) + hw) + 1);
            int j0 = Math.max(0, py(Math.max(y1, y2) + hw) - 1);
            int j1 = Math.min(h - 1, py(Math.min(y1, y2) - hw) + 1);
            float vx = x2 - x1;
            float vy = y2 - y1;
            float l2 = vx * vx + vy * vy;
            for (int j = j0; j <= j1; j++) {
                float wy = halfY - (j + 0.5f) / ppm;
                for (int i = i0; i <= i1; i++) {
                    float wx = -halfX + (i + 0.5f) / ppm;
                    float t = l2 < 1e-9f ? 0f : ((wx - x1) * vx + (wy - y1) * vy) / l2;
                    t = Math.max(0f, Math.min(1f, t));
                    float dx = wx - (x1 + vx * t);
                    float dy = wy - (y1 + vy * t);
                    put(i, j, (float) Math.sqrt(dx * dx + dy * dy), hw);
                }
            }
        }

        void rect(float x0, float y0, float x1, float y1, float hw) {
            seg(x0, y0, x1, y0, hw);
            seg(x1, y0, x1, y1, hw);
            seg(x1, y1, x0, y1, hw);
            seg(x0, y1, x0, y0, hw);
        }

        void dot(float cx, float cy, float r) {
            int i0 = Math.max(0, px(cx - r) - 1);
            int i1 = Math.min(w - 1, px(cx + r) + 1);
            int j0 = Math.max(0, py(cy + r) - 1);
            int j1 = Math.min(h - 1, py(cy - r) + 1);
            for (int j = j0; j <= j1; j++) {
                float wy = halfY - (j + 0.5f) / ppm;
                for (int i = i0; i <= i1; i++) {
                    float wx = -halfX + (i + 0.5f) / ppm;
                    float dx = wx - cx;
                    float dy = wy - cy;
                    put(i, j, (float) Math.sqrt(dx * dx + dy * dy) - r, 0f);
                }
            }
        }

        /** Arco de circunferencia de radio r desde a0 hasta a1 (grados, antihorario). */
        void arc(float cx, float cy, float r, float a0, float a1, float hw) {
            int i0 = Math.max(0, px(cx - r - hw) - 1);
            int i1 = Math.min(w - 1, px(cx + r + hw) + 1);
            int j0 = Math.max(0, py(cy + r + hw) - 1);
            int j1 = Math.min(h - 1, py(cy - r - hw) + 1);
            float span = a1 - a0;
            boolean full = span >= 359.9f;
            float e0x = cx + r * (float) Math.cos(Math.toRadians(a0));
            float e0y = cy + r * (float) Math.sin(Math.toRadians(a0));
            float e1x = cx + r * (float) Math.cos(Math.toRadians(a1));
            float e1y = cy + r * (float) Math.sin(Math.toRadians(a1));
            for (int j = j0; j <= j1; j++) {
                float wy = halfY - (j + 0.5f) / ppm;
                for (int i = i0; i <= i1; i++) {
                    float wx = -halfX + (i + 0.5f) / ppm;
                    float dx = wx - cx;
                    float dy = wy - cy;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);
                    float d;
                    boolean inRange = full;
                    if (!full) {
                        float ang = (float) Math.toDegrees(Math.atan2(dy, dx));
                        float rel = (ang - a0) % 360f;
                        if (rel < 0f) {
                            rel += 360f;
                        }
                        inRange = rel <= span;
                    }
                    if (inRange) {
                        d = Math.abs(dist - r);
                    } else {
                        float d0 = (float) Math.hypot(wx - e0x, wy - e0y);
                        float d1 = (float) Math.hypot(wx - e1x, wy - e1y);
                        d = Math.min(d0, d1);
                    }
                    put(i, j, d, hw);
                }
            }
        }
    }
}
