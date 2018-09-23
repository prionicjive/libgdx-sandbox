package com.austinauts.libgdx.modules.particleeffects.screens;

import com.austinauts.libgdx.AustinautsGame;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.g2d.ParticleEffect;

public class GameScreen extends ScreenAdapter  {
	// Reference to main game object
	private final AustinautsGame _game;

	// --------------------
	// Game Entities
	// --------------------
	private ParticleEffect defaultEffect;

	// --------------------
	// Scratch variables
	// --------------------

	public GameScreen(final AustinautsGame game) {
		_game = game;

		// -------------------------------------
		// Set up scratch variables
		// -------------------------------------

		// -------------------------------------
		// Set up the game entities
		// -------------------------------------
		defaultEffect = _game.assetManager.get(AustinautsGame.PARTICLE_EFFECT_DEFAULT);
		defaultEffect.start();
		defaultEffect.setPosition(100,  100);
	}

	@Override
	public void render(float delta) {
		// TODO Flesh out game loop as needed

		// Outline of game loop:
		//
		// 1) Update camera
		// 2) Handle input
		// 3) Update entities
		// 4) Process collision / physics
		// 5) Render

		_game.camera.update();

		// TODO Update entities

		// Clear the backbuffer (Dark blue-green)
		Gdx.gl30.glClearColor(0, 0.15f, 0.2f, 1);
		Gdx.gl30.glClear(GL30.GL_COLOR_BUFFER_BIT);

		_game.batch.begin();
		{
			// TODO Render particles
			defaultEffect.draw(_game.batch, delta);
		}
		_game.batch.end();
	}

	// ------------------------
	// Private methods
	// ------------------------
}
