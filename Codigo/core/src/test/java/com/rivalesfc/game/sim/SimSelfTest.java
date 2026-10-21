package com.rivalesfc.game.sim;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Box2D;
import com.rivalesfc.game.Constants;
import com.rivalesfc.game.GameMode;
import com.rivalesfc.game.Settings;
import com.rivalesfc.game.ai.SupportAI;
import com.rivalesfc.game.input.PlayerInput;

public class SimSelfTest {

    static int fails = 0;
    static final float DT = 1f / 30f;

    static void check(String name, boolean ok, String detail) {
        System.out.println((ok ? "OK    " : "FALLA ") + name + (detail.isEmpty() ? "" : "  [" + detail + "]"));
        if (!ok) fails++;
    }

    static MatchSimulation newPlaying(GameMode m) {
        MatchSimulation sim = new MatchSimulation(m);
        PlayerInput a = new PlayerInput(), b = new PlayerInput();
        for (int i = 0; i < 30 * 4; i++) sim.step(DT, a, b);
        return sim;
    }

    static void sprintSpeed() {
        MatchSimulation sim = newPlaying(GameMode.TWO_PLAYERS);
        PlayerInput l = new PlayerInput(), r = new PlayerInput();
        sim.getPlayerLeft().body.setTransform(-20f, 12f, 0f);
        sim.getBall().reset(new Vector2(15f, -10f));
        l.moveX = 1f; l.sprint = true;
        float max = 0f;
        for (int i = 0; i < 30; i++) { sim.step(DT, l, r); max = Math.max(max, sim.getPlayerLeft().body.getLinearVelocity().len()); }
        check("sprint alcanza ~10,5 m/s (era ~7,9)", max > 10f && max <= Constants.PLAYER_SPRINT_SPEED + 0.5f, String.format("%.2f m/s", max));

        l.sprint = false;
        sim.step(DT, l, r);
        float after = sim.getPlayerLeft().body.getLinearVelocity().len();
        check("al soltar Shift no frena de golpe", after > 9f, String.format("%.2f m/s tras 1 tick", after));
        float walk = 0f;
        for (int i = 0; i < 40; i++) { sim.step(DT, l, r); walk = sim.getPlayerLeft().body.getLinearVelocity().len(); }
        check("caminando se estabiliza en la velocidad normal", walk <= Constants.PLAYER_MAX_SPEED + 0.1f && walk > 6.5f, String.format("%.2f m/s", walk));
        sim.dispose();
    }

    static void cornerNotTrap() {

        MatchSimulation sim = newPlaying(GameMode.TWO_PLAYERS);
        PlayerInput l = new PlayerInput(), r = new PlayerInput();
        float hx = Constants.FIELD_WIDTH / 2f - 0.3f, hy = Constants.FIELD_HEIGHT / 2f - 0.3f;
        sim.getPlayerLeft().body.setTransform(-15f, -10f, 0f);
        sim.getPlayerRight().body.setTransform(-15f, 10f, 0f);
        sim.getBall().reset(new Vector2(hx, hy));
        sim.getBall().body.setLinearVelocity(8f, 8f);
        for (int i = 0; i < 30 * 3; i++) sim.step(DT, l, r);
        Vector2 p = sim.getBall().getPosition();
        float corner = new Vector2(Constants.FIELD_WIDTH / 2f, Constants.FIELD_HEIGHT / 2f).dst(p);
        check("pelota lanzada a la esquina no queda clavada en el vértice", corner > 0.5f, String.format("a %.2f m de la esquina", corner));
        sim.dispose();
    }

    static void unstuckWatchdog() {
        MatchSimulation sim = newPlaying(GameMode.TWO_PLAYERS);
        PlayerInput l = new PlayerInput(), r = new PlayerInput();
        sim.getPlayerLeft().body.setTransform(-15f, 10f, 0f);
        sim.getPlayerRight().body.setTransform(-15f, -10f, 0f);
        sim.getBall().reset(new Vector2(0f, Constants.FIELD_HEIGHT / 2f - 0.3f));
        boolean fired = false;
        for (int i = 0; i < (int) ((Constants.BALL_STUCK_SECONDS * 2f + 2f) * 30); i++) {
            sim.getBall().body.setLinearVelocity(0f, 0f);
            sim.step(DT, l, r);
            if (sim.consumeUnstuckEvent()) { fired = true; break; }
        }
        float y = Math.abs(sim.getBall().getPosition().y);
        check("pelota quieta 5 s se reubica hacia adentro", fired && y <= Constants.FIELD_HEIGHT / 2f - Constants.BALL_UNSTUCK_INSET + 0.01f, String.format("|y|=%.2f", y));
        sim.dispose();
    }

    static void aiVsAiNoStall() {
        MathUtils.random.setSeed(11);
        Settings.difficulty = Settings.Difficulty.NORMAL;
        Settings.halfMinutes = 1;
        int goals = 0;
        float maxStill = 0f;
        for (int m = 0; m < 4; m++) {
            MatchSimulation sim = new MatchSimulation(GameMode.ONE_PLAYER);
            SupportAI ai = new SupportAI(false);
            PlayerInput l = new PlayerInput(), r = new PlayerInput(), o = new PlayerInput();
            Vector2 anchor = new Vector2();
            float still = 0f;
            int ticks = 0;
            while (sim.getPhase() != MatchSimulation.Phase.FULL_TIME && ticks < 30 * 60 * 10) {
                ai.update(o, sim.getPlayerLeft(), sim.getBall(), sim.getPlayerRight(), sim.getKeeperRight(), DT);
                l.moveX = o.moveX; l.moveY = o.moveY; l.sprint = o.sprint; l.kickHeld = o.kickHeld;
                l.kickReleased |= o.kickReleased; l.slidePressed |= o.slidePressed; o.clearTransient();
                sim.step(DT, l, r);
                ticks++;
                if (sim.getPhase() == MatchSimulation.Phase.PLAYING) {
                    if (sim.getBall().getPosition().dst(anchor) > 1.5f) { anchor.set(sim.getBall().getPosition()); still = 0f; }
                    else still += DT;
                    maxStill = Math.max(maxStill, still);
                } else { still = 0f; anchor.set(sim.getBall().getPosition()); }
            }
            goals += sim.getScoreLeft() + sim.getScoreRight();
            check("partido IA vs IA " + m + " termina (no se cuelga)", sim.getPhase() == MatchSimulation.Phase.FULL_TIME, "ticks=" + ticks);
            sim.dispose();
        }
        check("IA vs IA: la pelota nunca queda parada más de 7 s (antes hasta 55 s)", maxStill < 7f, String.format("máx %.1f s", maxStill));
        check("IA vs IA: se hacen goles (antes 0-0 en NORMAL)", goals > 0, goals + " goles");
    }

    static void renderInterpolation() {
        MatchSimulation sim = newPlaying(GameMode.TWO_PLAYERS);
        PlayerInput l = new PlayerInput(), r = new PlayerInput();
        sim.getPlayerLeft().body.setTransform(-20f, 12f, 0f);
        sim.getBall().reset(new Vector2(15f, -10f));
        l.moveX = 1f; l.sprint = true;
        for (int i = 0; i < 10; i++) sim.step(DT, l, r);
        Vector2 v0 = new Vector2(), v1 = new Vector2();
        sim.step(DT * 0.25f, l, r);
        sim.renderPlayerLeft(v0);
        float a0 = sim.getRenderAlpha();
        sim.step(DT * 0.25f, l, r);
        sim.renderPlayerLeft(v1);
        float a1 = sim.getRenderAlpha();
        Vector2 cur = sim.getPlayerLeft().getPosition();
        check("el render avanza entre ticks (suavidad a 60 fps)", a1 > a0 && v1.x > v0.x && v1.x <= cur.x + 1e-4f, String.format("alpha %.2f->%.2f, x %.3f->%.3f", a0, a1, v0.x, v1.x));
        sim.dispose();
    }

    static void matchFlow() {

        Settings.halfMinutes = 1;
        MatchSimulation sim = new MatchSimulation(GameMode.TWO_PLAYERS);
        PlayerInput l = new PlayerInput(), r = new PlayerInput();
        boolean sawReplay = false, sawHalf = false;
        int ticks = 0;
        try {
            while (sim.getPhase() != MatchSimulation.Phase.FULL_TIME && ticks < 30 * 60 * 12) {
                if (sim.getPhase() == MatchSimulation.Phase.PLAYING && ticks % 200 == 0) {
                    sim.getKeeperRight().body.setTransform(21.4f, -2.6f, 0f);
                    sim.getBall().reset(new Vector2(19f, 2.6f));
                    sim.getBall().body.setLinearVelocity(9f, 0f);
                }
                sim.step(DT, l, r);
                if (sim.getPhase() == MatchSimulation.Phase.REPLAY) sawReplay = true;
                if (sim.getPhase() == MatchSimulation.Phase.HALFTIME) sawHalf = true;
                ticks++;
            }
            check("flujo completo: gol -> repetición -> descanso -> final", sawReplay && sawHalf && sim.getPhase() == MatchSimulation.Phase.FULL_TIME,
                    "replay=" + sawReplay + " descanso=" + sawHalf + " goles=" + sim.getScoreLeft() + "-" + sim.getScoreRight());
        } catch (RuntimeException e) {
            check("flujo completo sin excepciones", false, e.toString());
        }
        sim.dispose();
    }

    static void dribbleControl() {
        MatchSimulation sim = newPlaying(GameMode.TWO_PLAYERS);
        PlayerInput l = new PlayerInput(), r = new PlayerInput();
        sim.getPlayerRight().body.setTransform(-15f, 12f, 0f);
        sim.getKeeperRight().body.setTransform(21.4f, -10f, 0f);
        sim.getPlayerLeft().body.setTransform(-18f, 0f, 0f);
        sim.getPlayerLeft().body.setLinearVelocity(0f, 0f);
        sim.getBall().reset(new Vector2(-14f, 0f));
        l.moveX = 1f; l.sprint = true;
        int near = 0, n = 0;
        for (int i = 0; i < 90; i++) {
            sim.step(DT, l, r);
            if (sim.getBall().getPosition().x > 18f) break;
            n++;
            if (sim.getBall().getPosition().dst(sim.getPlayerLeft().getPosition()) <= Constants.KICK_RANGE + Constants.BALL_RADIUS) near++;
        }
        int pct = near * 100 / Math.max(1, n);
        check("conduciendo a sprint la pelota queda al alcance >75 % del tiempo (antes ~50 %)", pct > 75, pct + " %");
        sim.dispose();
    }

    static void keeperStillBounces() {
        MatchSimulation sim = newPlaying(GameMode.TWO_PLAYERS);
        PlayerInput l = new PlayerInput(), r = new PlayerInput();
        sim.getPlayerLeft().body.setTransform(-15f, 10f, 0f);
        sim.getPlayerRight().body.setTransform(-15f, -10f, 0f);
        sim.getKeeperRight().body.setTransform(21.4f, 0f, 0f);
        sim.getBall().reset(new Vector2(18f, 0f));
        sim.getBall().body.setLinearVelocity(12f, 0f);
        float minVx = 0f;
        for (int i = 0; i < 20; i++) { sim.step(DT, l, r); minVx = Math.min(minVx, sim.getBall().body.getLinearVelocity().x); }
        check("el arquero sigue rebotando la pelota (no se la queda muerta)", minVx < -3f, String.format("rebote %.1f m/s", minVx));
        sim.dispose();
    }

    static void coop2v2Setup() {
        MatchSimulation sim = new MatchSimulation(GameMode.TWO_VS_TWO);
        boolean four = sim.hasMates() && sim.getMateLeft() != null && sim.getMateRight() != null;
        check("2v2: hay cuatro jugadores de campo", four, "");
        Vector2[] ps = {sim.getPlayerLeft().getPosition(), sim.getMateLeft().getPosition(), sim.getPlayerRight().getPosition(), sim.getMateRight().getPosition()};
        float minSep = 99f;
        for (int i = 0; i < 4; i++) for (int j = i + 1; j < 4; j++) minSep = Math.min(minSep, ps[i].dst(ps[j]));
        check("2v2: los cuatro arrancan separados", minSep > 2f, String.format("sep mín %.1f m", minSep));
        check("2v2: los compañeros son humanos y los rivales IA",
                sim.getMateLeft().humanControlled && !sim.getPlayerRight().humanControlled && !sim.getMateRight().humanControlled, "");
        MatchSimulation one = new MatchSimulation(GameMode.TWO_PLAYERS);
        check("1v1: no se crean compañeros (modos anteriores intactos)", !one.hasMates() && one.getMateLeft() == null, "");
        one.dispose();
        sim.dispose();
    }

    static void coop2v2Controls() {
        MatchSimulation sim = newPlaying(GameMode.TWO_VS_TWO);
        PlayerInput l = new PlayerInput(), r = new PlayerInput();
        Vector2 p1 = new Vector2(sim.getPlayerLeft().getPosition());
        Vector2 mate = new Vector2(sim.getMateLeft().getPosition());
        sim.getBall().reset(new Vector2(0f, -12f));
        r.moveY = 1f;
        for (int i = 0; i < 20; i++) sim.step(DT, l, r);
        float dMate = sim.getMateLeft().getPosition().dst(mate);
        check("2v2: el segundo teclado mueve al compañero (J2)", dMate > 2f, String.format("%.1f m", dMate));
        float dP1 = sim.getPlayerLeft().getPosition().dst(p1);
        check("2v2: y el primer jugador (J1) no se mueve con él", dP1 < 0.6f, String.format("%.1f m", dP1));
        sim.dispose();

        MatchSimulation s2 = newPlaying(GameMode.TWO_VS_TWO);
        PlayerInput a = new PlayerInput(), b = new PlayerInput();
        s2.getPlayerLeft().body.setTransform(-18f, 12f, 0f);
        s2.getPlayerRight().body.setTransform(18f, 12f, 0f);
        s2.getMateRight().body.setTransform(18f, 11f, 0f);
        s2.getMateLeft().body.setTransform(-3f, 0f, 0f);
        s2.getMateLeft().body.setLinearVelocity(0f, 0f);
        s2.getBall().reset(new Vector2(-2.3f, 0f));
        for (int i = 0; i < 6; i++) { b.kickHeld = true; s2.step(DT, a, b); }
        b.kickHeld = false; b.kickReleased = true;
        s2.step(DT, a, b);
        float v = s2.getBall().body.getLinearVelocity().len();
        check("2v2: el compañero (J2) puede cargar y patear la pelota", v > 15f, String.format("%.1f m/s", v));
        s2.dispose();
    }

    static void coop2v2AiTeam() {
        MathUtils.random.setSeed(21);
        Settings.difficulty = Settings.Difficulty.NORMAL;
        Settings.halfMinutes = 1;
        double sumDist = 0; long n = 0; int goals = 0; float maxStill = 0f;
        for (int m = 0; m < 3; m++) {
            MatchSimulation sim = new MatchSimulation(GameMode.TWO_VS_TWO);
            SupportAI aiA = new SupportAI(false), aiB = new SupportAI(false);
            PlayerInput l = new PlayerInput(), r = new PlayerInput(), oa = new PlayerInput(), ob = new PlayerInput();
            Vector2 anchor = new Vector2();
            float still = 0f;
            int ticks = 0;
            while (sim.getPhase() != MatchSimulation.Phase.FULL_TIME && ticks < 30 * 60 * 10) {
                Vector2 bp = sim.getBall().getPosition();
                boolean aChase = sim.getPlayerLeft().getPosition().dst2(bp) <= sim.getMateLeft().getPosition().dst2(bp);
                com.rivalesfc.game.entities.PlayerEntity riv = sim.getPlayerRight().getPosition().dst2(bp) <= sim.getMateRight().getPosition().dst2(bp)
                        ? sim.getPlayerRight() : sim.getMateRight();
                aiA.update(oa, sim.getPlayerLeft(), sim.getBall(), riv, sim.getKeeperRight(), DT, !aChase, sim.getMateLeft().getPosition());
                aiB.update(ob, sim.getMateLeft(), sim.getBall(), riv, sim.getKeeperRight(), DT, aChase, sim.getPlayerLeft().getPosition());
                l.moveX = oa.moveX; l.moveY = oa.moveY; l.sprint = oa.sprint; l.kickHeld = oa.kickHeld;
                l.kickReleased |= oa.kickReleased; l.slidePressed |= oa.slidePressed;
                r.moveX = ob.moveX; r.moveY = ob.moveY; r.sprint = ob.sprint; r.kickHeld = ob.kickHeld;
                r.kickReleased |= ob.kickReleased; r.slidePressed |= ob.slidePressed;
                sim.step(DT, l, r);
                ticks++;
                if (sim.getPhase() == MatchSimulation.Phase.PLAYING) {
                    sumDist += sim.getPlayerRight().getPosition().dst(sim.getMateRight().getPosition()); n++;
                    if (bp.dst(anchor) > 1.5f) { anchor.set(bp); still = 0f; } else still += DT;
                    maxStill = Math.max(maxStill, still);
                } else { still = 0f; anchor.set(sim.getBall().getPosition()); }
            }
            goals += sim.getScoreLeft() + sim.getScoreRight();
            check("2v2: partido " + m + " con 4 jugadores termina", sim.getPhase() == MatchSimulation.Phase.FULL_TIME, "ticks=" + ticks);
            sim.dispose();
        }
        double avg = sumDist / Math.max(1, n);
        check("2v2: los dos jugadores IA del rojo no se amontonan (reparto de roles)", avg > 5.0, String.format("distancia media %.1f m", avg));
        check("2v2: se hacen goles", goals > 0, goals + " goles");
        check("2v2: la pelota no queda parada más de 8 s", maxStill < 8f, String.format("máx %.1f s", maxStill));
    }

    static void coop2v2ReplayAndReset() {
        Settings.halfMinutes = 1;
        MatchSimulation sim = new MatchSimulation(GameMode.TWO_VS_TWO);
        PlayerInput l = new PlayerInput(), r = new PlayerInput();
        boolean sawReplay = false;
        try {
            for (int i = 0; i < 30 * 40 && !sawReplay; i++) {
                if (sim.getPhase() == MatchSimulation.Phase.PLAYING && i % 150 == 0) {
                    sim.getKeeperRight().body.setTransform(21.4f, -2.6f, 0f);
                    sim.getBall().reset(new Vector2(19f, 2.6f));
                    sim.getBall().body.setLinearVelocity(9f, 0f);
                }
                sim.step(DT, l, r);
                if (sim.getPhase() == MatchSimulation.Phase.REPLAY) sawReplay = true;
            }
            check("2v2: gol -> repetición con los cuatro jugadores sin excepciones", sawReplay, "");
            for (int i = 0; i < 30 * 30 && sim.getPhase() != MatchSimulation.Phase.PLAYING; i++) sim.step(DT, l, r);
            Vector2 m = sim.getMateLeft().getPosition();
            check("2v2: tras el gol todos vuelven a sus posiciones de saque",
                    Math.abs(m.x + Constants.KICKOFF_2V2_BACK_X) < 0.6f && Math.abs(sim.getMateRight().getPosition().x - Constants.KICKOFF_2V2_BACK_X) < 0.6f,
                    String.format("compañero en (%.1f, %.1f)", m.x, m.y));
        } catch (RuntimeException e) {
            check("2v2: repetición sin excepciones", false, e.toString());
        }
        sim.dispose();
    }

    static void bigFrameDelta() {
        MatchSimulation sim = new MatchSimulation(GameMode.TWO_PLAYERS);
        PlayerInput l = new PlayerInput(), r = new PlayerInput();
        long t0 = sim.getTick();
        sim.step(5f, l, r);
        long ticks = sim.getTick() - t0;
        check("un frame colgado de 5 s simula como máximo ~0,1 s", ticks <= 4, ticks + " ticks");
        sim.dispose();
    }

    static void fourPlayersSetup() {
        MatchSimulation sim = new MatchSimulation(GameMode.FOUR_PLAYERS);
        check("4J: hay cuatro jugadores de campo", sim.hasMates() && sim.getMateLeft() != null && sim.getMateRight() != null, "");
        check("4J: por defecto los dos rojos son IA (sin mandos)", !sim.isRightHuman(0) && !sim.isRightHuman(1), "");
        PlayerInput i3 = new PlayerInput(), i4 = new PlayerInput();
        sim.setRightHumans(true, i3, false, i4);
        check("4J: J3 humano, J4 sigue IA", sim.isRightHuman(0) && !sim.isRightHuman(1) && sim.getPlayerRight().humanControlled, "");
        sim.setRightHumans(true, i3, true, i4);
        check("4J: J3 y J4 humanos", sim.isRightHuman(0) && sim.isRightHuman(1) && sim.getMateRight().humanControlled, "");
        sim.dispose();
        check("4J: GameMode.isTeamMode cubre 2v2 y 4J, no los demás",
                GameMode.TWO_VS_TWO.isTeamMode() && GameMode.FOUR_PLAYERS.isTeamMode()
                        && !GameMode.TWO_PLAYERS.isTeamMode() && !GameMode.ONLINE_HOST.isTeamMode(), "");
    }

    static void fourPlayersControls() {
        MatchSimulation sim = newPlaying(GameMode.FOUR_PLAYERS);
        PlayerInput l = new PlayerInput(), r = new PlayerInput(), i3 = new PlayerInput(), i4 = new PlayerInput();
        sim.setRightHumans(true, i3, true, i4);
        sim.getBall().reset(new Vector2(0f, 12f));
        Vector2 p3 = new Vector2(sim.getPlayerRight().getPosition());
        Vector2 p4 = new Vector2(sim.getMateRight().getPosition());
        Vector2 p1 = new Vector2(sim.getPlayerLeft().getPosition());
        Vector2 p2 = new Vector2(sim.getMateLeft().getPosition());
        i3.moveY = 1f;
        i4.moveX = -1f;
        for (int i = 0; i < 20; i++) sim.step(DT, l, r);
        float d3 = sim.getPlayerRight().getPosition().dst(p3);
        float d4 = sim.getMateRight().getPosition().dst(p4);
        check("4J: el input 3 mueve a J3 (rojo)", d3 > 1.5f, String.format("%.1f m", d3));
        check("4J: el input 4 mueve a J4 (rojo)", d4 > 1.5f, String.format("%.1f m", d4));
        check("4J: J1 y J2 no se mueven con los inputs rojos",
                sim.getPlayerLeft().getPosition().dst(p1) < 0.6f && sim.getMateLeft().getPosition().dst(p2) < 0.6f, "");
        sim.dispose();

        MatchSimulation s2 = newPlaying(GameMode.FOUR_PLAYERS);
        PlayerInput a = new PlayerInput(), b = new PlayerInput(), c3 = new PlayerInput(), c4 = new PlayerInput();
        s2.setRightHumans(true, c3, true, c4);
        s2.getPlayerLeft().body.setTransform(-18f, 12f, 0f);
        s2.getMateLeft().body.setTransform(-18f, 11f, 0f);
        s2.getPlayerRight().body.setTransform(18f, 12f, 0f);
        s2.getMateRight().body.setTransform(3f, 0f, 0f);
        s2.getMateRight().body.setLinearVelocity(0f, 0f);
        s2.getBall().reset(new Vector2(2.3f, 0f));
        for (int i = 0; i < 6; i++) { c4.kickHeld = true; c4.moveX = -1f; s2.step(DT, a, b); }
        c4.kickHeld = false; c4.kickReleased = true;
        s2.step(DT, a, b);
        float v = s2.getBall().body.getLinearVelocity().len();
        check("4J: J4 puede cargar y patear la pelota", v > 15f, String.format("%.1f m/s", v));
        s2.dispose();
    }

    static void fourPlayersMixedAi() {

        MatchSimulation sim = newPlaying(GameMode.FOUR_PLAYERS);
        PlayerInput l = new PlayerInput(), r = new PlayerInput(), i3 = new PlayerInput(), i4 = new PlayerInput();
        sim.setRightHumans(true, i3, false, i4);
        sim.getPlayerLeft().body.setTransform(-20f, 12f, 0f);
        sim.getMateLeft().body.setTransform(-20f, 10f, 0f);
        sim.getPlayerRight().body.setTransform(20f, -12f, 0f);
        sim.getMateRight().body.setTransform(12f, 6f, 0f);
        sim.getBall().reset(new Vector2(4f, 6f));
        float d0 = sim.getMateRight().getPosition().dst(sim.getBall().getPosition());
        for (int i = 0; i < 60; i++) sim.step(DT, l, r);
        float d1 = sim.getMateRight().getPosition().dst(sim.getBall().getPosition());
        check("4J: con J3 humano, la IA de J4 persigue la pelota", d1 < d0 - 2f, String.format("%.1f -> %.1f m", d0, d1));
        sim.dispose();
    }

    static void fourPlayersFullMatch() {

        MatchSimulation sim = new MatchSimulation(GameMode.FOUR_PLAYERS);
        PlayerInput l = new PlayerInput(), r = new PlayerInput(), i3 = new PlayerInput(), i4 = new PlayerInput();
        sim.setRightHumans(true, i3, false, i4);
        int ticks = 0;
        try {
            while (sim.getPhase() != MatchSimulation.Phase.FULL_TIME && ticks < 30 * 60 * 40) {
                sim.step(DT, l, r);
                ticks++;
            }
            check("4J: el partido completo termina", sim.getPhase() == MatchSimulation.Phase.FULL_TIME, "ticks=" + ticks);
        } catch (RuntimeException e) {
            check("4J: el partido completo termina", false, e.toString());
        }
        sim.dispose();
    }

    public static void main(String[] args) {
        String lib = System.getProperty("box2d.lib");
        if (lib != null) System.load(lib); else Box2D.init();
        sprintSpeed();
        cornerNotTrap();
        unstuckWatchdog();
        aiVsAiNoStall();
        renderInterpolation();
        matchFlow();
        bigFrameDelta();
        dribbleControl();
        keeperStillBounces();
        coop2v2Setup();
        coop2v2Controls();
        coop2v2AiTeam();
        coop2v2ReplayAndReset();
        fourPlayersSetup();
        fourPlayersControls();
        fourPlayersMixedAi();
        fourPlayersFullMatch();
        System.out.println(fails == 0 ? "\nTODO OK" : "\nFALLARON " + fails);
        if (fails != 0) System.exit(1);
    }
}
