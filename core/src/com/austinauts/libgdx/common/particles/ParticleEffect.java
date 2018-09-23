package com.austinauts.libgdx.common.particles;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.utils.Array;

public class ParticleEffect {
	private final Array<ParticleEmitter> emitters;

	public ParticleEffect () {
		emitters = new Array(8);
	}

	public void start () {
		for (int i = 0, n = emitters.size; i < n; i++) {
			// emitters.get(i).start();
		}
	}

	public void reset (boolean resetScaling){
		for (int i = 0, n = emitters.size; i < n; i++)
			emitters.get(i).reset();
	}

	public void update (float delta) {
		for (int i = 0, n = emitters.size; i < n; i++)
			emitters.get(i).update(delta);
	}

	public void draw (Batch spriteBatch) {
		for (int i = 0, n = emitters.size; i < n; i++) {
			//emitters.get(i).draw(spriteBatch);
		}
	}

	public ParticleEmitter findEmitter (String name) {
		for (int i = 0, n = emitters.size; i < n; i++) {
			//ParticleEmitter emitter = emitters.get(i);
			//if (emitter.getName().equals(name)) return emitter;
		}
		return null;
	}

	public void setPosition (float x, float y) {
		for (int i = 0, n = emitters.size; i < n; i++) {
			//emitters.get(i).setPosition(x, y);
		}
	}

	public void allowCompletion () {
		for (int i = 0, n = emitters.size; i < n; i++) {
//			emitters.get(i).allowCompletion();
		}
	}

	public boolean isComplete () {
		for (int i = 0, n = emitters.size; i < n; i++) {
//			ParticleEmitter emitter = emitters.get(i);
//			if (!emitter.isComplete()) return false;
		}
		return true;
	}
}
