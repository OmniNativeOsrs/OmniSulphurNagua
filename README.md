# Omni Sulphur Nagua

Fights Sulphur Naguas in Cam Torum: drinks Moonlight potions, brews new ones from grubs and vials, loots runes and ore, and can trade essence for Runecrafting experience.

An OmniNative plugin. This repository is private and owned by OmniNative; you have write access as its developer.

## Build on your PC

1. Download the SDK (`omni-osrs-api.jar`) from the Developer page on the OmniNative website and put it in `libs/`.
2. Build with Gradle 8 and a JDK 17: `gradle jar`. The plugin is in `build/libs/`.

## Release

1. Set `version` in `src/main/resources/META-INF/omni-plugin.properties` and in your provider's `version()`.
2. Push, then create a release with the same version on the website and pick the commit.
3. Staff review the source at that commit. When they approve it, the OmniNative build service builds and obfuscates
   exactly that commit, and the result is published on its own.

## Rules the build service checks

- Only `src/main/java` and `src/main/resources` are built. Every class must be in a package.
- No compiled code, archives or native libraries in resources.
- `id` must be this listing's plugin id (`omnisulphurnagua`) and `version` the release version.
- Gson, SLF4J and javax.inject come with the client. Other libraries need staff approval
  (list them in `omni-plugin-libraries.txt`).
- Lombok is available. No other annotation processors run.
