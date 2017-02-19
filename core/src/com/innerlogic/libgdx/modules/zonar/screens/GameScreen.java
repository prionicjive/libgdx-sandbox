package com.innerlogic.libgdx.modules.zonar.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
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
import com.innerlogic.libgdx.InnerLogicGame;
import com.innerlogic.libgdx.common.utils.ShaderHelper;
import com.innerlogic.libgdx.common.utils.UserFloatFrameBuffer;
import net.dermetfan.gdx.graphics.g2d.Box2DSprite;

public class GameScreen extends ScreenAdapter {
	// TODO Again, JSON?
	private final int PIXELS_PER_METER = 16;
	private final float GRAVITY_PER_SECOND_Y = 0f; //-10f;
	private final float STRENGTH_OF_FORCE = 5f;
	private final float LINEAR_DAMPING = 0.35f;
	// Reference to main game object
	private final InnerLogicGame _game;
	private final int CHANCE_OF_TILE = 5; // TODO Make all of these clearer
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

	// --------------------
	// Game Entities
	// --------------------
	// TODO Better way to store the most recent direction to the player?
	private Vector2 dirToBody = new Vector2();
	private boolean[][] map;
	private int numCols;
	private int numRows;
	private Array<Body> bodies;
	private boolean showTiles = true;
	private boolean showSprites = true;

	// TODO How to better define the player?
	private Box2DSprite playerSprite;
	private Texture playerTexture;
	private BodyDef playerBodyDef;
	private Body playerBody;
	private FixtureDef playerFixtureDef;
	private Fixture playerFixture;
	private CircleShape circle;

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

	// TODO Better way to set up sensing endpoint?
	private BodyDef endPointBodyDef;
	private Body endPointBody;
	private PolygonShape endPointBox;

	// TODO Make end point a little more special
	private Vector2 endPoint = new Vector2();

	// --------------------
	// Scratch variables
	// --------------------
	private BitmapFont bigFont;
	private BitmapFont smallFont;

	// TODO Rename to "light radius" or "light diameter"
	// Length of light cast
	private int lightDiameter = 512; // TODO Can we leverage this without increasing the number of rays emitted?

	// The number of rays emitted for a "light". This is used in a 360 degree fashion, so the higher, the higher the precision
	// but higher fill rate as well. Needs to be power of 2
	private int numRaysEmitted = 512;

	private UserFloatFrameBuffer occludersFBO;
	private UserFloatFrameBuffer shadowMapFBO;

	ShaderProgram shadowMapShader, shadowRenderShader;

	Array<Light> lights;

	boolean additive = true;
	boolean softShadows = true;

	// TODO Just scratch... should go elsewhere?
	private Vector3 touchPos = new Vector3();

	public GameScreen(final InnerLogicGame game) {
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

		// Set the sprite batch to use the camera's combined projection/view matrix
		_game.batch.setProjectionMatrix(_game.camera.combined);

		// Set up shaders
		initializeShaders();

		// Randomly fill map
		map = new boolean[numRows][numCols];
		randomMapFill();

		// Set up Box2D elements
		initializeBox2dElements();

		// Now that we have the phy
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
		// 3) Render
		// 4) Process/step physics

		// Check the timer and properly boost the row speed
		checkTimer();

		// Update game entities
		updateEntities(delta);

		doPhysicsStep(delta);

		// Clear the backbuffer
		Gdx.gl.glClearColor(.1f, .1f, .1f, 1);
		Gdx.gl.glClear(GL30.GL_COLOR_BUFFER_BIT);

		// Set additive if needed
		if (additive) {
			_game.batch.setBlendFunction(GL30.GL_SRC_ALPHA, GL30.GL_ONE);
		}

		for (int i = 0; i < lights.size; i++) {
			// Update the last light's pos to be where the player is
			Light currLight = lights.get(i);

			renderLight(currLight);
		}

		// Reset color and shader
		_game.batch.setColor(Color.WHITE);
		_game.batch.setShader(null);

		// Lastly, restore the prior blending mode if additive blending was used
		if (additive) {
			_game.batch.setBlendFunction(GL30.GL_SRC_ALPHA, GL30.GL_ONE_MINUS_SRC_ALPHA);
		}

		// Lastly, render the player and the endpoint

		_game.viewport.setWorldSize(numCols, numRows);
		_game.viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
		_game.shapeRenderer.setProjectionMatrix(_game.camera.combined);

		_game.shapeRenderer.setColor(0.1f, 0.8f, 0.5f, .1f);

		_game.shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
		_game.shapeRenderer.rect(endPoint.x, endPoint.y, 1f, 1f);
		_game.shapeRenderer.end();

		// TODO Doing this is critical. Does this have to be done every time, everywhere?
		_game.batch.setProjectionMatrix(_game.camera.combined);

		_game.batch.begin();
		{
			// TODO Draw player
			// _game.batch.draw(playerTexture, playerBody.getPosition().x - 0.5f, playerBody.getPosition().y - 0.5f, 1f, 1f);
			playerSprite.draw(_game.batch, playerBody);
		}
		_game.batch.end();

		// Do some debug drawing for Box2D
		if (showTiles) {
			_game.batch.begin();
			{
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

	}

	@Override
	public void resize(int width, int height) {
		super.resize(width, height);

		// Resize the lights
		for (int i = 0; i < lights.size; i++) {
			// Update the last light's pos to be where the player is
			Light currLight = lights.get(i);

			renderLight(currLight);
		}
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
		// TODO Check this method for areas of optimization
		boolean isTouched = Gdx.input.isTouched();

		// See if the user is clicking / touching at all
		if (isTouched) {

			// Get vector from touch point to center of playerBody
			dirToBody = playerBody.getPosition().sub(touchPos.x, touchPos.y).nor();

			playerBody.applyForceToCenter(dirToBody.scl(STRENGTH_OF_FORCE), true);
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

	private void initializeShaders() {
		// -------------------------------------
		// Set up shader related things
		// -------------------------------------

		// Set up Occluders FBO and texture that'll be generated from FBO
		occludersFBO = new UserFloatFrameBuffer(lightDiameter, lightDiameter, false);
		occludersFBO.getColorBufferTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

		// Set up 1D Shadow map FBO and texture that'll be generated from FBO
		shadowMapFBO = new UserFloatFrameBuffer(numRaysEmitted, 1, false);
		occludersFBO.getColorBufferTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

		// Ensure that not everything about a shader needs to be configured
		ShaderProgram.pedantic = false;

		// Get the vertex passthrough shader
		final String VERT_SRC = Gdx.files.internal("shaders/2dshadowmapping/pass.vert").readString();

		// Shader that uses the  occluders texture to generate a 1D shadow map texture
		shadowMapShader = ShaderHelper.createShader(VERT_SRC, Gdx.files.internal("shaders/2dshadowmapping/shadowMap.frag").readString());

		// Shader that samples the 1D shadow map texture to create shadows
		shadowRenderShader = ShaderHelper.createShader(VERT_SRC, Gdx.files.internal("shaders/2dshadowmapping/shadowRender.frag").readString());

		// Set up lights
		lights = new Array<>(true, 20);
	}

	private void initializeInputProcessor() {
		// TODO Put InputProcessing in another place?
		Gdx.input.setInputProcessor(new InputAdapter() {
			public boolean keyDown(int key) {
				if (key == Input.Keys.NUM_1) {
					showTiles = !showTiles;
					return true;
				}
				else if (key == Input.Keys.NUM_2) {
					showSprites = !showSprites;
					return true;
				}
				else if (key == Input.Keys.BACKSPACE) {
					// Destroy the body and recreate it
					createPlayerBody();

					return true;
				}
				else if (key == Input.Keys.SPACE) {
					// Randomly re
					randomMapFill();
					reinitializeMapPhysics();

					// Clear the lights before rebuilding the bodies that will house the lights
					lights.clear();

					createPlayerBody();
					createEndPointBody();
					setupContactListener();
				}

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

		playerTexture = _game.assetManager.get(InnerLogicGame.TEXTURE_PLAYER, Texture.class);
		playerSprite = new Box2DSprite(playerTexture);

		tileTexture = _game.assetManager.get(InnerLogicGame.TEXTURE_TILE, Texture.class);

		// First we create a playerBody definition
		createPlayerBody();
		createEndPointBody();
		setupContactListener();

		// Build the bounds for the level
		buildLevelBounds();
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

		// Create a circle shape and set its radius to 6
		circle = new CircleShape();
		circle.setRadius(0.49f);

		// Create a playerFixture definition to apply our shape to
		playerFixtureDef = new FixtureDef();
		playerFixtureDef.shape = circle;
		playerFixtureDef.density = 1.0f;
		playerFixtureDef.friction = 0.0f;
		playerFixtureDef.restitution = 0.95f; // Make it mostly bounce back perfectly

		// Create our playerFixture and attach it to the playerBody
		playerFixture = playerBody.createFixture(playerFixtureDef);

		// Lastly, clear the light and add it back to the players location
		// TODO Properly pull from screen width / height
		lights.add(new Light(playerBody, lightDiameter, Color.WHITE));
	}

	private void createEndPointBody() {
		// Destroy body if it exists
		// TODO Is this smart to hide it away?
		if (endPointBody != null) {
			_game.world.destroyBody(endPointBody);
		}

		endPointBodyDef = new BodyDef();

		// We set our endPointBody to static, for something like ground which doesn't move we would set it to StaticBody
		endPointBodyDef.type = BodyDef.BodyType.DynamicBody;

		// Set our endPointBody's position in the world
		// TODO This is the end point. Better way to do this?
		endPoint = generateRandomPoint(numCols - 7, numCols - 1,
			1, numRows - 1, map);
		endPointBodyDef.position.set(endPoint.x + 0.5f, endPoint.y + 0.5f);

		// Create our endPointBody in the world using our endPointBody definition
		endPointBody = _game.world.createBody(endPointBodyDef);

		// Create a polygon shape
		endPointBox = new PolygonShape();

		// Set the polygon shape  (setAsBox takes half-width and half-height as arguments)
		endPointBox.setAsBox(0.5f, 0.5f);
		// Create a playerFixture from our polygon shape and add it to our tile playerBody
		endPointBody.createFixture(endPointBox, 0.0f);

		// Lastly, make it a sensor (We only have 1 fixture
		net.dermetfan.gdx.physics.box2d.Box2DUtils.setSensor(endPointBody, true);

		lights.add(new Light(endPointBody, lightDiameter, Color.ROYAL));
	}

	private Vector2 generateRandomPoint(int spawnStartCol, int spawnEndCol, int spawnStartRow, int spawnEndRow, boolean[][] mapToUse) {
		Vector2 pos = new Vector2();

		pos.x = spawnStartCol + _game.randomizer.nextInt((spawnEndCol + 1) - spawnStartCol);
		pos.y = spawnStartRow + _game.randomizer.nextInt((spawnEndRow + 1) - spawnStartRow);

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
					// We now know we have collision between the player and the sensor so we are SUCCESSFUL!!!

					// TODO Switch to a screen to lead into a new level
					_game.setScreen(new LevelCompleteScreen(_game));
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
		topWallBodyDef.position.set(new Vector2(worldToViewport(_game.masterWorldWidth / 2.0f), worldToViewport(_game.masterWorldHeight) - 0.5f));

		// Create a playerBody from the defintion and add it to the world
		topWallBody = _game.world.createBody(topWallBodyDef);

		// Create a polygon shape
		topWallBox = new PolygonShape();

		// Set the polygon shape (setAsBox takes half-width and half-height as arguments)
		topWallBox.setAsBox(worldToViewport(_game.masterWorldWidth / 2.0f), 0.5f);
		// Create a playerFixture from our polygon shape and add it to our topWall playerBody
		topWallBody.createFixture(topWallBox, 0.0f);


		bottomWallBodyDef = new BodyDef();
		// Set its world position
		bottomWallBodyDef.position.set(new Vector2(worldToViewport(_game.masterWorldWidth / 2.0f), 0.5f));

		bottomWallBody = _game.world.createBody(bottomWallBodyDef);

		// Create a polygon shape
		bottomWallBox = new PolygonShape();

		// Set the polygon shape (setAsBox takes half-width and half-height as arguments)
		bottomWallBox.setAsBox(worldToViewport(_game.masterWorldWidth / 2), 0.5f);

		bottomWallBody.createFixture(bottomWallBox, 0.0f);


		leftWallBodyDef = new BodyDef();
		// Set its world position
		leftWallBodyDef.position.set(new Vector2(0.5f, worldToViewport(_game.masterWorldHeight / 2.0f)));

		leftWallBody = _game.world.createBody(leftWallBodyDef);

		// Create a polygon shape
		leftWallBox = new PolygonShape();

		// Set the polygon shape (setAsBox takes half-width and half-height as arguments)
		leftWallBox.setAsBox(0.5f, worldToViewport(_game.masterWorldHeight / 2.0f));

		leftWallBody.createFixture(leftWallBox, 0.0f);


		rightWallBodyDef = new BodyDef();
		// Set its world position
		rightWallBodyDef.position.set(new Vector2(worldToViewport(_game.masterWorldWidth) - 0.5f, worldToViewport(_game.masterWorldHeight / 2.0f)));

		rightWallBody = _game.world.createBody(rightWallBodyDef);

		// Create a polygon shape
		rightWallBox = new PolygonShape();

		// Set the polygon shape (setAsBox takes half-width and half-height as arguments)
		rightWallBox.setAsBox(0.5f, worldToViewport(_game.masterWorldHeight / 2.0f));

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
				else if (_game.randomizer.nextInt(101) <= CHANCE_OF_TILE) {
					map[y][x] = true;
				}
				else {
					map[y][x] = false;
				}
			}
		}
	}

	private void reinitializeMapPhysics() {
		// Clear out all the bodies
		removeAllBodies();

		// Parse the map and generate all the physics data (No need to do edges
		for (int r = 1; r < numRows - 1; r++) {
			for (int c = 1; c < numCols - 1; ) {
				// Start looking at the row, assuming the column index will be iterated
				c = makeBoxesForRow(map[r], r, c, -1);
			}
		}
	}

	private int makeBoxesForRow(boolean rowAsArray[], int rowIndex, int colIndex, int startingColumn) {
		// If tile at col for current row is a wall...
		if (rowAsArray[colIndex]) {
			//If startingColumn is not set
			if (startingColumn == -1) {
				// Store column index as starting tile
				startingColumn = colIndex;
			}

			// If right neighbor not out of bounds AND is a wall...
			if ((colIndex + 1) < (rowAsArray.length - 1) && rowAsArray[colIndex + 1]) {
				// Continue elongating box
				return makeBoxesForRow(rowAsArray, rowIndex, colIndex + 1, startingColumn);
			}
			// Otherwise, the box can be made
			else {
				// The anchor point and the width / height of the box
				float startX = (float) startingColumn;
				float endX = (float) (colIndex + 1);
				float startY = (float) rowIndex;
				float anchorX = startX + ((endX - startX) / 2.0f);
				float anchorY = startY + 0.5f;
				float width = endX - startX;
				float height = 1.0f;

				// Create box
				// TODO Extract this?
				// Create our playerBody definition
				BodyDef tileBodyDef = new BodyDef();
				// Set its world position
				tileBodyDef.position.set(new Vector2(anchorX, anchorY));

				// Create a playerBody from the defintion and add it to the world
				Body tileBody = _game.world.createBody(tileBodyDef);

				// Create a polygon shape
				PolygonShape tileBox = new PolygonShape();

				// Set the polygon shape  (setAsBox takes half-width and half-height as arguments)
				tileBox.setAsBox(width / 2.0f, height / 2.0f);
				// Create a playerFixture from our polygon shape and add it to our tile playerBody
				tileBody.createFixture(tileBox, 0.0f);

				// Lastly, add a reference to our bodies array
				bodies.add(tileBody);
			}
		}

		// Let it be known to go to the next column (Regardless if it is valid or not)
		return colIndex + 1;
	}

	private void removeAllBodies() {
		while (bodies.size > 0) {
			Body body = bodies.get(0);
			_game.world.destroyBody(body);
			bodies.removeIndex(0);
		}

		bodies.clear();
	}

	// ----------------------------------
	// Shader related private methods
	// ----------------------------------

	private void renderLight(Light lightToRender) {
		_game.viewport.setWorldSize(_game.masterWorldWidth, _game.masterWorldHeight);
		_game.viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);

		// STEP 1: Render the occluders to texture via FBO

		// Bind the occludersTexture FBO
		occludersFBO.begin();
		{
			// Clear the FBO fully
			Gdx.gl30.glClearColor(0f, 0f, 0f, 0f);
			Gdx.gl30.glClear(GL30.GL_COLOR_BUFFER_BIT);

			// Set the camera to the size of our FBO
			_game.camera.setToOrtho(false, occludersFBO.getWidth(), occludersFBO.getHeight());

			// Translate camera so that light is in the center
			_game.camera.translate(viewportToWorld(lightToRender.bodyRef.getPosition().x) - lightToRender.diameter / 2f, viewportToWorld(lightToRender.bodyRef.getPosition().y) - lightToRender.diameter / 2f);

			// Make sure the camera is up to date
			_game.camera.update();

			// Set up our batch for the occluders pass
			_game.batch.setProjectionMatrix(_game.camera.combined);
			_game.batch.setShader(null); // Use default shader

			// Draw any sprites
			if (showSprites) {
				_game.batch.begin();
				{
					// Draw all world tiles
					for (int y = 0; y < numRows; y++) {
						for (int x = 0; x < numCols; x++) {
							// Add a tile at the outer rows and edges
							if (map[y][x]) {
								_game.batch.draw(tileTexture, viewportToWorld(x), viewportToWorld(y), viewportToWorld(1f), viewportToWorld(1f));
							}
						}
					}
				}
				_game.batch.end();
			}
		}
		// Unbind the FBO
		occludersFBO.end();

		// STEP 2: Build a 1D shadow map texture via FBO with the occluders as the bound texture

		// Bind the shadow map
		shadowMapFBO.begin();
		{
			// Clear the FBO fully
			Gdx.gl30.glClearColor(0f, 0f, 0f, 0f);
			Gdx.gl30.glClear(GL30.GL_COLOR_BUFFER_BIT);

			// Set our shadow map shader
			_game.batch.setShader(shadowMapShader);
			_game.batch.begin();
			{
				shadowMapShader.setUniformf("lightCastLength", lightToRender.diameter);

				// Reset our camera to the FBO size
				_game.camera.setToOrtho(false, shadowMapFBO.getWidth(), shadowMapFBO.getHeight());
				_game.batch.setProjectionMatrix(_game.camera.combined);

				// Draw the capture Occluders texture to our 1D shadow map FBO
				_game.batch.draw(occludersFBO.getColorBufferTexture(), 0, 0, lightToRender.diameter, shadowMapFBO.getHeight());
			}
			// Flush batch
			_game.batch.end();
		}
		// Unbind shadow map FBO
		shadowMapFBO.end();

		// STEP 3: Render the blurred shadows using the 1D shadow map texture.

		// Reset projection matrix to screen
		_game.camera.setToOrtho(false);
		_game.viewport.setWorldSize(numCols, numRows);
		_game.viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
		_game.batch.setProjectionMatrix(_game.camera.combined);

		// Set the shader which will actually render the shadows and light
		_game.batch.setShader(shadowRenderShader);
		_game.batch.begin();
		{
			shadowRenderShader.setUniformf("lightCastLength", lightToRender.diameter);
			shadowRenderShader.setUniformf("softShadows", softShadows ? 1f : 0f);

			// Set the color of the light
			_game.batch.setColor(lightToRender.color);

			// draw centered on light position
			_game.batch.draw(shadowMapFBO.getColorBufferTexture(), lightToRender.bodyRef.getPosition().x - worldToViewport(lightToRender.diameter / 2),
				lightToRender.bodyRef.getPosition().y - worldToViewport(lightToRender.diameter / 2), worldToViewport(lightToRender.diameter), worldToViewport(lightToRender.diameter));
		}
		// Flush the batch before swapping shaders
		_game.batch.end();
	}

	private class Light {
		Body bodyRef;
		int diameter;
		Color color;

		public Light(Body bodyRef, int diameter, Color color) {
			this.bodyRef = bodyRef;
			this.diameter = diameter;
			this.color = color;
		}
	}
}




