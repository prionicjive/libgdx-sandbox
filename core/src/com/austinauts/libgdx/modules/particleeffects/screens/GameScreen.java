package com.austinauts.libgdx.modules.particleeffects.screens;

import com.austinauts.libgdx.AustinautsGame;
import com.austinauts.libgdx.common.loaders.ParticleEmitterSettings;
import com.austinauts.libgdx.common.particles.ParticleEmitter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.ParticleEffect;

public class GameScreen extends ScreenAdapter  {
	// Reference to main game object
	private final AustinautsGame _game;

	// --------------------
	// Game Entities
	// --------------------
	private ParticleEffect defaultEffect;
	private ParticleEmitter trailEmitter;

	// --------------------
	// Scratch variables
	// --------------------

	public GameScreen(final AustinautsGame game) {
		_game = game;

		// ---------------------------------
		// Set up systems
		// --------------------------------
		initializeInputProcessor();

		// -------------------------------------
		// Set up scratch variables
		// -------------------------------------

		// -------------------------------------
		// Set up the game entities
		// -------------------------------------
		defaultEffect = _game.assetManager.get(AustinautsGame.PARTICLE_EFFECT_DEFAULT);
		defaultEffect.start();
		defaultEffect.setPosition(100,  100);

		ParticleEmitterSettings settings = _game.json.fromJson(ParticleEmitterSettings.class, Gdx.files.internal(AustinautsGame.CONFIG_PARTICLE_TRAIL));
		trailEmitter = new ParticleEmitter(_game.assetManager.get(AustinautsGame.TEXTURE_PARTICLE, Texture.class), settings);
		trailEmitter.reset();
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
		trailEmitter.position.set(Gdx.input.getX(), _game.masterWorldHeight - Gdx.input.getY());
		trailEmitter.update(delta);

		// Clear the backbuffer (Dark blue-green)
		Gdx.gl30.glClearColor(0, 0.15f, 0.2f, 1);
		Gdx.gl30.glClear(GL30.GL_COLOR_BUFFER_BIT);

		trailEmitter.render(_game.batch);

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

	private void initializeInputProcessor() {
		// TODO Put InputProcessing in another place?
		Gdx.input.setInputProcessor(new InputAdapter() {
			public boolean keyDown(int key) {
				if (key == Input.Keys.SPACE) {
					trailEmitter.paused = !trailEmitter.paused;

					return true;
				}

				return false;
			}
		});
	}
}
