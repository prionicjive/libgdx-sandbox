package com.austinauts.libgdx.common.particles;

import com.austinauts.libgdx.common.utils.misc.FloatDimension;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

public class Particle {
	public Sprite sprite;
	public Color startColor, endColor, interpolatedColor;
	public Vector3 scratchColorValues;
	public float scratchAlphaValue; // Store these as {start, end, tweenequation}?
	public FloatDimension startSize, endSize, interpolatedSize;
	public float speed;
	public Vector2 direction;
	public float age, ttl;

	public Particle(Texture texture, ParticleTemplate template) {
		sprite = new Sprite(texture);
		set(template);
	}

	public Particle(TextureRegion region, ParticleTemplate template) {
		sprite = new Sprite(region);
		set(template);
	}

	private void set(ParticleTemplate template) {
		sprite.setOrigin(0f, 0f);

		startColor = new Color(template.startColor);
		endColor = new Color(template.endColor);
		interpolatedColor = new Color(startColor);
		scratchColorValues = new Vector3();
		scratchAlphaValue = 1.0f;
		startSize = new FloatDimension(template.startSize);
		endSize = new FloatDimension(template.endSize);
		interpolatedSize = new FloatDimension(startSize);
		speed = template.speed;
		direction = template.direction;
		ttl = template.ttl;
		age = 0;
	}

	public void reset() {
		sprite.setColor(startColor);
		interpolatedColor.set(startColor);
		sprite.setSize(startSize.width, startSize.height);
		interpolatedSize.set(startSize);
		age = 0;
	}

	public void update(float delta) {
		age += delta;

		sprite.translate(direction.x * speed * delta, direction.y * speed * delta);

		// Adjust color

		float redDelta = ((endColor.r - startColor.r) / ttl) * delta;
		float greenDelta = ((endColor.g - startColor.g) / ttl) * delta;
		float blueDelta = ((endColor.b - startColor.b) / ttl) * delta;
		float alphaDelta = ((endColor.a - startColor.a) / ttl) * delta;
		interpolatedColor.set(interpolatedColor.r + redDelta, interpolatedColor.g + greenDelta, interpolatedColor.b + blueDelta, interpolatedColor.a + alphaDelta);
		sprite.setColor(interpolatedColor); // Need to set verticies with new color

		// Adjust size
		interpolatedSize.width = (endSize.width - startSize.width) / ttl * delta;
		interpolatedSize.height = (endSize.height - startSize.height) / ttl * delta;
		sprite.setSize(sprite.getWidth() + interpolatedSize.width, sprite.getHeight() + interpolatedSize.width);
	}

	public boolean alive() {
		return age < ttl;
	}
}
