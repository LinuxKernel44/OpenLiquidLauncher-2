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
