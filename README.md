# Billow

Better clouds for Minecraft. Pick a cloud style: soft rounded clouds, realistic puffy cumulus, or a sky full of
creeper heads, hearts and stars. Move the clouds up or down, make them thicker, slower or more see-through.

Billow is a small client-side mod for Minecraft on Fabric and NeoForge, compatible with Mod Menu on Fabric.

Supported versions: Fabric and NeoForge 26.3. Works with Sodium and Iris (with no shader pack loaded).

## Cloud styles

- **Rounded** (default) - vanilla's cloud layout with soft rounded edges and corners, so every cloud reads as one
  smooth shape instead of a stack of boxes. Resource packs that change `clouds.png` change these clouds too.
- **Realistic** - soft, wispy clouds made of gas instead of solid shapes, with slowly swirling wisps and edges that
  fade into the sky. Thick clouds pile up higher in the middle and have greyer bases. Coverage sets how cloudy the
  sky is. This style asks the most of your graphics card.
- **Shapes** - clouds shaped like creeper heads, hearts, smileys, stars, swords, diamonds, ghasts, pigs, cats, fish
  and mushrooms, scattered between regular clouds (or on their own).
- **Vanilla** - the game's own clouds. Height, speed and opacity still apply.

## Settings

- **Height** - the cloud base height, from 64 to 512, or the dimension's default.
- **Thickness** - how thick the clouds are. Realistic clouds grow up to six times this tall.
- **Roundness** - how round the edges of Rounded and Shapes clouds are; 0% gives sharp boxes.
- **Coverage** - how much of the sky Realistic clouds cover.
- **Regular Clouds Too** - keep regular clouds between the shapes, or show shapes only.
- **Speed** - how fast the clouds drift, from stopped to five times vanilla speed.
- **Opacity** - how see-through the clouds are.

Settings are saved in `config/billow.json`. The vanilla *Clouds* and *Cloud Distance* video settings still decide
whether clouds show and how far they reach.

## Built to stay fast

Clouds are built in tiles on background threads and uploaded once; drifting clouds only move, they are never
rebuilt. Far-away tiles use simpler geometry, and a tile is only swapped for a finer or coarser one once the new one is
ready, so the sky never flickers or shows holes.

## Install

**Fabric**

1. Install [Fabric Loader](https://fabricmc.net/use/) for your version of Minecraft.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) and the Fabric Billow jar for your version in your `mods`
   folder.
3. Start the game.

Optional: add [Mod Menu](https://modrinth.com/mod/modmenu) to get a settings screen (*Mods > Billow > Configure*).

**NeoForge**

1. Install NeoForge for your version of Minecraft.
2. Put the NeoForge Billow jar in your `mods` folder.
3. Start the game. Settings are under *Mods > Billow > Config*.

## Building

`./gradlew build -Pminecraft_version=26.3` builds the Fabric jar; add `-Ploader=neoforge` for NeoForge (version
configs in `versions/`).
`./gradlew runSelftest` launches the game and runs the in-world checks, taking screenshots of every style; add
`-Pbl.style=realistic` to check one style only, and `-Psodium` or `-Piris` to run alongside those mods.
