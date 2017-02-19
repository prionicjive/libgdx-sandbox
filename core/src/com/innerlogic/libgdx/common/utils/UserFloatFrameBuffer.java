package com.innerlogic.libgdx.common.utils;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FloatFrameBuffer;

public class UserFloatFrameBuffer extends FloatFrameBuffer {
	/**
	 * Creates a new FrameBuffer with a float backing texture, having the given dimensions and potentially a depth buffer attached.
	 *
	 * @param width    the width of the framebuffer in pixels
	 * @param height   the height of the framebuffer in pixels
	 * @param hasDepth whether to attach a depth buffer
	 * @throws com.badlogic.gdx.utils.GdxRuntimeException in case the FrameBuffer could not be created
	 */
	public UserFloatFrameBuffer(int width, int height, boolean hasDepth) {
		super(width, height, hasDepth);
	}

	@Override
	protected Texture createColorTexture() {
		UserFloatTextureData data = new UserFloatTextureData(width, height);
		Texture result = new Texture(data);
		result.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
		result.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);

		return result;
	}
}
