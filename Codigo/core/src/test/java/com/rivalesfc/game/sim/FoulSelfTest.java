package com.rivalesfc.game.sim;

import com.badlogic.gdx.math.Vector2;
import com.rivalesfc.game.Constants;
import com.rivalesfc.game.sim.FoulRules.Call;
import com.rivalesfc.game.sim.FoulRules.Card;
import com.rivalesfc.game.sim.FoulRules.DisciplineBook;
import com.rivalesfc.game.sim.FoulRules.Type;

/**
 * MK8: pruebas de la lógica de faltas. No usa Box2D ni ventana (FoulRules y SetPieceLayout son Java puro),
 * así que corre en cualquier lado: {@code java -cp <clases> com.rivalesfc.game.sim.FoulSelfTest}.
 */
public class FoulSelfTest {

    static int fails = 0;

    static void check(String name, boolean ok) {
        check(name, ok, "");
    }

    static void check(String name, boolean ok, String detail) {
        System.out.println((ok ? "OK    " : "FALLA ") + name + (detail.isEmpty() ? "" : "  [" + detail + "]"));
        if (!ok) fails++;
    }

    /** Atacante en el origen mirando a +X; rival 1 m adelante. */
    static Call judge(boolean sliding, boolean touched, boolean victimSliding, boolean playsBall, boolean victimBall,
                      float attackerVx, float victimVx) {
        Call c = new Call();
        FoulRules.judge(sliding, touched, victimSliding, playsBall, victimBall,
                0f, 0f, attackerVx, 0f, 1f, 0f, victimVx, 0f, c);
        return c;
    }

    static void slides() {
        Call c = judge(true, false, false, false, false, 8f, 0f);
        check("planchazo al rival sin tocar la pelota es falta", c.type == Type.SLIDE && c.severity >= 1, "gravedad " + c.severity);

        c = judge(true, true, false, true, false, 8f, 0f);
        check("planchazo que toca la pelota primero es limpio", !c.isFoul(), "");

        c = judge(true, false, true, false, false, 8f, 0f);
        check("dos planchazos que se cruzan: no se cobra", !c.isFoul(), "");

        c = judge(true, false, false, false, false, 8f, 0f);
        int plain = c.severity;
        c = judge(true, false, false, false, true, 8f, 0f);
        check("si el rival tenía la pelota, la gravedad sube", c.severity == plain + 1 && c.victimHadBall, plain + " -> " + c.severity);

        c = judge(true, false, false, false, false, 8f, 4f);
        check("planchazo por detrás (el rival se aleja) suma gravedad", c.fromBehind && c.severity >= 2, "gravedad " + c.severity);

        c = judge(true, false, false, false, true, 8f, 4f);
        check("por detrás + con pelota = amarilla (gravedad >= 3)", c.severity >= Constants.FOUL_YELLOW_SEVERITY, "gravedad " + c.severity);

        c = judge(true, false, false, false, false, 8f, -3f);
        check("entrada fuerte de frente (cierre alto) suma gravedad", c.hardHit, "cierre " + (8f + 3f));
    }

    static void charges() {
        Call c = judge(false, false, false, false, false, 10.5f, 0f);
        check("chocar en sprint a un rival quieto, sin jugar la pelota, es falta de carga", c.type == Type.CHARGE, "");

        c = judge(false, false, false, true, false, 10.5f, 0f);
        check("si el que choca está jugando la pelota, no se cobra", !c.isFoul(), "");

        c = judge(false, false, false, false, false, 7.5f, 0f);
        check("caminando (7,5 m/s) no es falta", !c.isFoul(), "");

        c = judge(false, false, false, false, false, 10.5f, -9.5f);
        check("choque de frente a velocidades parecidas: nadie es culpable", !c.isFoul(), "");

        c = judge(false, false, false, false, false, 10.5f, -3f);
        check("choque de frente: el que viene mucho más rápido sí es culpable", c.type == Type.CHARGE, "");
    }

    static void cards() {
        DisciplineBook b = new DisciplineBook();
        check("1ª falta leve: sin tarjeta", b.record(0, 1) == Card.NONE, "");
        check("2ª falta leve: sin tarjeta", b.record(0, 2) == Card.NONE, "");
        check("3ª falta del mismo jugador: amarilla por reincidencia", b.record(0, 1) == Card.YELLOW, "");
        check("falta grave: amarilla", b.record(1, Constants.FOUL_YELLOW_SEVERITY) == Card.YELLOW, "");
        check("2ª amarilla del mismo jugador = roja", b.record(1, Constants.FOUL_YELLOW_SEVERITY) == Card.RED, "");
        check("tras la roja las amarillas vuelven a cero", b.yellows(1) == 0, "");
        check("roja directa por gravedad máxima", b.record(2, Constants.FOUL_RED_SEVERITY) == Card.RED, "");
        check("las faltas se cuentan por jugador", b.fouls(0) == 3 && b.fouls(1) == 2 && b.fouls(2) == 1, "");
        b.reset();
        check("reset deja todo en cero", b.fouls(0) == 0 && b.yellows(0) == 0, "");
    }

    static void penaltyArea() {
        float halfW = Constants.FIELD_WIDTH / 2f;
        check("punto dentro del área izquierda", FoulRules.inPenaltyArea(0, -halfW + 2f, 3f), "");
        check("punto dentro del área derecha", FoulRules.inPenaltyArea(1, halfW - 2f, -3f), "");
        check("el área izquierda no vale para el equipo derecho", !FoulRules.inPenaltyArea(1, -halfW + 2f, 0f), "");
        check("fuera del área por profundidad", !FoulRules.inPenaltyArea(0, -halfW + Constants.PENALTY_BOX_DEPTH + 1f, 0f), "");
        check("fuera del área por costado", !FoulRules.inPenaltyArea(0, -halfW + 2f, Constants.PENALTY_BOX_HEIGHT / 2f + 1f), "");
        check("mitad de cancha no es área", !FoulRules.inPenaltyArea(0, 0f, 0f) && !FoulRules.inPenaltyArea(1, 0f, 0f), "");
    }

    static boolean inField(Vector2 p) {
        return Math.abs(p.x) <= Constants.FIELD_WIDTH / 2f && Math.abs(p.y) <= Constants.FIELD_HEIGHT / 2f;
    }

    static void layouts() {
        Vector2 t = new Vector2(), w0 = new Vector2(), w1 = new Vector2(), m0 = new Vector2();

        // Tiro libre en medio de la cancha, patea el equipo izquierdo (ataca hacia +X).
        SetPieceLayout l = new SetPieceLayout(0, -3f, 4f, false);
        l.takerSpot(t);
        check("el que patea queda detrás de la pelota respecto del arco", t.x < l.ball.x && t.dst(l.ball) > 0.8f && t.dst(l.ball) < 1.3f,
                String.format("dist %.2f", t.dst(l.ball)));
        l.wallSpot(0, w0);
        l.wallSpot(1, w1);
        check("la barrera está a ~5,5 m y entre la pelota y el arco", Math.abs(w0.dst(l.ball) - Constants.FREE_KICK_WALL_DIST) < 0.2f && w0.x > l.ball.x,
                String.format("dist %.2f", w0.dst(l.ball)));
        check("los dos de la barrera no se superponen", w0.dst(w1) > 1.2f, String.format("sep %.2f", w0.dst(w1)));

        // Todas las posiciones en lugares posibles: se barre una grilla de faltas por toda la cancha, para los dos equipos.
        int bad = 0, total = 0;
        for (int team = 0; team < 2; team++) {
            for (float x = -22f; x <= 22f; x += 1.1f) {
                for (float y = -14f; y <= 14f; y += 1.1f) {
                    SetPieceLayout s = new SetPieceLayout(team, x, y, false);
                    Vector2 a = new Vector2(), b = new Vector2(), c = new Vector2(), d = new Vector2();
                    s.takerSpot(a); s.wallSpot(0, b); s.wallSpot(1, c); s.mateSpot(0, d);
                    total++;
                    boolean ok = inField(s.ball) && inField(a) && inField(b) && inField(c) && inField(d)
                            && a.dst(s.ball) > 0.6f && b.dst(s.ball) > 3f && c.dst(s.ball) > 3f && b.dst(c) > 0.9f
                            && s.toGoal.len() > 0.99f && s.toGoal.len() < 1.01f;
                    if (!ok) {
                        bad++;
                        if (bad <= 3) System.out.println("      mal: equipo " + team + " falta en (" + x + "," + y + ")");
                    }
                }
            }
        }
        check("barrido de " + total + " faltas: todos adentro y sin amontonarse sobre la pelota", bad == 0, bad + " malas");

        // Penal.
        SetPieceLayout p = new SetPieceLayout(0, 20f, 1f, true);
        check("penal: la pelota va al punto de penal del arco derecho", Math.abs(p.ball.x - (Constants.FIELD_WIDTH / 2f - Constants.PENALTY_SPOT_DIST)) < 0.01f && p.ball.y == 0f,
                "x=" + p.ball.x);
        p.takerSpot(t);
        check("penal: el que patea queda en línea con el arco", Math.abs(t.y) < 0.01f && t.x < p.ball.x, "");
        p.wallSpot(0, w0);
        p.mateSpot(0, m0);
        check("penal: los demás esperan lejos de la pelota", w0.dst(p.ball) > 10f && m0.dst(p.ball) > 10f,
                String.format("%.1f / %.1f m", w0.dst(p.ball), m0.dst(p.ball)));
        SetPieceLayout p2 = new SetPieceLayout(1, -20f, -1f, true);
        check("penal para la derecha: punto en el arco izquierdo", p2.ball.x < -Constants.FIELD_WIDTH / 2f + Constants.PENALTY_BOX_DEPTH && p2.ball.x > -Constants.FIELD_WIDTH / 2f && p2.toGoal.x < 0f, "");
        check("el punto de penal coincide con el que dibuja ArtPainter (4,1 m de la línea)", Math.abs(Constants.PENALTY_SPOT_DIST - 4.1f) < 0.001f, "");
    }

    static void advantageRules() {
        float T = 60f;
        check("ventaja: se da si el perjudicado tiene la pelota (falta común)", AdvantageRules.eligible(true, false, FoulRules.Card.NONE, true, T));
        check("ventaja: también con amarilla", AdvantageRules.eligible(true, false, FoulRules.Card.YELLOW, true, T));
        check("ventaja: no con roja", !AdvantageRules.eligible(true, false, FoulRules.Card.RED, true, T));
        check("ventaja: no en penal", !AdvantageRules.eligible(true, true, FoulRules.Card.NONE, true, T));
        check("ventaja: no si el perjudicado no tiene la pelota", !AdvantageRules.eligible(true, false, FoulRules.Card.NONE, false, T));
        check("ventaja: no si está apagada", !AdvantageRules.eligible(false, false, FoulRules.Card.NONE, true, T));
        check("ventaja: no con poco tiempo", !AdvantageRules.eligible(true, false, FoulRules.Card.NONE, true, 2f));

        float dt = 1f / 30f;
        AdvantageRules.Tracker t = new AdvantageRules.Tracker();
        t.begin();
        AdvantageRules.Verdict v = AdvantageRules.Verdict.PENDING;
        int n = 0;
        while (v == AdvantageRules.Verdict.PENDING && n < 200) { v = t.update(dt, true, false, false, false); n++; }
        check("ventaja: con la pelota controlada se aprovecha antes de agotar la ventana", v == AdvantageRules.Verdict.REALIZED && n < 75, "ticks=" + n);

        t.begin(); v = AdvantageRules.Verdict.PENDING; n = 0;
        while (v == AdvantageRules.Verdict.PENDING && n < 200) { v = t.update(dt, false, true, false, false); n++; }
        check("ventaja: si el infractor se queda con la pelota se vuelve atrás enseguida", v == AdvantageRules.Verdict.CALL_BACK && n <= 8, "ticks=" + n);

        t.begin(); v = AdvantageRules.Verdict.PENDING; n = 0;
        while (v == AdvantageRules.Verdict.PENDING && n < 200) { v = t.update(dt, false, false, false, false); n++; }
        check("ventaja: pelota suelta toda la ventana, se cobra la falta", v == AdvantageRules.Verdict.CALL_BACK && n >= 70 && n <= 80, "ticks=" + n);

        t.begin();
        v = t.update(dt, false, false, true, false);
        check("ventaja: un remate del perjudicado la da por aprovechada", v == AdvantageRules.Verdict.REALIZED);

        t.begin();
        v = t.update(dt, true, false, false, true);
        check("ventaja: si se acaba el tiempo se cobra la falta", v == AdvantageRules.Verdict.CALL_BACK);

        t.begin();
        for (int i = 0; i < 5; i++) t.update(dt, false, true, false, false); // 5 ticks perdida (< 6)
        v = t.update(dt, true, false, false, false);
        check("ventaja: una pérdida breve (disputa) no la cancela", v == AdvantageRules.Verdict.PENDING);
    }

    public static void main(String[] args) {
        advantageRules();
        slides();
        charges();
        cards();
        penaltyArea();
        layouts();
        System.out.println(fails == 0 ? "\nTODO OK" : "\nFALLAS: " + fails);
        System.exit(fails == 0 ? 0 : 1);
    }
}
