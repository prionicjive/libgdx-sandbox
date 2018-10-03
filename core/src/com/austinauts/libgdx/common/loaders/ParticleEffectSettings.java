package com.austinauts.libgdx.common.loaders;

import com.austinauts.libgdx.common.particles.ParticleEmitterTemplate;
import com.badlogic.gdx.utils.Array;

public class ParticleEffectSettings {
	public String name;
	public boolean continuous;
	public boolean instaKill;
	public float ttl;
	public float timeScale;

	public Array<ParticleEmitterTemplate> emitterTemplates;
}
