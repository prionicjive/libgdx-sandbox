package com.austinauts.libgdx.common.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxRuntimeException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShaderHelper {
	// Matches `#include "some/relative/path.glsl"` on its own line.
	private static final Pattern INCLUDE_PATTERN = Pattern.compile("(?m)^\\s*#include\\s+\"([^\"]+)\"\\s*$");

	/**
	 * Load a shader source file from the internal assets, expanding any `#include "path"` directives relative
	 * to the including file. Includes are resolved recursively.
	 */
	public static String loadShaderSource(String internalPath) {
		return loadShaderSource(Gdx.files.internal(internalPath));
	}

	private static String loadShaderSource(FileHandle file) {
		String src = file.readString();
		Matcher m = INCLUDE_PATTERN.matcher(src);
		StringBuilder out = new StringBuilder();
		int last = 0;
		while (m.find()) {
			out.append(src, last, m.start());
			FileHandle included = file.parent().child(m.group(1));
			out.append(loadShaderSource(included));
			last = m.end();
		}
		out.append(src, last, src.length());
		return out.toString();
	}

	/**
	 * Compiles and returns a new instance of the default shader. If compilation was unsuccessful, GdxRuntimeException will be thrown.
	 *
	 * @return The default shader
	 */
	public static ShaderProgram createDefaultShader() {
		return createDefaultShader(true);
	}

	public static ShaderProgram createDefaultShader(boolean isTextured) {
		String vertexShader = "attribute vec4 " + ShaderProgram.POSITION_ATTRIBUTE + ";\n" //
			+ "attribute vec4 " + ShaderProgram.COLOR_ATTRIBUTE + ";\n" //
			+ "attribute vec2 " + ShaderProgram.TEXCOORD_ATTRIBUTE + "0;\n" //
			+ "uniform mat4 u_projTrans;\n" //
			+ "varying vec4 v_color;\n" //
			+ "varying vec2 v_texCoords;\n" //
			+ "\n" //
			+ "void main()\n" //
			+ "{\n" //
			+ "   v_color = " + ShaderProgram.COLOR_ATTRIBUTE + ";\n" //
			+ "   v_texCoords = " + ShaderProgram.TEXCOORD_ATTRIBUTE + "0;\n" //
			+ "   gl_Position =  u_projTrans * " + ShaderProgram.POSITION_ATTRIBUTE + ";\n" //
			+ "}\n";

		String fragmentShader = "varying vec4 v_color;\n" //
			+ "varying vec2 v_texCoords;\n" //
			+ "uniform sampler2D u_texture;\n" //
			+ "void main()\n"//
			+ "{\n" //
			+ "  gl_FragColor = v_color%s;\n" //
			+ "}";

		fragmentShader = String.format(fragmentShader, isTextured ? " * texture2D(u_texture, v_texCoords)" : "");

		return createShader(vertexShader, fragmentShader);
	}

	/**
	 * Compiles a new instance of the specified shader and returns it. If compilation was unsuccessful, GdxRuntimeException will be thrown.
	 *
	 * @return The compiled shader
	 */
	public static ShaderProgram createShader(String vert, String frag) {
		ShaderProgram prog = new ShaderProgram(vert, frag);

		if (!prog.isCompiled()) {
			throw new GdxRuntimeException("could not compile shader : " + prog.getLog());
		}
		if (prog.getLog().length() != 0) {
			Gdx.app.log("ShaderHelper", prog.getLog());
		}

		return prog;
	}

	public static Mesh createFullScreenQuad() {
		return createFullScreenQuad(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true, 1f, 1f, 1f, 1f);
	}

	public static Mesh createFullScreenQuad(int widthToUse, int heightToUse) {
		return createFullScreenQuad(widthToUse, heightToUse, true, 1f, 1f, 1f, 1f);
	}

	public static Mesh createFullScreenQuad(int widthToUse, int heightToUse, boolean isTextured, float r, float g, float b, float a) {
		Mesh mesh;

		if (isTextured) {
			mesh = new Mesh(true, 4, 6,  // Static mesh with 4 vertices and 6 indices
				new VertexAttribute(VertexAttributes.Usage.Position, 2, ShaderProgram.POSITION_ATTRIBUTE),
				new VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 4, ShaderProgram.COLOR_ATTRIBUTE),
				new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, ShaderProgram.TEXCOORD_ATTRIBUTE + 0));
		}
		else {
			mesh = new Mesh(true, 4, 6,  // Static mesh with 4 vertices and 6 indices
				new VertexAttribute(VertexAttributes.Usage.Position, 2, ShaderProgram.POSITION_ATTRIBUTE),
				new VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 4, ShaderProgram.COLOR_ATTRIBUTE));
		}

		Vector2 vec0 = new Vector2(0, 0);
		Vector2 vec1 = new Vector2(widthToUse, heightToUse);

		float[] verts;

		if (isTextured) {
			verts = new float[]{vec0.x, vec0.y, r, g, b, a, 0f, 1f,
				vec1.x, vec0.y, r, g, b, a, 1f, 1f,
				vec1.x, vec1.y, r, g, b, a, 1f, 0f,
				vec0.x, vec1.y, r, g, b, a, 0f, 0f};
		}
		else {
			verts = new float[]{vec0.x, vec0.y, r, g, b, a,
				vec1.x, vec0.y, r, g, b, a,
				vec1.x, vec1.y, r, g, b, a,
				vec0.x, vec1.y, r, g, b, a};
		}

		mesh.setVertices(verts);
		mesh.setIndices(new short[]{0, 1, 2, 2, 3, 0});

		return mesh;
	}
}
