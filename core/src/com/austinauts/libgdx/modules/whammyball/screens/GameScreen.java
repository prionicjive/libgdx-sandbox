package com.austinauts.libgdx.modules.whammyball.screens;

import com.austinauts.libgdx.AustinautsGame;
import com.austinauts.libgdx.common.utils.ShaderHelper;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.FloatFrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.ContactListener;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.Manifold;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.TimeUtils;
import net.dermetfan.gdx.graphics.g2d.Box2DSprite;

public class GameScreen extends ScreenAdapter {
	// TODO Again, JSON?
	private final int PIXELS_PER_METER = 16;
	private final float GRAVITY_PER_SECOND_Y = 0f; //-10f;
	private final float STRENGTH_OF_FORCE = 5f;
	private final float STRENGTH_OF_IMPULSE = 400f;
	private final float LINEAR_DAMPING = 0.87f;
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

	// --------------------
	// Game Entities
	// --------------------
	private boolean[][] map;
	private int numCols;
	private int numRows;
	private Array<Body> bodies;
	private boolean showDebugger = false;

	// TODO How to better define the player?
	private float playerRadius = 16f / PIXELS_PER_METER; // In Box2d coords
	private Texture playerTexture;
	private BodyDef playerBodyDef;
	private Body playerBody;
	private CircleShape circle;
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

		// Set up the viewport to play well with Box2D
		numCols = _game.virtualScreenSize.width / PIXELS_PER_METER;
		numRows = _game.virtualScreenSize.height / PIXELS_PER_METER;
		_game.viewport.setWorldSize(numCols, numRows);
		_game.viewport.apply(true);

		// Set the sprite batch to use the camera's combined projection/view matrix
		_game.batch.setProjectionMatrix(_game.camera.combined);

		// Randomly fill map
		map = new boolean[numRows][numCols];
		randomMapFill();

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

		if (showDebugger) {
			// Do some debug drawing for Box2D
			_game.box2DDebugRenderer.render(_game.world, _game.camera.combined);
		} else {
			_game.batch.begin();
			{
				// TODO Draw player
				_game.batch.draw(playerTexture, playerBody.getPosition().x - playerRadius, playerBody.getPosition().y - playerRadius, playerRadius * 2f, playerRadius * 2f);

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
		circle.dispose();
		topWallBox.dispose();
		bottomWallBox.dispose();
		leftWallBox.dispose();
		rightWallBox.dispose();
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
		// See if the player is stopped
		if (!readyToFire && playerBody.getAngularVelocity() <= 0.1 && playerBody.getLinearVelocity().len() <= 0.1) {
			readyToFire = true;

			// TODO Grow the active ball!
			float closestDistance = Float.MAX_VALUE;

			// Look at all the walls

			// Top
			float topWallDistance = Vector2.dst(0, playerBody.getPosition().y, 0, worldToViewport(_game.virtualScreenSize.height) - 1) - playerRadius;
			if (topWallDistance < closestDistance) {
				closestDistance = topWallDistance;
			}

			// Bottom
			float bottomWallDistance = Vector2.dst(0, playerBody.getPosition().y, 0, 1) - playerRadius;
			if (bottomWallDistance < closestDistance) {
				closestDistance = bottomWallDistance;
			}

			// Left
			float leftWallDistance = Vector2.dst(playerBody.getPosition().x, 0, 1, 0) - playerRadius;
			if (leftWallDistance < closestDistance) {
				closestDistance = leftWallDistance;
			}

			// Right
			float rightWallDistance = Vector2.dst(playerBody.getPosition().x, 0, worldToViewport(_game.virtualScreenSize.width) - 1, 0) - playerRadius;
			if (rightWallDistance < closestDistance) {
				closestDistance = rightWallDistance;
			}

			// TODO Look at all the OTHER balls
//			for (let ball of staticBalls) {
//				// Calculate the distance between the center points
//				let distance = distanceBetween(activeBall.x, activeBall.y, ball.x, ball.y) - activeBall.radius - ball.radius;
//
//				// If this ball is closer, flag it as such
//				if (!closestDistance || distance < closestDistance) {
//					closestDistance = distance;
//				}
//			}

			// At this point, we can set the new radius of the active ball
			//
			// ... that said, don't update radius if there are no other balls
			if (closestDistance > 0.0f) {
				// If the player has stopped, removed the related body (We'll add it back in as a static)
				if (playerBody != null) {
					_game.world.destroyBody(playerBody);
				}

				playerRadius += closestDistance;
//				activeBall.isGrowing = true;
//				activeBall.growTo = closestDistance + activeBall.radius; // Include the previous radius
			}
		}

		// TODO Check this method for areas of optimization
		boolean isTouched = Gdx.input.justTouched();

		// See if the user is clicking / touching at all
		if (isTouched && readyToFire) {
			// Get vector from center of player body to mouse position
			scratchVec2d.set(touchPos.x, touchPos.y);
			dirToMouse = scratchVec2d.sub(playerBody.getPosition()).nor();

			playerBody.applyLinearImpulse(dirToMouse.scl(STRENGTH_OF_IMPULSE), playerBody.getPosition(),true);

			readyToFire = false;
		}
	}

	private void doPhysicsStep(float deltaTime) {
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
			public boolean keyDown(int key) {
				if (key == Input.Keys.NUM_1) {
					showDebugger = !showDebugger;
					return true;
				}
				else if (key == Input.Keys.BACKSPACE) {
					// Destroy the body and recreate it
					createPlayerBody();

					return true;
				}
				else if (key == Input.Keys.SPACE) {
					// Randomly refill map and reset player
					randomMapFill();
					reinitializeMapPhysics();
					return true;
				}

				return false;
			}

			@Override
			public boolean keyUp(int keycode) {


				return false;
			}

			public boolean touchDown(int screenX, int screenY, int pointer, int button) {
				if (button == Input.Buttons.LEFT) {
					// "Unproject" from the screen to world position, taking the viewport dimensions into account
					touchPos.set(screenX, screenY, 0f);
					touchPos = _game.camera.unproject(touchPos, _game.viewport.getScreenX(), _game.viewport.getScreenY(), _game.viewport.getScreenWidth(), _game.viewport.getScreenHeight());

					return true;
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
		bodies = new Array<>();

		playerTexture = _game.assetManager.get(AustinautsGame.TEXTURE_HARDCIRCLE_1024, Texture.class);

		tileTexture = _game.assetManager.get(AustinautsGame.TEXTURE_TILE, Texture.class);

		setupContactListener();
	}

	private void createPlayerBody() {
		// Destroy body if it exists
		// TODO Is this smart to hide it away?
		if (playerBody != null) {
			_game.world.destroyBody(playerBody);
		}

		playerBodyDef = new BodyDef();

		// We set our playerBody to dynamic, for something like ground which doesn't move we would set it to StaticBody
		playerBodyDef.type = BodyDef.BodyType.DynamicBody;
		playerBodyDef.linearDamping = LINEAR_DAMPING;

		// Set our playerBody's starting position in the world
		// TODO This is the spawn point. Better way to do this?
		playerBodyDef.position.set(generateRandomPoint(1, 4,
			1, numRows - 1, map));

		// Create our playerBody in the world using our playerBody definition
		playerBody = _game.world.createBody(playerBodyDef);

		// Create a circle shape and set its radius
		circle = new CircleShape();
		circle.setRadius(playerRadius - 0.01f);

		// Create a playerFixture definition to apply our shape to
		FixtureDef playerFixtureDef = new FixtureDef();
		playerFixtureDef.shape = circle;
		playerFixtureDef.density = 1.0f;
		playerFixtureDef.friction = 0.0f;
		playerFixtureDef.restitution = 0.95f; // Make it mostly bounce back perfectly

		// Create our playerFixtureDef and attach it to the playerBody
		playerBody.createFixture(playerFixtureDef);
	}

	private Vector2 generateRandomPoint(int spawnStartCol, int spawnEndCol, int spawnStartRow, int spawnEndRow, boolean[][] mapToUse) {
		Vector2 pos = new Vector2();

		pos.x = spawnStartCol + MathUtils.random((spawnEndCol + 1) - spawnStartCol);
		pos.y = spawnStartRow + MathUtils.random((spawnEndRow + 1) - spawnStartRow);

		// Now, see if this is generated at a tile. If so, search for a clear spot
		if (mapToUse[(int) pos.y][(int) pos.x]) {
			for (int r = spawnStartRow; r < spawnEndRow; r++) {
				for (int c = spawnStartCol; c < spawnEndCol; c++) {
					if (!mapToUse[r][c]) {
						pos.x = c;
						pos.y = r;
						return pos;
					}
				}
			}
		}

		return pos;
	}

	// TODO Organize this  better?
	private void setupContactListener() {
		_game.world.setContactListener(new ContactListener() {
			@Override
			public void beginContact(Contact contact) {
				Fixture fixtureA = contact.getFixtureA();
				Fixture fixtureB = contact.getFixtureB();

				if ((fixtureA.isSensor() || fixtureB.isSensor())
					&& (fixtureA.getBody().equals(playerBody) || fixtureB.getBody().equals(playerBody))) {
					// TODO DO something!!
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
	private void buildLevelBounds() {

		topWallBodyDef = new BodyDef();
		// Set its world position
		topWallBodyDef.position.set(new Vector2(worldToViewport(_game.virtualScreenSize.width / 2.0f), worldToViewport(_game.virtualScreenSize.height) - 0.5f));

		// Create a playerBody from the defintion and add it to the world
		topWallBody = _game.world.createBody(topWallBodyDef);

		// Create a polygon shape
		topWallBox = new PolygonShape();

		// Set the polygon shape (setAsBox takes half-width and half-height as arguments)
		topWallBox.setAsBox(worldToViewport(_game.virtualScreenSize.width / 2.0f), 0.5f);
		// Create a playerFixture from our polygon shape and add it to our topWall playerBody
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
	}

	private void randomMapFill() {
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
		buildLevelBounds();

		// Create the player body if need be
		createPlayerBody();
	}

	private void removeAllBodies() {
		while (bodies.size > 0) {
			Body body = bodies.get(0);
			_game.world.destroyBody(body);
			bodies.removeIndex(0);
		}

		bodies.clear();
	}
}




