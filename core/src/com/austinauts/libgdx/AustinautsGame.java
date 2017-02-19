package com.austinauts.libgdx;

import com.austinauts.libgdx.modules.particlesgalore.screens.LoadingScreen;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.physics.box2d.Box2D;
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.ArrayMap;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import org.uncommons.maths.random.MersenneTwisterRNG;

import java.util.Random;

public class AustinautsGame extends Game {
	public final static String TEXTURE_BLOCK = "textures/block_64x64.png";
	public final static String TEXTURE_TILE = "textures/tile_16.png";
	public final static String TEXTURE_PLAYER = "textures/morphball.png";
	public final static String TEXTURE_PLAYERANMIATION = "textures/player_animation.png";
	public final static String TEXTURE_PARTICLE = "textures/particle_soft_64x64.png";

	// TODO May be a better way to do this
	public final static String TILEMAP_SAMPLE_TILESET = "tilemaps/tiles.png";
	public final static String TILEMAP_SAMPLE_MAP = "tilemaps/test.tmx";

	//public final static String MUSIC_TRACK = "music/retrace_the_circle.mp3";

	// TODO Using asset manager correctly?
	// Our AssetManager! Very important!
	public AssetManager assetManager;

	// Box2D
	public World world;
	public Box2DDebugRenderer box2DDebugRenderer;

	// Our shape renderer, used to render shapes and perform debug drawing
	public ShapeRenderer shapeRenderer;

	// PRNGs from the org.uncommon.maths library
	// - XORShiftRNG (Fastest but least random)
	// - CellularAutomatonRNG
	// - MersenneTwisterRNG (Slowest but more random)
	// Many more with difference in distribution, period and security
	public Random randomizer = new MersenneTwisterRNG();

	// Our sprite batch, used to optimize 2D rendering
	public SpriteBatch batch;

	// Our fonts
	public ArrayMap<String, BitmapFont> fontMap;
	public BitmapFont bigFont;
	public BitmapFont mediumFont;
	public BitmapFont smallFont;

	// Our GlyphLayout
	public GlyphLayout glyphLayout;

	// Camera and viewport
	public OrthographicCamera camera;
	public Viewport viewport;

	// TODO Have this accessible from a common place
	public int masterWorldWidth = 1280 / 1; // IMPORTANT: This is the TARGET resolution and what will be scaled to window size
	public int masterWorldHeight = 720 / 1; // IMPORTANT: This is the TARGET resolution and what will be scaled to window size

	@Override
	public void create() {
		// TODO Figure out how to go fullscreen in 720p
//		Graphics.DisplayMode mode = Gdx.graphics.getDisplayMode();
//		Gdx.graphics.setFullscreenMode(mode);

		// Set up the asset manager
		assetManager = new AssetManager();

		Box2D.init();
		box2DDebugRenderer = new Box2DDebugRenderer();

		shapeRenderer = new ShapeRenderer();

		batch = new SpriteBatch();

		fontMap = new ArrayMap<>();

		// TODO Should be doable via JSON
		// Load up and load all the relevant fonts
		FileHandle fontPaths = Gdx.files.internal("fonts/paths.txt");
		if (fontPaths.exists()) {
			loadFontsFromPathsFile(fontPaths);
		}

		if (fontMap.size == 0) {
			throw new GdxRuntimeException("path_fonts.txt AND fonts/ dir not found!");
		}

		bigFont = fontMap.get("munro_72");
		mediumFont = fontMap.get("munro_40");
		smallFont = fontMap.get("munro_30");

		glyphLayout = new GlyphLayout();

		// Create and set up the camera
		camera = new OrthographicCamera();
		camera.setToOrtho(false, masterWorldWidth, masterWorldHeight); // The camera's dimensions mirror view

		// Set up the viewport
		// TODO Make the type of view port and size configurable
		viewport = new FitViewport(masterWorldWidth, masterWorldHeight, camera);

		// Set the initial screen of our game to an instance the LoadingScreen
		this.setScreen(new LoadingScreen(this));
	}

	@Override
	public void resize(int width, int height) {
		super.resize(width, height);

		viewport.update(width, height);
		batch.setProjectionMatrix(camera.combined);
	}

	@Override
	public void dispose() {
		// TODO Make sure we are disposing properly
		super.dispose();

		// Dispose the asset manager and all its managed assets
		assetManager.dispose();

		// TODO Dispose of all the assets and other native resources
		batch.dispose();
		shapeRenderer.dispose();


		box2DDebugRenderer.dispose();

		// Guarding again exiting before the game screen is ever brought into play
		if (world != null) {
			world.dispose();
		}

		// Dispose of all the fonts
		disposeCollection(fontMap.values());
		fontMap.clear();
	}

	// ------------------------
	// Private methods
	// ------------------------
	private void disposeCollection(Iterable<? extends Disposable> valuesToDispose) {
		for (Disposable valueToDispose : valuesToDispose) {
			valueToDispose.dispose();
		}
	}

	private void loadFontsFromPathsFile(FileHandle pathsFile) {
		String[] paths = pathsFile.readString().split("\\n");

		for (String currPath : paths) {
			FileHandle currFile = Gdx.files.internal(currPath.trim());

			if (currFile.extension().equalsIgnoreCase("fnt")) {
				fontMap.put(currFile.nameWithoutExtension(), new BitmapFont(Gdx.files.internal(currFile.path())));
			}
		}
	}
}
