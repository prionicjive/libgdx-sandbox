package com.austinauts.libgdx.modules.fallthru.screens;

import com.austinauts.libgdx.AustinautsGame;
import com.austinauts.libgdx.modules.fallthru.entities.Player;
import com.austinauts.libgdx.modules.fallthru.entities.Row;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import com.badlogic.gdx.utils.TimeUtils;

import java.util.Iterator;

public class GameScreen extends ScreenAdapter {
	// TODO Again, JSON?
	private final int BLOCK_SIZE = 16; // TODO Need to unify this with how big we elect the tile texture to be
	private final int MAX_ROWS = 100;  // TODO Could be put in a better location that is centralized
	private final int PADDING_BETWEEN_ROWS = 50; // TODO Could be put in a better location that is centralized
	private final float GRAVITY_PER_SECOND_Y = -12f; // TODO Could be put in a better location that is centralized
	private final float SPEED_INCREASE = 2f;

	// Reference to main game object
	private final AustinautsGame _game;

	// Timer and speed
	private long gameStartTime;
	private long elapsedGameTime;

	private long intervalStartTime;
	private long elapsedIntervalTime;

	private long intervalInSeconds = 1L;
	private float rowSpeed = 64f;

	// TODO Make GRAVITY affect velocity for proper acceleration
	private float playerGravity = 0f;

	// --------------------
	// Game Entities
	// --------------------
	private Player player;
	private Array<Row> activeRows;
	private Pool<Row> rowPool;

	// --------------------
	// Scratch variables
	// --------------------
	private Vector3 touchPos = new Vector3();
	private float playerPrevX; // Used to track the player's previous X

	private BitmapFont bigFont;
	private BitmapFont smallFont;

	// Score related
	private int score = 0;
	boolean eligibleForScore = false;

	public GameScreen(final AustinautsGame game) {
		_game = game;

		// Start the timer
		gameStartTime = TimeUtils.millis();
		intervalStartTime = gameStartTime;

		// -------------------------------------
		// Set up scratch variables
		// -------------------------------------
		bigFont = _game.fontMap.get("munrosmall_30");
		smallFont = _game.fontMap.get("munrosmall_10");

		// -------------------------------------
		// Set up the game entities
		// -------------------------------------
		player = new Player(_game);
		player.setPosition((_game.masterWorldWidth / 2) - (BLOCK_SIZE / 2), _game.masterWorldHeight - BLOCK_SIZE); // Start at the top of the screen, centered
		player.setVelocity(1f, 0f);
		player.setSpeed(250f);

		// Set up the rows
		rowPool = new Pool<Row>(MAX_ROWS, MAX_ROWS) {
			@Override
			protected Row newObject() {
				return new Row(_game);
			}
		};

		activeRows = new Array<>(true, 100);

		// Create the first row that appears offscreen
		Row newRow = rowPool.obtain();
		newRow.init(BLOCK_SIZE, 2, _game.masterWorldWidth, 7, 0);
		activeRows.add(newRow);
	}

	@Override
	public void render(float delta) {
		// Outline of game loop:
		//
		// 1) Update camera
		// 2) Handle input and update entities
		// 3) Process collision / physics
		// 4) Render

		// Check the timer and properly boost the row speed
		checkTimer();

		// Update game entities
		updateEntities(delta);

		// Process collision / physics
		processCollision();

		// Clear the backbuffer
		Gdx.gl.glClearColor(.1f, .1f, .1f, 1);
		Gdx.gl.glClear(GL30.GL_COLOR_BUFFER_BIT);

		_game.batch.begin();
		{
			// Draw the player
			player.draw(_game.batch);

			for (Row currRow : activeRows) {
				// Render our game entities
				currRow.render();
			}

			// Lastly, draw the score

			bigFont.setColor(Color.BLACK);
			smallFont.setColor(Color.BLACK);
			bigFont.draw(_game.batch, Integer.toString(score), 5 + 1, _game.masterWorldHeight + 5 - 1);
			smallFont.draw(_game.batch, "Score", 5 + 1, _game.masterWorldHeight - 1);

			bigFont.setColor(Color.WHITE);
			smallFont.setColor(Color.WHITE);
			bigFont.draw(_game.batch, Integer.toString(score), 5, _game.masterWorldHeight + 5);
			smallFont.draw(_game.batch, "Score", 5, _game.masterWorldHeight);
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

	// ------------------------
	// Private methods
	// ------------------------

	private void checkTimer() {
		// Capture the total time since the beginning of play
		long currTime = TimeUtils.millis();
		elapsedGameTime = currTime - gameStartTime;

		// Capture the time since the last interval
		elapsedIntervalTime = currTime - intervalStartTime;

		// If we have arrived at or past the interval, reset the timer and boost the speed
		if (elapsedIntervalTime >= intervalInSeconds * 1000L) {
			intervalStartTime = currTime;
			elapsedIntervalTime -= (intervalInSeconds * 1000L);

			rowSpeed += SPEED_INCREASE;
		}
	}

	private void updateEntities(float delta) {
		// TODO Check this method for areas of optimization

		// Be sure to track what the player's previous X was
		playerPrevX = player.getX();

		float pointerX = 0;
		boolean isTouched = Gdx.input.isTouched();

		// See if the user is clicking / touching at all
		if (isTouched) {
			touchPos.x = Gdx.input.getX();
			touchPos.y = Gdx.input.getY();

			// "Unproject" from the screen to world position, taking the viewport dimensions into account
			_game.camera.unproject(touchPos, _game.viewport.getScreenX(), _game.viewport.getScreenY(), _game.viewport.getScreenWidth(), _game.viewport.getScreenHeight());
			pointerX = touchPos.x;

			// Adjust the velocity of the player based on where the click/touch is
			if (pointerX < (player.getX() + player.getOriginX())) {
				player.setVelocity(-1f, 0f);
			}
			else {
				player.setVelocity(1f, 0f);
			}
		}
		else {
			// If not clicked / touched at all, set the velocity to 0
			player.setVelocity(0f, 0f);
		}

		// Update the player
		playerGravity += (GRAVITY_PER_SECOND_Y * delta);
		float updatedX = player.getX() + (player.getVelocity().x * player.getSpeed() * delta);
		float updatedY = player.getY() + playerGravity;

		if (isTouched) {
			// SPECIAL CASE: If the player's center / origin crosses over where the click / touch is detected in this frame,
			//                   lock the player over it. This is to prevent jittering when a click / touch is held.
			float startX = player.getX() + player.getOriginX();
			float endX = updatedX + player.getOriginX();

			// Make sure the start is smaller than the end
			if (startX > endX) {
				startX = updatedX + player.getOriginX();
				endX = player.getX() + player.getOriginX();
			}

			if (pointerX >= startX && pointerX <= endX) {
				updatedX = pointerX - player.getOriginX();
			}
		}

		player.setPosition(updatedX, updatedY);

		// Check the horizontal bounds of the player
		if (player.getX() < 0) {
			player.setX(0f);
		}
		else if (player.getX() > _game.masterWorldWidth - BLOCK_SIZE) {
			player.setX(_game.masterWorldWidth - BLOCK_SIZE);
		}

		// Update rows and gaps based on how fast the scrolling speed is
		Iterator<Row> rowIterator = activeRows.iterator();

		while (rowIterator.hasNext()) {
			Row currRow = rowIterator.next();

			for (Rectangle currGap : currRow.getGaps()) {
				currGap.setY(currGap.getY() + (rowSpeed * delta));
			}

			currRow.getBoundingRectangle().setY(currRow.getBoundingRectangle().getY() + (rowSpeed * delta));

			// If the current row is off screen, remove it
			if (currRow.getBoundingRectangle().getY() > _game.masterWorldHeight) {
				rowPool.free(currRow);
				rowIterator.remove();
			}
		}

		// If the most recently created row (The one closest to the bottom) is past the padding point, spawn an new
		if (activeRows.size != 0 && activeRows.get(activeRows.size - 1).getBoundingRectangle().getY() > PADDING_BETWEEN_ROWS) {
			Row newRow = rowPool.obtain();
			newRow.init(BLOCK_SIZE, 2, _game.masterWorldWidth, 7, -BLOCK_SIZE);
			activeRows.add(newRow);
		}
	}

	private void processCollision() {
		Rectangle possibleBoundingRect;

		boolean noCollision = true;

		// Check until we find a row that are colliding with
		for (Row currRow : activeRows) {
			possibleBoundingRect = currRow.getBoundingRectangle();

			// If we are overlapping the overall bounding rect, hunt for a gap that we might be falling through
			if (player.getBoundingRectangle().overlaps(possibleBoundingRect)) {
				// See if the player is already falling thru
				if (player.IsFallingThru) {
					// Make sure the player is within the gap
					if (player.getX() < player.gapFallingThru.x) {
						player.setX(player.gapFallingThru.x);
					}
					else if (player.getX() > (player.gapFallingThru.x + player.gapFallingThru.width) - BLOCK_SIZE) {
						player.setX((player.gapFallingThru.x + player.gapFallingThru.width) - BLOCK_SIZE);
					}

					// Update the score only if we are eligible
					if (eligibleForScore && player.getBoundingRectangle().overlaps(player.gapFallingThru)) {
						score += 10;
						eligibleForScore = false;
					}
				}
				else {
					// Check each gap to see if the player is falling thru
					for (Rectangle currGap : currRow.getGaps()) {
						// Based on the player's velocity, determine if they are in a location that would say there are falling thru the current gap
						if ((player.getVelocity().x > 0f && playerPrevX < currGap.x && player.getX() >= currGap.x) ||
							(player.getVelocity().x < 0f && playerPrevX + BLOCK_SIZE > (currGap.x + currGap.width) && player.getX() + BLOCK_SIZE <= (currGap.x + currGap.width)) ||
							(player.getX() >= currGap.x && player.getX() + BLOCK_SIZE <= (currGap.x + currGap.width))) {
							// If we have determined the player is falling thru, no need to check any other gap
							player.IsFallingThru = true;
							player.gapFallingThru = currGap;

							break;
						}
					}

					// Correct the Y position if the player is not falling thru
					if (!player.IsFallingThru) {
						player.setY(possibleBoundingRect.getY() + possibleBoundingRect.getHeight());
						playerGravity = 0f; // TODO Probably merge into player class
					}
				}

				// We make the assumption that we can only be colliding with one row at a time, so we will break out of our loop
				noCollision = false;
				break;
			}
		}

		// If we haven't collided with any row, we KNOW that we can't be falling thru
		if (noCollision) {
			player.IsFallingThru = false;
			player.gapFallingThru = null;
			eligibleForScore = true;
		}

		// Lastly, keep the player within the screen and see if they got "crushed"
		if (player.getY() < 0) {
			player.setY(0f);
			playerGravity = 0f; // TODO Probably merge into player class
		}
		else if (player.getY() > _game.masterWorldHeight - BLOCK_SIZE) {
			// Switch to the game over screen
			_game.setScreen(new GameOverScreen(_game, score, elapsedGameTime));
		}
	}


}




