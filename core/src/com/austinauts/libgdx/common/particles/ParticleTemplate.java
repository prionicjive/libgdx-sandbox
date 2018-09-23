package com.austinauts.libgdx.common.particles;

import com.austinauts.libgdx.common.utils.misc.FloatDimension;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;

public class ParticleTemplate {
	public Color startColor, endColor;
	public FloatDimension startSize, endSize;
	public float ttl;
	public float speed;
	public Vector2 direction;

	public ParticleTemplate() {
		// TODO CLAMP!!!!!!!!!!!!
		startColor = new Color(Color.BLUE);
		endColor = new Color(Color.RED);
		startSize = new FloatDimension(16f, 16f);
		endSize = new FloatDimension(16f, 16f);
		ttl = 1;
		speed = 10;
		direction = new Vector2(0, 0f);
	}
}
