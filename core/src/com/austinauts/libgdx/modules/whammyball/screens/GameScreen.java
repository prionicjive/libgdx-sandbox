package com.austinauts.libgdx.modules.whammyball.screens;

import com.austinauts.libgdx.AustinautsGame;
import com.austinauts.libgdx.common.utils.DisposalHelper;
import com.austinauts.libgdx.modules.whammyball.entities.Ball;
import com.austinauts.libgdx.modules.whammyball.entities.BallUserData;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.ContactListener;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.Manifold;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Queue;
import com.badlogic.gdx.utils.TimeUtils;

public class GameScreen extends ScreenAdapter {
	// TODO Again, JSON?
	private final int PIXELS_PER_METER = 16;
	private final float GRAVITY_PER_SECOND_Y = 0f; //-10f;
	private final float STRENGTH_OF_FORCE = 5f;
	private final float STRENGTH_OF_IMPULSE = 400f;
	// Reference to main game object
	private final AustinautsGame _game;
	private final int CHANCE_OF_TILE = 0; // TODO Make all of these clearer
	private float TIME_STEP = 1 / 60f;
	private int VELOCITY_ITERATIONS = 8;
	private int POSITION_ITERATIONS = 3;
	// Timer and speed
	private long gameStartTime;
	private long elapsedGameTime;
	private long intervalStartTime;
	private long elapsedIntervalTime;
	private long intervalInSeconds = 1L;
	private float accumulator = 0f;
	// TODO Better way to store the most recent direction to the player?
	private Vector2 dirToMouse = new Vector2(); // Initializing for scratch purposes
	private Vector2 scratchVec2d = new Vector2();
	private boolean hasMouseMoved = false;

	// --------------------
	// Game Entities
	// --------------------
	private boolean[][] map;
	private int numCols;
	private int numRows;
	private Rectangle deathZoneRect;
	private BodyDef deathZoneBodyDef;
	private Body deathZoneBody;
	private PolygonShape deathZoneBox;
	private boolean insideDeathZone = false;
	private boolean showDebugger = false;

	// TODO How to better define the player?
	private Ball activeBall;
	private Array<Ball> balls = new Array<>();
	private Vector2 spawnPoint;
	private Queue<Ball> ballsToCleanUp;

	private Texture ballTexture;
	private float growthPerSec = 8f;
	private boolean readyToFire = true;
	private final String TEXT_READY = "Ready!!!";

	private Texture tileTexture;

	// TODO Need to keep track of these?
	private BodyDef topWallBodyDef;
	private Body topWallBody;
	private PolygonShape topWallBox;

	private BodyDef bottomWallBodyDef;
	private Body bottomWallBody;
	private PolygonShape bottomWallBox;

	private BodyDef leftWallBodyDef;
	private Body leftWallBody;
	private PolygonShape leftWallBox;

	private BodyDef rightWallBodyDef;
	private Body rightWallBody;
	private PolygonShape rightWallBox;

	// --------------------
	// Scratch variables
	// --------------------

	// TODO Just scratch... should go elsewhere?
	private Vector3 touchPos = new Vector3();

	public GameScreen(final AustinautsGame game) {
		_game = game;

		// Start the timer
		gameStartTime = TimeUtils.millis();
		intervalStartTime = gameStartTime;

		// -------------------------------------
		// Set up the game entities
		// -------------------------------------

		// Set up the spawn point
		spawnPoint = new Vector2(10, worldToViewport(_game.virtualScreenSize.height / 2f));

		// Set up the death zone rect (All in viewport / BOX2D coords)
		deathZoneRect = new Rectangle(1, 1, 10, worldToViewport(_game.virtualScreenSize.height) - 2);

		// Set up the viewport to play well with Box2D
		numCols = _game.virtualScreenSize.width / PIXELS_PER_METER;
		numRows = _game.virtualScreenSize.height / PIXELS_PER_METER;
		_game.viewport.setWorldSize(numCols, numRows);
		_game.viewport.apply(true);

		// Set the sprite batch to use the camera's combined projection/view matrix
		_game.batch.setProjectionMatrix(_game.camera.combined);

		// Randomly fill map
		map = new boolean[numRows][numCols];
		fillMap();

		// Set up Box2D elements
		initializeBox2dElements();

		// Now that we have the physics, let's build the walls
		reinitializeMapPhysics();

		// Initialize input
		initializeInputProcessor();
	}

	@Override
	public void render(float delta) {
		// Outline of game loop:
		//
		// 1) Update camera
		// 2) Handle input and update entities
		// 3) Process/step physics
		// 4) Render

		// Check the timer and properly boost the row speed
		checkTimer();

		// Update game entities
		updateEntities(delta);

		doPhysicsStep(delta);

		// Clear the backbuffer
		Gdx.gl.glClearColor(.1f, .1f, .1f, 1);
		Gdx.gl.glClear(GL30.GL_COLOR_BUFFER_BIT);

		// Render the player, tiles and the endpoint (At a world size in terms of numCols and numRows
		_game.viewport.setWorldSize(numCols, numRows);
		_game.viewport.apply(true);

		// TODO Doing this is critical. Does this have to be done every time, everywhere?
		_game.batch.setProjectionMatrix(_game.camera.combined);

		_game.shapeRenderer.setProjectionMatrix(_game.camera.combined);
		_game.shapeRenderer.setColor(0.9f, 0.3f, 0.2f, 1f);
		_game.shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
		_game.shapeRenderer.rect(deathZoneRect.x, deathZoneRect.y, deathZoneRect.width, deathZoneRect.height);
		_game.shapeRenderer.end();

		if (showDebugger) {
			// Do some debug drawing for Box2D
			_game.box2DDebugRenderer.render(_game.world, _game.camera.combined);
		} else {
			_game.batch.begin();
			{
				_game.batch.setColor(Color.WHITE);
				// Draw the active ball
				if (activeBall != null) {
					_game.batch.setColor(Color.TEAL);

					_game.batch.draw(
							activeBall.texture,
							activeBall.body.getPosition().x - activeBall.radius,
							activeBall.body.getPosition().y - activeBall.radius,
							activeBall.radius * 2f,
							activeBall.radius * 2f
					);
				}

				// Draw all other balls
				for (Ball ball : balls) {
					int health = ((BallUserData)ball.body.getUserData()).health;

					if (health >= 3) {
						_game.batch.setColor(Color.GREEN);
					} else if (health == 2) {
						_game.batch.setColor(Color.YELLOW);
					} else {
						_game.batch.setColor(Color.RED);
					}

					_game.batch.draw(
						ball.texture,
						ball.body.getPosition().x - ball.radius,
						ball.body.getPosition().y - ball.radius,
						ball.radius * 2f,
						ball.radius * 2f
					);
				}

				_game.batch.setColor(Color.WHITE);

				// Draw all world tiles
				for (int y = 0; y < numRows; y++) {
					for (int x = 0; x < numCols; x++) {
						// Add a tile at the outer rows and edges
						if (map[y][x]) {
							_game.batch.draw(tileTexture, x, y, 1f, 1f);
						}
					}
				}
			}
			_game.batch.end();
		}

		if (readyToFire) {
			// Change the camera size to the virtual screen size
			_game.viewport.setWorldSize(_game.virtualScreenSize.width, _game.virtualScreenSize.height);
			_game.viewport.apply(true);
			_game.batch.setProjectionMatrix(_game.camera.combined);

			if (hasMouseMoved) {
				_game.shapeRenderer.setProjectionMatrix(_game.camera.combined);
				_game.shapeRenderer.setColor(0.1f, 0.8f, 0.5f, 1f);
				_game.shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
				_game.shapeRenderer.line(viewportToWorld(spawnPoint.x), viewportToWorld(spawnPoint.y), touchPos.x, touchPos.y);
				_game.shapeRenderer.end();
			}

			// Lastly, render any text / UI needed
			_game.batch.begin();
			{
				_game.bigFont.setColor(Color.BLACK);
				_game.glyphLayout.setText(_game.bigFont, TEXT_READY);
				_game.bigFont.draw(_game.batch, TEXT_READY, (_game.virtualScreenSize.width / 2) - (_game.glyphLayout.width / 2) + 1, _game.virtualScreenSize.height / 2 + _game.glyphLayout.height - 1);
				_game.bigFont.setColor(Color.WHITE);
				_game.bigFont.draw(_game.batch, TEXT_READY, (_game.virtualScreenSize.width / 2) - (_game.glyphLayout.width / 2), _game.virtualScreenSize.height / 2 + _game.glyphLayout.height);
			}
			_game.batch.end();
		}
	}

	@Override
	public void resize(int width, int height) {
		super.resize(width, height);
	}

	@Override
	public void hide() {

		// To dispose or not to dispose
		dispose();
	}

	@Override
	public void dispose() {
		// Remember to dispose of any shapes after you're done with them!
		// BodyDef and FixtureDef don't need disposing, but shapes do.
		if (activeBall != null) {
			activeBall.dispose();
		}

		DisposalHelper.disposeCollection(balls);

		topWallBox.dispose();
		bottomWallBox.dispose();
		leftWallBox.dispose();
		rightWallBox.dispose();

		deathZoneBox.dispose();
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

			// TODO Do something here
		}
	}

	private void updateEntities(float delta) {
		if (activeBall != null) {
			if (activeBall.isGrowing) {
				activeBall.radius += growthPerSec * delta;

				// If we've reached the growth, rebuild the body
				if (activeBall.radius >= activeBall.radiusToGrowTo) {
					activeBall.radius = activeBall.radiusToGrowTo;
					activeBall.isGrowing = false;
					readyToFire = true;

					// Use scratch to store position to exist after the body is destroyed
					scratchVec2d = activeBall.body.getPosition();
					float radiusToUse = activeBall.radius;

					// Clear out the active ball for right now
					if (activeBall.body != null) {
						_game.world.destroyBody(activeBall.body);
					}
					activeBall = null;

					// Create a new ball to be used as a static one in place of what WAS the active ball
					Ball newBall = new Ball(
							scratchVec2d.x,
							scratchVec2d.y,
							radiusToUse,
							ballTexture,
							_game.world,
							true
					);

					// Add the newly created ball to the array of balls
					balls.add(newBall);
				}
			} else {

				// See if the player is stopped
				if (!readyToFire && activeBall.body.getAngularVelocity() <= 0.1 && activeBall.body.getLinearVelocity().len() <= 0.1) {
					// Before we do anymore calculation, let's see if we are in the death zone (And now, you know, dead.)
					if (insideDeathZone) {
						_game.setScreen(new GameOverScreen(_game));
					}

					// TODO Grow the active ball!
					float closestDistance = Float.MAX_VALUE;

					// Look at all the walls

					// Top
					float topWallDistance = Vector2.dst(
							0,
							activeBall.body.getPosition().y,
							0,
							worldToViewport(_game.virtualScreenSize.height) - 1
					) - activeBall.radius;
					if (topWallDistance < closestDistance) {
						closestDistance = topWallDistance;
					}

					// Bottom
					float bottomWallDistance = Vector2.dst(
							0,
							activeBall.body.getPosition().y,
							0,
							1
					) - activeBall.radius;
					if (bottomWallDistance < closestDistance) {
						closestDistance = bottomWallDistance;
					}

					// Death zone (Since left wall isn't accessible anyway)
					float deathZoneDistance = Vector2.dst(
							activeBall.body.getPosition().x,
							0,
							deathZoneRect.x + deathZoneRect.width,
							0
					) - activeBall.radius;
					if (deathZoneDistance < closestDistance) {
						closestDistance = deathZoneDistance;
					}

					// Right
					float rightWallDistance = Vector2.dst(
							activeBall.body.getPosition().x,
							0,
							worldToViewport(_game.virtualScreenSize.width) - 1,
							0
					) - activeBall.radius;
					if (rightWallDistance < closestDistance) {
						closestDistance = rightWallDistance;
					}

					// TODO Look at all the OTHER balls
					for (Ball ball : balls) {
						// Calculate the distance between the center points
						float distance = Vector2.dst(
								activeBall.body.getPosition().x,
								activeBall.body.getPosition().y,
								ball.body.getPosition().x,
								ball.body.getPosition().y
						) - activeBall.radius - ball.radius;

						// If this ball is closer, flag it as such
						if (distance < closestDistance) {
							closestDistance = distance;
						}
					}

					// At this point, we can set the new radius of the active ball
					//
					// ... that said, don't update radius if there are no other balls
					if (closestDistance > 0.0f) {
						activeBall.isGrowing = true;
						activeBall.radiusToGrowTo = closestDistance + activeBall.radius;
					}
				}
			}
		}

		// TODO Check this method for areas of optimization
		boolean isTouched = Gdx.input.justTouched();

		// See if the user is clicking / touching at all
		if (isTouched && readyToFire) {
			// Ready to fire? Let's make the active ball then!
			activeBall = new Ball(
				spawnPoint.x,
				spawnPoint.y,
				worldToViewport(16f),
				ballTexture,
				_game.world,
				false
			);

			// Get vector from center of player body to mouse position
			scratchVec2d.set(worldToViewport(touchPos.x), worldToViewport(touchPos.y));
			dirToMouse = scratchVec2d.sub(activeBall.body.getPosition()).nor();

			activeBall.body.applyLinearImpulse(dirToMouse.scl(STRENGTH_OF_IMPULSE), activeBall.body.getPosition(),true);

			readyToFire = false;
		}
	}

	private void doPhysicsStep(float deltaTime) {
		// Clean up balls that need it
		cleanUpBalls();

		// fixed time step
		// max frame time to avoid spiral of death (on slow devices)
		float frameTime = Math.min(deltaTime, 0.25f);
		accumulator += frameTime;
		while (accumulator >= TIME_STEP) {
			_game.world.step(TIME_STEP, VELOCITY_ITERATIONS, POSITION_ITERATIONS);
			accumulator -= TIME_STEP;
		}
	}

	// ---------------------------
	// Private methods
	// ---------------------------

	private void initializeInputProcessor() {
		// TODO Put InputProcessing in another place?
		Gdx.input.setInputProcessor(new InputAdapter() {
			@Override
			public boolean keyDown(int key) {
				if (key == Input.Keys.NUM_1) {
					showDebugger = !showDebugger;
					return true;
				} else if (key == Input.Keys.SPACE) {
					// Randomly refill map and reset player
					fillMap();
					reinitializeMapPhysics();
					return true;
				}

				return false;
			}

			@Override
			public boolean keyUp(int keycode) {


				return false;
			}

			@Override
			public boolean mouseMoved(int screenX, int screenY) {
				hasMouseMoved = true;

				// "Unproject" from the screen to world position, taking the viewport dimensions into account
				touchPos.set(screenX, screenY, 0f);
				touchPos = _game.camera.unproject(touchPos, _game.viewport.getScreenX(), _game.viewport.getScreenY(), _game.viewport.getScreenWidth(), _game.viewport.getScreenHeight());

				return super.mouseMoved(screenX, screenY);
			}

			@Override
			public boolean touchDown(int screenX, int screenY, int pointer, int button) {
				if (button == Input.Buttons.LEFT) {
					if (hasMouseMoved) {
						// "Unproject" from the screen to world position, taking the viewport dimensions into account
						touchPos.set(screenX, screenY, 0f);
						touchPos = _game.camera.unproject(touchPos, _game.viewport.getScreenX(), _game.viewport.getScreenY(), _game.viewport.getScreenWidth(), _game.viewport.getScreenHeight());

						return true;
					}

					return false;
				}

				return false;
			}
		});
	}

	// -------------------------
	// Physics related methods
	// -------------------------

	private float worldToViewport(float worldSpace) {
		return worldSpace / PIXELS_PER_METER;
	}

	private float viewportToWorld(float viewportSpace) {
		return viewportSpace * PIXELS_PER_METER;
	}

	private void initializeBox2dElements() {

		_game.world = new World(new Vector2(0, GRAVITY_PER_SECOND_Y), true);

		ballTexture = _game.assetManager.get(AustinautsGame.TEXTURE_HARDCIRCLE_1024, Texture.class);
		tileTexture = _game.assetManager.get(AustinautsGame.TEXTURE_TILE, Texture.class);

		ballsToCleanUp = new Queue<>();

		setupContactListener();
	}

	private void cleanUpBalls() {
		for (Ball ball : ballsToCleanUp) {
			if (ball.body != null && !_game.world.isLocked()) {
				_game.world.destroyBody(ball.body);
				balls.removeValue(ball, true);
			}
		}

		ballsToCleanUp.clear();
	}

	// TODO Organize this  better?
	private void setupContactListener() {
		_game.world.setContactListener(new ContactListener() {
			@Override
			public void beginContact(Contact contact) {
				// See if the collision is for the death zone
				if (contact.getFixtureA().getBody() == deathZoneBody || contact.getFixtureB().getBody() == deathZoneBody) {
					// Let it be known that we are in the death zone
					insideDeathZone = true;
				} else {
					// We definitely aren't in the death zone
					insideDeathZone = false;

					BallUserData userDataA = contact.getFixtureA().getBody().getUserData() != null ? (BallUserData) contact.getFixtureA().getBody().getUserData() : null;
					BallUserData userDataB = contact.getFixtureB().getBody().getUserData() != null ? (BallUserData) contact.getFixtureB().getBody().getUserData() : null;

					if (userDataA != null) {
						userDataA.health--;

						if (userDataA.health <= 0) {
							// With the health gone, add ball to clean up
							ballsToCleanUp.addFirst(userDataA.correspondingBall);
						}
					}

					if (userDataB != null) {
						userDataB.health--;

						if (userDataB.health <= 0) {
							// With the health gone, add ball to clean up
							ballsToCleanUp.addFirst(userDataB.correspondingBall);
						}
					}
				}
			}

			@Override
			public void endContact(Contact contact) {

			}

			@Override
			public void preSolve(Contact contact, Manifold oldManifold) {

			}

			@Override
			public void postSolve(Contact contact, ContactImpulse impulse) {

			}
		});
	}

	// TODO Can these go into a helper class?
	private void buildLevelBoundsAndDeathZone() {

		topWallBodyDef = new BodyDef();
		// Set its world position
		topWallBodyDef.position.set(new Vector2(worldToViewport(_game.virtualScreenSize.width / 2.0f), worldToViewport(_game.virtualScreenSize.height) - 0.5f));

		// Create a activeBall.body from the defintion and add it to the world
		topWallBody = _game.world.createBody(topWallBodyDef);

		// Create a polygon shape
		topWallBox = new PolygonShape();

		// Set the polygon shape (setAsBox takes half-width and half-height as arguments)
		topWallBox.setAsBox(worldToViewport(_game.virtualScreenSize.width / 2.0f), 0.5f);
		// Create a playerFixture from our polygon shape and add it to our topWall activeBall.body
		topWallBody.createFixture(topWallBox, 0.0f);


		bottomWallBodyDef = new BodyDef();
		// Set its world position
		bottomWallBodyDef.position.set(new Vector2(worldToViewport(_game.virtualScreenSize.width / 2.0f), 0.5f));

		bottomWallBody = _game.world.createBody(bottomWallBodyDef);

		// Create a polygon shape
		bottomWallBox = new PolygonShape();

		// Set the polygon shape (setAsBox takes half-width and half-height as arguments)
		bottomWallBox.setAsBox(worldToViewport(_game.virtualScreenSize.width / 2f), 0.5f);

		bottomWallBody.createFixture(bottomWallBox, 0.0f);


		leftWallBodyDef = new BodyDef();
		// Set its world position
		leftWallBodyDef.position.set(new Vector2(0.5f, worldToViewport(_game.virtualScreenSize.height / 2.0f)));

		leftWallBody = _game.world.createBody(leftWallBodyDef);

		// Create a polygon shape
		leftWallBox = new PolygonShape();

		// Set the polygon shape (setAsBox takes half-width and half-height as arguments)
		leftWallBox.setAsBox(0.5f, worldToViewport(_game.virtualScreenSize.height / 2.0f));

		leftWallBody.createFixture(leftWallBox, 0.0f);


		rightWallBodyDef = new BodyDef();
		// Set its world position
		rightWallBodyDef.position.set(new Vector2(worldToViewport(_game.virtualScreenSize.width) - 0.5f, worldToViewport(_game.virtualScreenSize.height / 2.0f)));

		rightWallBody = _game.world.createBody(rightWallBodyDef);

		// Create a polygon shape
		rightWallBox = new PolygonShape();

		// Set the polygon shape (setAsBox takes half-width and half-height as arguments)
		rightWallBox.setAsBox(0.5f, worldToViewport(_game.virtualScreenSize.height / 2.0f));

		rightWallBody.createFixture(rightWallBox, 0.0f);


		// Lastly, make the death zone!
		deathZoneBodyDef = new BodyDef();

		deathZoneBodyDef.type = BodyDef.BodyType.StaticBody;

		// Set its world position
		deathZoneBodyDef.position.set(new Vector2(deathZoneRect.x + deathZoneRect.width / 2f, deathZoneRect.y + deathZoneRect.height / 2f));

		deathZoneBody = _game.world.createBody(deathZoneBodyDef);

		// Create a polygon shape
		deathZoneBox = new PolygonShape();

		// Set the polygon shape (setAsBox takes half-width and half-height as arguments)
		deathZoneBox.setAsBox(deathZoneRect.width / 2f,deathZoneRect.height / 2f);

		FixtureDef deathZoneFixtureDef = new FixtureDef();
		deathZoneFixtureDef.shape = deathZoneBox;
		deathZoneFixtureDef.isSensor = true;

		deathZoneBody.createFixture(deathZoneFixtureDef);
	}

	private void fillMap() {
		for (int y = 0; y < numRows; y++) {
			for (int x = 0; x < numCols; x++) {
				// Add a tile at the outer rows and edges
				if (x == 0 || y == 0 || x == numCols - 1 || y == numRows - 1) {
					map[y][x] = true;
				} else {
					map[y][x] = false;
				}
			}
		}
	}

	private void reinitializeMapPhysics() {
		// Clear out all the bodies
		removeAllBodies();

		// Build the outer boundaries of the level
		buildLevelBoundsAndDeathZone();
	}

	private void removeAllBodies() {
		while (balls.size > 0) {
			Ball ball = balls.get(0);
			
			if (ball.body != null) {
				_game.world.destroyBody(ball.body);
				balls.removeIndex(0);
			}
		}

		balls.clear();

		if (activeBall != null && activeBall.body != null) {
			_game.world.destroyBody(activeBall.body);
		}
	}
}




