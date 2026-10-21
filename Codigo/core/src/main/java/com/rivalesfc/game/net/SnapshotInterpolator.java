package com.rivalesfc.game.net;

public class SnapshotInterpolator {

    public static final long DELAY_NANOS = 70_000_000L;

    public static final float TELEPORT_METERS = 4f;

    private static final long MAX_EXTRAPOLATE_NANOS = 100_000_000L;

    private static final int CAP = 8;

    private final Snapshot[] snaps = new Snapshot[CAP];
    private final long[] arrival = new long[CAP];
    private int count = 0;
    private int head = 0;

    public SnapshotInterpolator() {
        for (int i = 0; i < CAP; i++) snaps[i] = new Snapshot();
    }

    public void clear() {
        count = 0;
        head = 0;
    }

    public boolean hasData() {
        return count > 0;
    }

    public void push(Snapshot s, long nowNanos) {
        snaps[head].copyFrom(s);
        arrival[head] = nowNanos;
        head = (head + 1) % CAP;
        if (count < CAP) count++;
    }

    private int idx(int i) {
        return (head - count + i + CAP * 2) % CAP;
    }

    public boolean sample(long nowNanos, Snapshot out) {
        if (count == 0) return false;
        long t = nowNanos - DELAY_NANOS;

        int newest = idx(count - 1);
        int oldest = idx(0);

        if (count == 1 || t <= arrival[oldest]) {
            out.copyFrom(snaps[oldest]);
            return true;
        }
        if (t >= arrival[newest]) {

            out.copyFrom(snaps[newest]);
            long ahead = Math.min(t - arrival[newest], MAX_EXTRAPOLATE_NANOS);
            extrapolate(out, ahead / 1_000_000_000f);
            return true;
        }

        int a = oldest;
        int b = oldest;
        for (int i = 0; i < count - 1; i++) {
            int ia = idx(i);
            int ib = idx(i + 1);
            if (arrival[ia] <= t && t < arrival[ib]) {
                a = ia;
                b = ib;
                break;
            }
        }
        long span = arrival[b] - arrival[a];
        float alpha = span <= 0 ? 1f : (float) (t - arrival[a]) / (float) span;
        alpha = Math.max(0f, Math.min(1f, alpha));
        blend(snaps[a], snaps[b], alpha, out);
        return true;
    }

    private static void extrapolate(Snapshot s, float seconds) {
        if (seconds <= 0f) return;
        s.ballX += s.ballVX * seconds;
        s.ballY += s.ballVY * seconds;
        for (int i = 0; i < 2; i++) {
            s.px[i] += s.pvx[i] * seconds;
            s.py[i] += s.pvy[i] * seconds;
            s.ky[i] += s.kvy[i] * seconds;
        }
    }

    static void blend(Snapshot a, Snapshot b, float alpha, Snapshot out) {
        out.copyFrom(alpha < 0.5f ? a : b);

        if (dist(a.ballX, a.ballY, b.ballX, b.ballY) <= TELEPORT_METERS) {
            out.ballX = lerp(a.ballX, b.ballX, alpha);
            out.ballY = lerp(a.ballY, b.ballY, alpha);
            out.ballAngle = lerpAngle(a.ballAngle, b.ballAngle, alpha);
            out.ballVX = lerp(a.ballVX, b.ballVX, alpha);
            out.ballVY = lerp(a.ballVY, b.ballVY, alpha);
        } else {
            out.ballX = b.ballX; out.ballY = b.ballY; out.ballAngle = b.ballAngle;
            out.ballVX = b.ballVX; out.ballVY = b.ballVY;
        }

        for (int i = 0; i < 2; i++) {

            if (dist(a.px[i], a.py[i], b.px[i], b.py[i]) <= TELEPORT_METERS) {
                out.px[i] = lerp(a.px[i], b.px[i], alpha);
                out.py[i] = lerp(a.py[i], b.py[i], alpha);
                out.pvx[i] = lerp(a.pvx[i], b.pvx[i], alpha);
                out.pvy[i] = lerp(a.pvy[i], b.pvy[i], alpha);
            } else {
                out.px[i] = b.px[i]; out.py[i] = b.py[i];
                out.pvx[i] = b.pvx[i]; out.pvy[i] = b.pvy[i];
            }
            out.kickPower[i] = lerp(a.kickPower[i], b.kickPower[i], alpha);

            if (Math.abs(a.ky[i] - b.ky[i]) <= TELEPORT_METERS) {
                out.ky[i] = lerp(a.ky[i], b.ky[i], alpha);
                out.kvy[i] = lerp(a.kvy[i], b.kvy[i], alpha);
                out.kdive[i] = lerp(a.kdive[i], b.kdive[i], alpha);
            } else {
                out.ky[i] = b.ky[i]; out.kvy[i] = b.kvy[i]; out.kdive[i] = b.kdive[i];
            }
        }
    }

    private static float lerp(float x, float y, float t) {
        return x + (y - x) * t;
    }

    private static float lerpAngle(float x, float y, float t) {
        float d = y - x;
        float twoPi = (float) (Math.PI * 2.0);
        while (d > Math.PI) d -= twoPi;
        while (d < -Math.PI) d += twoPi;
        return x + d * t;
    }

    private static float dist(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
