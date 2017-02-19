package com.austinauts.libgdx.common.screens;

import com.austinauts.libgdx.AustinautsGame;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.g2d.BitmapFont;

public abstract class BaseMainMenuScreen extends ScreenAdapter {
	// Text for the main menu
	private final String _welcomeText;
	private final String _clickToBeginText;

	// Reference to main game object
	protected final AustinautsGame _game;

	// --------------------
	// Scratch variables
	// --------------------
	private BitmapFont bigFont;
	private BitmapFont smallFont;

	public BaseMainMenuScreen(final AustinautsGame game, final String welcomeText, final String clickToBeginText) {
		_game = game;
		bigFont = _game.bigFont;
		smallFont = _game.smallFont;

		_welcomeText = welcomeText;
		_clickToBeginText = clickToBeginText;

		// Set the sprite batch to use the camera's combined projection/view matrix
		_game.batch.setProjectionMatrix(_game.camera.combined);
	}

	@Override
	public void render(float delta) {
		// When just touched or clicked, switch to the main game screen
		if (Gdx.input.justTouched()) {
			switchScreen();
		}

		// Clear the backbuffer
		Gdx.gl.glClearColor(.1f, .1f, .1f, 1);
		Gdx.gl.glClear(GL30.GL_COLOR_BUFFER_BIT);

		_game.batch.begin();
		{
			bigFont.setColor(Color.BLACK);
			_game.glyphLayout.setText(bigFont, _welcomeText);
			bigFont.draw(_game.batch, _welcomeText, (_game.masterWorldWidth / 2) - (_game.glyphLayout.width / 2) + 1, _game.masterWorldHeight / 2 + _game.glyphLayout.height - 1);
			bigFont.setColor(Color.WHITE);
			bigFont.draw(_game.batch, _welcomeText, (_game.masterWorldWidth / 2) - (_game.glyphLayout.width / 2), _game.masterWorldHeight / 2 + _game.glyphLayout.height);

			smallFont.setColor(Color.BLACK);
			_game.glyphLayout.setText(smallFont, _clickToBeginText);
			smallFont.draw(_game.batch, _clickToBeginText, (_game.masterWorldWidth / 2) - (_game.glyphLayout.width / 2) + 1, _game.masterWorldHeight / 2 - 1);
			smallFont.setColor(Color.WHITE);
			smallFont.draw(_game.batch, _clickToBeginText, (_game.masterWorldWidth / 2) - (_game.glyphLayout.width / 2), _game.masterWorldHeight / 2);
		}
		_game.batch.end();
	}

	protected abstract void switchScreen();

	@Override
	public void hide() {

		// To dispose or not to dispose
		dispose();
	}

	@Override
	public void dispose() {
		// TODO Any custom disposing
	}
}
