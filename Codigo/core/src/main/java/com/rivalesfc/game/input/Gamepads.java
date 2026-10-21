package com.rivalesfc.game.input;

public final class Gamepads {

    public static final int MAX = 4;
    private static final float DEAD = 0.28f;

    public static final class Raw {
        public float lx, ly, lt, rt;
        public boolean a, b, x, y, lb, rb, start, up, down, left, right;
    }

    public interface Provider {

        int refresh();

        boolean read(int index, Raw out);
    }

    public static final class State {
        public boolean present;
        public float moveX, moveY;
        public boolean kick;
        public boolean sprint;
        public boolean slideJust;
        public boolean startJust;

        public boolean upJust, downJust, leftJust, rightJust, confirmJust, backJust;

        private boolean pSlide, pStart, pUp, pDown, pLeft, pRight, pConfirm, pBack;
    }

    public static Provider provider;

    private static final State NONE = new State();

    private static final State[] STATES = new State[MAX];
    private static final Raw RAW = new Raw();
    private static int count = 0;

    static {
        for (int i = 0; i < MAX; i++) {
            STATES[i] = new State();
        }
    }

    private Gamepads() {
    }

    public static int count() {
        return count;
    }

    public static State state(int index) {
        return index >= 0 && index < MAX ? STATES[index] : NONE;
    }

    public static boolean has(int index) {
        return index >= 0 && index < MAX && STATES[index].present;
    }

    public static void poll() {
        int n = 0;
        if (provider != null) {
            try {
                n = Math.max(0, Math.min(MAX, provider.refresh()));
            } catch (RuntimeException | LinkageError e) {
                provider = null;
                n = 0;
            }
        }
        count = n;
        for (int i = 0; i < MAX; i++) {
            State s = STATES[i];
            boolean ok = false;
            if (i < n) {
                try {
                    ok = provider.read(i, RAW);
                } catch (RuntimeException | LinkageError e) {
                    ok = false;
                }
            }
            if (!ok) {
                clear(s);
                continue;
            }
            fill(s, RAW);
        }
    }

    private static void clear(State s) {
        s.present = false;
        s.moveX = 0f;
        s.moveY = 0f;
        s.kick = false;
        s.sprint = false;
        s.slideJust = false;
        s.startJust = false;
        s.upJust = s.downJust = s.leftJust = s.rightJust = s.confirmJust = s.backJust = false;
        s.pSlide = s.pStart = s.pUp = s.pDown = s.pLeft = s.pRight = s.pConfirm = s.pBack = false;
    }

    private static void fill(State s, Raw r) {
        s.present = true;

        float x = r.lx, y = r.ly;
        float len = (float) Math.sqrt(x * x + y * y);
        if (len < DEAD) {
            x = 0f;
            y = 0f;
        } else {

            float k = Math.min(1f, (len - DEAD) / (1f - DEAD)) / len;
            x *= k;
            y *= k;
        }
        if (r.left) x = -1f;
        if (r.right) x = 1f;
        if (r.up) y = 1f;
        if (r.down) y = -1f;
        s.moveX = x;
        s.moveY = y;

        s.kick = r.a || r.rt > 0.5f;
        s.sprint = r.b || r.lb || r.rb || r.lt > 0.5f;

        boolean slide = r.x;
        s.slideJust = slide && !s.pSlide;
        s.pSlide = slide;

        s.startJust = r.start && !s.pStart;
        s.pStart = r.start;

        boolean up = r.up || r.ly > 0.6f;
        boolean down = r.down || r.ly < -0.6f;
        boolean left = r.left || r.lx < -0.6f;
        boolean right = r.right || r.lx > 0.6f;
        s.upJust = up && !s.pUp;
        s.downJust = down && !s.pDown;
        s.leftJust = left && !s.pLeft;
        s.rightJust = right && !s.pRight;
        s.pUp = up;
        s.pDown = down;
        s.pLeft = left;
        s.pRight = right;

        s.confirmJust = r.a && !s.pConfirm;
        s.pConfirm = r.a;
        s.backJust = r.b && !s.pBack;
        s.pBack = r.b;
    }

    public static boolean anyStartJust() {
        for (int i = 0; i < MAX; i++) {
            if (STATES[i].present && STATES[i].startJust) return true;
        }
        return false;
    }

    public static boolean anyUpJust() {
        for (int i = 0; i < MAX; i++) if (STATES[i].present && STATES[i].upJust) return true;
        return false;
    }

    public static boolean anyDownJust() {
        for (int i = 0; i < MAX; i++) if (STATES[i].present && STATES[i].downJust) return true;
        return false;
    }

    public static boolean anyLeftJust() {
        for (int i = 0; i < MAX; i++) if (STATES[i].present && STATES[i].leftJust) return true;
        return false;
    }

    public static boolean anyRightJust() {
        for (int i = 0; i < MAX; i++) if (STATES[i].present && STATES[i].rightJust) return true;
        return false;
    }

    public static boolean anyConfirmJust() {
        for (int i = 0; i < MAX; i++) if (STATES[i].present && STATES[i].confirmJust) return true;
        return false;
    }
}
