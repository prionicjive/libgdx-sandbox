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
	private boolean[][] procGenMap;
	private boolean[][] procGenMapSnapshot;
	private short[][] floodFillMap;

	private Array<Array<Vector2>> chambers;
	private Array<Vector2> centralChamber;
	private boolean allChambersShouldConnect = true;
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
					resetMapForRendering(procGenMap);
					return true;
				}
				else if (key == Input.Keys.F) {
					detectAndConnectChambers(procGenMap, floodFillMap);
					resetMapForRendering(procGenMap);
					return true;
				}
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

		// Use procedural generation to initial smatter the level with tiles
		performRandomFillOfTileMap(procGenMap);

		// Generate a tilemap that can be used
		resetMapForRendering(procGenMap);
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

	private void resetMapForRendering(boolean[][] mapToRef) {
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

						// SHow a palette color only if we have one to use
						int palettePosition = (floodFillMap[y][x] - 1) % 16;
						if (palettePosition >= 0) {
							cell.setTile(new StaticTiledMapTile(splitPalette[0][palettePosition]));
							layer.setCell(x, y, cell);
						}
						else {
							layer.setCell(x, y, null);
						}
					}
				}
			}

			// Add to our set of layers
			tileMap.getLayers().add(layer);
		}

		// Lastly, create our special tile map renderer!
		tileMapRenderer = new OrthogonalTiledMapRenderer(tileMap);
	}

	private void iterateMap(boolean[][] mapToIterate, boolean[][] mapToUseAsSnapshot, boolean doLargeNeighborhoodCheck) {
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
		if (isOutOfBounds(col, row)) {
			return true;
		}
		else if(mapSnapshot[row][col]) {
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

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	private void detectAndConnectChambers(boolean[][] mapToRef, short[][] mapForFloodFill) {
		// Clear out any chamber data we have
		for (Array<Vector2> chamber: chambers) {
			chamber.clear();
		}
		chambers.clear();

		// Step through all empty tiles and determine what unique "chamber" they are a part of
		short fillNumber = 1; // Would be used to uniquely identify the specific cavern
		for (int c = 1; c < numCols - 1; c++) {
			for (int r = 1; r < numRows - 1; r++) {
				// If this tile is empty...
				if(!mapToRef[r][c] && mapForFloodFill[r][c] == 0) {
					// Construct a new chamber
					chambers.add(new Array<>());

					// Perform the actual flood fill (Recursively)
					performFloodFill(mapToRef, mapForFloodFill, c, r, fillNumber);

					// Bump the fill number to make the next discovered chamber unique
					fillNumber++;
				}
			}
		}

		if (allChambersShouldConnect) {
			// Determine the largest and thus "central" chamber
			centralChamber = determineLargestChamber(chambers);

			// Connect all chambers to the central chamber
//			connectAllChambers(chambers, centralChamber);
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

	private void performFloodFill(boolean[][] mapToRef, short[][] mapForFloodFill, int c, int r, short fillNumber) {
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
			performFloodFill(mapToRef, mapForFloodFill, c - 1, r, fillNumber);
		}

		// East
		if (c < numCols - 2) {
			performFloodFill(mapToRef, mapForFloodFill, c + 1, r, fillNumber);
		}

		// North
		if (r < numRows - 2) {
			performFloodFill(mapToRef, mapForFloodFill, c, r + 1, fillNumber);
		}

		// South
		if (r > 1) {
			performFloodFill(mapToRef, mapForFloodFill, c, r - 1, fillNumber);
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
			Vector2 goalCoord = chamber.get(MathUtils.random(chamber.size - 1));

			// Using A* pathfinding, create a path between the start and goal coordinates
			createAStarPath(startCoord, goalCoord);
		}
	}

	// `````````````````````````````````````

	private void createAStarPath(Vector2 startCoord, Vector2 goalCoord) {
//		// The set of nodes already evaluated
//		Array<Vector2> closedSet = new Array<>();
//
//		// The set of currently discovered nodes that are not evaluated yet.
//		// Initially, only the start node is known.
//		Array<Vector2> openSet = new Array<>();
//		openSet.add(startCoord);

//		// For each node, which node it can most efficiently be reached from.
//		// If a node can be reached from many nodes, cameFrom will eventually contain the
//		// most efficient previous step.
//		Vector2[] cameFrom = new Vector2[numRows * numCols];

//		// For each node, the cost of getting from the start node to that node.
//		gScore := map with default value of Infinity
//
//		// The cost of going from start to start is zero.
//		gScore[start] := 0
//
//		// For each node, the total cost of getting from the start node to the goal
//		// by passing by that node. That value is partly known, partly heuristic.
//		fScore := map with default value of Infinity
//
//		// For the first node, that value is completely heuristic.
//		fScore[start] := heuristic_cost_estimate(start, goal)
//
//		while openSet is not empty
//		current := the node in openSet having the lowest fScore[] value
//		if current = goal
//		return reconstruct_path(cameFrom, current)
//
//		openSet.Remove(current)
//		closedSet.Add(current)
//
//		for each neighbor of current
//		if neighbor in closedSet
//		continue		// Ignore the neighbor which is already evaluated.
//
//				// The distance from start to a neighbor
//				tentative_gScore := gScore[current] + dist_between(current, neighbor)
//
//		if neighbor not in openSet	// Discover a new node
//		openSet.Add(neighbor)
//            else if tentative_gScore >= gScore[neighbor]
//		continue		// This is not a better path.
//
//				// This path is the best until now. Record it!
//				cameFrom[neighbor] := current
//		gScore[neighbor] := tentative_gScore
//		fScore[neighbor] := gScore[neighbor] + heuristic_cost_estimate(neighbor, goal)
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


