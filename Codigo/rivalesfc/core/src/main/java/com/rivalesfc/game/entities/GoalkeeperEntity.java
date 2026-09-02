package com.rivalesfc.game.entities;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.World;
import com.rivalesfc.game.Constants;

/**
 * Arquero 100% controlado por IA (nunca recibe input humano).
 *
 * Es intencionalmente la IA más simple del proyecto: un único estado
 * ("seguir la coordenada Y de la pelota"), a diferencia de la máquina de
 * estados de 4 estados descripta en la sección 1.2 de la propuesta para los
 * jugadores de campo asistidos por IA. Se modela como un body *cinemático*
 * de Box2D (no dinámico): así nunca es empujado por choques con jugadores o
 * con la pelota, y su posición X queda clavada sobre la línea de su propio
 * arco — sólo se mueve hacia arriba y hacia abajo, sin salir de la boca del
 * arco.
 */
public class GoalkeeperEntity {

    public final Body body;
    public final Color color;
    private final float fixedX;
    private final float patrolHalfRange;

    /**
     * Posición Y "percibida" por el arquero, con retraso respecto a la pelota real
     * (ver {@link #update}). Antes el arquero clavaba su objetivo en la posición
     * exacta de la pelota en el mismo instante, sin ningún tiempo de reacción, lo
     * que lo hacía prácticamente imbatible.
     */
    private float trackedBallY = 0f;

    public GoalkeeperEntity(World world, float fixedX, float patrolHalfRange, Color color) {
        this.fixedX = fixedX;
        this.patrolHalfRange = patrolHalfRange;
        this.color = color;

        BodyDef bd = new BodyDef();
        bd.type = BodyDef.BodyType.KinematicBody; // se mueve por velocidad, nunca por fuerzas externas
        bd.position.set(fixedX, 0);
        bd.fixedRotation = true;
        body = world.createBody(bd);

        CircleShape shape = new CircleShape();
        shape.setRadius(Constants.PLAYER_RADIUS);

        FixtureDef fd = new FixtureDef();
        fd.shape = shape;
        fd.filter.categoryBits = Constants.CAT_PLAYER;

        body.createFixture(fd);
        shape.dispose();
    }

    /**
     * Se llama una vez por tick de simulación (30 Hz). Único estado de IA:
     * perseguir en Y la posición de la pelota, recortada al ancho del arco.
     *
     * A diferencia de la versión original (que apuntaba de forma instantánea y
     * perfecta a la pelota, haciendo al arquero imposible de superar), acá el
     * objetivo se persigue con un tiempo de reacción simulado
     * ({@link Constants#GK_REACTION_TIME}): el arquero "se entera" de hacia dónde
     * se mueve la pelota con cierto retraso, como un jugador real, en vez de
     * teletransportar su intención al instante.
     */
    public void update(float ballY, float dt) {
        float desired = MathUtils.clamp(ballY, -patrolHalfRange, patrolHalfRange);
        float lag = MathUtils.clamp(dt / Constants.GK_REACTION_TIME, 0f, 1f);
        trackedBallY = MathUtils.lerp(trackedBallY, desired, lag);

        float diff = trackedBallY - body.getPosition().y;

        // La velocidad en X siempre es 0: el arquero está clavado sobre la línea de su propio arco.
        if (Math.abs(diff) < Constants.GK_DEAD_ZONE) {
            body.setLinearVelocity(0, 0);
        } else {
            body.setLinearVelocity(0, Math.signum(diff) * Constants.GK_SPEED);
        }
    }

    public Vector2 getPosition() {
        return body.getPosition();
    }

    public void resetToCenter() {
        body.setTransform(fixedX, 0, 0);
        body.setLinearVelocity(0, 0);
        trackedBallY = 0f;
    }
}
