package com.austinauts.libgdx.modules.box2d.screens;

import com.austinauts.libgdx.AustinautsGame;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Box2D;
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.TimeUtils;

public class GameScreen extends ScreenAdapter {
	private Box2DDebugRenderer box2DDebugRenderer;

	// TODO Again, JSON?
	private final int PIXELS_PER_METER = 16;
	private final float GRAVITY_PER_SECOND_Y = 0f; //-10f;
	private final float STRENGTH_OF_FORCE = 5f;
	private final float LINEAR_DAMPING = 0.35f;
	private float TIME_STEP = 1 / 60f;
	private int VELOCITY_ITERATIONS = 8;
	private int POSITION_ITERATIONS = 3;

	// Reference to main game object
	private final AustinautsGame _game;

	// Timer and speed
	private long gameStartTime;
	private long elapsedGameTime;

	private long intervalStartTime;
	private long elapsedIntervalTime;

	private long intervalInSeconds = 1L;
	private float accumulator = 0f;

	// --------------------
	// Game Entities
	// --------------------

	private boolean[][] map;
	private int numCols;
	private int numRows;
	private final int CHANCE_OF_TILE = 5;

	private World world;
	private BodyDef bodyDef;
	private Body body;
	private FixtureDef fixtureDef;
	private Fixture fixture;
	private CircleShape circle;

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
	private Vector3 touchPos = new Vector3();

	private BitmapFont bigFont;
	private BitmapFont smallFont;

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

		// Set up the viewport to play well with Box2D
		numCols = _game.masterWorldWidth / PIXELS_PER_METER;
		numRows = _game.masterWorldHeight / PIXELS_PER_METER;
		_game.viewport.setWorldSize(numCols, numRows);
		_game.viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);

		// Set up Box2D elements
		setupBox2dElements();

		// Randomly fill map
		map = new boolean[numRows][numCols];
		randomMapFill();
	}

	@Override
	public void render(float delta) {
		// Outline of game loop:
		//
		// 1) Update camera
		// 2) Handle input and update entities
		// 3) Render
		// 4) Process/step physics

		// Check the timer and properly boost the row speed
		checkTimer();

		// Update game entities
		updateEntities(delta);

		// Clear the backbuffer
		Gdx.gl.glClearColor(.1f, .1f, .1f, 1);
		Gdx.gl.glClear(GL30.GL_COLOR_BUFFER_BIT);

		// Draw any sprites
		_game.batch.begin();
		{

		}
		_game.batch.end();

		// Do some debug drawing for Box2D
		box2DDebugRenderer.render(world, _game.camera.combined);

		doPhysicsStep(delta);
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

		world.dispose();
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
		// TODO Check this method for areas of optimization
		boolean isTouched = Gdx.input.isTouched();

		// See if the user is clicking / touching at all
		if (isTouched) {
			touchPos.x = Gdx.input.getX();
			touchPos.y = Gdx.input.getY();

			// "Unproject" from the screen to world position, taking the viewport dimensions into account
			_game.camera.unproject(touchPos, _game.viewport.getScreenX(), _game.viewport.getScreenY(), _game.viewport.getScreenWidth(), _game.viewport.getScreenHeight());

			// Get vector from touch point to center of body
			Vector2 dirToBody = body.getPosition().sub(touchPos.x, touchPos.y).nor();

			body.applyForceToCenter(dirToBody.scl(STRENGTH_OF_FORCE), true);
		}
	}

	private void doPhysicsStep(float deltaTime) {
		// fixed time step
		// max frame time to avoid spiral of death (on slow devices)
		float frameTime = Math.min(deltaTime, 0.25f);
		accumulator += frameTime;
		while (accumulator >= TIME_STEP) {
			world.step(TIME_STEP, VELOCITY_ITERATIONS, POSITION_ITERATIONS);
			accumulator -= TIME_STEP;
		}
	}

	// -------------------------
	// Physics related methods
	// -------------------------

	private float worldToViewport(float worldSpace) {
		return worldSpace / PIXELS_PER_METER;
	}

	private void setupBox2dElements() {

		Box2D.init();
		box2DDebugRenderer = new Box2DDebugRenderer();

		world = new World(new Vector2(0, GRAVITY_PER_SECOND_Y), true);

		// First we create a body definition
		bodyDef = new BodyDef();

		// We set our body to dynamic, for something like ground which doesn't move we would set it to StaticBody
		bodyDef.type = BodyDef.BodyType.DynamicBody;
		bodyDef.linearDamping = LINEAR_DAMPING;

		// Set our body's starting position in the world
		bodyDef.position.set(20, 20);

		// Create our body in the world using our body definition
		body = world.createBody(bodyDef);

		// Create a circle shape and set its radius to 6
		circle = new CircleShape();
		circle.setRadius(0.5f);

		// Create a fixture definition to apply our shape to
		FixtureDef fixtureDef = new FixtureDef();
		fixtureDef.shape = circle;
		fixtureDef.density = 1.0f;
		fixtureDef.friction = 0.0f;
		fixtureDef.restitution = 0.95f; // Make it mostly bounce back perfectly

		// Create our fixture and attach it to the body
		fixture = body.createFixture(fixtureDef);

		// Build the bounds for the level
		buildLevelBounds();
	}

	// TODO Can these go into a helper class?
	private void buildLevelBounds() {

		// Create our body definition
		topWallBodyDef = new BodyDef();
		// Set its world position
		topWallBodyDef.position.set(new Vector2(worldToViewport(_game.masterWorldWidth / 2.0f), worldToViewport(_game.masterWorldHeight) - 0.5f));

		// Create a body from the defintion and add it to the world
		topWallBody = world.createBody(topWallBodyDef);

		// Create a polygon shape
		topWallBox = new PolygonShape();

		// Set the polygon shape (setAsBox takes half-width and half-height as arguments)
		topWallBox.setAsBox(worldToViewport(_game.masterWorldWidth / 2.0f), 0.5f);
		// Create a fixture from our polygon shape and add it to our topWall body
		topWallBody.createFixture(topWallBox, 0.0f);


		// Create our body definition
		bottomWallBodyDef = new BodyDef();
		// Set its world position
		bottomWallBodyDef.position.set(new Vector2(worldToViewport(_game.masterWorldWidth / 2.0f), 0.5f));

		// Create a body from the defintion and add it to the world
		bottomWallBody = world.createBody(bottomWallBodyDef);

		// Create a polygon shape
		bottomWallBox = new PolygonShape();

		// Set the polygon shape (setAsBox takes half-width and half-height as arguments)
		bottomWallBox.setAsBox(worldToViewport(_game.masterWorldWidth / 2), 0.5f);
		// Create a fixture from our polygon shape and add it to our bottomWall body
		bottomWallBody.createFixture(bottomWallBox, 0.0f);


		// Create our body definition
		leftWallBodyDef = new BodyDef();
		// Set its world position
		leftWallBodyDef.position.set(new Vector2(0.5f, worldToViewport(_game.masterWorldHeight / 2.0f)));

		// Create a body from the defintion and add it to the world
		leftWallBody = world.createBody(leftWallBodyDef);

		// Create a polygon shape
		leftWallBox = new PolygonShape();

		// Set the polygon shape (setAsBox takes half-width and half-height as arguments)
		leftWallBox.setAsBox(0.5f, worldToViewport(_game.masterWorldHeight / 2.0f));
		// Create a fixture from our polygon shape and add it to our leftWall body
		leftWallBody.createFixture(leftWallBox, 0.0f);


		// Create our body definition
		rightWallBodyDef = new BodyDef();
		// Set its world position
		rightWallBodyDef.position.set(new Vector2(worldToViewport(_game.masterWorldWidth) - 0.5f, worldToViewport(_game.masterWorldHeight / 2.0f)));

		// Create a body from the defintion and add it to the world
		rightWallBody = world.createBody(rightWallBodyDef);

		// Create a polygon shape
		rightWallBox = new PolygonShape();

		// Set the polygon shape (setAsBox takes half-width and half-height as arguments)
		rightWallBox.setAsBox(0.5f, worldToViewport(_game.masterWorldHeight / 2.0f));
		// Create a fixture from our polygon shape and add it to our rightWall body
		rightWallBody.createFixture(rightWallBox, 0.0f);
	}

	private void randomMapFill() {
		for (int y = 0; y < numRows; y++) {
			for (int x = 0; x < numCols; x++) {
				// Add a tile at the outer rows and edges
				if (x == 0 || y == 0 || x == numCols - 1 || y == numRows - 1) {
					map[y][x] = true;
				}
				// OR Determine if a tile should be randomly created at this cell (AND create the geometry
				else if (MathUtils.random(101) <= CHANCE_OF_TILE) {
					map[y][x] = true;

					// Create our body definition
					BodyDef tileBodyDef = new BodyDef();
					// Set its world position
					tileBodyDef.position.set(new Vector2(x + 0.5f, y + 0.5f));

					// Create a body from the defintion and add it to the world
					Body tileBody = world.createBody(tileBodyDef);

					// Create a polygon shape
					PolygonShape tileBox = new PolygonShape();

					// Set the polygon shape  (setAsBox takes half-width and half-height as arguments)
					tileBox.setAsBox(0.5f, 0.5f);
					// Create a fixture from our polygon shape and add it to our tile body
					tileBody.createFixture(tileBox, 0.0f);
				}
				else {
					map[y][x] = false;
				}
			}
		}
	}
}




