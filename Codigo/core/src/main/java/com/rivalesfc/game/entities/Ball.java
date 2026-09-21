package com.rivalesfc.game.entities;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.World;
import com.rivalesfc.game.Constants;

/**
 * Pelota: un único body dinámico circular. En la Etapa 1 el "efecto" en el remate
 * (curva) se deja simplificado como un torque aplicado junto al impulso lineal;
 * la física completa de Magnus queda para una iteración posterior (fuera del
 * alcance mínimo de la Etapa 1).
 */
public class Ball {

    public final Body body;

    public Ball(World world, Vector2 startPosition) {
        BodyDef bd = new BodyDef();
        bd.type = BodyDef.BodyType.DynamicBody;
        bd.position.set(startPosition);
        bd.bullet = true; // evita "tunneling" a alta velocidad en los remates
        bd.linearDamping = Constants.BALL_LINEAR_DAMPING;
        bd.angularDamping = 0.8f;

        body = world.createBody(bd);

        CircleShape shape = new CircleShape();
        shape.setRadius(Constants.BALL_RADIUS);

        FixtureDef fd = new FixtureDef();
        fd.shape = shape;
        fd.density = Constants.BALL_DENSITY;
        fd.friction = Constants.BALL_FRICTION;
        fd.restitution = Constants.BALL_RESTITUTION;
        fd.filter.categoryBits = Constants.CAT_BALL;

        body.createFixture(fd);
        shape.dispose();
    }

    public Vector2 getPosition() {
        return body.getPosition();
    }

    /**
     * Aplica un impulso de pase/remate en la dirección indicada.
     *
     * @param direction dirección normalizada del remate
     * @param power     0..1, mapeado linealmente entre PASS_IMPULSE_MIN y SHOT_IMPULSE_MAX
     * @param spin      efecto lateral simplificado: torque adicional (positivo = sentido horario)
     */
    public void kick(Vector2 direction, float power, float spin) {
        power = Math.max(0f, Math.min(1f, power));
        float impulseMag = Constants.PASS_IMPULSE_MIN
                + power * (Constants.SHOT_IMPULSE_MAX - Constants.PASS_IMPULSE_MIN);

        Vector2 impulse = new Vector2(direction).nor().scl(impulseMag);
        body.setLinearVelocity(0, 0); // el pateo reemplaza la velocidad previa, no se acumula
        body.applyLinearImpulse(impulse, body.getWorldCenter(), true);
        body.applyAngularImpulse(spin * impulseMag * 0.15f, true);
    }

    public void reset(Vector2 position) {
        body.setTransform(position, 0);
        body.setLinearVelocity(0, 0);
        body.setAngularVelocity(0);
    }
}
