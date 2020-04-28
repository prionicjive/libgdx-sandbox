package com.austinauts.libgdx.modules.whammyball.entities;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Disposable;

public class Ball implements Disposable {
	// Static fields
	public static final float LINEAR_DAMPING = 0.87f;
	public static final int STARTING_HEALTH = 3;

	// Properties
	public float radius;
	public Texture texture;
	public Body body;
	public boolean isGrowing = false;
	public float radiusToGrowTo;

	// Privates
	public CircleShape circleShape;

	public Ball(float x, float y, float radius, Texture texture, World world, boolean isStatic) {
		this.radius = radius;
		this.texture = texture;

		createPhysicsBody(x, y, radius, world, isStatic);
	}

	private void createPhysicsBody(float x, float y, float radius, World world, boolean isStatic) {
		// Destroy body if it exists
		// TODO Is this smart to hide it away?
		if (body != null) {
			world.destroyBody(body);
		}

		if (isStatic) {
			BodyDef bodyDef = new BodyDef();

			// We set our body to dynamic, for something like ground which doesn't move we would set it to StaticBody
			bodyDef.type = BodyDef.BodyType.StaticBody;

			// Set our body's starting position in the world
			bodyDef.position.set(x, y);

			// Create our body in the world using our body definition
			body = world.createBody(bodyDef);

			// Create a circle shape and set its radius
			circleShape = new CircleShape();
			circleShape.setRadius(radius - 0.01f);

			// Create our fixtureDef and attach it to the playerBody
			body.createFixture(circleShape, 0f);

			// Lastly, because we have a static body, we should start to be concerned about its health
			body.setUserData(new BallUserData(this, STARTING_HEALTH));

		} else {
			BodyDef bodyDef = new BodyDef();

			// We set our body to dynamic, for something like ground which doesn't move we would set it to StaticBody
			bodyDef.type = BodyDef.BodyType.DynamicBody;
			bodyDef.linearDamping = LINEAR_DAMPING;

			// Set our bbody's starting position in the world
			// TODO This is the spawn point. Better way to do this?
			bodyDef.position.set(x, y);

			// Create our body in the world using our body definition
			body = world.createBody(bodyDef);

			// Create a circle shape and set its radius
			circleShape = new CircleShape();
			circleShape.setRadius(radius - 0.01f);

			// Create a fixture definition to apply our shape to
			FixtureDef fixtureDef = new FixtureDef();
			fixtureDef.shape = circleShape;
			fixtureDef.density = 1.0f;
			fixtureDef.friction = 0.0f;
			fixtureDef.restitution = 0.95f; // Make it mostly bounce back perfectly

			// Create our fixtureDef and attach it to the playerBody
			body.createFixture(fixtureDef);
		}
	}

	@Override
	public void dispose() {
		if (circleShape != null) {
			circleShape.dispose();
		}
	}
}
