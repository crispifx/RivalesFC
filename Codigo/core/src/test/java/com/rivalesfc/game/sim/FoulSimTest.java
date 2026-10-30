package com.rivalesfc.game.sim;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.rivalesfc.game.Constants;
import com.rivalesfc.game.GameMode;
import com.rivalesfc.game.Settings;
import com.rivalesfc.game.ai.SupportAI;
import com.rivalesfc.game.entities.PlayerEntity;
import com.rivalesfc.game.input.PlayerInput;
import com.rivalesfc.game.sim.MatchSimulation.Phase;

/**
 * MK8: pruebas del sistema de faltas CON la física Box2D real y sin ventana (lo que FoulSelfTest no cubre:
 * detección por contacto en {@link MatchSimulation}, expulsión con body.setActive, acomodo del tiro,
 * ventaja, barrera y partidos completos de IA contra IA).
 * Correr con main().
 */
public class FoulSimTest {

    static int fails = 0;
    static final float DT = 1f / 30f;
    static final float HW = Constants.FIELD_WIDTH / 2f;

    static void check(String name, boolean ok, String detail) {
        System.out.println((ok ? "OK    " : "FALLA ") + name + (detail.isEmpty() ? "" : "  [" + detail + "]"));
        if (!ok) fails++;
    }

    static final class Rig {
        final MatchSimulation sim;
        final PlayerInput l = new PlayerInput(), r = new PlayerInput();

        Rig(GameMode m) {
            sim = new MatchSimulation(m);
            for (int i = 0; i < 30 * 4; i++) sim.step(DT, l, r); // sale del saque
        }

        void tick() {
            sim.step(DT, l, r);
        }

        /** Avanza hasta que se cumpla la condición o pasen {@code maxTicks}. Devuelve los ticks usados (-1 si no se cumplió). */
        int until(java.util.function.BooleanSupplier c, int maxTicks) {
            for (int i = 0; i < maxTicks; i++) {
                if (c.getAsBoolean()) return i;
                tick();
            }
            return c.getAsBoolean() ? maxTicks : -1;
        }

        void place(PlayerEntity p, float x, float y) {
            p.body.setTransform(x, y, 0f);
            p.body.setLinearVelocity(0f, 0f);
            p.resetState();
        }

        /** Todos lejos de la pelota y entre sí; pelota quieta en un rincón. */
        void calm() {
            place(sim.getPlayerLeft(), -15f, 10f);
            place(sim.getPlayerRight(), 15f, -10f);
            sim.getBall().reset(new Vector2(0f, 12f));
            sim.getBall().body.setLinearVelocity(0f, 0f);
            l.moveX = 0; l.moveY = 0; l.sprint = false; l.kickHeld = false;
            r.moveX = 0; r.moveY = 0; r.sprint = false; r.kickHeld = false;
        }

        /** Sale de cualquier pausa de falta y vuelve a PLAYING con el tiro ya ejecutado/protección vencida. */
        void backToPlay() {
            until(() -> sim.getPhase() == Phase.PLAYING, 30 * 20);
            for (int i = 0; i < 30 * 4; i++) tick();
        }
    }

    // ------------------------------------------------------------------ escenarios dirigidos

    static void slideFoul() {
        Settings.foulsEnabled = true;
        Settings.halfMinutes = 5;
        Rig g = new Rig(GameMode.TWO_PLAYERS);
        g.calm();
        g.place(g.sim.getPlayerLeft(), -6f, -5f);
        g.place(g.sim.getPlayerRight(), -3.3f, -5f); // alcance real de la barrida: ~2,3 m + contacto
        g.l.slidePressed = true;
        int t = g.until(() -> g.sim.getPhase() == Phase.FOUL, 30);
        check("barrida a un rival sin tocar la pelota: se cobra falta (con física real)", t >= 0, "ticks=" + t);
        check("la falta queda a nombre del que barrió y el que patea es la víctima",
                g.sim.getLastFoulerIdx() == 0 && g.sim.getSetPieceTakerIdx() == 1,
                "fouler=" + g.sim.getLastFoulerIdx() + " taker=" + g.sim.getSetPieceTakerIdx());
        check("estadística de faltas del azul = 1", g.sim.getStats().fouls[0] == 1, "");
        check("es tiro libre (fuera del área) y sin tarjeta", !g.sim.isLastFoulPenalty() && g.sim.getLastFoulCard() == FoulRules.Card.NONE, "");

        g.until(() -> g.sim.getPhase() == Phase.SET_PIECE, 30 * 5);
        Vector2 b = g.sim.getBall().getPosition();
        check("en el tiro libre la pelota está donde fue la falta", b.dst(g.sim.getLastFoulSpot()) < 1.0f, String.format("%.2f m", b.dst(g.sim.getLastFoulSpot())));
        float dTaker = g.sim.getPlayerRight().getPosition().dst(b);
        float dWall = g.sim.getPlayerLeft().getPosition().dst(b);
        check("el que patea queda pegado a la pelota", dTaker < 2f, String.format("%.2f m", dTaker));
        check("el infractor queda a ~5,5 m (barrera)", Math.abs(dWall - Constants.FREE_KICK_WALL_DIST) < 0.8f, String.format("%.2f m", dWall));
        g.until(() -> g.sim.getPhase() == Phase.PLAYING, 30 * 5);
        check("tras la cuenta se vuelve a jugar", g.sim.getPhase() == Phase.PLAYING, g.sim.getPhase().toString());
        g.sim.dispose();
    }

    static void chargeFoul() {
        Settings.foulsEnabled = true;
        Rig g = new Rig(GameMode.TWO_PLAYERS);
        g.calm();
        g.place(g.sim.getPlayerLeft(), -18f, -5f);
        g.place(g.sim.getPlayerRight(), 0f, -5f);
        g.l.moveX = 1f;
        g.l.sprint = true;
        int t = g.until(() -> g.sim.getPhase() == Phase.FOUL, 30 * 4);
        check("chocar en sprint a un rival quieto, sin jugar la pelota, es falta de carga", t >= 0 && g.sim.getLastFoulType() == FoulRules.Type.CHARGE,
                "ticks=" + t + " tipo=" + g.sim.getLastFoulType());
        g.sim.dispose();
    }

    static void walkingIntoIsNotFoul() {
        Settings.foulsEnabled = true;
        Rig g = new Rig(GameMode.TWO_PLAYERS);
        g.calm();
        g.place(g.sim.getPlayerLeft(), -6f, -5f);
        g.place(g.sim.getPlayerRight(), 0f, -5f);
        g.l.moveX = 1f; // camina (sin sprint): ~7,5 m/s < 8,5
        for (int i = 0; i < 30 * 3; i++) g.tick();
        check("caminar contra un rival no es falta", g.sim.getStats().fouls[0] == 0 && g.sim.getPhase() == Phase.PLAYING,
                "faltas=" + g.sim.getStats().fouls[0]);
        g.sim.dispose();
    }

    static void cleanSlide() {
        Settings.foulsEnabled = true;
        Rig g = new Rig(GameMode.TWO_PLAYERS);
        g.calm();
        g.place(g.sim.getPlayerLeft(), -6f, -5f);
        g.place(g.sim.getPlayerRight(), -3.3f, -5f);
        g.sim.getBall().reset(new Vector2(-4.6f, -5f));
        g.l.slidePressed = true;
        for (int i = 0; i < 30; i++) g.tick();
        check("planchazo que toca primero la pelota es limpio (no hay falta)", g.sim.getStats().fouls[0] == 0 && g.sim.getPhase() == Phase.PLAYING,
                "faltas=" + g.sim.getStats().fouls[0] + " fase=" + g.sim.getPhase());
        g.sim.dispose();
    }

    static void foulsOff() {
        Settings.foulsEnabled = false;
        Rig g = new Rig(GameMode.TWO_PLAYERS);
        g.calm();
        g.place(g.sim.getPlayerLeft(), -6f, -5f);
        g.place(g.sim.getPlayerRight(), -3.3f, -5f);
        g.l.slidePressed = true;
        for (int i = 0; i < 30; i++) g.tick();
        check("con faltas apagadas no se cobra nada", g.sim.getStats().fouls[0] == 0 && g.sim.getPhase() == Phase.PLAYING, "");
        g.sim.dispose();
        Settings.foulsEnabled = true;
    }

    static void penalty() {
        Settings.foulsEnabled = true;
        Rig g = new Rig(GameMode.TWO_PLAYERS);
        g.calm();
        // El azul (izquierda) defiende el arco izquierdo: su área va de x=-22 a x=-17.
        g.place(g.sim.getPlayerLeft(), -HW + 4.7f, 0f);
        g.place(g.sim.getPlayerRight(), -HW + 1.3f, 0f);
        g.l.moveX = -1f;
        g.tick();
        g.tick();
        g.l.moveX = 0f;
        g.l.slidePressed = true;
        int t = g.until(() -> g.sim.getPhase() == Phase.FOUL, 30);
        check("barrida dentro del área propia: se cobra falta", t >= 0, "ticks=" + t);
        check("la falta dentro del área es penal", g.sim.isLastFoulPenalty(), "");
        g.until(() -> g.sim.getPhase() == Phase.SET_PIECE, 30 * 5);
        Vector2 b = g.sim.getBall().getPosition();
        check("el penal se cobra en el punto de penal del arco izquierdo", Math.abs(b.x - (-(HW - Constants.PENALTY_SPOT_DIST))) < 0.3f && Math.abs(b.y) < 0.3f,
                String.format("(%.2f, %.2f)", b.x, b.y));
        g.until(() -> g.sim.getPhase() == Phase.PLAYING, 30 * 5);
        check("en el penal el rival del que patea queda quieto un rato", g.sim.getPlayerLeft().body.getLinearVelocity().len() < 0.2f, "");
        g.sim.dispose();
    }

    /** Seis faltas leves del mismo jugador: amarilla a la 3ª, y a la 6ª segunda amarilla = roja. */
    static void redCardFlow() {
        Settings.foulsEnabled = true;
        Rig g = new Rig(GameMode.TWO_PLAYERS);
        int yellowAfter3 = -1;
        for (int k = 1; k <= 6; k++) {
            g.calm();
            g.place(g.sim.getPlayerLeft(), -6f, -5f);
            g.place(g.sim.getPlayerRight(), -3.3f, -5f);
            g.l.slidePressed = true;
            int t = g.until(() -> g.sim.getPhase() == Phase.FOUL, 30);
            if (t < 0) {
                check("falta " + k + " se cobra", false, "no hubo falta");
                break;
            }
            if (k == 3) yellowAfter3 = g.sim.getStats().yellows[0];
            if (k == 6) {
                break; // miramos el estado en plena pausa de la roja
            }
            g.backToPlay();
            // el enfriamiento de la falta y la recarga del planchazo
            for (int i = 0; i < 30 * 2; i++) g.tick();
        }
        check("3ª falta del mismo jugador: amarilla", yellowAfter3 == 1, "amarillas=" + yellowAfter3);
        check("6ª falta: segunda amarilla = roja", g.sim.getLastFoulCard() == FoulRules.Card.RED && g.sim.getStats().reds[0] == 1,
                "tarjeta=" + g.sim.getLastFoulCard() + " rojas=" + g.sim.getStats().reds[0]);
        PlayerEntity red = g.sim.getPlayerLeft();
        check("el expulsado queda suspendido, con el cuerpo desactivado", red.isSuspended() && !red.body.isActive(), "");
        float rem = g.sim.getSuspendRemaining(0);
        check("la expulsión dura ~25 s", rem > 20f && rem <= Constants.RED_SUSPENSION_SECONDS, String.format("%.1f s", rem));

        g.backToPlay();
        // Mientras está afuera el rival juega con tranquilidad y el partido sigue.
        g.calm();
        int ticks = 0;
        while (g.sim.isSuspended(0) && ticks < 30 * 40) {
            g.tick();
            ticks++;
        }
        check("el expulsado vuelve a cancha solo después de cumplir el tiempo", !g.sim.isSuspended(0) && ticks > 30 * 10 && ticks < 30 * 30,
                "volvió a los " + (ticks / 30f) + " s de juego");
        check("al volver: cuerpo activo y por su propio campo", red.body.isActive() && red.getPosition().x < 0f,
                String.format("x=%.1f", red.getPosition().x));
        g.sim.dispose();
    }

    // ------------------------------------------------------------------ ventaja

    /** Barrida a un rival que tiene la pelota al alcance, sin tocarla: falta con la pelota quedándole al perjudicado. */
    static Rig advantageRig() {
        Settings.foulsEnabled = true;
        Settings.advantageEnabled = true;
        Settings.halfMinutes = 5;
        Rig g = new Rig(GameMode.TWO_PLAYERS);
        g.calm();
        g.place(g.sim.getPlayerLeft(), -6f, -5f);
        g.place(g.sim.getPlayerRight(), -3.3f, -5f);
        g.sim.getBall().reset(new Vector2(-2.4f, -5f)); // detrás del rival, del lado contrario al que barre
        g.l.slidePressed = true;
        return g;
    }

    static void advantagePlayed() {
        Rig g = advantageRig();
        int t = g.until(() -> g.sim.isAdvantageActive() || g.sim.getPhase() == Phase.FOUL, 30);
        check("ventaja: la falta con pelota del perjudicado abre ventaja (no corta el juego)", t >= 0 && g.sim.isAdvantageActive() && g.sim.getPhase() == Phase.PLAYING,
                "ticks=" + t + " fase=" + g.sim.getPhase());
        check("ventaja: la falta queda anotada igual", g.sim.getStats().fouls[0] == 1 && g.sim.getAdvantageTeam() == 1, "faltas=" + g.sim.getStats().fouls[0]);
        int events = g.sim.getFoulEventCount();
        g.l.slidePressed = false;
        g.r.moveX = 1f; // el perjudicado se lleva la pelota
        for (int i = 0; i < 30 * 4; i++) g.tick();
        check("ventaja aprovechada: sin tiro libre, el juego siguió", !g.sim.isAdvantageActive() && g.sim.getPhase() == Phase.PLAYING
                && g.sim.getFoulEventCount() == events && g.sim.getAdvantagesPlayed() == 1,
                "jugadas=" + g.sim.getAdvantagesPlayed() + " devueltas=" + g.sim.getAdvantagesCalledBack() + " fase=" + g.sim.getPhase());
        g.sim.dispose();
    }

    static void advantageLostBall() {
        Rig g = advantageRig();
        g.until(() -> g.sim.isAdvantageActive(), 30);
        // el infractor se queda con la pelota
        Vector2 f = g.sim.getPlayerLeft().getPosition();
        g.sim.getBall().reset(new Vector2(f.x - 0.9f, f.y)); // del lado opuesto al perjudicado, no en disputa
        g.sim.getBall().body.setLinearVelocity(0f, 0f);
        int t = g.until(() -> g.sim.getPhase() == Phase.FOUL, 30);
        check("ventaja perdida: se vuelve atrás y se cobra la falta enseguida", t >= 0 && t < 15 && g.sim.getAdvantagesCalledBack() == 1, "ticks=" + t);
        check("al volver atrás patea el perjudicado", g.sim.getSetPieceTakerIdx() == 1, "taker=" + g.sim.getSetPieceTakerIdx());
        g.until(() -> g.sim.getPhase() == Phase.SET_PIECE, 30 * 5);
        float d = g.sim.getBall().getPosition().dst(g.sim.getLastFoulSpot());
        check("el tiro libre se cobra donde fue la falta", d < 1.0f, String.format("%.2f m", d));
        g.sim.dispose();
    }

    static void advantageLooseBall() {
        Rig g = advantageRig();
        g.until(() -> g.sim.isAdvantageActive(), 30);
        g.sim.getBall().reset(new Vector2(0f, 12f)); // pelota suelta lejos de todos
        g.sim.getBall().body.setLinearVelocity(0f, 0f);
        int t = g.until(() -> g.sim.getPhase() == Phase.FOUL, 30 * 4);
        check("ventaja no aprovechada (pelota suelta): al vencer la ventana se cobra la falta", t >= 60 && t <= 90, "ticks=" + t);
        g.sim.dispose();
    }

    static void advantageOff() {
        Rig g = advantageRig();
        Settings.advantageEnabled = false;
        int t = g.until(() -> g.sim.getPhase() == Phase.FOUL, 30);
        check("ventaja apagada: se corta el juego enseguida", t >= 0 && !g.sim.isAdvantageActive(), "ticks=" + t);
        g.sim.dispose();
        Settings.advantageEnabled = true;
    }

    // ------------------------------------------------------------------ roja definitiva en 2v2

    static void permanentRed2v2() {
        Settings.foulsEnabled = true;
        Settings.halfMinutes = 5;
        Settings.red2v2Permanent = true;
        MatchSimulation sim = new MatchSimulation(GameMode.TWO_VS_TWO);
        PlayerInput l = new PlayerInput(), r = new PlayerInput();
        for (int i = 0; i < 30 * 4; i++) sim.step(DT, l, r);
        check("2v2: el modo roja definitiva está activo", sim.isRedPermanentMode(), "");
        // roja directa (gravedad máxima) del azul 0 sobre el rojo 1
        FoulRules.Call c = new FoulRules.Call();
        c.type = FoulRules.Type.SLIDE; c.severity = 4;
        invokeRegister(sim, 0, 1, c);
        check("2v2: la roja expulsa hasta el final", sim.getPlayerLeft().isSuspended() && sim.isPermanentlySuspended(0), "rem=" + sim.getSuspendRemaining(0));
        for (int i = 0; i < 30 * 40; i++) sim.step(DT, l, r);
        check("2v2: pasados 40 s el expulsado sigue afuera", sim.getPlayerLeft().isSuspended() && !sim.getPlayerLeft().body.isActive(), "");
        check("2v2: el compañero sigue jugando", !sim.getMateLeft().isSuspended(), "");
        // segunda roja en el mismo equipo: el compañero NO queda afuera para siempre (nunca sin jugadores de campo)
        invokeRegister(sim, 2, 1, c);
        check("2v2: el último jugador de campo recibe una roja temporal (no se queda sin nadie)",
                sim.getMateLeft().isSuspended() && !sim.isPermanentlySuspended(2), "rem=" + sim.getSuspendRemaining(2));
        sim.dispose();

        Settings.red2v2Permanent = false;
        MatchSimulation sim2 = new MatchSimulation(GameMode.TWO_VS_TWO);
        for (int i = 0; i < 30 * 4; i++) sim2.step(DT, l, r);
        invokeRegister(sim2, 0, 1, c);
        check("2v2 con la opción apagada: la roja sigue siendo de 25 s", sim2.getPlayerLeft().isSuspended() && !sim2.isPermanentlySuspended(0)
                && sim2.getSuspendRemaining(0) <= Constants.RED_SUSPENSION_SECONDS, "");
        sim2.dispose();
    }

    static void invokeRegister(MatchSimulation sim, int att, int vict, FoulRules.Call c) {
        try {
            java.lang.reflect.Method m = MatchSimulation.class.getDeclaredMethod("registerFoul", int.class, int.class, FoulRules.Call.class);
            m.setAccessible(true);
            m.invoke(sim, att, vict, c);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ------------------------------------------------------------------ partidos completos de IA

    static final class MatchResult {
        int fouls, yellows, reds, setPieces, penalties;
        float maxDeadBall; // segundos máximos seguidos en FOUL/SET_PIECE
        boolean finished, badBody, ballOut;
        int scoreL, scoreR;
    }

    static MatchResult aiMatch1v1(Settings.Difficulty d, long seed) {
        MathUtils.random.setSeed(seed);
        Settings.difficulty = d;
        Settings.halfMinutes = 1;
        Settings.foulsEnabled = true;
        MatchResult res = new MatchResult();
        MatchSimulation sim = new MatchSimulation(GameMode.ONE_PLAYER);
        SupportAI ai = new SupportAI(false);
        PlayerInput l = new PlayerInput(), r = new PlayerInput(), o = new PlayerInput();
        int ticks = 0;
        int lastFoulCount = 0;
        float dead = 0f;
        while (sim.getPhase() != Phase.FULL_TIME && ticks < 30 * 60 * 10) {
            ai.update(o, sim.getPlayerLeft(), sim.getBall(), sim.getPlayerRight(), sim.getKeeperRight(), DT);
            l.moveX = o.moveX; l.moveY = o.moveY; l.sprint = o.sprint; l.kickHeld = o.kickHeld;
            l.kickReleased |= o.kickReleased; l.slidePressed |= o.slidePressed; o.clearTransient();
            sim.step(DT, l, r);
            ticks++;
            if (sim.isDeadBallFoulPhase()) {
                dead += DT;
                res.maxDeadBall = Math.max(res.maxDeadBall, dead);
            } else {
                dead = 0f;
            }
            if (sim.getFoulEventCount() != lastFoulCount) {
                lastFoulCount = sim.getFoulEventCount();
                res.setPieces++;
                if (sim.isLastFoulPenalty()) res.penalties++;
            }
            for (int i = 0; i < 2; i++) {
                PlayerEntity p = i == 0 ? sim.getPlayerLeft() : sim.getPlayerRight();
                if (p.isSuspended() == p.body.isActive()) res.badBody = true;
            }
            Vector2 bp = sim.getBall().getPosition();
            if (Math.abs(bp.x) > HW + 3f || Math.abs(bp.y) > Constants.FIELD_HEIGHT / 2f + 3f) res.ballOut = true;
        }
        res.finished = sim.getPhase() == Phase.FULL_TIME;
        res.fouls = sim.getStats().fouls[0] + sim.getStats().fouls[1];
        res.yellows = sim.getStats().yellows[0] + sim.getStats().yellows[1];
        res.reds = sim.getStats().reds[0] + sim.getStats().reds[1];
        res.scoreL = sim.getScoreLeft();
        res.scoreR = sim.getScoreRight();
        sim.dispose();
        return res;
    }

    static void aiMatches() {
        Settings.Difficulty[] ds = Settings.Difficulty.values();
        int totalFouls = 0, matches = 0, totalReds = 0;
        float maxDead = 0f;
        boolean allFinished = true, anyBadBody = false, anyBallOut = false;
        for (Settings.Difficulty d : ds) {
            for (long seed = 1; seed <= 3; seed++) {
                MatchResult m = aiMatch1v1(d, seed * 7 + d.ordinal());
                System.out.printf("      IA vs IA %-8s seed %d: %2d faltas (%d amarillas, %d rojas, %d penales) goles %d-%d pausa mx %.1f s%n",
                        d.label, seed, m.fouls, m.yellows, m.reds, m.penalties, m.scoreL, m.scoreR, m.maxDeadBall);
                totalFouls += m.fouls;
                totalReds += m.reds;
                matches++;
                maxDead = Math.max(maxDead, m.maxDeadBall);
                allFinished &= m.finished;
                anyBadBody |= m.badBody;
                anyBallOut |= m.ballOut;
            }
        }
        float perMatch = totalFouls / (float) matches;
        check("IA vs IA con faltas: los 9 partidos terminan", allFinished, "");
        check("IA vs IA: el cuerpo está activo si y solo si no está expulsado", !anyBadBody, "");
        check("IA vs IA: la pelota no sale nunca de la cancha tras una falta", !anyBallOut, "");
        check("IA vs IA: ninguna pausa de falta se queda colgada", maxDead < 14f, String.format("máx %.1f s", maxDead));
        check("IA vs IA: cantidad razonable de faltas (no un festival)", perMatch <= 5f, String.format("%.1f por partido de 2 min", perMatch));
        System.out.printf("      promedio: %.1f faltas y %.2f rojas por partido de 2 min%n", perMatch, totalReds / (float) matches);
    }

    static void aiMatch2v2() {
        MathUtils.random.setSeed(5);
        Settings.difficulty = Settings.Difficulty.NORMAL;
        Settings.halfMinutes = 1;
        Settings.foulsEnabled = true;
        int fouls = 0;
        boolean ok = true, bad = false;
        for (int m = 0; m < 3; m++) {
            MatchSimulation sim = new MatchSimulation(GameMode.TWO_VS_TWO);
            SupportAI aiA = new SupportAI(false), aiB = new SupportAI(false);
            PlayerInput l = new PlayerInput(), r = new PlayerInput(), oa = new PlayerInput(), ob = new PlayerInput();
            int ticks = 0;
            while (sim.getPhase() != Phase.FULL_TIME && ticks < 30 * 60 * 10) {
                Vector2 bp = sim.getBall().getPosition();
                boolean aChase = sim.getPlayerLeft().getPosition().dst2(bp) <= sim.getMateLeft().getPosition().dst2(bp);
                PlayerEntity riv = sim.getPlayerRight().getPosition().dst2(bp) <= sim.getMateRight().getPosition().dst2(bp) ? sim.getPlayerRight() : sim.getMateRight();
                aiA.update(oa, sim.getPlayerLeft(), sim.getBall(), riv, sim.getKeeperRight(), DT, !aChase, sim.getMateLeft().getPosition());
                aiB.update(ob, sim.getMateLeft(), sim.getBall(), riv, sim.getKeeperRight(), DT, aChase, sim.getPlayerLeft().getPosition());
                l.moveX = oa.moveX; l.moveY = oa.moveY; l.sprint = oa.sprint; l.kickHeld = oa.kickHeld;
                l.kickReleased |= oa.kickReleased; l.slidePressed |= oa.slidePressed;
                r.moveX = ob.moveX; r.moveY = ob.moveY; r.sprint = ob.sprint; r.kickHeld = ob.kickHeld;
                r.kickReleased |= ob.kickReleased; r.slidePressed |= ob.slidePressed;
                sim.step(DT, l, r);
                ticks++;
                for (int i = 0; i < 4; i++) {
                    PlayerEntity p = i == 0 ? sim.getPlayerLeft() : i == 1 ? sim.getPlayerRight() : i == 2 ? sim.getMateLeft() : sim.getMateRight();
                    if (p.isSuspended() == p.body.isActive()) bad = true;
                }
            }
            ok &= sim.getPhase() == Phase.FULL_TIME;
            fouls += sim.getStats().fouls[0] + sim.getStats().fouls[1];
            System.out.printf("      2v2 partido %d: %d faltas, %d rojas, goles %d-%d%n", m, sim.getStats().fouls[0] + sim.getStats().fouls[1],
                    sim.getStats().reds[0] + sim.getStats().reds[1], sim.getScoreLeft(), sim.getScoreRight());
            sim.dispose();
        }
        check("2v2 con faltas: los 3 partidos terminan", ok, "");
        check("2v2 con faltas: cuerpos activos solo si no están expulsados", !bad, "");
    }

    public static void main(String[] args) {
        slideFoul();
        chargeFoul();
        walkingIntoIsNotFoul();
        cleanSlide();
        foulsOff();
        penalty();
        redCardFlow();
        advantagePlayed();
        advantageLostBall();
        advantageLooseBall();
        advantageOff();
        permanentRed2v2();
        aiMatches();
        aiMatch2v2();
        System.out.println(fails == 0 ? "\nTODO OK" : "\nFALLARON " + fails);
        if (fails != 0) System.exit(1);
    }
}
