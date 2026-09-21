package com.rivalesfc.game.entities;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.EdgeShape;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.rivalesfc.game.Constants;

/**
 * Cancha reglamentaria simplificada: cuatro bordes (con dos huecos para los arcos)
 * y dos sensores de gol. Todo estático, sin body dinámico.
 */
public class Field {

    public final float width = Constants.FIELD_WIDTH;
    public final float height = Constants.FIELD_HEIGHT;

    public Fixture leftGoalSensor;
    public Fixture rightGoalSensor;

    public Field(World world) {
        buildBoundaries(world);
        leftGoalSensor = buildGoalSensor(world, -width / 2f - Constants.GOAL_DEPTH / 2f);
        rightGoalSensor = buildGoalSensor(world, width / 2f + Constants.GOAL_DEPTH / 2f);
    }

    private void buildBoundaries(World world) {
        BodyDef bd = new BodyDef();
        bd.type = BodyDef.BodyType.StaticBody;
        bd.position.set(0, 0);
        Body body = world.createBody(bd);

        float halfW = width / 2f;
        float halfH = height / 2f;
        float goalHalf = Constants.GOAL_WIDTH / 2f;

        // Borde superior e inferior (completos)
        addEdge(body, -halfW, halfH, halfW, halfH);
        addEdge(body, -halfW, -halfH, halfW, -halfH);

        // Borde izquierdo, con hueco para el arco
        addEdge(body, -halfW, halfH, -halfW, goalHalf);
        addEdge(body, -halfW, -goalHalf, -halfW, -halfH);

        // Borde derecho, con hueco para el arco
        addEdge(body, halfW, halfH, halfW, goalHalf);
        addEdge(body, halfW, -goalHalf, halfW, -halfH);

        // "Fondo" del arco (para que la pelota no siga viajando al infinito tras el gol)
        addEdge(body, -halfW - Constants.GOAL_DEPTH, goalHalf, -halfW - Constants.GOAL_DEPTH, -goalHalf);
        addEdge(body, halfW + Constants.GOAL_DEPTH, goalHalf, halfW + Constants.GOAL_DEPTH, -goalHalf);
        addEdge(body, -halfW - Constants.GOAL_DEPTH, goalHalf, -halfW, goalHalf);
        addEdge(body, -halfW - Constants.GOAL_DEPTH, -goalHalf, -halfW, -goalHalf);
        addEdge(body, halfW + Constants.GOAL_DEPTH, goalHalf, halfW, goalHalf);
        addEdge(body, halfW + Constants.GOAL_DEPTH, -goalHalf, halfW, -goalHalf);
    }

    private void addEdge(Body body, float x1, float y1, float x2, float y2) {
        EdgeShape edge = new EdgeShape();
        edge.set(new Vector2(x1, y1), new Vector2(x2, y2));

        FixtureDef fd = new FixtureDef();
        fd.shape = edge;
        fd.friction = 0.1f;
        fd.restitution = 0.6f;
        fd.filter.categoryBits = Constants.CAT_BOUNDARY;

        body.createFixture(fd);
        edge.dispose();
    }

    private Fixture buildGoalSensor(World world, float x) {
        BodyDef bd = new BodyDef();
        bd.type = BodyDef.BodyType.StaticBody;
        bd.position.set(x, 0);
        Body body = world.createBody(bd);

        PolygonShape box = new PolygonShape();
        box.setAsBox(Constants.GOAL_DEPTH / 2f, Constants.GOAL_WIDTH / 2f);

        FixtureDef fd = new FixtureDef();
        fd.shape = box;
        fd.isSensor = true;
        fd.filter.categoryBits = Constants.CAT_GOAL_SENSOR;

        Fixture fixture = body.createFixture(fd);
        box.dispose();
        return fixture;
    }
}
