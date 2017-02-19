package com.austinauts.libgdx.common.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.TextureData;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.GdxRuntimeException;

import java.nio.FloatBuffer;

/**
 * Identical to LibGDX's FloatTextureData with the exception that the internal buffer can be specified by the user.
 */
public class UserFloatTextureData implements TextureData {
	int width = 0;
	int height = 0;
	boolean isPrepared = false;

	FloatBuffer buffer;

	public UserFloatTextureData(int w, int h) {
		this.width = w;
		this.height = h;
	}

	public UserFloatTextureData(int w, int h, FloatBuffer buffer) {
		this.width = w;
		this.height = h;
		this.buffer = buffer;
	}

	public FloatBuffer getBuffer() {
		return buffer;
	}

	public void setBuffer(FloatBuffer buffer) {
		this.buffer = buffer;
	}

	@Override
	public TextureDataType getType() {
		return TextureDataType.Custom;
	}

	@Override
	public boolean isPrepared() {
		return isPrepared;
	}

	@Override
	public void prepare() {
		if (isPrepared) {
			throw new GdxRuntimeException("Already prepared");
		}

		// Ensure that we don't send a null buffer. Hopefully the user has already set up the buffer.
		if (buffer == null) {
			buffer = BufferUtils.newFloatBuffer(width * height * 4);
		}

		isPrepared = true;
	}

	@Override
	public void consumeCustomData(int target) {
		if (!Gdx.graphics.supportsExtension("GL_ARB_texture_float")) {
			throw new GdxRuntimeException("Extension OES_TEXTURE_FLOAT not supported!");
		}

		// in desktop OpenGL the texture format is defined only by the third argument,
		// hence we need to use GL_RGBA32F
		Gdx.gl.glTexImage2D(target, 0, GL30.GL_RGBA32F, width, height, 0, GL30.GL_RGBA, GL30.GL_FLOAT, buffer);
	}

	@Override
	public Pixmap consumePixmap() {
		throw new GdxRuntimeException("This TextureData implementation does not return a Pixmap");
	}

	@Override
	public boolean disposePixmap() {
		throw new GdxRuntimeException("This TextureData implementation does not return a Pixmap");
	}

	@Override
	public int getWidth() {
		return width;
	}

	@Override
	public int getHeight() {
		return height;
	}

	@Override
	public Pixmap.Format getFormat() {
		return Pixmap.Format.RGBA8888; // it's not true, but FloatTextureData.getFormat() isn't used anywhere
	}

	@Override
	public boolean useMipMaps() {
		return false;
	}

	@Override
	public boolean isManaged() {
		return true;
	}
}
