package com.rivalesfc.game.gfx;

/**
 * Rasterizador vectorial por software (Java puro, sin LibGDX) con antialiasing.
 *
 * Reemplaza al dibujo con {@code Pixmap.fillRectangle/fillCircle} de la MK6, que
 * producía bordes dentados. Cada forma se define como un campo de distancia con
 * signo (SDF); el borde se suaviza con cobertura analítica y la imagen final se
 * reduce desde una grilla supermuestreada. Todas las coordenadas se expresan en
 * píxeles de la imagen FINAL (con decimales), el supermuestreo es interno.
 *
 * Al no depender de LibGDX se puede probar y previsualizar fuera del juego.
 */
public final class SoftCanvas {

    // ------------------------------------------------------------------
    // Formas (campos de distancia con signo: negativo = adentro)
    // ------------------------------------------------------------------

    public interface Shape {
        /** Distancia con signo al borde de la forma, en píxeles finales. */
        float dist(float x, float y);

        /** Caja que contiene la forma: {minX, minY, maxX, maxY}. */
        void bounds(float[] out);
    }

    /** Color por posición (ARGB sin premultiplicar). */
    public interface Paint {
        int argb(float x, float y);
    }

    public static Shape ellipse(final float cx, final float cy, final float rx, final float ry) {
        return new Shape() {
            @Override
            public float dist(float x, float y) {
                float dx = x - cx;
                float dy = y - cy;
                float k = (float) Math.sqrt((dx * dx) / (rx * rx) + (dy * dy) / (ry * ry));
                if (k < 1e-5f) {
                    return -Math.min(rx, ry);
                }
                float gx = dx / (rx * rx * k);
                float gy = dy / (ry * ry * k);
                float g = (float) Math.sqrt(gx * gx + gy * gy);
                return (k - 1f) / g;
            }

            @Override
            public void bounds(float[] o) {
                o[0] = cx - rx;
                o[1] = cy - ry;
                o[2] = cx + rx;
                o[3] = cy + ry;
            }
        };
    }

    public static Shape circle(float cx, float cy, float r) {
        return ellipse(cx, cy, r, r);
    }

    /** Rectángulo redondeado centrado en (cx,cy) con semiancho hw, semialto hh y radio de esquina r. */
    public static Shape roundRect(final float cx, final float cy, final float hw, final float hh, final float r0) {
        final float r = Math.min(r0, Math.min(hw, hh));
        return new Shape() {
            @Override
            public float dist(float x, float y) {
                float qx = Math.abs(x - cx) - hw + r;
                float qy = Math.abs(y - cy) - hh + r;
                float ox = Math.max(qx, 0f);
                float oy = Math.max(qy, 0f);
                return (float) Math.sqrt(ox * ox + oy * oy) + Math.min(Math.max(qx, qy), 0f) - r;
            }

            @Override
            public void bounds(float[] o) {
                o[0] = cx - hw;
                o[1] = cy - hh;
                o[2] = cx + hw;
                o[3] = cy + hh;
            }
        };
    }

    /** Cápsula (segmento con grosor) con radio r1 en el primer extremo y r2 en el segundo. */
    public static Shape capsule(final float x1, final float y1, final float x2, final float y2,
                                final float r1, final float r2) {
        return new Shape() {
            @Override
            public float dist(float x, float y) {
                float vx = x2 - x1;
                float vy = y2 - y1;
                float len2 = vx * vx + vy * vy;
                float t = len2 < 1e-6f ? 0f : ((x - x1) * vx + (y - y1) * vy) / len2;
                t = Math.max(0f, Math.min(1f, t));
                float px = x1 + vx * t;
                float py = y1 + vy * t;
                float dx = x - px;
                float dy = y - py;
                return (float) Math.sqrt(dx * dx + dy * dy) - (r1 + (r2 - r1) * t);
            }

            @Override
            public void bounds(float[] o) {
                float r = Math.max(r1, r2);
                o[0] = Math.min(x1, x2) - r;
                o[1] = Math.min(y1, y2) - r;
                o[2] = Math.max(x1, x2) + r;
                o[3] = Math.max(y1, y2) + r;
            }
        };
    }

    /** Polígono (pares x,y), distancia exacta a las aristas y signo por par-impar. */
    public static Shape polygon(final float... pts) {
        final int n = pts.length / 2;
        return new Shape() {
            @Override
            public float dist(float x, float y) {
                float best = Float.MAX_VALUE;
                boolean inside = false;
                for (int i = 0, j = n - 1; i < n; j = i++) {
                    float xi = pts[i * 2];
                    float yi = pts[i * 2 + 1];
                    float xj = pts[j * 2];
                    float yj = pts[j * 2 + 1];
                    if (((yi > y) != (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi) + xi)) {
                        inside = !inside;
                    }
                    float ex = xj - xi;
                    float ey = yj - yi;
                    float l2 = ex * ex + ey * ey;
                    float t = l2 < 1e-6f ? 0f : ((x - xi) * ex + (y - yi) * ey) / l2;
                    t = Math.max(0f, Math.min(1f, t));
                    float dx = x - (xi + ex * t);
                    float dy = y - (yi + ey * t);
                    float d = dx * dx + dy * dy;
                    if (d < best) {
                        best = d;
                    }
                }
                float d = (float) Math.sqrt(best);
                return inside ? -d : d;
            }

            @Override
            public void bounds(float[] o) {
                float minX = Float.MAX_VALUE;
                float minY = Float.MAX_VALUE;
                float maxX = -Float.MAX_VALUE;
                float maxY = -Float.MAX_VALUE;
                for (int i = 0; i < n; i++) {
                    minX = Math.min(minX, pts[i * 2]);
                    maxX = Math.max(maxX, pts[i * 2]);
                    minY = Math.min(minY, pts[i * 2 + 1]);
                    maxY = Math.max(maxY, pts[i * 2 + 1]);
                }
                o[0] = minX;
                o[1] = minY;
                o[2] = maxX;
                o[3] = maxY;
            }
        };
    }

    /** a menos b. */
    public static Shape minus(final Shape a, final Shape b) {
        return new Shape() {
            @Override
            public float dist(float x, float y) {
                return Math.max(a.dist(x, y), -b.dist(x, y));
            }

            @Override
            public void bounds(float[] o) {
                a.bounds(o);
            }
        };
    }

    /** Intersección de a y b. */
    public static Shape intersect(final Shape a, final Shape b) {
        return new Shape() {
            @Override
            public float dist(float x, float y) {
                return Math.max(a.dist(x, y), b.dist(x, y));
            }

            @Override
            public void bounds(float[] o) {
                float[] ob = new float[4];
                a.bounds(o);
                b.bounds(ob);
                o[0] = Math.max(o[0], ob[0]);
                o[1] = Math.max(o[1], ob[1]);
                o[2] = Math.min(o[2], ob[2]);
                o[3] = Math.min(o[3], ob[3]);
            }
        };
    }

    // ------------------------------------------------------------------
    // Colores y pinturas
    // ------------------------------------------------------------------

    public static int argb(float r, float g, float b, float a) {
        return (clamp8(a) << 24) | (clamp8(r) << 16) | (clamp8(g) << 8) | clamp8(b);
    }

    public static int rgb(float r, float g, float b) {
        return argb(r, g, b, 1f);
    }

    private static int clamp8(float v) {
        int i = Math.round(v * 255f);
        return i < 0 ? 0 : (i > 255 ? 255 : i);
    }

    /** Multiplica el RGB por f (>1 aclara, <1 oscurece), conserva el alfa. */
    public static int shade(int c, float f) {
        int a = c >>> 24;
        int r = Math.min(255, Math.max(0, Math.round(((c >> 16) & 255) * f)));
        int g = Math.min(255, Math.max(0, Math.round(((c >> 8) & 255) * f)));
        int b = Math.min(255, Math.max(0, Math.round((c & 255) * f)));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int lerp(int c0, int c1, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = Math.round((c0 >>> 24) + ((c1 >>> 24) - (c0 >>> 24)) * t);
        int r = Math.round(((c0 >> 16) & 255) + (((c1 >> 16) & 255) - ((c0 >> 16) & 255)) * t);
        int g = Math.round(((c0 >> 8) & 255) + (((c1 >> 8) & 255) - ((c0 >> 8) & 255)) * t);
        int b = Math.round((c0 & 255) + ((c1 & 255) - (c0 & 255)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int withAlpha(int c, float a) {
        return (clamp8(a) << 24) | (c & 0x00FFFFFF);
    }

    public static Paint solid(final int c) {
        return new Paint() {
            @Override
            public int argb(float x, float y) {
                return c;
            }
        };
    }

    /** Degradé vertical entre y0 (color c0) e y1 (color c1). */
    public static Paint vgrad(final float y0, final int c0, final float y1, final int c1) {
        return new Paint() {
            @Override
            public int argb(float x, float y) {
                return lerp(c0, c1, (y - y0) / (y1 - y0));
            }
        };
    }

    /** Degradé horizontal entre x0 (color c0) y x1 (color c1). */
    public static Paint hgrad(final float x0, final int c0, final float x1, final int c1) {
        return new Paint() {
            @Override
            public int argb(float x, float y) {
                return lerp(c0, c1, (x - x0) / (x1 - x0));
            }
        };
    }

    /** Degradé radial: color c0 en el centro, c1 a distancia r. */
    public static Paint radial(final float cx, final float cy, final float r, final int c0, final int c1) {
        return new Paint() {
            @Override
            public int argb(float x, float y) {
                float dx = x - cx;
                float dy = y - cy;
                return lerp(c0, c1, (float) Math.sqrt(dx * dx + dy * dy) / r);
            }
        };
    }

    // ------------------------------------------------------------------
    // Lienzo
    // ------------------------------------------------------------------

    public final int width;
    public final int height;
    private final int ss;
    private final int bigW;
    private final int bigH;
    /** RGBA premultiplicado, bigW*bigH*4. */
    private final float[] buf;
    private final float[] bb = new float[4];

    public SoftCanvas(int width, int height, int supersample) {
        this.width = width;
        this.height = height;
        this.ss = Math.max(1, supersample);
        this.bigW = width * ss;
        this.bigH = height * ss;
        this.buf = new float[bigW * bigH * 4];
    }

    /** Rellena la forma con la pintura. */
    public void fill(Shape s, Paint p) {
        raster(s, p, 0f);
    }

    /** Rellena la forma con un color sólido. */
    public void fill(Shape s, int color) {
        raster(s, solid(color), 0f);
    }

    /** Dibuja un contorno de grosor {@code w} hacia afuera (llamar ANTES de {@link #fill}). */
    public void outline(Shape s, float w, int color) {
        raster(s, solid(color), w);
    }

    /** Contorno + relleno. */
    public void fillOutlined(Shape s, Paint p, float w, int outlineColor) {
        raster(s, solid(outlineColor), w);
        raster(s, p, 0f);
    }

    public void fillOutlined(Shape s, int color, float w, int outlineColor) {
        fillOutlined(s, solid(color), w, outlineColor);
    }

    private void raster(Shape s, Paint p, float grow) {
        s.bounds(bb);
        int x0 = Math.max(0, (int) Math.floor((bb[0] - grow - 1f) * ss));
        int y0 = Math.max(0, (int) Math.floor((bb[1] - grow - 1f) * ss));
        int x1 = Math.min(bigW - 1, (int) Math.ceil((bb[2] + grow + 1f) * ss));
        int y1 = Math.min(bigH - 1, (int) Math.ceil((bb[3] + grow + 1f) * ss));
        final float inv = 1f / ss;
        for (int iy = y0; iy <= y1; iy++) {
            float fy = (iy + 0.5f) * inv;
            for (int ix = x0; ix <= x1; ix++) {
                float fx = (ix + 0.5f) * inv;
                float d = s.dist(fx, fy);
                float cov = (grow - d) * ss + 0.5f;
                if (cov <= 0f) {
                    continue;
                }
                if (cov > 1f) {
                    cov = 1f;
                }
                int c = p.argb(fx, fy);
                float sa = ((c >>> 24) / 255f) * cov;
                if (sa <= 0f) {
                    continue;
                }
                int o = (iy * bigW + ix) * 4;
                float k = 1f - sa;
                buf[o] = ((c >> 16) & 255) / 255f * sa + buf[o] * k;
                buf[o + 1] = ((c >> 8) & 255) / 255f * sa + buf[o + 1] * k;
                buf[o + 2] = (c & 255) / 255f * sa + buf[o + 2] * k;
                buf[o + 3] = sa + buf[o + 3] * k;
            }
        }
    }

    /**
     * Reduce a la resolución final y devuelve ARGB sin premultiplicar. Los píxeles
     * totalmente transparentes llevan {@code bleedColor} como RGB, para que el filtro
     * bilineal/mipmaps de la GPU no dibuje un halo de otro color en los bordes.
     */
    public int[] toArgb(int bleedColor) {
        int[] out = new int[width * height];
        float n = ss * ss;
        int bleed = bleedColor & 0x00FFFFFF;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float r = 0f;
                float g = 0f;
                float b = 0f;
                float a = 0f;
                for (int sy = 0; sy < ss; sy++) {
                    int row = ((y * ss + sy) * bigW + x * ss) * 4;
                    for (int sx = 0; sx < ss; sx++) {
                        int o = row + sx * 4;
                        r += buf[o];
                        g += buf[o + 1];
                        b += buf[o + 2];
                        a += buf[o + 3];
                    }
                }
                if (a < 1e-4f) {
                    out[y * width + x] = bleed;
                    continue;
                }
                int ri = clamp8(r / a);
                int gi = clamp8(g / a);
                int bi = clamp8(b / a);
                int ai = clamp8(a / n);
                out[y * width + x] = (ai << 24) | (ri << 16) | (gi << 8) | bi;
            }
        }
        return out;
    }
}
