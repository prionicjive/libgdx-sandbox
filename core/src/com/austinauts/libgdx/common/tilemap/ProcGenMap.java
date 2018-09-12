package com.austinauts.libgdx.common.tilemap;

import com.austinauts.libgdx.common.utils.PathingHelper;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapRenderer;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

public class ProcGenMap {
	private TiledMap tileMap; // For rendering
	private Tile[][] procGenMap; // Representing out actual data
	private Tile[][] procGenMapSnapshot; // Scratch snapshot in order to prevent reallocation every iteration

	private int numCols;
	private int numRows;

	// TODO Try to remove these
	private int tileSize;
	private final TextureRegion[][] splitTiles;
	private final TextureRegion[][] splitPalette;

	// TODO Need to either be passed to the constructor
	private final int initialChanceOfWall;
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

	private Array<Array<Vector2>> chambers;
	private Array<Vector2> centralChamber;
	private TiledMapRenderer tileMapRenderer;

	public ProcGenMap(int numCols, int numRows, int initialChanceOfTile, int tileSize, TextureRegion[][] splitTiles, TextureRegion[][] splitPalette) {
		this.numCols = numCols;
		this.numRows = numRows;
		this.initialChanceOfWall = initialChanceOfTile;
		this.tileSize = tileSize;
		this.splitTiles = splitTiles; // TODO Have single sprite map
		this.splitPalette = splitPalette;

		tileMap = new TiledMap();

		procGenMap = new Tile[numRows][numCols];
		procGenMapSnapshot = new Tile[numRows][numCols];
		// Having to flip the y to match world contents
		for (int y = numRows - 1; y >= 0; y--) {
			for (int x = 0; x < numCols; x++) {
				// Reset the current tile
				procGenMap[y][x] = new Tile();
				procGenMapSnapshot[y][x] = new Tile();
			}
		}

		// Determine the minimum distance (Manhattan) between entrance and exit
		minDistanceBetweenEntranceAndExit = 50;

		// Set up the array of collectibles
		collectibles = new Array<>();
		numCollectibles = 10;
		nestledCollectibleNeighborThreshold = 5;

		// Set up our chambers map
		chambers = new Array<>();
	}

	// TODO Rename to reset?
	public void initialize() {
		// Having to flip the y to match world contents
		for (int y = numRows - 1; y >= 0; y--) {
			for (int x = 0; x < numCols; x++) {
				// Reset the current tile
				procGenMap[y][x].reset();
			}
		}
	}

	public void resetWithRandomFill() {
		// TODO Anything magical with tiles?
		resetObjects();

		// Having to flip the y to match world contents
		for (int y = numRows - 1; y >= 0; y--) {
			for (int x = 0; x < numCols; x++) {
				// Add a wall at the outer rows and edges
				if (x == 0 || y == 0 || x == numCols - 1 || y == numRows - 1) {
					procGenMap[y][x].type = TileType.WALL;
					procGenMap[y][x].associatedChamber = -1;
				}
				// OR determine if a wall should be randomly created at this tile
				else if (MathUtils.random(1, 100) <= initialChanceOfWall) {
					procGenMap[y][x].type = TileType.WALL;
					procGenMap[y][x].associatedChamber = -1;
				}
				else {
					procGenMap[y][x].type = TileType.EMPTY;
					procGenMap[y][x].associatedChamber = 0;
				}
			}
		}
	}

	private void resetObjects() {
		// TODO Better place to null this out
		entranceCoord = null;
		exitCoord = null;

		// TODO Better place to clear this
		collectibles.clear();
	}

	public void resetRenderLayer(boolean showFloodFill) {
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


		// Construct the layer to our params
		TiledMapTileLayer layer = new TiledMapTileLayer(numCols, numRows, tileSize, tileSize);
		layer.setName("layer-main"); // TODO Any need to make this more configurable

		for (int x = 0; x < numCols; x++) {
			for (int y = 0; y < numRows; y++) {
				if (procGenMap[y][x].type == TileType.WALL) {
					// Create a cell and set a wall to it
					// NOTE: Think of the cell as containing and determining how to render the wall (Really just the image data)
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
						// TODO Get rid of this... this is uber hacky
						int palettePosition = (procGenMap[y][x].associatedChamber - 1) % 15;
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

	public void iterateMap(boolean doLargeNeighborhoodCheck) {
		resetObjects();

		// Set the snapshot's tiles to mirror that of the actual tileMap's tiles
		// TODO Can this be combine with other iteration so the whole map doesn't have to be walked multiple times
		for (int y = numRows - 1; y >= 0; y--) {
			for (int x = 0; x < numCols; x++) {
				procGenMapSnapshot[y][x].type = procGenMap[y][x].type;
				procGenMapSnapshot[y][x].associatedChamber = procGenMap[y][x].associatedChamber;
			}
		}

		// Iterate through all tiles and do the magic!
		// TODO Better way other than this to not take into account the first and last row and column
		for (int r = numRows - 2; r >= 1; r--) {
			for (int c = 1; c < numCols - 1; c++) {
				int numTilesInNeighborhood = getNeighborTiles(procGenMapSnapshot, c, r, neighborhoodScope);

				// If this tile is a tile...
				if(procGenMapSnapshot[r][c].type == TileType.WALL) {
					// If at least the surviveThreshold of neighbors are tiles, it stays alive
					if (numTilesInNeighborhood >= surviveThreshold) {
						procGenMap[r][c].type = TileType.WALL;
						procGenMap[r][c].associatedChamber = -1;
					}
					// Otherwise, kill it
					else {
						procGenMap[r][c].type = TileType.EMPTY;
						procGenMap[r][c].associatedChamber = 0;
					}
				}
				// If at least the birthThreshold of neighbors are tiles OR there are barely any tiles around in the larger neighborhood, give this tile the gift of life
				else {
					int numTilesInLargeNeighborhood = getNeighborTiles(procGenMapSnapshot, c, r, largeNeighborhoodScope);

					if (numTilesInNeighborhood >= birthThreshold || (doLargeNeighborhoodCheck && numTilesInLargeNeighborhood <= largeSpaceThreshold)) {
						procGenMap[r][c].type = TileType.WALL;
						procGenMap[r][c].associatedChamber = -1;
					}
					// Otherwise, it stays dead
					else {
						procGenMap[r][c].type = TileType.EMPTY;
						procGenMap[r][c].associatedChamber = 0;
					}
				}
			}
		}
	}

	private int getNeighborTiles(Tile[][] mapToRef, int col, int row, int scope) {
		int startX = col - scope;
		int startY = row - scope;
		int endX = col + scope;
		int endY = row + scope;

		int tileCounter = 0;

		for(int iY = startY; iY <= endY; iY++) {
			for(int iX = startX; iX <= endX; iX++) {
				if(!(iX==col && iY==row)) {
					if (isTile(mapToRef, iX, iY)) {
						tileCounter++;
					}
				}
			}
		}
		return tileCounter;
	}


	private boolean isTile(Tile[][] mapToRef, int col, int row) {
		// Consider out-of-bound a tile
		if (PathingHelper.isOutOfBounds(col, row, numCols, numRows)) {
			return true;
		}
		else if(mapToRef[row][col].type == TileType.WALL) {
			return true;
		}

		return false;
	}

	public void detectAndConnectChambers(boolean allChambersShouldConnect) {
		// Clear out any chamber data we have
		for (Array<Vector2> chamber: chambers) {
			chamber.clear();
		}
		chambers.clear();

		resetObjects();

		// TODO Pull out elsewhere
		for (int c = 1; c < numCols - 1; c++) {
			for (int r = 1; r < numRows - 1; r++) {
				if (procGenMap[r][c].type == TileType.WALL) {
					procGenMap[r][c].associatedChamber = -1;
				}
				// Otherwise, kill it
				else {
					procGenMap[r][c].associatedChamber = 0;
				}
			}
		}

		// Step through all empty tiles and determine what unique "chamber" they are a part of
		short fillNumber = 1; // Would be used to uniquely identify the specific cavern
		for (int c = 1; c < numCols - 1; c++) {
			for (int r = 1; r < numRows - 1; r++) {
				// If this tile is empty and hasn't been tested yet (Still flagged as the default empty chamber)...
				if(procGenMap[r][c].type == TileType.EMPTY && procGenMap[r][c].associatedChamber == 0) {
					// Construct a new chamber
					chambers.add(new Array<>());

					// Perform the actual flood fill (Recursively)
					performFloodFill(c, r, fillNumber);

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

	private void performFloodFill(int c, int r, short chamberIndex) {
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

		// Don't go any further if this is actually a tile OR has been checked
		if (procGenMap[r][c].associatedChamber != 0) {
			return;
		}

		// Set the associated chamber index for the tile
		procGenMap[r][c].associatedChamber  = chamberIndex;

		// NOW... <breathe>... add this coordinate to the chamber
		chambers.get(chambers.size - 1).add(new Vector2(c, r));

		// Lastly, check NSEW and recursively fill

		// West
		if (c > 1) {
			performFloodFill(c - 1, r, chamberIndex);
		}

		// East
		if (c < numCols - 2) {
			performFloodFill(c + 1, r, chamberIndex);
		}

		// North
		if (r < numRows - 2) {
			performFloodFill(c, r + 1, chamberIndex);
		}

		// South
		if (r > 1) {
			performFloodFill(c, r - 1, chamberIndex);
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
					procGenMap[(int) currCoord.y][(int) currCoord.x].type = TileType.EMPTY;
					procGenMap[(int) currCoord.y][(int) currCoord.x].associatedChamber = 15;
				}
			}
		}

		// Make sure the now SINGLE chamber has all of the open coords in it... to reference elsewhere if need be
		chambers.clear();
		centralChamber.clear();

		// TODO Consider return a new chamber that can be used so that this function is more "pure"
		for (int y = numRows - 1; y >= 0; y--) {
			for (int x = 0; x < numCols; x++) {
				if (procGenMap[y][x].type == TileType.EMPTY ) {
					centralChamber.add(new Vector2(x, y));
				}
			}
		}

		chambers.add(centralChamber);
	}

	public void determineEntranceAndExit() {
		// Make a copy of all coords from the chamber
		Array<Vector2> allPossibleCoords = new Array<>(centralChamber);
		entranceCoord =  allPossibleCoords.removeIndex(MathUtils.random(allPossibleCoords.size - 1));

		// While not perfect, we will go so far as to pick 2 random points and test if they meet the distance criteria
		// If they don't we will indicate that the level should maybe be
		while (allPossibleCoords.size > 0) {
			// TODO Use a distance to determine and check if the exit works
			exitCoord = allPossibleCoords.removeIndex(MathUtils.random(allPossibleCoords.size - 1));

			// IF the distance check is satisfied, break!
			if (PathingHelper.distanceBetween(entranceCoord, exitCoord) >= minDistanceBetweenEntranceAndExit) {
				break;
			}
		}

		// If we get here, we've exhausted do our reasonable attempt so just use the last entrance / exit pair we decided to test
	}

	public void calculateCollectibleLocations() {
		if (collectibles != null && centralChamber != null && centralChamber.size >= numCollectibles) {
			// Clear out what we do have
			collectibles.clear();

			// Possible locations to put things
			Array<Vector2> possibleLocations = new Array<>(centralChamber);

			// TODO Keep in mind... we don't wanna spawn on player, other collectibles, entrance, exit, etc
			// TODO Best to just have map to reference have an enum for tiles to more easily check what is there
			for (int i = 0; i < numCollectibles; i++) {
				collectibles.add(possibleLocations.removeIndex(MathUtils.random(possibleLocations.size - 1)));
			}
		}
	}

	public void calculateNestledCollectibleLocations() {
		// TODO Best to not change this via its reference
		// TODO Not consistent with either using parameter or global values
		if (collectibles != null) {
			// Clear out what we do have
			collectibles.clear();

			for (int r = numRows - 2; r >= 1; r--) {
				for (int c = 1; c < numCols - 1; c++) {
					// If this tile is empty...
					if(procGenMap[r][c].type == TileType.EMPTY) {
						// Count the number of tiles in the neighborhood
						int numTilesInNeighborhood = getNeighborTiles(procGenMap, c, r, 1);

						if (numTilesInNeighborhood >= nestledCollectibleNeighborThreshold) {
							collectibles.add(new Vector2(c, r));
						}
					}
				}
			}
		}
	}

	public TiledMapRenderer getTileMapRenderer() {
		return tileMapRenderer;
	}
}
