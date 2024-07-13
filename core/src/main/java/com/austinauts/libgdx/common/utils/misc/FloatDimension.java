package com.austinauts.libgdx.common.utils.misc;

public class FloatDimension {
	public float width;
	public float height;

	public FloatDimension() {
		this.width = 0f;
		this.height = 0f;
	}

	public FloatDimension(float width, float height) {
		this.width = width;
		this.height = height;
	}

	public FloatDimension(FloatDimension dimension) {
		this.width = dimension.width;
		this.height = dimension.height;
	}

	public void set(FloatDimension dimension) {
		this.width = dimension.width;
		this.height = dimension.height;
	}
}
