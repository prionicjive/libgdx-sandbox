package com.austinauts.libgdx.common.tilemap;

import com.badlogic.gdx.graphics.g2d.TextureRegion;

public class Tile {
	public int size;
	public int associatedChamber;
	public TileType type;

	public Tile() {
		// Give the tile default values
		reset();

		// Default to sensible 16px for size
		size = 16;
	}

	public Tile(int associatedChamber, TileType type) {
		this.associatedChamber = associatedChamber;
		this.type = type;

		// Default to sensible 16px for size
		size = 16;
	}

	public void reset() {
		// Associate with no chamber by default
		this.associatedChamber = -1;

		// All tiles are empty by default
		type = TileType.EMPTY;
	}
}
