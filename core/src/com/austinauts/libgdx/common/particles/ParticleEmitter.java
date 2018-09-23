package com.austinauts.libgdx.common.particles;

import com.austinauts.libgdx.common.loaders.ParticleEmitterSettings;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

import java.util.Iterator;

// TODO Have EmitterTemplate that can drive what kind of Continuous Emitter to be
public class ParticleEmitter {
	private int maxActiveParticles;
	private int emitPerSecond;

	private Array<Particle> activeParticles;
	private Array<Particle> deadPool;

	private float accum;

	public Vector2 position;

	public ParticleEmitter(Texture texture, ParticleEmitterSettings settings) {
		this(texture, settings, new Vector2(0f, 0f));
	}

	public ParticleEmitter(Texture texture, ParticleEmitterSettings settings, Vector2 position) {
		maxActiveParticles = settings.maxActiveParticles;
		emitPerSecond = settings.emitPerSecond;

		activeParticles = new Array<>(false, maxActiveParticles);
		deadPool = new Array<>(false, maxActiveParticles);

		// Pool the emitters
		for (int i = 0; i < maxActiveParticles; i++) {
			Particle p = new Particle(texture, settings.particleTemplate);
			deadPool.add(p);
		}

		this.position = position;
		accum = 0f;
	}

	public void update(float delta) {
		// Emit if need be
		accum += delta;

		if (accum >= 1f / emitPerSecond) {
			accum -= (1f / emitPerSecond);

			emitParticle();
		}

		Iterator<Particle> iter = activeParticles.iterator();

		while (iter.hasNext()) {
			Particle curr = iter.next();
			curr.update(delta);

			if (!curr.alive()) {
				iter.remove();
				deadPool.add(curr);
			}
		}
	}

	// TODO Unify with update so no need to loop multiple times?
	public void render(SpriteBatch batch) {
		// TODO Establish a certain blend func?
		batch.begin();
		{
			for (int i = 0; i < activeParticles.size; i++) {
				Particle curr = activeParticles.get(i);

				batch.setColor(curr.sprite.getColor());
				batch.draw(curr.sprite.getTexture(), curr.sprite.getX(), curr.sprite.getY(), curr.sprite.getWidth(), curr.sprite.getHeight());
			}
		}
		batch.end();
	}

	public void reset() {
		Iterator<Particle> iter = activeParticles.iterator();

		while (iter.hasNext()) {
			Particle curr = iter.next();
			iter.remove();
			deadPool.add(curr);
		}
	}

	private void emitParticle() {
		// Only emit if we can
		if (deadPool.size > 0) {
			Particle emit = deadPool.removeIndex(0);
			emit.reset();
			emit.sprite.setPosition(position.x, position.y);
			activeParticles.add(emit);
		}
	}
}
