package com.rivalesfc.game.gfx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.rivalesfc.game.Constants;

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

            points.removeIndex(0);
        }
    }

    public boolean isEmpty() {
        return points.size < 2;
    }

    public void clear() {
        points.clear();
    }

    public void render(ShapeRenderer shapes, Color teamColor, float baseRadius) {
        if (isEmpty()) {
            return;
        }
        int n = points.size;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < n; i++) {
            Point p = points.get(i);
            float t = (i + 1f) / n;
            float alpha = 0.40f * t;
            float radius = baseRadius * (0.35f + 0.65f * t);
            shapes.setColor(teamColor.r, teamColor.g, teamColor.b, alpha);
            shapes.circle(p.x, p.y, radius, 14);
        }
        shapes.end();
    }
}
