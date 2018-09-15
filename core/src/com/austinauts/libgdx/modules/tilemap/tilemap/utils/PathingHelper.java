package com.austinauts.libgdx.modules.tilemap.tilemap.utils;

import com.austinauts.libgdx.modules.tilemap.tilemap.Tile;
import com.austinauts.libgdx.modules.tilemap.tilemap.TileType;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;

import java.util.HashSet;
import java.util.PriorityQueue;

public class PathingHelper {
	public static Array<Vector2> findAStarPath(Tile[][] mapToRef, Vector2 startCoord, Vector2 goalCoord) {
		int numRows = mapToRef.length;
		int numCols = mapToRef[0].length;

		// Create a 2D array of nodes that can be referenced and updated
		AStarNode[][] nodes = new AStarNode[numRows][numCols];

		for (int r = numRows - 2; r >= 1; r--) {
			for (int c = 1; c < numCols - 1; c++) {
				nodes[r][c] = new AStarNode(c, r);
			}
		}

		// The set of nodes already evaluated
		HashSet<AStarNode> closedSet = new HashSet<>();

		// The queue of currently discovered nodes that are not evaluated yet.
		// Initially, only the start node is known.
		PriorityQueue<AStarNode> openQueue = new PriorityQueue<>();

		AStarNode startNode = new AStarNode(startCoord);
		startNode.fScore = heuristicCostEstimate(mapToRef, startCoord, goalCoord); // For each node, the total cost of getting from the start node to the goal
		startNode.gScore = 0; // For each node, the cost of getting from the start node to that node.
		openQueue.add(startNode);

		// For each node, which node it can most efficiently be reached from.
		// If a node can be reached from many nodes, cameFrom will eventually contain the
		// most efficient previous step.
		ObjectMap<AStarNode, AStarNode> cameFrom = new ObjectMap<>();

		// Use to store neighbors nodes as we need them
		Array<AStarNode> neighbors = new Array<>();

		while (!openQueue.isEmpty()) {
			AStarNode currentNode = openQueue.poll(); // Since we are processing the current node, remove from open set
			Vector2 currentCoord = currentNode.coord;

			// If we've reached the goal, give back the reconstructed path
			if (currentCoord.equals(goalCoord)) {
				return reconstructPath(cameFrom, currentNode);
			}

			// Put the recently polled / popped node in the closed set
			closedSet.add(currentNode);

			// Clear out any older neighbors from other checks so that we can add the neighbors for this current coord
			neighbors.clear();

			// Add neighbors of the current coord that are in bounds

			// West
			if (!isOutOfBounds((int) currentCoord.x - 1, (int) currentCoord.y, mapToRef[0].length, mapToRef.length)) {
				neighbors.add(nodes[(int) currentCoord.y][(int) currentCoord.x - 1]);
			}

			// East
			if (!isOutOfBounds((int) currentCoord.x + 1, (int) currentCoord.y, mapToRef[0].length, mapToRef.length)) {
				neighbors.add(nodes[(int) currentCoord.y][(int) currentCoord.x + 1]);
			}

			// South
			if (!isOutOfBounds((int) currentCoord.x, (int) currentCoord.y - 1, mapToRef[0].length, mapToRef.length)) {
				neighbors.add(nodes[(int) currentCoord.y - 1][(int) currentCoord.x]);
			}

			// North
			if (!isOutOfBounds((int) currentCoord.x, (int) currentCoord.y + 1, mapToRef[0].length, mapToRef.length)) {
				neighbors.add(nodes[(int) currentCoord.y + 1][(int) currentCoord.x]);
			}

			for (AStarNode neighbor : neighbors) {
				// Ignore the neighbor if its already been processed
				if (closedSet.contains(neighbor)) {
					continue;
				}

				// Find the distance from start to a neighbor
				int tentativeGScoreForNeighbor = currentNode.gScore + distanceBetween(currentCoord, neighbor.coord);

				// Otherwise, if the distance from start to the neighbor is "better", record it
				if (tentativeGScoreForNeighbor < neighbor.gScore) {
					openQueue.remove(neighbor); // Remove in case it's there and needs to be updated / reinserted

					neighbor.gScore = tentativeGScoreForNeighbor;
					neighbor.fScore = neighbor.gScore + heuristicCostEstimate(mapToRef, neighbor.coord, goalCoord);
					cameFrom.put(neighbor, currentNode);

					openQueue.add(neighbor); // Add / reinsert
				}
			}
		}

		// If we made it here, there was no valid way to connect the chamber so return null (Pretty much SHOULD NOT happen)
		return null;
	}

	public static boolean isOutOfBounds(int col, int row, int numCols, int numRows) {
		// The edges are considered out of bounds
		if (col < 1 || row < 1) {
			return true;
		}
		else if (col > numCols - 2 || row > numRows - 2) {
			return true;
		}

		return false;
	}

	public static int distanceBetween(Vector2 coord1, Vector2 coord2) {
		return (int) (Math.abs(coord1.x - coord2.x) + Math.abs(coord1.y - coord2.y));
	}

	private static int heuristicCostEstimate(Tile[][] mapToRef, Vector2 coord1, Vector2 coord2) {
		int D = 1;

		// Jack up the cost depending on the tile type
		if (mapToRef[(int) coord1.y][(int) coord1.x].type == TileType.WALL) {
			D = 5;
		}

		return D * (int) (Math.abs(coord1.x - coord2.x) + Math.abs(coord1.y - coord2.y));
	}

	private static Array<Vector2> reconstructPath(ObjectMap<AStarNode, AStarNode> cameFrom, AStarNode current) {
		Array<Vector2> reconstructedPath = new Array<>();
		reconstructedPath.add(current.coord);

		while (cameFrom.containsKey(current)) {
			current = cameFrom.get(current);
			reconstructedPath.add(current.coord);
		}

		return reconstructedPath;
	}
}
