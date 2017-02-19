package com.austinauts.libgdx.modules.fallthru.screens;

import com.austinauts.libgdx.AustinautsGame;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class GameOverScreen extends ScreenAdapter {
	// Reference to main game object
	private final AustinautsGame _game;

	private int _finalScore;
	private long _elapsedGameTime;
	private boolean newHighScore, newBestTime;

	// --------------------
	// Scratch variables
	// --------------------
	private BitmapFont bigFont;
	private BitmapFont mediumFont;
	private BitmapFont smallFont;

	// Strings for displaying results
	private String gameOverText = "Game Over";
	private String summaryText = "Score: %d\n" +
		"Time: %s\n" +
		"High Score: %d\n" +
		"Best Time: %s";
	private String retryText = "Touch to retry";

	// Locations to render text
	private Vector2 gameOverPos, summaryPos, retryPos;

	public GameOverScreen(final AustinautsGame game, final int finalScore, final long elapsedGameTime) {
		_game = game;
		_finalScore = finalScore;
		_elapsedGameTime = elapsedGameTime;

		// Set up fonts
		bigFont = _game.bigFont;
		mediumFont = _game.mediumFont;
		smallFont = _game.smallFont;

		// Calculate the time string to display
		long remainingGameTime = _elapsedGameTime;

		long minutes = TimeUnit.MILLISECONDS.toMinutes(remainingGameTime);
		remainingGameTime -= TimeUnit.MINUTES.toMillis(minutes);

		long seconds = TimeUnit.MILLISECONDS.toSeconds(remainingGameTime);
		remainingGameTime -= TimeUnit.SECONDS.toMillis(seconds);

		long milliseconds = remainingGameTime;

		String timeStr = String.format(Locale.US, "%02d:%02d.%03d", minutes, seconds, milliseconds);

		// Update high score and best time if need be
		Preferences prefs = Gdx.app.getPreferences("fallthru_records");

		int highScore = prefs.getInteger("highScore");
		if (finalScore > highScore) {
			highScore = finalScore;
			prefs.putInteger("highScore", highScore);
			newHighScore = true;
		}

		String bestTimeStr;
		long bestTime = prefs.getLong("bestTime");
		if (elapsedGameTime > bestTime) {
			bestTime = elapsedGameTime;
			bestTimeStr = timeStr;
			prefs.putLong("bestTime", bestTime);
			newBestTime = true;
		}
		else {
			remainingGameTime = bestTime;

			minutes = TimeUnit.MILLISECONDS.toMinutes(remainingGameTime);
			remainingGameTime -= TimeUnit.MINUTES.toMillis(minutes);

			seconds = TimeUnit.MILLISECONDS.toSeconds(remainingGameTime);
			remainingGameTime -= TimeUnit.SECONDS.toMillis(seconds);

			milliseconds = remainingGameTime;

			bestTimeStr = String.format(Locale.US, "%02d:%02d.%03d", minutes, seconds, milliseconds);
		}

		// Flush the preferences to write the changes
		prefs.flush();

		// Calculate text to display and the bounds

		summaryText = String.format(summaryText, finalScore, timeStr, highScore, bestTimeStr);
		_game.glyphLayout.setText(mediumFont, summaryText);
		summaryPos = new Vector2((_game.masterWorldWidth / 2) - (_game.glyphLayout.width / 2), (_game.masterWorldHeight / 2) + (_game.glyphLayout.height / 2));

		_game.glyphLayout.setText(bigFont, gameOverText);
		gameOverPos = new Vector2((_game.masterWorldWidth / 2) - (_game.glyphLayout.width / 2), summaryPos.y + _game.glyphLayout.height + 3);

		_game.glyphLayout.setText(smallFont, retryText);
		retryPos = new Vector2((_game.masterWorldWidth / 2) - (_game.glyphLayout.width / 2), summaryPos.y - _game.glyphLayout.height - 10);
	}

	@Override
	public void render(float delta) {
		// When just touched or clicked, switch to the game screen
		if (Gdx.input.justTouched()) {
			_game.setScreen(new GameScreen(_game));
		}

		// Clear the backbuffer
		Gdx.gl.glClearColor(0.8f, 0.1f, 0.1f, 1f);
		Gdx.gl.glClear(GL30.GL_COLOR_BUFFER_BIT);

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
		drawTextWithShadow(_game.batch, bigFont, gameOverText, gameOverPos.x, gameOverPos.y);
		drawTextWithShadow(_game.batch, mediumFont, summaryText, summaryPos.x, summaryPos.y);
		drawTextWithShadow(_game.batch, smallFont, retryText, retryPos.x, retryPos.y);
	}

	// TODO Consider breaking out
	private void drawTextWithShadow(SpriteBatch batch, BitmapFont font, String text, float x, float y) {
		font.setColor(Color.BLACK);
		font.draw(batch, text, x + 1, y - 1);

		font.setColor(Color.WHITE);
		font.draw(batch, text, x, y);
	}
}
