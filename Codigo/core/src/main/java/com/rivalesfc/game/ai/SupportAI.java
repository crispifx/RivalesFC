package com.rivalesfc.game.ai;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.rivalesfc.game.Constants;
import com.rivalesfc.game.Settings;
import com.rivalesfc.game.entities.Ball;
import com.rivalesfc.game.entities.GoalkeeperEntity;
import com.rivalesfc.game.entities.PlayerEntity;
import com.rivalesfc.game.input.PlayerInput;

/**
 * IA del jugador de campo (modo 1 jugador, rivales del 2v2). Llena un {@link PlayerInput} igual que un teclado, así que
 * movimiento, pateo y planchazo siguen teniendo una única implementación.
 *
 * MK9 — rehecha para que juegue y no solo "vaya y patee":
 *  - PERCEPCIÓN: ve la pelota y al rival con un pequeño retraso según la dificultad (fácil ~0,27 s, normal ~0,1 s).
 *  - PREDICCIÓN: calcula dónde va a estar la pelota (con el frenado real de la física) y va al punto de intercepción,
 *    en vez de correr detrás de donde estuvo.
 *  - CONDUCCIÓN: con la pelota al pie la empuja corriendo hacia el arco, esquivando al rival que se le cruza. Antes la
 *    "tocaba suave" a ~27 m/s, la pelota se iba lejos y había que ir a buscarla (36 % del tiempo suelta).
 *  - TIRO: elige el rincón según dónde está el arquero, si el rival tapa la línea y cuánto tiene que reacomodarse;
 *    remata desde más lejos y sin esperar a alinearse perfecto (el pateo se corrige con el movimiento).
 *  - PASE/DESPEJE: la potencia se calcula para que la pelota llegue con velocidad útil (no a la pared de atrás).
 *    En el 2v2 le pasa al compañero si está mejor ubicado.
 *  - DEFENSA: se coloca del lado del arco propio y tapa la línea de pase/remate; solo se tira cuando llega primero.
 *
 * Aclaración: sigue sin ver el marcador ni el reloj (no "se juega" el partido), y no conoce el tiempo de la ventaja.
 */
public class SupportAI {

    private enum Plan { DRIBBLE, SHOOT, PASS, CLEAR }

    // ----------------------------------------------------------- ajustes rápidos (MK9)
    /** Velocidad media con la que estima cuánto tarda en llegar a un punto (m/s). */
    private static final float EST_SPEED = 9.0f;
    /** Alcance del tiro respecto de {@link Settings.Difficulty#aiShootRange}. */
    private static final float SHOOT_RANGE_MULT = 1.25f;
    /** Cuánto pesa la comodidad de la puntería (menos reacomodarse) al elegir el rincón. */
    private static final float SHOT_COMFORT_WEIGHT = 0.9f;
    /** Con un rival más cerca que esto y delante, la conducción lo esquiva. */
    private static final float EVADE_DIST = 3.6f;
    /** Zona de peligro propia donde, con un rival encima, se despeja en vez de conducir. */
    private static final float DANGER_DEPTH = 7.0f;
    private static final float DANGER_HALF_HEIGHT = 8.5f;
    private static final int HIST = 16;

    /** Solo pruebas: fuerza una dificultad para esta IA (null = la de Ajustes). */
    public Settings.Difficulty forcedDifficulty = null;

    private final float attackSign;   // +1 ataca hacia X+, -1 hacia X-
    private final float goalX;
    private final float ownGoalX;

    private Plan plan = Plan.DRIBBLE;
    private boolean planLocked = false;           // true mientras carga un pateo (no cambia de idea a mitad)
    private final Vector2 target = new Vector2();
    private float targetCharge = 0.5f;
    private float kickChargeTimer = 0f;
    private float slideRetryCooldown = 0f;
    private float stuckTimer = 0f;
    private float replanTimer = 0f;
    private float evadeSide = 0f;                 // +1 / -1: lado por el que esquiva (se mantiene unos instantes)
    private float evadeHold = 0f;
    private float carryNoProgress = 0f;
    private float planAge = 0f;
    /** Desvío lento al azar del punto al que se dirige (que dos partidos iguales no sean idénticos, y que se vea humano). */
    private final Vector2 wander = new Vector2();
    private float wanderTimer = 0f;
    private final Vector2 carryAnchor = new Vector2();

    // Historia de lo que "vio" (para el retraso de percepción).
    private final Vector2[] hBp = new Vector2[HIST], hBv = new Vector2[HIST], hRp = new Vector2[HIST];
    private int hHead = 0, hCount = 0;
    private final Vector2 oBp = new Vector2(), oBv = new Vector2(), oRp = new Vector2();

    /**
     * @param attackingTowardsNegativeX true si este jugador ataca hacia el arco de X negativo
     *                                  (equipo ROJO, panel derecho).
     */
    public SupportAI(boolean attackingTowardsNegativeX) {
        this.attackSign = attackingTowardsNegativeX ? -1f : 1f;
        float edge = Constants.FIELD_WIDTH / 2f + Constants.GOAL_DEPTH * 0.4f;
        this.goalX = attackSign * edge;
        this.ownGoalX = -attackSign * edge;
        for (int i = 0; i < HIST; i++) {
            hBp[i] = new Vector2(); hBv[i] = new Vector2(); hRp[i] = new Vector2();
        }
    }

    /** Se llama una vez por tick (30 Hz). */
    public void update(PlayerInput out, PlayerEntity self, Ball ball, PlayerEntity rival,
                       GoalkeeperEntity oppKeeper, float dt) {
        update(out, self, ball, rival, oppKeeper, dt, false, null);
    }

    /**
     * 2v2: igual que {@link #update(PlayerInput, PlayerEntity, Ball, PlayerEntity, GoalkeeperEntity, float)} pero con
     * rol. Si {@code support} es true y la pelota no está a su alcance, en vez de ir a buscarla se coloca para apoyar
     * (adelante y del lado contrario si el equipo ataca) o cubrir (entre la pelota y su arco si defiende).
     * {@code mate} es la posición del compañero (puede ser null si no hay o está expulsado).
     */
    public void update(PlayerInput out, PlayerEntity self, Ball ball, PlayerEntity rival,
                       GoalkeeperEntity oppKeeper, float dt, boolean support, Vector2 mate) {
        Settings.Difficulty diff = forcedDifficulty != null ? forcedDifficulty : Settings.difficulty;
        out.sequence++;
        out.slidePressed = false;
        out.kickHeld = false;
        out.kickReleased = false;
        out.sprint = false;
        out.moveX = 0f;
        out.moveY = 0f;

        Vector2 sp = self.getPosition();
        Vector2 bp = ball.getPosition();
        Vector2 rp = rival.getPosition();
        observe(bp, ball.body.getLinearVelocity(), rp, lagTicks(diff));
        slideRetryCooldown -= dt;
        replanTimer -= dt;
        evadeHold -= dt;
        wanderTimer -= dt;
        if (wanderTimer <= 0f) {
            float amp = diff == Settings.Difficulty.EASY ? 1.0f : diff == Settings.Difficulty.NORMAL ? 0.6f : 0.1f;
            wander.set(MathUtils.random(-amp, amp), MathUtils.random(-amp, amp));
            wanderTimer = MathUtils.random(0.4f, 1.0f);
        }

        float distBall = sp.dst(bp);
        boolean ballInOwnHalf = bp.x * attackSign < 0f;
        boolean rivalHasBall = rival.isNear(ball);

        if (self.isNear(ball)) {
            carry(out, self, ball, rival, oppKeeper, mate, dt, diff);
        } else {
            resetKick();
            if (support) {
                supportMove(out, sp, oBp, mate, oBp.x * attackSign < 0f, diff);
            } else {
                if (rivalHasBall && sp.dst(rp) > 2.4f && (ballInOwnHalf || rivalAttacking(rival, ball))) {
                    defend(out, sp, oBp, oRp, diff);
                } else {
                    chase(out, sp, rp, ball, distBall, diff, rivalHasBall ? rp : null);
                }
                trySlide(out, self, ball, rival, distBall);
            }
        }

        // Con faltas activas, cerca de un rival y sin la pelota al alcance, no esprinta: a más de 8,5 m/s un choque
        // es falta de carga (un defensor cuidadoso).
        if (Settings.foulsEnabled && out.sprint && !self.isNear(ball) && sp.dst(rp) < Constants.AI_FOUL_CAUTION_DIST) {
            out.sprint = false;
        }

        out.moveX *= diff.aiMoveScale;
        out.moveY *= diff.aiMoveScale;
        out.normalizeMove();
    }

    /** Solo para depurar/pruebas. */
    public String debugPlan() { return plan + (planLocked ? "*" : ""); }

    // ------------------------------------------------------------------ percepción
    private static int lagTicks(Settings.Difficulty d) {
        switch (d) {
            case EASY: return 9;
            case NORMAL: return 5;
            default: return 0;
        }
    }

    private void observe(Vector2 bp, Vector2 bv, Vector2 rp, int lag) {
        hHead = (hHead + 1) % HIST;
        hBp[hHead].set(bp); hBv[hHead].set(bv); hRp[hHead].set(rp);
        if (hCount < HIST) {
            hCount++;
        }
        int back = Math.min(lag, hCount - 1);
        int idx = ((hHead - back) % HIST + HIST) % HIST;
        oBp.set(hBp[idx]); oBv.set(hBv[idx]); oRp.set(hRp[idx]);
    }

    // ---------------------------------------------------------------- predicción
    /** Dónde estará la pelota dentro de t segundos (frenado exponencial de la física + rebote en las bandas). */
    private static Vector2 predictBall(Vector2 bp, Vector2 bv, float t, Vector2 out) {
        float k = Constants.BALL_LINEAR_DAMPING;
        float f = (1f - (float) Math.exp(-k * t)) / k;
        out.set(bp).mulAdd(bv, f);
        float hy = Constants.FIELD_HEIGHT / 2f - Constants.BALL_RADIUS;
        if (out.y > hy) out.y = 2f * hy - out.y;
        if (out.y < -hy) out.y = -2f * hy - out.y;
        out.y = MathUtils.clamp(out.y, -hy, hy);
        float hx = Constants.FIELD_WIDTH / 2f - Constants.BALL_RADIUS;
        if (Math.abs(out.y) > Constants.GOAL_WIDTH / 2f) {
            if (out.x > hx) out.x = 2f * hx - out.x;
            if (out.x < -hx) out.x = -2f * hx - out.x;
            out.x = MathUtils.clamp(out.x, -hx, hx);
        }
        return out;
    }

    /** Primer punto de la trayectoria de la pelota al que se llega a tiempo. */
    private Vector2 interceptPoint(Vector2 sp, Vector2 bp, Vector2 bv, Vector2 out) {
        Vector2 tmp = new Vector2();
        for (float t = 0.1f; t <= 2.0f; t += 0.1f) {
            predictBall(bp, bv, t, tmp);
            if (sp.dst(tmp) / EST_SPEED <= t) {
                return out.set(tmp);
            }
        }
        return predictBall(bp, bv, 2.0f, out);
    }

    private boolean rivalAttacking(PlayerEntity rival, Ball ball) {
        // El rival lleva la pelota hacia nuestro arco, aunque todavía esté pasando la mitad de la cancha.
        Vector2 v = rival.body.getLinearVelocity();
        return v.x * attackSign < -1.5f && ball.getPosition().x * attackSign < 8f;
    }

    // --------------------------------------------------------------- SUPPORT (2v2)
    private void supportMove(PlayerInput out, Vector2 sp, Vector2 bp, Vector2 mate, boolean ballInOwnHalf,
                             Settings.Difficulty diff) {
        Vector2 spot = new Vector2();
        if (ballInOwnHalf) {
            // Cubre: entre la pelota y el propio arco, más cerca del arco.
            spot.set(ownGoalX, 0f).lerp(bp, 0.45f);
            spot.y = bp.y * 0.5f;
        } else {
            // Apoya: adelante de la pelota y del lado contrario, como opción de pase.
            spot.set(bp.x + attackSign * 5f, bp.y > 0f ? bp.y - 7f : bp.y + 7f);
        }
        if (mate != null && spot.dst(mate) < 3f) {
            Vector2 away = new Vector2(spot).sub(mate);
            if (away.len2() < 0.01f) {
                away.set(0f, 1f);
            }
            spot.set(mate).mulAdd(away.nor(), 3f);
        }
        clampInside(spot);
        Vector2 go = new Vector2(spot).sub(sp);
        float len = go.len();
        if (len > 0.6f) {
            go.nor();
            out.moveX = go.x;
            out.moveY = go.y;
        }
        out.sprint = len > diff.aiSprintDist;
    }

    // ---------------------------------------------------------------- DEFEND
    /**
     * Rival con la pelota yendo hacia nuestro arco: se coloca entre la pelota y el arco, a una distancia que se acorta
     * cuanto más cerca de nuestra área está (cierra el ángulo), y se corre al lado por el que el rival tiene espacio
     * para que no pase por el costado.
     */
    private void defend(PlayerInput out, Vector2 sp, Vector2 bp, Vector2 rp, Settings.Difficulty diff) {
        Vector2 toOwnGoal = new Vector2(ownGoalX, 0f).sub(bp).nor();
        float distToGoal = Math.abs(bp.x - ownGoalX);
        float standOff = MathUtils.clamp(distToGoal * 0.18f, 1.8f, 3.2f);
        Vector2 post = new Vector2(bp).mulAdd(toOwnGoal, standOff);
        // No se queda parada delante del arco más allá de lo razonable (el arquero cubre el resto).
        post.x = attackSign > 0f ? Math.max(post.x, ownGoalX + 3.5f) : Math.min(post.x, ownGoalX - 3.5f);
        clampInside(post);
        Vector2 go = new Vector2(post).sub(sp);
        float len = go.len();
        if (len > 0.25f) {
            go.nor();
            out.moveX = go.x;
            out.moveY = go.y;
        }
        out.sprint = len > diff.aiSprintDist;
    }

    // ----------------------------------------------------------------- CHASE
    private void chase(PlayerInput out, Vector2 sp, Vector2 rp, Ball ball, float distBall,
                       Settings.Difficulty diff, Vector2 rivalWithBall) {
        Vector2 target = new Vector2();
        float speed = oBv.len();
        if (speed > 1.5f) {
            interceptPoint(sp, oBp, oBv, target);   // pelota en movimiento: ir a donde va a estar
        } else {
            target.set(oBp);
        }
        float d = sp.dst(target);
        if (rivalWithBall != null && Settings.foulsEnabled && distBall < 5f && rivalBetween(sp, target, rivalWithBall)) {
            // El rival protege la pelota con el cuerpo; ir derecho a ella es chocarlo (falta). Se rodea hasta quedar del
            // otro lado de la pelota, así el planchazo (o el toque) llega a la pelota antes que al rival.
            Vector2 away = new Vector2(target).sub(rivalWithBall);
            if (away.len2() > 0.0004f) {
                target.mulAdd(away.nor(), Constants.AI_FLANK_DIST);
            }
        } else if (distBall < 4f && speed < 4f) {
            // Cerca de una pelota quieta se acerca desde atrás, sobre la línea hacia el arco rival.
            Vector2 toGoal = new Vector2(goalX, 0f).sub(target).nor();
            target.mulAdd(toGoal, -0.55f);
        }
        if (d > 3f) {
            target.add(wander);   // lejos de la pelota no va exacto; cerca sí
        }
        clampInside(target);
        Vector2 go = new Vector2(target).sub(sp);
        if (go.len2() > 0.0004f) {
            go.nor();
            out.moveX = go.x;
            out.moveY = go.y;
        }
        out.sprint = d > diff.aiSprintDist;
    }

    /** ¿El rival está en el camino entre {@code from} y {@code to} (ir derecho a la pelota sería chocarlo)? */
    private static boolean rivalBetween(Vector2 from, Vector2 to, Vector2 rp) {
        float dx = to.x - from.x, dy = to.y - from.y;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.01f) {
            return false;
        }
        dx /= len; dy /= len;
        float rx = rp.x - from.x, ry = rp.y - from.y;
        float along = rx * dx + ry * dy;
        float lateral = Math.abs(rx * dy - ry * dx);
        return along > 0f && along < len + 0.6f && lateral < 1.1f;
    }

    // ---------------------------------------------------------------- CARRY
    private void carry(PlayerInput out, PlayerEntity self, Ball ball, PlayerEntity rival,
                       GoalkeeperEntity oppKeeper, Vector2 mate, float dt, Settings.Difficulty diff) {
        Vector2 sp = self.getPosition();
        Vector2 bp = ball.getPosition();
        Vector2 rp = rival.getPosition();

        // Anti-atasco: si lleva más de ~1,6 s sin sacar la pelota de un mismo lugar, despeja hacia el centro.
        if (bp.dst(carryAnchor) > 1.2f) {
            carryAnchor.set(bp);
            carryNoProgress = 0f;
        } else {
            carryNoProgress += dt;
        }

        planAge += dt;
        boolean stale = plan != Plan.DRIBBLE && planAge > 1.6f && !planLocked;   // la jugada no sale: replantea
        if (!planLocked && (stale || carryNoProgress > 1.6f || (plan == Plan.DRIBBLE && replanTimer <= 0f))) {
            decide(sp, bp, rp, oppKeeper, mate, diff, carryNoProgress > 1.6f);
            planAge = 0f;
            replanTimer = 0.25f;
            if (carryNoProgress > 1.6f) {
                carryNoProgress = 0f;
            }
        }

        if (plan == Plan.DRIBBLE) {
            dribble(out, sp, bp, rp, diff);
            return;
        }
        kickPlan(out, sp, bp, dt);
    }

    /** Elige qué hacer con la pelota al pie. */
    private void decide(Vector2 sp, Vector2 bp, Vector2 rp, GoalkeeperEntity oppKeeper, Vector2 mate,
                        Settings.Difficulty diff, boolean forceClear) {
        float goalDist = Math.abs(goalX - bp.x);
        float rivalDist = sp.dst(rp);
        boolean pressured = rivalDist < 2.8f;
        float keeperY = oppKeeper != null ? oppKeeper.getPosition().y : 0f;

        if (forceClear) {
            planClear(sp, rp);
            return;
        }

        // 1) TIRO
        float shootRange = diff.aiShootRange * SHOOT_RANGE_MULT;
        if (goalDist < shootRange) {
            float q = chooseShotTarget(sp, bp, rp, keeperY, diff);
            // Cerca del arco dispara casi siempre; de lejos, solo con la línea razonablemente libre (o con un rival encima).
            float need = goalDist < 8f ? 0.05f : (pressured ? 0.15f : 0.35f);
            if (q >= need) {
                plan = Plan.SHOOT;
                targetCharge = MathUtils.random(0.62f, 0.95f);
                if (pressured) {
                    targetCharge = Math.min(targetCharge, 0.5f);   // remate más rápido con el rival encima
                }
                return;
            }
        }

        // 2) PELOTA DISPUTADA: con el rival pegado a la pelota no se la puede conducir; se la saca con un toque hacia
        //    el espacio (adelante y al lado contrario del rival) y se corre a buscarla.
        boolean contested = rivalDist < 2.0f && rp.dst(bp) < 1.9f;
        if (contested) {
            planPoke(sp, rp);
            return;
        }

        // 3) DESPEJE en la propia zona de peligro con un rival encima
        boolean inDanger = Math.abs(bp.x - ownGoalX) < DANGER_DEPTH && Math.abs(bp.y) < DANGER_HALF_HEIGHT;
        if (inDanger && rivalDist < 3.4f) {
            planClear(sp, rp);
            return;
        }

        // 4) PASE al compañero (2v2) si está mejor ubicado y la línea está libre
        if (mate != null && pressured && mate.x * attackSign > bp.x * attackSign - 1f
                && mate.dst(bp) > 4f && mate.dst(bp) < 18f && laneClear(bp, mate, rp, 1.0f)) {
            plan = Plan.PASS;
            // El pase va un poco adelantado respecto de la posición del compañero (se mueve hacia adelante).
            target.set(mate).add(attackSign * 1.2f, 0f);
            clampInside(target);
            targetCharge = passPower(bp.dst(target));
            return;
        }

        // 5) por defecto: CONDUCIR
        plan = Plan.DRIBBLE;
    }

    private void planClear(Vector2 sp, Vector2 rp) {
        plan = Plan.CLEAR;
        // Despeja hacia adelante y al costado contrario al rival (así no se la devuelve).
        float side = rp.y >= sp.y ? -1f : 1f;
        float hy = Constants.FIELD_HEIGHT / 2f - 3f;
        target.set(sp.x + attackSign * 14f, MathUtils.clamp(sp.y + side * 9f, -hy, hy));
        clampInside(target);
        targetCharge = passPower(Math.max(14f, sp.dst(target)) + 4f);   // largo, pero que no llegue a la pared de atrás
    }

    /** Toque al espacio: sale por delante y por el lado libre, con potencia para ~9 m. */
    private void planPoke(Vector2 sp, Vector2 rp) {
        plan = Plan.PASS;
        float side = rp.y >= sp.y ? -1f : 1f;
        float hy = Constants.FIELD_HEIGHT / 2f - 2f;
        target.set(sp.x + attackSign * 8f, MathUtils.clamp(sp.y + side * 6f, -hy, hy));
        clampInside(target);
        targetCharge = passPower(sp.dst(target) + 1f);
    }

    /** Potencia 0..1 para que la pelota recorra {@code dist} metros llegando todavía a ~8 m/s. */
    private static float passPower(float dist) {
        float mass = Constants.BALL_DENSITY * MathUtils.PI * Constants.BALL_RADIUS * Constants.BALL_RADIUS;
        float v0 = 8f + Constants.BALL_LINEAR_DAMPING * dist;
        float impulse = v0 * mass;
        float p = (impulse - Constants.PASS_IMPULSE_MIN) / (Constants.SHOT_IMPULSE_MAX - Constants.PASS_IMPULSE_MIN);
        return MathUtils.clamp(p, 0f, 1f);
    }

    /**
     * Elige el rincón al que tirar y lo deja en {@code target}. Devuelve la calidad 0..1 del tiro (qué tan limpia
     * está la línea y qué tan lejos queda del arquero).
     */
    private float chooseShotTarget(Vector2 sp, Vector2 bp, Vector2 rp, float keeperY, Settings.Difficulty diff) {
        float half = Constants.GOAL_WIDTH / 2f - 0.75f;
        Vector2 kickDir = new Vector2(bp).sub(sp);
        if (kickDir.len2() < 0.0001f) {
            kickDir.set(attackSign, 0f);
        }
        kickDir.nor();
        float bestScore = -1e9f, bestY = 0f, bestQ = 0f;
        Vector2 end = new Vector2();
        for (int i = -3; i <= 3; i++) {
            float ty = half * i / 3f;
            end.set(goalX, ty);
            float keeperGap = Math.min(Math.abs(ty - keeperY), 3.2f) / 3.2f;           // 0..1
            float lane = laneClear(bp, end, rp, 1.0f) ? 1f : 0f;
            Vector2 dir = new Vector2(end).sub(bp).nor();
            float comfort = Math.max(0f, kickDir.dot(dir));                            // 0..1
            float q = 0.55f * keeperGap + 0.45f * lane;
            float score = q * 2f + comfort * SHOT_COMFORT_WEIGHT;
            if (score > bestScore) {
                bestScore = score;
                bestY = ty;
                bestQ = q;
            }
        }
        bestY += MathUtils.random(-1f, 1f) * diff.aiAimJitter;
        bestY = MathUtils.clamp(bestY, -Constants.GOAL_WIDTH / 2f + 0.35f, Constants.GOAL_WIDTH / 2f - 0.35f);
        target.set(goalX, bestY);
        return bestQ;
    }

    /** ¿El rival NO está sobre el segmento a->b (a menos de {@code width} metros)? */
    private static boolean laneClear(Vector2 a, Vector2 b, Vector2 rp, float width) {
        float abx = b.x - a.x, aby = b.y - a.y;
        float len2 = abx * abx + aby * aby;
        if (len2 < 0.0001f) {
            return true;
        }
        float t = ((rp.x - a.x) * abx + (rp.y - a.y) * aby) / len2;
        if (t < 0.05f || t > 1.0f) {
            return true;
        }
        float px = a.x + abx * t, py = a.y + aby * t;
        float dx = rp.x - px, dy = rp.y - py;
        return dx * dx + dy * dy > width * width;
    }

    // -------------------------------------------------------------- DRIBBLE
    /** Con la pelota al pie: la empuja corriendo hacia el arco, esquivando al rival que se le cruza. */
    private void dribble(PlayerInput out, Vector2 sp, Vector2 bp, Vector2 rp, Settings.Difficulty diff) {
        kickChargeTimer = 0f;
        planLocked = false;
        // Hacia dónde conviene llevarla: al arco rival, desde lejos hacia la franja central.
        float aimY = bp.y * (Math.abs(goalX - bp.x) > 14f ? 0.35f : 0.1f);
        Vector2 dir = new Vector2(goalX, aimY).sub(bp);
        if (dir.len2() < 0.0001f) {
            dir.set(attackSign, 0f);
        }
        dir.nor();

        // Esquive: rival delante y cerca → se rota la dirección hacia el lado con más espacio.
        Vector2 toRival = new Vector2(rp).sub(sp);
        float rd = toRival.len();
        if (rd < EVADE_DIST && rd > 0.01f) {
            Vector2 rn = new Vector2(toRival).scl(1f / rd);
            if (rn.dot(dir) > 0.25f) {
                if (evadeHold <= 0f) {
                    // Lado: el contrario a donde está el rival; si hay pared de ese lado, el otro.
                    float cross = dir.x * rn.y - dir.y * rn.x;     // >0: el rival está a la izquierda de la marcha
                    evadeSide = cross > 0f ? -1f : 1f;
                    float room = Constants.FIELD_HEIGHT / 2f - 1.5f;
                    float sideY = sp.y + (-dir.x * evadeSide) * 3f; // hacia dónde nos llevaría el esquive (eje Y)
                    if (Math.abs(sideY) > room) {
                        evadeSide = -evadeSide;
                    }
                    evadeHold = 0.6f;
                }
                float w = MathUtils.clamp((EVADE_DIST - rd) / EVADE_DIST, 0.25f, 1f);
                Vector2 perp = new Vector2(-dir.y * evadeSide, dir.x * evadeSide);   // dir rotada 90° hacia evadeSide
                dir.scl(1f - 0.5f * w).mulAdd(perp, 1.1f * w).nor();
            }
        }
        wallAware(bp, dir);

        Vector2 toBall = new Vector2(bp).sub(sp);
        float d = toBall.len();
        if (d > 0.0001f) {
            toBall.scl(1f / d);
        } else {
            toBall.set(dir);
        }
        float cos = toBall.dot(dir);
        if (cos > 0.62f) {
            // La pelota está delante en la dirección de marcha: avanza empujándola, manteniéndola centrada.
            Vector2 mv = new Vector2(dir).scl(0.65f).mulAdd(toBall, 0.35f).nor();
            out.moveX = mv.x;
            out.moveY = mv.y;
            out.sprint = diff != Settings.Difficulty.EASY;
        } else {
            // Se acomoda detrás de la pelota (sobre la línea de marcha) rodeándola sin patearla hacia atrás.
            Vector2 stand = new Vector2(bp).mulAdd(dir, -0.85f);
            clampInside(stand);
            float len = moveAround(out, sp, bp, stand, 1.0f);
            out.sprint = len > 1.5f;
            stuckTimer += Constants.SIM_STEP;
            if (stuckTimer > 1.2f) {
                stuckTimer = 0f;
                replanTimer = 0f;
            }
            return;
        }
        stuckTimer = 0f;
    }

    // ------------------------------------------------------------ SHOOT/PASS/CLEAR
    private void kickPlan(PlayerInput out, Vector2 sp, Vector2 bp, float dt) {
        Vector2 dirT = new Vector2(target).sub(bp);
        if (dirT.len2() < 0.0001f) {
            dirT.set(attackSign, 0f);
        }
        dirT.nor();
        bounceAim(bp, dirT);   // pelota pegada a la banda: se apunta contra la pared para que rebote hacia el objetivo

        Vector2 kickDir = new Vector2(bp).sub(sp);
        float dist = kickDir.len();
        if (dist < 0.0001f) {
            kickDir.set(dirT);
            dist = 0.0001f;
        }
        kickDir.nor();
        float alignment = kickDir.dot(dirT);
        float need = plan == Plan.SHOOT ? 0.74f : 0.62f;   // al soltar se apunta con el movimiento (AIM_ASSIST)

        if (alignment > need && dist < Constants.KICK_RANGE) {
            planLocked = true;
            kickChargeTimer += dt;
            if (kickChargeTimer >= targetCharge * Constants.KICK_CHARGE_TIME) {
                out.kickHeld = false;
                out.kickReleased = true;
                out.moveX = dirT.x;
                out.moveY = dirT.y;
                kickChargeTimer = 0f;
                planLocked = false;
                plan = Plan.DRIBBLE;
                planAge = 0f;
                replanTimer = 0.35f; // tras patear, un respiro antes de elegir otra jugada
            } else {
                out.kickHeld = true;
                out.moveX = dirT.x * 0.15f;
                out.moveY = dirT.y * 0.15f;
            }
        } else {
            // Se acomoda detrás de la pelota, sobre la línea hacia el objetivo.
            kickChargeTimer = 0f;
            planLocked = false;
            Vector2 stand = new Vector2(bp).mulAdd(dirT, -0.6f);
            clampInside(stand);
            float len = moveAround(out, sp, bp, stand, Math.min(1f, Math.max(0.35f, len2(stand, sp) * 2f)));
            stuckTimer += dt;
            if (stuckTimer > 1.1f) {   // se pasó de largo o lleva mucho acomodándose: replantea
                stuckTimer = 0f;
                plan = Plan.DRIBBLE;
                replanTimer = 0.3f;
            }
        }
    }

    /** Pierde la pelota o termina la jugada: la próxima vez que la tenga vuelve a decidir desde cero. */
    private void resetKick() {
        kickChargeTimer = 0f;
        planLocked = false;
        plan = Plan.DRIBBLE;
        replanTimer = 0f;
        planAge = 0f;
    }

    private static float len2(Vector2 a, Vector2 b) {
        return a.dst(b);
    }

    /**
     * Va hacia {@code stand} sin chocar la pelota: si la pelota queda en el camino, rodea hacia el lado por el que ya
     * está. Devuelve la distancia que falta al punto.
     */
    private float moveAround(PlayerInput out, Vector2 sp, Vector2 bp, Vector2 stand, float scale) {
        Vector2 toStand = new Vector2(stand).sub(sp);
        float len = toStand.len();
        if (len < 0.05f) {
            return len;
        }
        Vector2 dirS = new Vector2(toStand).scl(1f / len);
        Vector2 toBall = new Vector2(bp).sub(sp);
        float proj = toBall.dot(dirS);
        float perpDist = Math.abs(toBall.x * dirS.y - toBall.y * dirS.x);
        Vector2 go = dirS;
        if (proj > 0f && proj < len + 0.3f && perpDist < 0.95f && toBall.len() < 2.2f) {
            // La pelota está en el camino: rodeo tangente por el lado en que ya está el jugador.
            Vector2 perp = new Vector2(-dirS.y, dirS.x);
            float side = perp.dot(new Vector2(sp).sub(bp)) >= 0f ? 1f : -1f;
            Vector2 way = new Vector2(bp).mulAdd(perp, side * 1.05f);
            clampInside(way);
            go = new Vector2(way).sub(sp).nor();
        }
        go.scl(scale);
        out.moveX = go.x;
        out.moveY = go.y;
        return len;
    }

    /** Pelota pegada a una banda: no se la empuja contra ella; se conduce a lo largo de la pared. */
    private static void wallAware(Vector2 bp, Vector2 dir) {
        float edge = Constants.FIELD_HEIGHT / 2f - 0.9f;
        if (Math.abs(bp.y) > edge) {
            dir.y = 0f;
            if (Math.abs(dir.x) < 0.0001f) dir.x = 1f;
        }
        dir.nor();
    }

    /**
     * Pelota pegada a una banda: el jugador solo puede estar del lado de adentro, así que patea "hacia la pared". La
     * pared devuelve la pelota con una fracción de la velocidad normal (restitución), por eso se apunta con la
     * componente normal agrandada. Modifica {@code dir} en el lugar.
     */
    private static void bounceAim(Vector2 bp, Vector2 dir) {
        float edge = Constants.FIELD_HEIGHT / 2f - 0.9f;
        if (Math.abs(bp.y) <= edge) {
            return;
        }
        float inward = bp.y > 0f ? -1f : 1f;      // sentido hacia adentro de la cancha
        float awayFromWall = dir.y * inward;        // >0: el objetivo está hacia adentro
        if (awayFromWall <= 0.05f) {
            return;                                 // ya apunta a lo largo o contra la pared: patada normal
        }
        dir.y = -inward * awayFromWall / Constants.BALL_RESTITUTION;
        dir.nor();
    }

    /** Lleva el punto adentro de la cancha, a un radio de jugador de los muros. */
    private static void clampInside(Vector2 p) {
        float mx = Constants.FIELD_WIDTH / 2f - Constants.PLAYER_RADIUS - 0.05f;
        float my = Constants.FIELD_HEIGHT / 2f - Constants.PLAYER_RADIUS - 0.05f;
        p.x = MathUtils.clamp(p.x, -mx, mx);
        p.y = MathUtils.clamp(p.y, -my, my);
    }

    // ---------------------------------------------------------------- SLIDE
    private void trySlide(PlayerInput out, PlayerEntity self, Ball ball, PlayerEntity rival, float distBall) {
        boolean rivalHasBallNearby = rival.isNear(ball) && distBall < Constants.KICK_RANGE + 0.9f;
        // Con faltas activadas se tira con la probabilidad de siempre solo si el planchazo toca la pelota ANTES que al
        // rival (si pega primero en el rival es falta); dentro de su área, casi nunca (un penal sale caro).
        boolean foulsOn = Settings.foulsEnabled;
        float chance = 0.35f;
        if (foulsOn) {
            if (!ballWithinSlide(self, ball)) {
                return;
            }
            boolean clean = slideWinsBallFirst(self, ball, rival);
            boolean inOwnBox = Math.abs(self.getPosition().x - ownGoalX) <= Constants.PENALTY_BOX_DEPTH
                    && Math.abs(self.getPosition().y) <= Constants.PENALTY_BOX_HEIGHT / 2f;
            chance = clean ? (inOwnBox ? 0.05f : 0.25f) : (inOwnBox ? 0.0005f : Constants.AI_RISKY_SLIDE_CHANCE);
        }
        if (rivalHasBallNearby && slideRetryCooldown <= 0f && MathUtils.randomBoolean(chance)) {
            out.slidePressed = true;
            slideRetryCooldown = Constants.SLIDE_COOLDOWN + 0.5f;
        }
    }

    /** La pelota está sobre la línea del planchazo y dentro de su alcance. */
    private static boolean ballWithinSlide(PlayerEntity self, Ball ball) {
        Vector2 f = self.getFacing();
        float fl = f.len();
        if (fl < 0.0001f) {
            return false;
        }
        float fx = f.x / fl, fy = f.y / fl;
        float bx = ball.getPosition().x - self.getPosition().x, by = ball.getPosition().y - self.getPosition().y;
        float tb = bx * fx + by * fy;
        float pb = Math.abs(bx * fy - by * fx);
        return tb >= 0.3f && tb <= Constants.AI_SLIDE_MAX_BALL_DIST && pb <= Constants.SLIDE_REACH;
    }

    /**
     * ¿Un planchazo hacia donde mira ahora llega a la pelota antes de llegar al rival? El planchazo va en línea
     * recta hacia {@link PlayerEntity#getFacing()} unos 2,3 m. La pelota tiene que estar en esa línea, al alcance, y
     * el rival o no estar en el camino o quedar detrás de la pelota.
     */
    private static boolean slideWinsBallFirst(PlayerEntity self, Ball ball, PlayerEntity rival) {
        Vector2 f = self.getFacing();
        float fl = f.len();
        if (fl < 0.0001f) {
            return false;
        }
        float fx = f.x / fl, fy = f.y / fl;
        Vector2 sp = self.getPosition();
        float bx = ball.getPosition().x - sp.x, by = ball.getPosition().y - sp.y;
        float tb = bx * fx + by * fy;
        float rx = rival.getPosition().x - sp.x, ry = rival.getPosition().y - sp.y;
        float tr = rx * fx + ry * fy;
        float pr = Math.abs(rx * fy - ry * fx);
        boolean rivalInPath = tr > 0f && tr < 3.6f && pr < Constants.FOUL_CONTACT_DIST + 0.2f;
        return !rivalInPath || (tb - (Constants.SLIDE_REACH + Constants.BALL_RADIUS)) <= (tr - Constants.FOUL_CONTACT_DIST) - 0.2f;
    }
}
