package com.rivalesfc.game.gfx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.rivalesfc.game.Constants;

/**
 * Rastro de movimiento estilo arcade que aparece detrás de un jugador cuando
 * corre por encima de {@link Constants#TRAIL_SPEED_THRESHOLD}, inspirado en
 * la estela de color que se ve detrás de los personajes en la imagen de
 * referencia. No usa {@code FrameBuffer} ni shaders: es simplemente una cola
 * de posiciones recientes, dibujadas con radio y opacidad decrecientes hacia
 * atrás en el tiempo.
 */
public class MotionTrail {

    private static final class Point {
        float x;
        float y;
    }

    private final Array<Point> points = new Array<>();
    private final int maxPoints;

    public MotionTrail(int maxPoints) {
        this.maxPoints = Math.max(2, maxPoints);
    }

    /** Se llama una vez por tick de simulación con la posición y velocidad actual del jugador. */
    public void update(Vector2 position, float speed) {
        if (speed >= Constants.TRAIL_SPEED_THRESHOLD) {
            boolean addPoint = points.size == 0;
            if (!addPoint) {
                Point last = points.peek();
                float dx = position.x - last.x;
                float dy = position.y - last.y;
                addPoint = (dx * dx + dy * dy) >= Constants.TRAIL_MIN_DIST * Constants.TRAIL_MIN_DIST;
            }
            if (addPoint) {
                Point p = new Point();
                p.x = position.x;
                p.y = position.y;
                points.add(p);
                if (points.size > maxPoints) {
                    points.removeIndex(0);
                }
            }
        } else if (points.size > 0) {
            // Sin sprint sostenido: el rastro se va "consumiendo" de a un punto por tick.
            points.removeIndex(0);
        }
    }

    public boolean isEmpty() {
        return points.size < 2;
    }

    /** Vacía el rastro (por ejemplo, al reiniciar el partido) para no dejar estelas "fantasma". */
    public void clear() {
        points.clear();
    }

    /** Requiere que el {@link ShapeRenderer} ya tenga seteada la matriz de proyección de la cámara activa. */
    public void render(ShapeRenderer shapes, Color teamColor, float baseRadius) {
        if (isEmpty()) {
            return;
        }
        int n = points.size;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < n; i++) {
            Point p = points.get(i);
            float t = (i + 1f) / n; // 0 = más viejo/chico, 1 = más nuevo/grande
            float alpha = 0.40f * t;
            float radius = baseRadius * (0.35f + 0.65f * t);
            shapes.setColor(teamColor.r, teamColor.g, teamColor.b, alpha);
            shapes.circle(p.x, p.y, radius, 14);
        }
        shapes.end();
    }
}
