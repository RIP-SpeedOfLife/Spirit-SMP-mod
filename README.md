# Spirit SMP — Fabric Java conversion v1.2.0

Target: Minecraft Java 26.3, Fabric (Loader 0.19.5+, Fabric API 0.165.0+26.3), Java 25.

This update expands the roster to **56 spirits** and incorporates the requested behavior changes.

## Controls
- `/spirit claim` — claim an unclaimed spirit.
- `/spirit list` — list all 56 spirits.
- `/spirit ability` — activate the current spirit.
- `/spirit set <spirit>` — operator-only assignment.
- Holding an Amethyst Shard and using it activates the current spirit.

## Updated mechanics
- Lunar/Solar conditions are mutually exclusive and checked continuously.
- Nether buffs only exist in the Nether.
- Ranger has effective infinite arrows/Infinity behavior.
- Meteor uses a Ghast-style fireball and knockback behavior.
- Inferno dash has a 10-second cooldown and fire trail.
- Dragon/Wither flight uses server-side mayfly.
- Invis hides the player and armor while leaving particles visible.
- Miner is 12 hearts.
- Magnet directly pulls players instead of applying Levitation.
- TNT has separate Nuke Shot/Nuke Stab cooldown keys at 2 hours each.
- Warden Mode applies Darkness to nearby other players.
- New spirits 46–56 are registered with passives/active hooks and cooldown state.

## Important implementation note
This is a source project, not a byte-for-byte Bedrock Script API port. A few visual/client-only effects (exact skin-cloned walking decoys, a true Cyclops-style screen tint, exact Elytra rendering, and exact combat attribute manipulation) require client-side rendering/mixin work and are represented by server-safe equivalents in this build.

Build with Gradle 9.6 / Loom 1.17 using Java 25 (`./gradlew build`).
