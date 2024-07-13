package com.austinauts.libgdx.modules.tilemap.tilemap;

public class Tile {
	public int associatedChamber;
	public TileType type;

	public Tile() {
		// Give the tile default values
		reset();
	}

	public Tile(int associatedChamber, TileType type) {
		this.associatedChamber = associatedChamber;
		this.type = type;
	}

	public void reset() {
		// Associate with no chamber by default
		this.associatedChamber = -1;

		// All tiles are empty by default
		type = TileType.EMPTY;
	}
}
