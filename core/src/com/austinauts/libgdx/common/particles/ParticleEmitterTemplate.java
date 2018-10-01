package com.austinauts.libgdx.common.particles;

import com.badlogic.gdx.math.Vector2;

public class ParticleEmitterTemplate {
	public String name;
	public int maxActiveParticles;
	public int emitPerSecond;
	public boolean continuous;
	public boolean instaKill;
	public Vector2 emitDirection;
	public float emitSpreadAngle;
	public float emitRotateAnglePerSecond;
	public int ttl;

	public ParticleTemplate particleTemplate;
}
