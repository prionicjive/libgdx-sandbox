package com.austinauts.libgdx.common.particles;

import com.austinauts.libgdx.common.loaders.ParticleEffectSettings;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

public class ParticleEffect {
	public String name;
	private Vector2 position;

	public boolean isFinishing;
	public boolean done;
	public boolean continuous;
	public boolean instaKill;
	public float ttl;
	public float age;

	public boolean paused;
	private final Array<ParticleEmitter> emitters;

	public ParticleEffect(Texture texture, ParticleEffectSettings settings) {
		name = settings.name;
		position = new Vector2();

		continuous = settings.continuous;
		instaKill = settings.instaKill;
		ttl = settings.ttl;
		age = 0;

		emitters = new Array<>();

		// Set up each emitter
		for (ParticleEmitterTemplate emitterTemplate : settings.emitterTemplates) {
			// TODO How best not to drill this down
			emitters.add(new ParticleEmitter(texture, emitterTemplate));
		}
	}

	public void start() {
		for (int i = 0, n = emitters.size; i < n; i++) {
			//emitters.get(i).start();
		}
	}

	public void reset() {
		// TODO What can be reset about the effect itself?
		for (int i = 0, n = emitters.size; i < n; i++) {
			emitters.get(i).reset();
		}
	}

	public void update(float delta) {
		if (!done) {
			if (!continuous) {
				age += delta;

				// TODO Might have more complex death state...
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

			// No need to update if paused
			if (!paused) {
				for (int i = 0, n = emitters.size; i < n; i++) {
					emitters.get(i).update(delta);
				}
			}
		}
	}

	public void render(SpriteBatch spriteBatch) {
		for (int i = 0, n = emitters.size; i < n; i++) {
			emitters.get(i).render(spriteBatch);
		}
	}

	public void lazyKill() {
		for (int i = 0, n = emitters.size; i < n; i++) {
			emitters.get(i).lazyKill();
		}

		isFinishing = true;
	}

	public void instaKill() {
		for (int i = 0, n = emitters.size; i < n; i++) {
			emitters.get(i).instaKill();
		}

		done = true;
	}

	public ParticleEmitter findEmitter(String name) {
		for (int i = 0, n = emitters.size; i < n; i++) {
			ParticleEmitter emitter = emitters.get(i);
			if (emitter.name.equals(name)) {
				return emitter;
			}
		}
		return null;
	}

	public void setPosition(float x, float y) {
		// Update the effect's position as everything will be derived from that
		position.set(x, y);
		for (int i = 0, n = emitters.size; i < n; i++) {
			emitters.get(i).position.set(x, y);
		}
	}

	public void allowCompletion() {
		for (int i = 0, n = emitters.size; i < n; i++) {
//			emitters.get(i).allowCompletion();
		}
	}

	public boolean isDone() {
		for (int i = 0, n = emitters.size; i < n; i++) {
			ParticleEmitter emitter = emitters.get(i);
			if (!emitter.done) {
				return false;
			}
		}

		return true;
	}
}
