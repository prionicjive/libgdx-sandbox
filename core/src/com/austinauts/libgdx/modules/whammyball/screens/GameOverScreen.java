package com.austinauts.libgdx.modules.whammyball.screens;

import com.austinauts.libgdx.AustinautsGame;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;

public class GameOverScreen extends ScreenAdapter {
	// Reference to main game object
	private final AustinautsGame _game;

	// --------------------
	// Scratch variables
	// --------------------
	private BitmapFont bigFont;
	private BitmapFont smallFont;

	// Strings for displaying results
	private String gameOverText = "Game Over";
	private String continueText = "Touch to continue";

	// Locations to render text
	private Vector2 completePos, continuePos;

	public GameOverScreen(final AustinautsGame game) {
		_game = game;

		// Set up fonts
		bigFont = _game.bigFont;
		smallFont = _game.smallFont;

		_game.glyphLayout.setText(bigFont, gameOverText);
		completePos = new Vector2((_game.virtualScreenSize.width / 2f) - (_game.glyphLayout.width / 2) + 1, _game.virtualScreenSize.height / 2f + _game.glyphLayout.height - 1);

		_game.glyphLayout.setText(smallFont, continueText);
		continuePos = new Vector2((_game.virtualScreenSize.width / 2f) - (_game.glyphLayout.width / 2), _game.virtualScreenSize.height / 2f);
	}

	@Override
	public void render(float delta) {
		// When just touched or clicked, switch to the game screen
		// TODO Does this need to go to an InputProcessor?
		if (Gdx.input.justTouched()) {
			_game.setScreen(new GameScreen(_game));
		}

		// Clear the backbuffer
		Gdx.gl.glClearColor(0.9f, 0.3f, 0.2f, 1f);
		Gdx.gl.glClear(GL30.GL_COLOR_BUFFER_BIT);

		_game.viewport.setWorldSize(_game.virtualScreenSize.width, _game.virtualScreenSize.height);
		_game.viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
		_game.batch.setProjectionMatrix(_game.camera.combined);

		_game.batch.begin();
		{
			drawText();
		}
		_game.batch.end();
	}

	@Override
	public void hide() {

		// To dispose or not to dispose
		dispose();
	}

	@Override
	public void dispose() {
	}

	// ---------------------------
	// Private methods
	// ---------------------------

	private void drawText() {
		drawTextWithShadow(_game.batch, bigFont, gameOverText, completePos.x, completePos.y);
		drawTextWithShadow(_game.batch, smallFont, continueText, continuePos.x, continuePos.y);
	}

	// TODO Consider breaking out
	private void drawTextWithShadow(SpriteBatch batch, BitmapFont font, String text, float x, float y) {
		font.setColor(Color.BLACK);
		font.draw(batch, text, x + 1, y - 1);

		font.setColor(Color.WHITE);
		font.draw(batch, text, x, y);
	}
}
