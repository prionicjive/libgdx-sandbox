package com.austinauts.libgdx.modules.tileengine.screens;

import com.austinauts.libgdx.AustinautsGame;
import com.austinauts.libgdx.common.utils.PathingHelper;
import com.austinauts.libgdx.common.utils.ShaderHelper;
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
import com.badlogic.gdx.utils.ObjectMap;
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
	private boolean[][] procGenMap;
	private boolean[][] procGenMapSnapshot;
	private short[][] floodFillMap;

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
	private final String MAP_LAYER_NAME = "layer-main";

	private final int initialChanceOfTile = 45;
	private final int birthThreshold = 5;
	private final int surviveThreshold = 4;
	private final int largeSpaceThreshold = 2;
	private final int numCarvingPasses = 4;
	private final int numSmoothingPasses = 3;
	private final int neighborhoodScope = 1;
	private final int largeNeighborhoodScope = 2;

	// TODO Better way to store this
	Vector2 entranceCoord;
	Vector2 exitCoord;
	int minDistanceBetweenEntranceAndExit;
	Array<Vector2> collectibles;
	int numCollectibles;
	int nestledCollectibleNeighborThreshold;

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

		// Determine the minimum distance (Manhattan) between entrance and exit
		minDistanceBetweenEntranceAndExit = 50;

		// Set up the array of collectibles
		collectibles = new Array<>();
		numCollectibles = 10;
		nestledCollectibleNeighborThreshold = 6;

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
					iterateMap(procGenMap, procGenMapSnapshot, false);;
					resetMapForRendering(procGenMap, false);
					return true;
				}
				// Show each chamber with a unique color
				else if (key == Input.Keys.F) {
					detectAndConnectChambers(procGenMap, floodFillMap, false);
					resetMapForRendering(procGenMap, true);
					return true;
				}
				// Connect all chambers
				else if (key == Input.Keys.P) {
					detectAndConnectChambers(procGenMap, floodFillMap, true);
					resetMapForRendering(procGenMap, false);
					return true;
				}
				// Connect all chambers and place start / finish points
				else if (key == Input.Keys.S) {
					detectAndConnectChambers(procGenMap, floodFillMap, true);

					// Determine entrance and exit
					// TODO Need to make sure central chamber isn't just expected all willy nilly
					Array<Vector2> entranceExit = determineEntranceAndExitForChamber(centralChamber);

					if (entranceExit.size == 2) {
						entranceCoord = entranceExit.get(0);
						exitCoord = entranceExit.get(1);
					}

					resetMapForRendering(procGenMap, false);
					return true;
				}
				// Determine where random collectibles should go
				else if (key == Input.Keys.C) {
					calculateCollectibleLocations(collectibles, centralChamber);
					resetMapForRendering(procGenMap, false);
					return true;
				}
				// Determine where random NESTLED collectibles should go
				else if (key == Input.Keys.N) {
					calculateNestledCollectibleLocations(procGenMap, collectibles);
					resetMapForRendering(procGenMap, false);
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
		tileMap = new TiledMap();
		procGenMap = new boolean[numRows][numCols];
		procGenMapSnapshot = new boolean[numRows][numCols];
		floodFillMap = new short[numRows][numCols];

		// Having to flip the y to match world contents
		for (int y = numRows - 1; y >= 0; y--) {
			for (int x = 0; x < numCols; x++) {
				// Initialize the current tile
				procGenMap[y][x] = false;
				procGenMapSnapshot[y][x] = false;
				floodFillMap[y][x] = 0;
			}
		}
	}

	private void generateNewLevel() {
		// Reset the number of iterations
		numIterations = 0;

		// TODO Better place to null this out
		entranceCoord = null;
		exitCoord = null;

		// TODO Better place to clear this
		collectibles.clear();

		// Use procedural generation to initial smatter the level with tiles
		performRandomFillOfTileMap(procGenMap);

		// Generate a tilemap that can be used
		resetMapForRendering(procGenMap, false);
	}

	private void performRandomFillOfTileMap(boolean[][] mapToFill) {
		// Having to flip the y to match world contents
		for (int y = numRows - 1; y >= 0; y--) {
			for (int x = 0; x < numCols; x++) {
				// Add a tile at the outer rows and edges
				if (x == 0 || y == 0 || x == numCols - 1 || y == numRows - 1) {
					mapToFill[y][x] = true;
					floodFillMap[y][x] = -1;
				}
				// OR determine if a tile should be randomly created at this cell
				else if (MathUtils.random(1, 100) <= initialChanceOfTile) {
					mapToFill[y][x] = true;
					floodFillMap[y][x] = -1;
				}
				else {
					mapToFill[y][x] = false;
					floodFillMap[y][x] = 0;
				}
			}
		}
	}

	private void resetMapForRendering(boolean[][] mapToRef, boolean showFloodFill) {
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
					if (mapToRef[y][x]) {
						// Create a cell and set a tile to it
						// NOTE: Think of the cell as containing and determining how to render the Tile (Really just the image data)
						TiledMapTileLayer.Cell cell = new TiledMapTileLayer.Cell();
						cell.setTile(new StaticTiledMapTile(splitTiles[ty][tx]));

						// Lastly, set the cell at our current position
						layer.setCell(x, y, cell);
					}
					else {
						// Create a cell and set a palette tile to it
						// NOTE: Think of the cell as containing and determining how to render the Tile (Really just the image data)
						TiledMapTileLayer.Cell cell = new TiledMapTileLayer.Cell();

						if (showFloodFill) {
							// Show a palette color only if we have one to use
							int palettePosition = (floodFillMap[y][x] - 1) % 15;
							if (palettePosition >= 0) {
								cell.setTile(new StaticTiledMapTile(splitPalette[0][palettePosition]));
								layer.setCell(x, y, cell);
							}
							else {
								layer.setCell(x, y, null);
							}
						}
						else {
							layer.setCell(x, y, null);
						}
					}
				}
			}

			// If the entrance and exit have been placed, make them visible!

			if (entranceCoord != null && exitCoord != null) {
				// Show a palette color only if we have one to use
				int entranceIndex = 2;
				int exitIndex = 0;

				TiledMapTileLayer.Cell entranceCell = new TiledMapTileLayer.Cell();
				entranceCell.setTile(new StaticTiledMapTile(splitPalette[0][entranceIndex]));
				layer.setCell((int)entranceCoord.x, (int)entranceCoord.y, entranceCell);

				TiledMapTileLayer.Cell exitCell = new TiledMapTileLayer.Cell();
				exitCell.setTile(new StaticTiledMapTile(splitPalette[0][exitIndex]));
				layer.setCell((int)exitCoord.x, (int)exitCoord.y, exitCell);
			}

			if (collectibles.size > 0) {
				// Show a palette color only if we have one to use
				int collectibleIndex = 4;

				TiledMapTileLayer.Cell collectibleCell = new TiledMapTileLayer.Cell();
				collectibleCell.setTile(new StaticTiledMapTile(splitPalette[0][collectibleIndex]));

				for (Vector2 collectible : collectibles) {
					layer.setCell((int) collectible.x, (int) collectible.y, collectibleCell);
				}
			}

			// Add to our set of layers
			tileMap.getLayers().add(layer);
		}

		// Lastly, create our special tile map renderer!
		tileMapRenderer = new OrthogonalTiledMapRenderer(tileMap);
	}

	private void iterateMap(boolean[][] mapToIterate, boolean[][] mapToUseAsSnapshot, boolean doLargeNeighborhoodCheck) {
		// TODO Better place to null this out
		entranceCoord = null;
		exitCoord = null;

		// TODO Better place to clear this
		collectibles.clear();

		// Set the snapshot's tiles to mirror that of the actual tileMap's tiles
		// TODO Can this be combine with other iteration so the whole map doesn't have to be walked multiple times
		for (int y = numRows - 1; y >= 0; y--) {
			for (int x = 0; x < numCols; x++) {
				mapToUseAsSnapshot[y][x] = mapToIterate[y][x];
			}
		}
	
		// Iterate through all tiles and do the magic!
		// TODO Better way other than this to not take into account the first and last row and column
		for (int r = numRows - 2; r >= 1; r--) {
			for (int c = 1; c < numCols - 1; c++) {
				int numTilesInNeighborhood = getNeighborTiles(mapToUseAsSnapshot, c, r, neighborhoodScope);

				// If this tile is a tile...
				if(mapToUseAsSnapshot[r][c]) {
					// If at least the surviveThreshold of neighbors are tiles, it stays alive
					if (numTilesInNeighborhood >= surviveThreshold) {
						mapToIterate[r][c] = true;
						floodFillMap[r][c] = -1;
					}
					// Otherwise, kill it
					else {
						mapToIterate[r][c] = false;
						floodFillMap[r][c] = 0;
					}
				}
				// If at least the birthThreshold of neighbors are tiles OR there are barely any tiles around in the larger neighborhood, give this tile the gift of life
				else {
					int numTilesInLargeNeighborhood = getNeighborTiles(mapToUseAsSnapshot, c, r, largeNeighborhoodScope);

					if (numTilesInNeighborhood >= birthThreshold || (doLargeNeighborhoodCheck && numTilesInLargeNeighborhood <= largeSpaceThreshold)) {
						mapToIterate[r][c] = true;
						floodFillMap[r][c] = -1;
					}
					// Otherwise, it stays dead
					else {
						mapToIterate[r][c] = false;
						floodFillMap[r][c] = 0;
					}
				}
			}
		}

		numIterations++;
	}

	private int getNeighborTiles(boolean[][] mapSnapshot, int col, int row, int scope) {
		int startX = col - scope;
		int startY = row - scope;
		int endX = col + scope;
		int endY = row + scope;

		int tileCounter = 0;

		for(int iY = startY; iY <= endY; iY++) {
			for(int iX = startX; iX <= endX; iX++) {
				if(!(iX==col && iY==row)) {
					if (isTile(mapSnapshot, iX, iY)) {
						tileCounter++;
					}
				}
			}
		}
		return tileCounter;
	}

	private int getNeighborTilesNSEW(boolean[][] mapSnapshot, int col, int row) {
		int tileCounter = 0;

		// North
		if (isTile(mapSnapshot, col, row + 1)) {
			tileCounter++;
		}

		// South
		if (isTile(mapSnapshot, col, row - 1)) {
			tileCounter++;
		}

		// East
		if (isTile(mapSnapshot, col + 1, row)) {
			tileCounter++;
		}

		// West
		if (isTile(mapSnapshot, col - 1, row)) {
			tileCounter++;
		}

		return tileCounter;
	}

	private boolean isTile(boolean[][] mapSnapshot, int col, int row) {
		// Consider out-of-bound a tile
		if (PathingHelper.isOutOfBounds(col, row, numCols, numRows)) {
			return true;
		}
		else if(mapSnapshot[row][col]) {
			return true;
		}

		return false;
	}

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	private void detectAndConnectChambers(boolean[][] mapToRef, short[][] mapForFloodFill, boolean allChambersShouldConnect) {
		// Clear out any chamber data we have
		for (Array<Vector2> chamber: chambers) {
			chamber.clear();
		}
		chambers.clear();

		// TODO Best place to clear out entrance and exit?
		entranceCoord = null;
		exitCoord = null;

		// TODO Pull out elsewhere
		for (int c = 1; c < numCols - 1; c++) {
			for (int r = 1; r < numRows - 1; r++) {
				if (mapToRef[r][c]) {
					floodFillMap[r][c] = -1;
				}
				// Otherwise, kill it
				else {
					floodFillMap[r][c] = 0;
				}
			}
		}

		// Step through all empty tiles and determine what unique "chamber" they are a part of
		short fillNumber = 1; // Would be used to uniquely identify the specific cavern
		for (int c = 1; c < numCols - 1; c++) {
			for (int r = 1; r < numRows - 1; r++) {
				// If this tile is empty and hasn't been tested yet...
				if(!mapToRef[r][c] && mapForFloodFill[r][c] == 0) {
					// Construct a new chamber
					chambers.add(new Array<>());

					// Perform the actual flood fill (Recursively)
					performFloodFill(mapForFloodFill, c, r, fillNumber);

					// Bump the fill number to make the next discovered chamber unique
					fillNumber++;
				}
			}
		}

		if (allChambersShouldConnect) {
			// Determine the largest and thus "central" chamber
			centralChamber = determineLargestChamber(chambers);

			// Connect all chambers to central chamber, making a single chamber
			connectAllChambers(chambers, centralChamber);
		}
	}

	private Array<Vector2> determineLargestChamber(Array<Array<Vector2>> chambersToExamine) {
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

	private void performFloodFill(short[][] mapForFloodFill, int c, int r, short fillNumber) {
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

		// Don't go any further if this is actually a tile
		if (mapForFloodFill[r][c] != 0) {
			return;
		}

		// Set a fillNumber in the flood fill map
		mapForFloodFill[r][c] = fillNumber;

		// NOW... <breathe>... add this coordinate to the chamber
		chambers.get(chambers.size - 1).add(new Vector2(c, r));

		// Lastly, check NSEW and recursively fill

		// West
		if (c > 1) {
			performFloodFill(mapForFloodFill, c - 1, r, fillNumber);
		}

		// East
		if (c < numCols - 2) {
			performFloodFill(mapForFloodFill, c + 1, r, fillNumber);
		}

		// North
		if (r < numRows - 2) {
			performFloodFill(mapForFloodFill, c, r + 1, fillNumber);
		}

		// South
		if (r > 1) {
			performFloodFill(mapForFloodFill, c, r - 1, fillNumber);
		}

	}

	private void connectAllChambers(Array<Array<Vector2>> chambersToConnect) {
		Array<Vector2> largestChamber = determineLargestChamber(chambers);
		connectAllChambers(chambersToConnect, largestChamber);
	}

	private void connectAllChambers(Array<Array<Vector2>> chambersToConnect, Array<Vector2> largestChamber) {
		for (Array<Vector2> chamber : chambersToConnect) {
			// If the current chamber is really the largest chamber, skip it
			if (chamber == largestChamber) {
				continue;
			}

			// Determine start coordinate (Random coordinate in this chamber)
			Vector2 startCoord = chamber.get(MathUtils.random(chamber.size - 1));

			// Determine goal coordinate (Random coordinate in the largest chamber)
			Vector2 goalCoord = largestChamber.get(MathUtils.random(largestChamber.size - 1));

			// Using A* pathfinding, create a path between the start and goal coordinates
			Array<Vector2> calculatedPath = PathingHelper.findAStarPath(procGenMap, startCoord, goalCoord);

			// Finally, use the calculated path to connect the chamber to the largest one
			if (calculatedPath != null) {
				for (Vector2 currCoord : calculatedPath) {
					procGenMap[(int) currCoord.y][(int) currCoord.x] = false;
					floodFillMap[(int) currCoord.y][(int) currCoord.x] = 15;
				}
			}
		}

		// Make sure the now SINGLE chamber has all of the open coords in it... to reference elsewhere if need be
		chambers.clear();
		centralChamber.clear();

		// TODO Consider return a new chamber that can be used so that this function is more "pure"
		for (int y = numRows - 1; y >= 0; y--) {
			for (int x = 0; x < numCols; x++) {
				if (!procGenMap[y][x] ) {
					centralChamber.add(new Vector2(x, y));
				}
			}
		}

		chambers.add(centralChamber);
	}

	private Array<Vector2> determineEntranceAndExitForChamber( Array<Vector2> chamber) {
		Vector2 entrance = null;
		Vector2 exit = null;

		// Make a copy of all coords from the chamber
		Array<Vector2> allPossibleCoords = new Array<>(chamber);
		entrance =  allPossibleCoords.removeIndex(MathUtils.random(allPossibleCoords.size - 1));

		// While not perfect, we will go so far as to pick 2 random points and test if they meet the distance criteria
		// If they don't we will indicate that the level should maybe be
		while (allPossibleCoords.size > 0) {
			// TODO Use a distance to determine and check if the exit works
			exit = allPossibleCoords.removeIndex(MathUtils.random(allPossibleCoords.size - 1));

			// IF the distance check is satisfied, break!
			if (PathingHelper.distanceBetween(entrance, exit) >= minDistanceBetweenEntranceAndExit) {
				break;
			}
		}

		// If we get here, we've exhausted do our reasonable attempt so just use the last entrance / exit pair we decided to test
		Array<Vector2> entranceExit = new Array<>();

		if (entrance != null) {
			entranceExit.add(entrance);
		}

		if (exit != null) {
			entranceExit.add(exit);
		}

		return entranceExit;
	}

	private void calculateCollectibleLocations(Array<Vector2> collectibleLocations, Array<Vector2> chamber) {
		// TODO Best to not change this via its reference
		// TODO Not consistent with either using parameter or global values
		if (collectibleLocations != null && chamber != null && chamber.size >= numCollectibles) {
			// Clear out what we do have
			collectibleLocations.clear();

			// Possible locations to put things
			Array<Vector2> possibleLocations = new Array<>(chamber);

			// TODO Keep in mind... we don't wanna spawn on player, other collectibles, entrance, exit, etc
			// TODO Best to just have map to reference have an enum for tiles to more easily check what is there
			for (int i = 0; i < numCollectibles; i++) {
				collectibleLocations.add(possibleLocations.removeIndex(MathUtils.random(possibleLocations.size - 1)));
			}
		}
	}

	private void calculateNestledCollectibleLocations(boolean[][] mapToRef, Array<Vector2> collectibleLocations) {
		// TODO Best to not change this via its reference
		// TODO Not consistent with either using parameter or global values
		if (collectibleLocations != null) {
			// Clear out what we do have
			collectibleLocations.clear();

			for (int r = numRows - 2; r >= 1; r--) {
				for (int c = 1; c < numCols - 1; c++) {
					// If this tile is empty...
					if(!mapToRef[r][c]) {
						// Count the number of tiles in the neighborhood
						int numTilesInNeighborhood = getNeighborTiles(mapToRef, c, r, 1);

						if (numTilesInNeighborhood >= nestledCollectibleNeighborThreshold) {
							collectibleLocations.add(new Vector2(c, r));
						}
					}
				}
			}
		}
	}

	// `````````````````````````````````````





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


