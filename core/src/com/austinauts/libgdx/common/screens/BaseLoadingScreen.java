package com.austinauts.libgdx.common.screens;

import com.austinauts.libgdx.AustinautsGame;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL30;

import java.util.Locale;

public abstract class BaseLoadingScreen extends ScreenAdapter {
	private final static String LOADING_TEXT = "Loading... %d%% Complete";

	protected final AustinautsGame _game;

	protected BaseLoadingScreen(final AustinautsGame game) {
		_game = game;

		// Set the sprite batch to use the camera's combined projection/view matrix
		_game.batch.setProjectionMatrix(_game.camera.combined);
	}

	@Override
	public void render(float delta) {
		// Continuously call update until all assets are loaded, then switch to the Main Menu
		if (_game.assetManager.update()) {
			switchScreen();
		}

		// Clear the backbuffer
		Gdx.gl.glClearColor(.1f, .1f, .1f, 1);
		Gdx.gl.glClear(GL30.GL_COLOR_BUFFER_BIT);

		// Set the sprite batch to use the camera's combined projection/view matrix
		_game.batch.setProjectionMatrix(_game.camera.combined);

		_game.batch.begin();
		_game.smallFont.draw(_game.batch, String.format(Locale.US, LOADING_TEXT, Float.valueOf(_game.assetManager.getProgress()).intValue()), 2, 2);
		_game.batch.end();
	}

	public abstract void switchScreen();

	@Override
	public void hide() {
		// To dispose or not to dispose
		dispose();
	}

	@Override
	public void dispose() {
		// TODO Do any custom disposal here
	}
}
