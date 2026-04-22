# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & run

Only two Gradle subprojects exist: `core` (shared library code) and `lwjgl3` (desktop launcher, the only backend this repo ships). There are no Android / iOS / GWT / HTML projects despite the `gdx-liftoff` origin.

- `./gradlew lwjgl3:run` — launch the desktop app. Working dir is set to `assets/`, and on macOS `-XstartOnFirstThread` is appended automatically (see `lwjgl3/build.gradle`).
- `./gradlew build` — compile both subprojects and run tests. There are currently **no tests** (`compileTestJava NO-SOURCE`), so this is effectively a compile check.
- `./gradlew lwjgl3:jar` — produces a runnable fat jar at `lwjgl3/build/lib/libgdx-sandbox-<version>.jar`.
- `./gradlew lwjgl3:package<Target>` — `construo` (v1.2.0) packages native bundles for `linuxX64`, `macM1`, `macX64`, `winX64`. Each target pulls Temurin JDK 17 from adoptium — first run is slow.
- `./gradlew --refresh-dependencies clean build` — use when bumping `gdxVersion` or any extension version in `gradle.properties`.
- GraalVM native-image path exists but is **off by default**. Set `enableGraalNative=true` in `gradle.properties` to activate `lwjgl3/nativeimage.gradle`.

Java 11 source/target (`build.gradle:32`, `lwjgl3/build.gradle:23-24`). Gradle wrapper is 8.8.

## Asset pipeline

- All assets live at the repo-root `assets/` directory and are wired into `lwjgl3` via `sourceSets.main.resources.srcDirs += [ rootProject.file('assets').path ]`.
- `build.gradle:38-52` registers a `compileJava.doLast` that rewrites `assets/assets.txt` with every file under `assets/` on every compile. This file is consumed at runtime (pattern described at <https://lyze.dev/2021/04/29/libGDX-Internal-Assets-List/>). Don't hand-edit `assets/assets.txt`.
- Fonts are loaded by reading `assets/fonts/paths.txt`, which lists `.fnt` files to register into `AustinautsGame.fontMap` keyed by filename-without-extension. Three named handles are required at startup: `munro_72`, `munro_40`, `munro_30` (see `AustinautsGame.java:124-126`). Missing fonts throw `GdxRuntimeException` at boot.
- Common asset paths are declared as `public final static String` constants on `AustinautsGame` (e.g. `TEXTURE_PLAYER`, `TILEMAP_SAMPLE_MAP`, `CONFIG_EFFECTS_SPIN`). Add new shared-asset paths there rather than inlining strings in screens.

## Architecture: the "modules" pattern

This repo is a sandbox for self-contained demos ("modules"), not a single game. Each module lives under `core/src/main/java/com/austinauts/libgdx/modules/<name>/screens/` and follows a three-screen flow: `LoadingScreen → MainMenuScreen → GameScreen` (some modules add `GameOverScreen`). Modules as of now: `box2d`, `fallthru`, `particleeffects`, `particlesgalore`, `quadtree`, `shadowmapping`, `tilemap`, `whammyball`.

**There is no module registry or switcher UI.** The "active" demo is whichever `LoadingScreen` is instantiated at the end of `AustinautsGame.create()`:

```java
// AustinautsGame.java:139 — change the import + this line to swap active demo
this.setScreen(new LoadingScreen(this));
```

To switch which module runs, change the `import com.austinauts.libgdx.modules.<name>.screens.LoadingScreen;` line in `AustinautsGame.java` and rebuild. When adding a new module, create a new package under `modules/`, add its three screens, and point `AustinautsGame` at its `LoadingScreen`.

Module-level loading screens extend `common.screens.BaseLoadingScreen`; menu screens extend `common.screens.BaseMainMenuScreen`. Follow those patterns when adding a module rather than extending `ScreenAdapter` directly (that's only done where modules need something the base classes don't provide).

## `AustinautsGame` as a service locator

`AustinautsGame extends Game` owns every shared runtime resource and is passed into every screen constructor as `_game`. Screens should **reuse** these, not create their own:

- `assetManager` (single `AssetManager`)
- `json`, `jsonReader` (JSON loading — used e.g. by `ParticleEffectSettings` via `_game.json.fromJson(...)`)
- `batch` (single `SpriteBatch`), `shapeRenderer` (single `ShapeRenderer`)
- `world`, `box2DDebugRenderer` (Box2D — `world` is nullable; guard in `dispose`)
- `camera` (`OrthographicCamera`), `viewport` (`FitViewport` at `virtualScreenSize` 1280×720)
- `fontMap`, `bigFont`/`mediumFont`/`smallFont`, `glyphLayout`
- `fpsLogger`, `deltaTime`

`dispose()` cleans up these shared resources plus all fonts via `DisposalHelper.disposeCollection`. Module screens should dispose only their *own* locals, not the shared ones.

## `common/` utilities worth reusing

Before writing new infrastructure, check `core/src/main/java/com/austinauts/libgdx/common/`:

- `common/particles/` — custom CPU particle system (`ParticleEffect`, `ParticleEmitter`, `Particle`, `*Template`). **Not** LibGDX's `com.badlogic.gdx.graphics.g2d.ParticleEffect`; driven by `ParticleEffectSettings` JSON.
- `common/loaders/ParticleEffectSettings` — JSON schema loaded via `_game.json.fromJson(...)`.
- `common/collision/QuadTreeNode` — spatial partitioning used by the `quadtree` module.
- `common/utils/DisposalHelper` — bulk-dispose collections of `Disposable`.
- `common/utils/ShaderHelper` — shader loading conveniences (relevant for `shadowmapping`, `particlesgalore`).
- `common/utils/logging/PeriodicLogger` — rate-limited logging for tight loops.
- `common/utils/misc/IntDimensions`, `FloatDimension` — typed width/height tuples.

## Dependencies & LibGDX version

`gradle.properties` is the single source of truth for versions. `gdxVersion` flows into every `com.badlogicgames.gdx:*:$gdxVersion` coordinate, so bumping core only requires editing that one line.

Current pinned versions: LibGDX 1.13.1, gdx-ai 1.8.2, ashley 1.7.4, box2dlights 1.5, gdx-controllers 2.2.3, libgdx-utils-box2d 0.13.4 (third-party, unmaintained — `net.dermetfan.libgdx-utils` on jitpack).

When bumping LibGDX, consult <https://libgdx.com/wiki/articles/updating-libgdx> and the CHANGES file at <https://github.com/libgdx/libgdx/blob/master/CHANGES> — breaking changes since 1.9.13 are listed there.

## LWJGL3 launcher

`Lwjgl3Launcher.java` requests `GL32` emulation (`3, 2`), VSync on, foreground FPS capped to monitor refresh rate, windowed 1280×720. `StartupHelper.startNewJvmIfRequired()` handles the macOS `-XstartOnFirstThread` relaunch before the application constructs. Don't relocate that call — it must run before any LibGDX class loads.
