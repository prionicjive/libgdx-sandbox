package com.austinauts.libgdx.modules.tilemap.screens;

import com.austinauts.libgdx.AustinautsGame;
import com.austinauts.libgdx.modules.tilemap.tilemap.ProcGenMap;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.TimeUtils;

public class GameScreen extends ScreenAdapter {
	private final int BLOCK_SIZE = 16; // TODO Need to unify this with how big we select the tile texture to be

	// Reference to main game object
	private final AustinautsGame _game;

	// Timer and speed
	private long gameStartTime;
	private long elapsedGameTime;

	// --------------------
	// Scratch variables
	// --------------------
	private Vector3 touchPos = new Vector3();

	private BitmapFont bigFont;
	private BitmapFont smallFont;

	// ------------------------
	// Variable for map generation
	// ------------------------
	private ProcGenMap procGenMap;
	private TextureRegion[][] splitTiles;
	private TextureRegion[][] splitPalette;

	public GameScreen(final AustinautsGame game) {
		_game = game;

		// Start the timer
		gameStartTime = TimeUtils.millis();

		// -------------------------------------
		// Set up scratch variables
		// -------------------------------------
		bigFont = _game.fontMap.get("munrosmall_30");
		smallFont = _game.fontMap.get("munrosmall_10");

		// -------------------------------------
		// Set up the game entities
		// -------------------------------------

//		// Get our tilemap from the asset manager
//		tileMap = _game.assetManager.get(AustinautsGame.TILEMAP_SAMPLE_MAP);
//
//		// Lastly, create our special tile map renderer!
//		tileMapRenderer = new OrthogonalTiledMapRenderer(tileMap);


		// Divide up the individual sprites in the sprite sheet
		splitTiles = TextureRegion.split(_game.assetManager.get(AustinautsGame.TILEMAP_SAMPLE_TILESET, Texture.class), BLOCK_SIZE, BLOCK_SIZE);
		splitPalette = TextureRegion.split(_game.assetManager.get(AustinautsGame.TILEMAP_SAMPLE_PALETTE, Texture.class), BLOCK_SIZE, BLOCK_SIZE);

		// Initialize and generate a new level
		initializeTileMap();
		generateNewLevel();

		// Now, before we get this going, set up our input handling
		// TODO Can we streamline this? Maybe making a default game screen?
		initializeInputProcessor();
	}

	@Override
	public void render(float delta) {
		// Outline of game loop:
		//
		// 1) Update camera
		// 2) Handle input and update entities
		// 3) Process collision / physics
		// 4) Render

		// Check the timer
		checkTimer();

		// Update game entities
		updateEntities(delta);

		// Process collision / physics
		processCollision();

		// Clear the backbuffer
		Gdx.gl.glClearColor(.1f, .1f, .1f, 1);
		Gdx.gl.glClear(GL30.GL_COLOR_BUFFER_BIT);

		// Render the tilemap
		// TODO Have as part of the tilemap?
		procGenMap.getTileMapRenderer().setView(_game.camera);
		procGenMap.getTileMapRenderer().render();

		_game.batch.begin();

		// TODO Do rendering here

		_game.batch.end();
	}

	// ------------------------
	// Private methods
	// ------------------------

	private void initializeInputProcessor() {
		// TODO Put InputProcessing in another place?
		Gdx.input.setInputProcessor(new InputAdapter() {
			public boolean keyDown(int key) {
				if (key == Input.Keys.SPACE) {
					procGenMap.iterateMap(false);
					;
					procGenMap.resetRenderLayer(false);
					return true;
				}
				// Show each chamber with a unique color
				else if (key == Input.Keys.F) {
					procGenMap.detectAndConnectChambers(false);
					procGenMap.resetRenderLayer(true);
					return true;
				}
				// Connect all chambers
				else if (key == Input.Keys.P) {
					procGenMap.detectAndConnectChambers(true);
					procGenMap.resetRenderLayer(false);
					return true;
				}
				// Connect all chambers and place start / finish points
				else if (key == Input.Keys.S) {
					procGenMap.detectAndConnectChambers(true);
					procGenMap.calculateEntranceAndExit(); // TODO What to do if entrance and exit can't be found?
					procGenMap.resetRenderLayer(false);
					return true;
				}
				// Determine where random collectibles should go
				else if (key == Input.Keys.C) {
					procGenMap.detectAndConnectChambers(true);
					procGenMap.calculateCollectibleLocations();
					procGenMap.resetRenderLayer(false);
					return true;
				}
				// Determine where random NESTLED collectibles should go
				else if (key == Input.Keys.N) {
					procGenMap.detectAndConnectChambers(true);
					procGenMap.calculateNestledCollectibleLocations();
					procGenMap.resetRenderLayer(false);
					return true;
				}
				// Generate a new level with just a simple smattering of tiles
				else if (key == Input.Keys.ESCAPE) {
					generateNewLevel();

					return true;
				}

				return false;
			}
		});
	}

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	private void initializeTileMap() {
		int numCols = _game.masterWorldWidth / BLOCK_SIZE;
		int numRows = _game.masterWorldHeight / BLOCK_SIZE;

		// TODO Build out a better constructed map
		// TODO Ugh... shouldn't be passing any texture data (I believe...)
		procGenMap = new ProcGenMap(numCols, numRows, 45, BLOCK_SIZE, splitTiles, splitPalette);
		procGenMap.initialize();
	}

	private void generateNewLevel() {
		// Use procedural generation to initial smatter the level with tiles
		procGenMap.resetWithRandomFill();

		// Generate a tilemap that can be used
		procGenMap.resetRenderLayer(false);
	}

	// ````````````````````````````````

	private void checkTimer() {
		// Capture the total time since the beginning of play
		long currTime = TimeUtils.millis();
		elapsedGameTime = currTime - gameStartTime;
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

			// TODO Do other things if need be
		}
	}

	private void processCollision() {
		// TODO Process collision
	}
}


