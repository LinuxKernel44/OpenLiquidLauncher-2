# OpenLiquidLauncher-2

Fork de [Lawnchair](https://github.com/LawnchairLauncher/lawnchair) (branche `16-dev`, licence Apache 2.0) avec une UI "liquid glass"
(Kyant0/AndroidLiquidGlass). Usage strictement non commercial. Branche de travail: `liquid-glass`.

## Build
- `export ANDROID_HOME=$HOME/Android/Sdk JAVA_HOME=/usr/lib/jvm/java-21-openjdk`
- `./gradlew assembleLawnWithQuickstepGithubDebug` (le projet racine est l'app; variantes: app=lawn, recents=withQuickstep, channel=github|nightly|play)
- Sous-module à initialiser: `git submodule update --init --depth 1`

## Test
- Appareil: Galaxy S10 `RF8MA21K6YV` en adb (Android 12, SDK 31). Si absent ou incompatible: émulateur KVM (image android-36 dans ~/Android/Sdk/system-images).
- Ne jamais push/release sans accord de l'utilisateur.

## Règles
- Garder les en-têtes de licence et mentions d'origine (voir NOTICE).
- Remote `origin` = LinuxKernel44/OpenLiquidLauncher-2 (public).

## Liquid glass (Phase 3)
- `lawnchair/src/app/lawnchair/ui/glass/Glass.kt`: `liquidGlass` modifier + `GlassStyle` (port of the OpenLiquidLauncher one). Falls back to a flat tint below API 33.
- `GlassDock.kt`: `createGlassDockView()` returns a ComposeView added by `Hotseat` behind the icons (laid out in `Hotseat.onMeasure/onLayout`). It returns null (stock background kept) without API 33+ or wallpaper access.
- The glass refracts `WallpaperManager.drawable`, which needs Lawnchair's wallpaper access: all-files access + READ_MEDIA_IMAGES + READ_MEDIA_VIDEO.
  On the emulator: `adb shell pm grant <pkg> android.permission.READ_MEDIA_IMAGES`, same for `READ_MEDIA_VIDEO`, `adb shell appops set <pkg> MANAGE_EXTERNAL_STORAGE allow`.
- The Galaxy S10 (Android 12) cannot run the shaders: test glass on the emulator `olauncher_api36` (`emulator -avd olauncher_api36 -gpu host`, serial emulator-5554).

## Gotchas
- After editing `PreferenceManager.kt`, an incremental build produces `NoSuchFieldError: $stable ... BasePreferenceManager$StringPref` at launch. Run `./gradlew clean assembleLawnWithQuickstepGithubDebug --no-build-cache` (the Gradle build cache can restore the broken classes).
- Ignore the `tmux: command not found` lines printed by the user's zsh profile.
