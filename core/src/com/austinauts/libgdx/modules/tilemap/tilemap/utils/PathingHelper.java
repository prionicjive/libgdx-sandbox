package com.austinauts.libgdx.modules.tilemap.tilemap.utils;

import com.austinauts.libgdx.modules.tilemap.tilemap.Tile;
import com.austinauts.libgdx.modules.tilemap.tilemap.TileType;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;

public class PathingHelper {
	public static Array<Vector2> findAStarPath(Tile[][] mapToRef, Vector2 startCoord, Vector2 goalCoord) {
		// The set of nodes already evaluated
		Array<Vector2> closedSet = new Array<>();

		// The set of currently discovered nodes that are not evaluated yet.
		// Initially, only the start node is known.
		// TODO NOTE: Best to use a priority queue
		Array<Vector2> openSet = new Array<>();
		openSet.add(startCoord);

		// For each node, which node it can most efficiently be reached from.
		// If a node can be reached from many nodes, cameFrom will eventually contain the
		// most efficient previous step.
		ObjectMap<Vector2, Vector2> cameFrom = new ObjectMap<>();

		// For each node, the cost of getting from the start node to that node.
		ObjectMap<Vector2, Integer> gScores = new ObjectMap<>();

		// The cost of going from start to start is zero.
		gScores.put(startCoord, 0);

		// For each node, the total cost of getting from the start node to the goal
		// by passing by that node. That value is partly known, partly heuristic.
		ObjectMap<Vector2, Integer> fScores = new ObjectMap<>();

		// For the first node, that value is completely heuristic.
		fScores.put(startCoord, heuristicCostEstimate(mapToRef, startCoord, goalCoord));

		// Use to store neighbors as we need them
		Array<Vector2> neighbors = new Array<>();

		while (openSet.size > 0) {
			// TODO Could be replaced by priority queue
			Vector2 currentCoord = findLowestFScore(openSet, fScores);

			// If we've reached the goal, give back the reconstructed path
			if (currentCoord.equals(goalCoord)) {
				return reconstructPath(cameFrom, currentCoord);
			}

			// Since we are processing the current coord, remove from open set and put in closed set
			openSet.removeValue(currentCoord, false);
			closedSet.add(currentCoord);

			// Clear out any older neighbors from other checks so that we can add the neighbors for this current coord
			neighbors.clear();

			// Add neighbors of the current coord that are in bounds

			// West
			if (!isOutOfBounds((int) currentCoord.x - 1, (int) currentCoord.y, 80, 45)) {
				neighbors.add(new Vector2(currentCoord.x - 1, currentCoord.y));
			}

			// East
			if (!isOutOfBounds((int) currentCoord.x + 1, (int) currentCoord.y, 80, 45)) {
				neighbors.add(new Vector2(currentCoord.x + 1, currentCoord.y));
			}

			// South
			if (!isOutOfBounds((int) currentCoord.x, (int) currentCoord.y - 1, 80, 45)) {
				neighbors.add(new Vector2(currentCoord.x, currentCoord.y - 1));
			}

			// North
			if (!isOutOfBounds((int) currentCoord.x, (int) currentCoord.y + 1, 80, 45)) {
				neighbors.add(new Vector2(currentCoord.x, currentCoord.y + 1));
			}

			for (Vector2 neighbor : neighbors) {
				// Ignore the neighbor if its already been processed
				if (closedSet.indexOf(neighbor, false) > -1) {
					continue;
				}

				// Find the distance from start to a neighbor
				int tentativeGScoreForNeighbor = gScores.get(currentCoord, Integer.MAX_VALUE)
						+ distanceBetween(currentCoord, neighbor);
				boolean tentativeIsBetter = false;

				// If neighbor is not in the openSet, it mean we've discovered a brand new node that hasn't been processed
				if (openSet.indexOf(neighbor, false) == -1) {
					openSet.add(neighbor);
					tentativeIsBetter = true;
				}
				// Otherwise, if the distance from start to the neighbor is "better"
				else if (tentativeGScoreForNeighbor < gScores.get(neighbor, Integer.MAX_VALUE)) {
					tentativeIsBetter = true;
				}

				// This is the best path for this neighbor for now so record it
				if (tentativeIsBetter) {
					cameFrom.put(neighbor, currentCoord);
					gScores.put(neighbor, tentativeGScoreForNeighbor);
					fScores.put(neighbor, gScores.get(neighbor, Integer.MAX_VALUE) + heuristicCostEstimate(mapToRef, neighbor, goalCoord));
				}
			}
		}

		// If we made it here, there was no valid way to connect the chamber so return null (Pretty much SHOULD NOT happen)
		return null;
	}

	public static boolean isOutOfBounds(int col, int row, int numCols, int numRows){
		// The edges are considered out of bounds
		if( col < 1 || row < 1) {
			return true;
		}
		else if( col > numCols - 2 || row > numRows - 2) {
			return true;
		}

		return false;
	}

	public static int distanceBetween(Vector2 coord1, Vector2 coord2) {
		return (int)(Math.abs(coord1.x - coord2.x) + Math.abs(coord1.y - coord2.y));
	}

	private static Vector2 findLowestFScore(Array<Vector2> openSet, ObjectMap<Vector2, Integer> fScores) {
		int indexOfLowestFScore = 0;
		int lowestFScore = fScores.get(openSet.get(indexOfLowestFScore), Integer.MAX_VALUE);

		for (int i = 0; i < openSet.size; i ++) {
			Vector2 coordToCheck = openSet.get(i);
			int currFScore = fScores.get(coordToCheck, Integer.MAX_VALUE);

			if (currFScore < lowestFScore)
			{
				lowestFScore = currFScore;
				indexOfLowestFScore = i;
			}
		}

		return openSet.get(indexOfLowestFScore);
	}

	private static int heuristicCostEstimate(Tile[][] mapToRef, Vector2 coord1, Vector2 coord2) {
		int D = 1;

		// Jack up the cost depending on the tile type
		// TODO Determine what tiles affect this
		if (mapToRef[(int)coord1.y][(int)coord1.x].type == TileType.WALL) {
			D = 5;
		}

		return D * (int)(Math.abs(coord1.x - coord2.x) + Math.abs(coord1.y - coord2.y));
	}

	private static Array<Vector2> reconstructPath(ObjectMap<Vector2, Vector2> cameFrom, Vector2 current) {
		Array<Vector2> reconstructedPath = new Array<>();
		reconstructedPath.add(current);

		while (cameFrom.containsKey(current)) {
			current = cameFrom.get(current);
			reconstructedPath.add(current);
		}

		return reconstructedPath;
	}
}
