package com.austinauts.libgdx.common.particles;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.Array;

import java.util.Iterator;

// TODO Have EmitterTemplate that can drive what kind of Continuous Emitter to be
public class ParticleEmitter {
	public String name;
	public Vector2 position;
	public int maxActiveParticles;
	public int emitPerSecond;

	public boolean continuous;
	public boolean instaKill;

	public Vector2 emitDirection;
	public float emitSpreadAngle;
	public float emitRotateAnglePerSecond;

	public float ttl;
	public float age;

	public boolean isFinishing;
	public boolean done;

	private Array<Particle> activeParticles;
	private Array<Particle> deadPool;

	private float accum;
	public BoundingBox bounds;

	public boolean paused;

	public ParticleEmitter(Texture texture, ParticleEmitterTemplate template) {
		this(texture, template, new Vector2(0f, 0f));
	}

	public ParticleEmitter(Texture texture, ParticleEmitterTemplate template, Vector2 position) {
		name = template.name;
		maxActiveParticles = template.maxActiveParticles;
		emitPerSecond = template.emitPerSecond;

		continuous = template.continuous;
		instaKill = template.instaKill;

		// TODO Why should we have to invert the direction?
		emitDirection = template.emitDirection.nor().scl(-1f);
		emitSpreadAngle = template.emitSpreadAngle;
		emitRotateAnglePerSecond = template.emitRotateAnglePerSecond;

		ttl = template.ttl;
		age = 0;

		isFinishing = false;
		done = false;

		activeParticles = new Array<>(false, maxActiveParticles);
		deadPool = new Array<>(false, maxActiveParticles);
		paused = false;

		// Pool the emitters
		for (int i = 0; i < maxActiveParticles; i++) {
			Particle p = new Particle(texture, template.particleTemplate);

			// Update anything that might be needed on particle emission
			prepareParticle(p);

			deadPool.add(p);
		}

		this.position = position;
		accum = 0f;
	}

	public void update(float delta) {
		if (!paused && !done) {
			// No matter what, rotate the emitter
			emitDirection.rotate(emitRotateAnglePerSecond * delta);
			// Emit if need be
			accum += delta;

			if (!continuous) {
				age += delta;

				if (age >= ttl) {
					if (instaKill) {
						instaKill();
						return; // Nothing further to update
					}
					else {
						lazyKill();
					}
				}
			}

			if (accum >= 1f / emitPerSecond) {
				accum -= (1f / emitPerSecond);

				// Only emit if we are not in the phase of finishing
				if (!isFinishing) {
					emitParticle();
				}
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
	}

	// TODO Unify with update so no need to loop multiple times?
	public void render(SpriteBatch batch) {
		// Only render if we are NOT done
		if (!done) {
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
	}

	public void reset() {
		Iterator<Particle> iter = activeParticles.iterator();

		while (iter.hasNext()) {
			Particle curr = iter.next();
			iter.remove();
			deadPool.add(curr);
		}
	}

	public void lazyKill() {
		isFinishing = true;
	}

	public void instaKill() {
		done = true;
	}

	private void emitParticle() {
		// Only emit if we can
		if (deadPool.size > 0) {
			Particle emit = deadPool.removeIndex(0);
			emit.reset();
			emit.sprite.setPosition(position.x, position.y);

			// Update anything that might be needed on particle emission
			prepareParticle(emit);

			activeParticles.add(emit);
		}
	}

	private void prepareParticle(Particle particleToPrepare) {
		// Update anything that might be needed on particle emission
		// Treat direct as a normal, meaning have of the spread will be on 1 side and the other half on the other side
		particleToPrepare.startDirection.set(emitDirection).rotate(emitSpreadAngle / -2f).rotate(MathUtils.random(emitSpreadAngle));
		//	particleToPrepare.endDirection = particleToPrepare.startDirection;
	}
}
