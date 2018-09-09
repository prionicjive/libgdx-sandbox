package com.austinauts.libgdx.modules.tileengine.screens;

import com.austinauts.libgdx.AustinautsGame;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapRenderer;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.TimeUtils;

public class GameScreen extends ScreenAdapter {
	private final int BLOCK_SIZE = 16; // TODO Need to unify this with how big we select the tile texture to be

	// Reference to main game object
	private final AustinautsGame _game;

	// Timer and speed
	private long gameStartTime;
	private long elapsedGameTime;

	// --------------------
	// Game Entities
	// --------------------

	private TiledMap tileMap;
	private Array<Array<Vector2>> chambers;
	private Array<Vector2> centralChamber;
	private TiledMapRenderer tileMapRenderer;

	// --------------------
	// Scratch variables
	// --------------------
	private Vector3 touchPos = new Vector3();

	private BitmapFont bigFont;
	private BitmapFont smallFont;

	// ------------------------
	// Variable for map generation
	// ------------------------
	private TextureRegion[][] splitTiles;
	private TextureRegion[][] splitPalette;
	private int numIterations;
	private int numLayers;
	private int numCols;
	private int numRows;
	private int tileWidth;
	private int tileHeight;
	private int wallsThreshold;
	private final int CHANCE_OF_TILE = 45;
	private final String MAP_LAYER_NAME = "layer-main";

	public GameScreen(final AustinautsGame game) {
		_game = game;

		// Start the timer
		gameStartTime = TimeUtils.millis();

		// -------------------------------------
		// Set up scratch variables
		// -------------------------------------
		bigFont = _game.fontMap.get("munrosmall_30");
		smallFont = _game.fontMap.get("munrosmall_10");

		// ------------------------
		// Set up tile variables
		// ------------------------
		numIterations = 0;
		numLayers = 1;
		numCols = _game.masterWorldWidth / BLOCK_SIZE;
		numRows = _game.masterWorldHeight / BLOCK_SIZE;
		tileWidth = BLOCK_SIZE;
		tileHeight = BLOCK_SIZE;
		wallsThreshold = 4;

		// -------------------------------------
		// Set up the game entities
		// -------------------------------------

//		// Get our tilemap from the asset manager
//		tileMap = _game.assetManager.get(AustinautsGame.TILEMAP_SAMPLE_MAP);
//
//		// Lastly, create our special tile map renderer!
//		tileMapRenderer = new OrthogonalTiledMapRenderer(tileMap);

		// Set up our chambers map
		chambers = new Array<>();

		// Divide up the individual sprites in the sprite sheet
		splitTiles = TextureRegion.split(_game.assetManager.get(AustinautsGame.TILEMAP_SAMPLE_TILESET, Texture.class), BLOCK_SIZE, BLOCK_SIZE);
		splitPalette = TextureRegion.split(_game.assetManager.get(AustinautsGame.TILEMAP_SAMPLE_PALETTE, Texture.class), BLOCK_SIZE, BLOCK_SIZE);
		// Set up the map in random then calculated fashion
		randomFillMap();
		resetMapForRendering();

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
		tileMapRenderer.setView(_game.camera);
		tileMapRenderer.render();

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
					// TODO Do next generation of cellular automaton
					numIterations++;
					iterateMap();
					resetMapForRendering();
					return true;
				}
				else if (key == Input.Keys.F) {
					detectAndConnectChambers();
					resetMapForRendering();
					return true;
				}
				else if (key == Input.Keys.ESCAPE) {
					numIterations = 0;

					// Set up the map in random then calculated fashion
					randomFillMap();
					resetMapForRendering();

					return true;
				}

				return false;
			}
		});
	}





	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~




	private void randomFillMap() {
		if (tileMap != null) {
			tileMap.dispose();
			tileMap = null;
		}

		// Create an empty tile map
		tileMap = new TiledMap();

		// TODO Possibly determine a random sprite index to pull from
		int ty = 4; //(int)(Math.random() * splitTiles.length);
		int tx = 1; //(int)(Math.random() * splitTiles[ty].length);

		// Useful for constructing our map
		// TODO Maybe put elsewhere?

		// Let's create our layers
		for (int l = 0; l < numLayers; l++) {
			// Construct the layer to our params
			TiledMapTileLayer layer = new TiledMapTileLayer(numCols, numRows, tileWidth, tileHeight);
			layer.setName(MAP_LAYER_NAME);

			for (int x = 0; x < numCols; x++) {
				for (int y = 0; y < numRows; y++) {
					// Add a tile at the outer rows and edges
					// OR Determine if a tile should be randomly created at this cell
					if ((x == 0 || y == 0 || x == numCols - 1 || y == numRows - 1) ||
						(MathUtils.random(101) <= CHANCE_OF_TILE)){
						// Create a cell and set a tile to it
						// NOTE: Think of the cell as containing and determining how to render the Tile (Really just the image data)
						TiledMapTileLayer.Cell cell = new TiledMapTileLayer.Cell();
						cell.setTile(new StaticTiledMapTile(splitTiles[ty][tx]));

						// Lastly, set the cell at our current position
						layer.setCell(x, y, cell);
					}
					else {
						layer.setCell(x, y, null);
					}
				}
			}

			// Add to our set of layers
			tileMap.getLayers().add(layer);
		}

		// Lastly, create our special tile map renderer!
		tileMapRenderer = new OrthogonalTiledMapRenderer(tileMap);
	}

	private void resetMapForRendering() {

	}

	private void iterateMap() {
		// TODO Possibly determine a random sprite index to pull from
		int ty = 4; //(int)(Math.random() * splitTiles.length);
		int tx = 1; //(int)(Math.random() * splitTiles[ty].length);

		// Take a snapshot of the map to work off of. We don't sample and write to the same map!
		// TODO Needed some magic for this (Copying each array explicitly). Any better way to do this?
		int indexOfFetchedLayer = tileMap.getLayers().getIndex(MAP_LAYER_NAME); // TODO How to track map layer name with more than one layer?
		TiledMapTileLayer layer = (TiledMapTileLayer)tileMap.getLayers().get(indexOfFetchedLayer);

		// Make the snapshot
		TiledMapTileLayer layerSnapshot = new TiledMapTileLayer(numCols, numRows, tileWidth, tileHeight);

		for (int x = 0; x < numCols; x++) {
			for (int y = 0; y < numRows; y++) {
				TiledMapTileLayer.Cell oldCell = layer.getCell(x, y);
				TiledMapTileLayer.Cell newCell = oldCell != null ? new TiledMapTileLayer.Cell() : null;

				layerSnapshot.setCell(x, y, newCell);
			}
		}

		// Iterate through all tiles and do the magic!
		// TODO Better way other than this to not take into account the first and last row and column
		for (int c = 1; c < numCols - 1; c++) {
			for (int r = 1; r < numRows - 1; r++) {
				int numNeighborWalls = getNeighborWalls(layerSnapshot, c, r, 1, 1);

				// If this tile is a wall...
				if(layerSnapshot.getCell(c, r) != null) {
					// If at least WALLS_THRESHOLD are neighboring, keep this a wall
					if (numNeighborWalls >= wallsThreshold) {
						TiledMapTileLayer.Cell cell = layerSnapshot.getCell(c, r);

						// Create cell if we don't have it
						if (cell == null) {
							cell = new TiledMapTileLayer.Cell();
						}
						cell.setTile(new StaticTiledMapTile(splitTiles[ty][tx]));

						// Set this cell in the layer
						layer.setCell(c, r, cell);
					}
					else {
						layer.setCell(c, r,null);
					}
				}
				// If it is not a wall, make it a wall if there are WALLS_THRESHOLD walls neighboring
				else {
					if(numNeighborWalls > wallsThreshold) {
						TiledMapTileLayer.Cell cell = layerSnapshot.getCell(c, r);

						// Create cell if we don't have it
						if (cell == null) {
							cell = new TiledMapTileLayer.Cell();
						}
						cell.setTile(new StaticTiledMapTile(splitTiles[ty][tx]));

						// Set this cell in the layer
						layer.setCell(c, r, cell);
					}
					else {
						layer.setCell(c, r,null);
					}
				}
			}
		}

		tileMap.getLayers().remove(indexOfFetchedLayer);
		tileMap.getLayers().add(layer);
	}

	private void detectAndConnectChambers() {
		// Take a snapshot of the map to work off of. We don't sample and write to the same map!
		// TODO Needed some magic for this (Copying each array explicitly). Any better way to do this?
		int indexOfFetchedLayer = tileMap.getLayers().getIndex(MAP_LAYER_NAME); // TODO How to track map layer name with more than one layer?
		TiledMapTileLayer layer = (TiledMapTileLayer)tileMap.getLayers().get(indexOfFetchedLayer);

		// Make the snapshot
		TiledMapTileLayer layerSnapshot = new TiledMapTileLayer(numCols, numRows, tileWidth, tileHeight);

		for (int x = 0; x < numCols; x++) {
			for (int y = 0; y < numRows; y++) {
				TiledMapTileLayer.Cell oldCell = layer.getCell(x, y);
				TiledMapTileLayer.Cell newCell = oldCell != null ? new TiledMapTileLayer.Cell() : null;

				layerSnapshot.setCell(x, y, newCell);
			}
		}

		// ~~~~~~~~~~~~

		// Clear out any chamber data we have
		for (Array<Vector2> chamber: chambers) {
			chamber.clear();
		}
		chambers.clear();

		// Step through all empty tiles and determine what unique "chamber" they are a part of
		int fillNumber = 0; // Would be used to uniquely identify the specific cavern
		for (int c = 1; c < numCols - 1; c++) {
			for (int r = 1; r < numRows - 1; r++) {
				// If this tile is empty...
				if(layerSnapshot.getCell(c, r) == null) {
					// Construct a new chamber
					chambers.add(new Array<>());

					// TODO Possibly determine a random sprite index to pull from
					int ty = 0;
					int tx = fillNumber % splitPalette[ty].length;

					// Perform the actual flood fill (Recursively)
					performFloodFill(layer, c, r, fillNumber, tx, ty);

					// Bump the fill number to make the next discovered chamber unique
					fillNumber++;
				}
			}
		}

		// Determine the largest and thus "central" chamber
		centralChamber = determineCentralChamber(chambers);

		tileMap.getLayers().remove(indexOfFetchedLayer);
		tileMap.getLayers().add(layer);
	}

	private Array<Vector2> determineCentralChamber(Array<Array<Vector2>> chambersToExamine) {
		int largestChamberSizeSoFar = 0;
		Array<Vector2> largestChamber = null;

		for (Array<Vector2> chamber : chambersToExamine) {
			if (chamber.size > largestChamberSizeSoFar) {
				largestChamberSizeSoFar = chamber.size;
				largestChamber = chamber;
			}
		}

		return largestChamber;
	}

	private void performFloodFill(TiledMapTileLayer layer, int c, int r, int fillNumber, int tx, int ty) {
		/*
			From Wikipedia on flood fill...
			1. If the color of node is not equal to target-color, return.
            2. Set the color of node to replacement-color.
            3. Perform Flood-fill (one step to the west of node, target-color, replacement-color).
                Perform Flood-fill (one step to the east of node, target-color, replacement-color).
                Perform Flood-fill (one step to the north of node, target-color, replacement-color).
                Perform Flood-fill (one step to the south of node, target-color, replacement-color).
            4. Return.
        */
		TiledMapTileLayer.Cell cell = layer.getCell(c, r);

		// Don't go any further if this is actually a tile
		if (cell != null) {
			return;
		}

		// Create the cell and set its tile properly
		cell = new TiledMapTileLayer.Cell();
		StaticTiledMapTile tile = new StaticTiledMapTile(splitPalette[ty][tx]);
		tile.getProperties().put("fillNumber", fillNumber);
		cell.setTile(tile);

		// Set this cell in the layer
		layer.setCell(c, r, cell);

		// NOW... <breathe>... add this coordinate to the chamber
		chambers.get(chambers.size - 1).add(new Vector2(c, r));

		// Lastly, check WESN and recursively fill
		if (c > 1) {
			performFloodFill(layer, c - 1, r, fillNumber, tx, ty);
		}

		if (c < numCols - 2) {
			performFloodFill(layer, c + 1, r, fillNumber, tx, ty);
		}

		if (r < numRows - 2) {
			performFloodFill(layer, c, r + 1, fillNumber, tx, ty);
		}

		if (r > 1) {
			performFloodFill(layer, c, r - 1, fillNumber, tx, ty);
		}


	}

	private int getNeighborWalls(TiledMapTileLayer layerSnapshot, int col, int row, int scopeX, int scopeY) {
		int startX = col - scopeX;
		int startY = row - scopeY;
		int endX = col + scopeX;
		int endY = row + scopeY;

		int wallCounter = 0;

		for(int iY = startY; iY <= endY; iY++) {
			for(int iX = startX; iX <= endX; iX++) {
				if(!(iX==col && iY==row)) {
					if (isWall(layerSnapshot, iX, iY)) {
						wallCounter++;
					}
				}
			}
		}
		return wallCounter;
	}

	private boolean isWall(TiledMapTileLayer layerSnapshot, int col, int row) {
		// Consider out-of-bound a wall
		if (isOutOfBounds(col, row)) {
			return true;
		}
		else if(layerSnapshot.getCell(col, row) != null) {
			return true;
		}

		return false;
	}

	private boolean isOutOfBounds(int col, int row){
		// The edges are considered out of bounds
		if( col <= 0 || row <= 0) {
			return true;
		}
		else if( col >= numCols - 1 || row >= numRows - 1) {
			return true;
		}

		return false;
	}






	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~





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


