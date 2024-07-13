package com.austinauts.libgdx.modules.whammyball.entities;

public class BallUserData {
	public Ball correspondingBall;
	public int health;

	public BallUserData(Ball correspondingBall, int health) {
		this.correspondingBall = correspondingBall;
		this.health = health;
	}
}
