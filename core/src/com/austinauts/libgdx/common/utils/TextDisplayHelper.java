package com.austinauts.libgdx.common.utils;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

public class TextDisplayHelper {
	public static void drawTextWithShadow(SpriteBatch batch, BitmapFont font, String text, float x, float y) {
		// Assumes we are in full world space
		font.setColor(Color.BLACK);
		font.draw(batch, text, x + 1, y - 1);

		font.setColor(Color.WHITE);
		font.draw(batch, text, x, y);
	}
}
