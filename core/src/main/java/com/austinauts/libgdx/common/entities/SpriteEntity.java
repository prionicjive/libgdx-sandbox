package com.austinauts.libgdx.common.entities;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Vector2;

public class SpriteEntity extends Sprite {
	private Vector2 velocity;
	private float speed;

	public SpriteEntity(Texture texture) {
		// Construct the underlying sprite
		super(texture);

		// Set up the rest
		velocity = new Vector2();
		speed = 0f;
	}

	public void negateVelocityX() {
		velocity.x *= -1;
	}

	public void negateVelocityY() {
		velocity.y *= -1;
	}

	public Vector2 getVelocity() {
		return velocity;
	}

	public void setVelocity(Vector2 velocity) {
		this.velocity = velocity;

		// Normalize the given velocity to be sure
		this.velocity.nor();
	}

	public void setVelocity(float x, float y) {
		velocity.set(x, y);

		// Normalize the given velocity to be sure
		velocity.nor();
	}

	public float getSpeed() {
		return speed;
	}

	public void setSpeed(float speed) {
		this.speed = speed;
	}
}
