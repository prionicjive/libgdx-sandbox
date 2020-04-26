package com.austinauts.libgdx.modules.particleeffects.screens;

import com.austinauts.libgdx.AustinautsGame;
import com.austinauts.libgdx.common.loaders.ParticleEffectSettings;
import com.austinauts.libgdx.common.particles.ParticleEffect;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Texture;

public class GameScreen extends ScreenAdapter {
	// Reference to main game object
	private final AustinautsGame _game;

	// --------------------
	// Game Entities
	// --------------------
	private ParticleEffect trailEffect; // TODO Better way to store this and look up by name?

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
		ParticleEffectSettings settings = _game.json.fromJson(ParticleEffectSettings.class, Gdx.files.internal(AustinautsGame.CONFIG_EFFECTS_SPIN));
		Texture texToUse = _game.assetManager.get(AustinautsGame.TEXTURE_PARTICLE_TRAIL, Texture.class);
		trailEffect = new ParticleEffect(texToUse, settings);
		trailEffect.reset();
		trailEffect.setPosition(100, 100);
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
		trailEffect.setPosition(Gdx.input.getX(), _game.masterWorldHeight - Gdx.input.getY());
		trailEffect.update(delta);

		// Clear the backbuffer (Dark blue-green)
		Gdx.gl30.glClearColor(0, 0.15f, 0.2f, 1);
		Gdx.gl30.glClear(GL30.GL_COLOR_BUFFER_BIT);

		trailEffect.render(_game.batch);
	}

	@Override
	public void hide() {
		// To dispose or not to dispose
		dispose();
	}

	@Override
	public void dispose() {
	}

	// ------------------------
	// Private methods
	// ------------------------

	private void initializeInputProcessor() {
		// TODO Put InputProcessing in another place?
		Gdx.input.setInputProcessor(new InputAdapter() {
			public boolean keyDown(int key) {
				if (key == Input.Keys.SPACE) {
					trailEffect.paused = !trailEffect.paused;

					return true;
				}

				if (key == Input.Keys.ESCAPE) {
					trailEffect.instaKill();

					return true;
				}

				if (key == Input.Keys.SHIFT_LEFT) {
					trailEffect.lazyKill();
					return true;
				}

				return false;
			}
		});
	}
}
