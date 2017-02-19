package com.austinauts.libgdx.modules.quadtree.screens;

import com.austinauts.libgdx.AustinautsGame;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.austinauts.libgdx.common.collision.QuadTreeNode;
import com.austinauts.libgdx.common.entities.SpriteEntity;
import com.austinauts.libgdx.common.utils.logging.PeriodicLogger;

public class GameScreen extends ScreenAdapter {
	// Reference to main game object
	private final AustinautsGame _game;

	// --------------------
	// Game Entities
	// --------------------
	private Array<SpriteEntity> blockArray;
	private final int NUM_BLOCKS = 1000;

	// --------------------
	// Scratch variables
	// --------------------
	private Vector3 clickPos = new Vector3();
	private int screenWidth; // TODO Consider moving elsewhere
	private int screenHeight; // TODO Consider moving elsewhere

	// TODO Consider how to abstract these for easier use by Periodic Logger
	private int numCollisionChecksThisFrame;
	private int minNumCollisionChecks = Integer.MAX_VALUE;
	private int maxNumCollisionChecks;
	private int numCollisionPerSecond;
	private int updatesPerSecond;

	// ------------------------
	// Periodic logger
	// ------------------------
	private PeriodicLogger periodicLogger;

	// -----------------------------------
	// Quadtree related
	// -----------------------------------
	private QuadTreeNode quadTree;
	private Array<SpriteEntity> entitiesToCheck;

	public GameScreen(final AustinautsGame game) {
		_game = game;

		// -------------------------------------
		// Set up scratch variables
		// -------------------------------------
		screenWidth = Gdx.graphics.getWidth();
		screenHeight = Gdx.graphics.getHeight();

		// -------------------------------------
		// Set up the game entities
		// -------------------------------------
		blockArray = new Array<>(true, NUM_BLOCKS);

		for (int i = 0; i < NUM_BLOCKS; i++) {
			blockArray.add(generateValidBlock());
		}

		// Set up the periodic logger and its logging actions
		periodicLogger = new PeriodicLogger();
		periodicLogger.addLoggingAction(() -> Gdx.app.log("FPSLogger", "fps: " + Gdx.graphics.getFramesPerSecond()));
		periodicLogger.addLoggingAction(() -> Gdx.app.log("CollisionLogger", "Min Collision Checks: " + minNumCollisionChecks));
		periodicLogger.addLoggingAction(() -> Gdx.app.log("CollisionLogger", "Max Collision Checks: " + maxNumCollisionChecks));
		periodicLogger.addLoggingAction(() -> {
			Gdx.app.log("CollisionLogger", "Average Collision Checks Per Frame over past second: " + (numCollisionPerSecond / updatesPerSecond));
			numCollisionPerSecond = 0;
			updatesPerSecond = 0;
		});

		// Set up the quad tree
		quadTree = new QuadTreeNode(0, new Rectangle(0, 0, screenWidth, screenHeight));
		entitiesToCheck = new Array<>(true, QuadTreeNode.MAX_ENTITIES);
	}

	// TODO Probably can be added elsewhere
	private SpriteEntity generateValidBlock() {
		SpriteEntity block = new SpriteEntity(_game.assetManager.get(AustinautsGame.TEXTURE_BLOCK, Texture.class));
		block.setSize(4, 4);
		block.setPosition(MathUtils.random(0, screenWidth - block.getWidth()), MathUtils.random(0, screenHeight - block.getHeight()));
		block.setVelocity(MathUtils.random(-1.0f, 1.0f), MathUtils.random(-1.0f, 1.0f));
		block.setSpeed(MathUtils.random(5, 50));

		return block;
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

		// Clear the backbuffer (Dark blue-green)
		Gdx.gl30.glClearColor(0, 0.15f, 0.2f, 1);
		Gdx.gl30.glClear(GL30.GL_COLOR_BUFFER_BIT);

		updateEntities(delta);

		_game.batch.begin();

		for (SpriteEntity currBlock : blockArray) {
			// Render our game entities
			_game.batch.setColor(currBlock.getColor());
			// Render our game entities
			currBlock.draw(_game.batch);

			// Set the curr block back to white
			currBlock.setColor(Color.WHITE);
			_game.batch.setColor(currBlock.getColor());
		}

		_game.batch.end();

		// Now, render the debug lines for the quad tree
		_game.shapeRenderer.setProjectionMatrix(_game.camera.combined);

		_game.shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
		quadTree.render(_game.shapeRenderer);
		_game.shapeRenderer.end();

		// Lastly, attempt the periodic log
		periodicLogger.log();
	}

	// ------------------------
	// Private methods
	// ------------------------

	private void updateEntities(float delta) {
		// Reset the number of collision checks for this frame
		numCollisionChecksThisFrame = 0;

		// Clear out the quadtree
		quadTree.clear();

		for (int i = 0; i < blockArray.size; i++) {
			// Fetch the current block
			SpriteEntity currBlock = blockArray.get(i);

			// Update the velocity
			float updatedX = currBlock.getX() + (currBlock.getVelocity().x * currBlock.getSpeed() * delta);
			float updatedY = currBlock.getY() + (currBlock.getVelocity().y * currBlock.getSpeed() * delta);
			currBlock.setPosition(updatedX, updatedY);

			// Keep the entity within screen space
			if (currBlock.getX() < 0) {
				currBlock.setX(0);

				// Negate x component of velocity
				currBlock.negateVelocityX();
			}
			else if (currBlock.getX() > screenWidth - currBlock.getWidth()) {
				currBlock.setX(screenWidth - currBlock.getWidth());

				// Negate x component of velocity
				currBlock.negateVelocityX();
			}

			if (currBlock.getY() < 0) {
				currBlock.setY(0);

				// Negate y component of velocity
				currBlock.negateVelocityY();
			}
			else if (currBlock.getY() > screenHeight - currBlock.getHeight()) {
				currBlock.setY(screenHeight - currBlock.getHeight());

				// Negate y component of velocity
				currBlock.negateVelocityY();
			}

			// Now that we've updated all the objects based on screen bounds, insert them into the quad tree
			quadTree.insert(currBlock);
		}

		for (int i = 0; i < blockArray.size; i++) {
			// Fetch the current block
			SpriteEntity currBlock = blockArray.get(i);

			// Clear out the entities to check
			entitiesToCheck.clear();

			// Retrieve the entities we might be able to compare against
			quadTree.retrieve(entitiesToCheck, currBlock);

			// Check the blocks
			for (SpriteEntity blockToTest : entitiesToCheck) {
				// SPECIAL CASE: No need to check against our self
				if (currBlock == blockToTest) {
					continue;
				}

				// If the blocks are colliding (Overlapping), tint them as red
				if (currBlock.getBoundingRectangle().overlaps(blockToTest.getBoundingRectangle())) {
					currBlock.setColor(Color.RED);
					blockToTest.setColor(Color.RED);
				}

				// Increment the number of collision checks
				numCollisionChecksThisFrame++;
			}
		}

		// Update the minimum and maximum number of collisions detected
		if (numCollisionChecksThisFrame < minNumCollisionChecks) {
			minNumCollisionChecks = numCollisionChecksThisFrame;
		}

		if (numCollisionChecksThisFrame > maxNumCollisionChecks) {
			maxNumCollisionChecks = numCollisionChecksThisFrame;
		}

		// Lastly, tick the number of collisions per second and updates
		numCollisionPerSecond += numCollisionChecksThisFrame;
		updatesPerSecond++;
	}
}


