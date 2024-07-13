package com.austinauts.libgdx.modules.tilemap.tilemap.utils;

import com.badlogic.gdx.math.Vector2;

public class AStarNode implements Comparable<AStarNode>{
	public Vector2 coord;
	public int fScore = Integer.MAX_VALUE; // Effectively infinity by default
	public int gScore = Integer.MAX_VALUE; // Effectively infinity by default

	public AStarNode(int x, int y) {
		coord = new Vector2(x, y);
	}

	public AStarNode(float x, float y) {
		coord = new Vector2(x, y);
	}

	public AStarNode(Vector2 coord) {
		this.coord = new Vector2(coord);
	}

	@Override
	public int compareTo(AStarNode otherNode) {
		return Integer.compare(this.fScore, otherNode.fScore);
	}
}
