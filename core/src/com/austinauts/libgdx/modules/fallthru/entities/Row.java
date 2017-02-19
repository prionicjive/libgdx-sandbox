package com.austinauts.libgdx.modules.fallthru.entities;

import com.austinauts.libgdx.AustinautsGame;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.Pool;
import com.austinauts.libgdx.common.entities.SpriteEntity;

public class Row implements Pool.Poolable {
	private Array<SpriteEntity> _blocks;

	private AustinautsGame _game;

	// Used for collision
	private Rectangle _boundingRect; // TODO Consider better / easier way to access position info on the row
	private Array<Rectangle> _gaps; //

	public Row(AustinautsGame game) {
		_game = game;

		_blocks = new Array<>();
		_gaps = new Array<>();
		_boundingRect = null;
	}

	public void init(int blockSize, int numOpenings, int rowWidth, int xPadding, int yOffset) {
		// Reset by default to make sure we are dealing with a fresh row
		reset();

		int numBlocks = rowWidth / blockSize;

		if (numBlocks <= numOpenings) {
			// TODO Consider if we can handle this with a true checked exception or better recovery
			throw new GdxRuntimeException(String.format("numBlocks <= numOpenings :-(\nrowWidth=%d\nnumBlocks=%d\nnumOpenings=%d", rowWidth, numBlocks, numOpenings));
		}

		// Create a valid bounding rect for the entirety of the row
		_boundingRect = new Rectangle(xPadding, yOffset, rowWidth, blockSize);

		// Determine the location of the openings
		IntArray indiciesToPickFrom = new IntArray(true, numBlocks);
		for (int i = 0; i < numBlocks; i++) {
			indiciesToPickFrom.add(i);
		}

		IntArray indiciesToSkip = new IntArray(true, numOpenings);
		for (int i = 0; i < numOpenings; i++) {
			indiciesToSkip.add(indiciesToPickFrom.removeIndex(MathUtils.random(indiciesToPickFrom.size - 1)));
		}

		// Useful for tracking info needed for a gap
		int startIndexForGap = -1;

		for (int i = 0; i < numBlocks; i++) {
			// Skip over any index determined to be used for a block
			if (indiciesToPickFrom.size > 0 && indiciesToPickFrom.first() == i) {
				// Pop off the block with the same index (Always the first in this case)
				indiciesToPickFrom.removeIndex(0);

				// Build the block and add it to the array of blocks
				SpriteEntity tempBlock = new SpriteEntity(_game.assetManager.get(AustinautsGame.TEXTURE_TILE, Texture.class));
				tempBlock.setPosition(xPadding + (i * blockSize), yOffset);
				_blocks.add(tempBlock);

				// Only build the gap if we have detected a place to start it
				if (startIndexForGap != -1) {
					// SPECIAL CASE: Make sure the starting X is 0 if a block is supposed to be at index 0
					if (startIndexForGap == 0) {
						_gaps.add(new Rectangle(0, yOffset, i * blockSize, 1));
					}
					else {
						_gaps.add(new Rectangle(xPadding + (startIndexForGap * blockSize), yOffset, (i - startIndexForGap) * blockSize, 1));
					}

					// Reset the start index
					startIndexForGap = -1;
				}

				continue;
			}

			// If we haven't detected a start for the gap, make it so
			if (startIndexForGap == -1) {
				startIndexForGap = i;
			}
		}

		// If start index is a valid one, we know we need to complete the final gap
		if (startIndexForGap != -1) {
			_gaps.add(new Rectangle(xPadding + (startIndexForGap * blockSize), yOffset, (numBlocks - startIndexForGap) * blockSize + xPadding, 1));
		}
	}

	@Override
	public void reset() {
		_blocks.clear();
		_gaps.clear();
		_boundingRect = null;
	}

	public void render() {
		for (SpriteEntity currBlock : _blocks) {
			currBlock.setY(_boundingRect.y); // TODO Put this in a better spot

			currBlock.draw(_game.batch);
		}
	}

	public Rectangle getBoundingRectangle() {
		return _boundingRect;
	}

	public Array<Rectangle> getGaps() {
		return _gaps;
	}
}
