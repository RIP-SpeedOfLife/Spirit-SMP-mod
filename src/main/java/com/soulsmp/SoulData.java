package com.soulsmp;

import java.util.List;
import java.util.Map;

/** Canonical 57-spirit roster and balance metadata. */
public final class SoulData {
    private SoulData() {}
    public static final List<String> ALL = List.of(
        "Venom Spirit","Ranger Spirit","Sentinel Spirit","Lunar Spirit","Solar Spirit","Meteor Spirit","Destiny Spirit","Inferno Spirit","Vengeance Spirit","Nether Spirit","Illusionist Spirit","Coinflip Spirit","Dragon Spirit","XP Spirit","Random Spirit","Lifesteal Spirit","Weather Spirit","Oracle Spirit","Fire Born","Titan Spirit","Invis Spirit","Misfortune Spirit","Teleporter Spirit","Feather Spirit","Abyss Spirit","Berserker Spirit","Golden Spirit","Miner Spirit","Magnet Spirit","Aqua Spirit","Spider Spirit","Pearl Infinity Spirit","TNT Spirit","Knockback Spirit","Angel Spirit","Juggernaut Spirit","Colossus Spirit","Nightmare Spirit","Orbit Spirit","Thief Spirit","Deep Dark Spirit","Warden Spirit","Wither Spirit","Light Spirit","Ominous Spirit",
        "Ghost Spirit","Anchor Spirit","Blazing Spirit","Aerodynamic Spirit","Vampiric Spirit","Ravenging Spirit","Breach Spirit","Glider Spirit","Mapper Spirit","Blacksmithing Spirit","Lazer Spirit","Projection Spirit"
    );
    public static final Map<String,Integer> MAX_HEALTH = Map.ofEntries(
        Map.entry("Ranger Spirit",24),Map.entry("Meteor Spirit",24),Map.entry("Illusionist Spirit",22),Map.entry("Dragon Spirit",22),Map.entry("Titan Spirit",30),Map.entry("Weather Spirit",26),Map.entry("Oracle Spirit",22),Map.entry("Miner Spirit",24),Map.entry("Angel Spirit",24),Map.entry("Ominous Spirit",24),Map.entry("Wither Spirit",24),Map.entry("Ghost Spirit",18),Map.entry("Ravenging Spirit",22),Map.entry("Breach Spirit",22),Map.entry("Mapper Spirit",24),Map.entry("Lazer Spirit",20)
    );
    public static final Map<String,Long> COOLDOWNS = Map.ofEntries(
        Map.entry("Venom Spirit",240_000L),Map.entry("Meteor Spirit",4_000L),Map.entry("Illusionist Spirit",180_000L),Map.entry("Dragon Spirit",120_000L),Map.entry("Titan Spirit",120_000L),Map.entry("Invis Spirit",1_800_000L),Map.entry("Misfortune Spirit",120_000L),Map.entry("Teleporter Spirit",15_000L),Map.entry("Feather Spirit",300_000L),Map.entry("Inferno Spirit",10_000L),Map.entry("Oracle Spirit",86_400_000L),Map.entry("Magnet Spirit",180_000L),Map.entry("Spider Spirit",30_000L),Map.entry("TNT Nuke Shot",7_200_000L),Map.entry("TNT Nuke Stab",7_200_000L),Map.entry("Juggernaut Spirit",120_000L),Map.entry("Colossus Spirit",60_000L),Map.entry("Nightmare Spirit",300_000L),Map.entry("Orbit Spirit",300_000L),Map.entry("Thief Spirit",300_000L),Map.entry("Deep Dark Spirit",60_000L),Map.entry("Warden Spirit",60_000L),Map.entry("Wither Spirit",60_000L),Map.entry("Light Spirit",30_000L),Map.entry("Ominous Spirit",120_000L),Map.entry("Angel Heal",240_000L),Map.entry("Angel Clap",60_000L),Map.entry("Ravenging Dash",300_000L),Map.entry("Mapper Spirit",7_200_000L),Map.entry("Lazer Broad",30_000L),Map.entry("Lazer Pinpoint",30_000L),Map.entry("Glider Spirit",0L),Map.entry("Coinflip Spirit",86_400_000L),Map.entry("Random Spirit",259_200_000L),Map.entry("Destiny Spirit",604_800_000L),Map.entry("Projection Spirit",180_000L)
    );
    public static final Map<String,String> DESCRIPTIONS = Map.ofEntries(
        Map.entry("Venom Spirit","Poison II to nearby players for 15s; holder is poison-immune; 4m cooldown."),
        Map.entry("Ranger Spirit","12 hearts; permanent Speed I; bows have Infinity and the spirit supplies effectively infinite arrows."),
        Map.entry("Sentinel Spirit","Permanent Resistance I; alerts when players enter render distance."),
        Map.entry("Lunar Spirit","At night only: Speed I, Strength I, Resistance I and Night Vision; all lunar buffs deactivate in daytime."),
        Map.entry("Solar Spirit","During daytime only: Speed I, Strength I, Resistance I and Fire Resistance; all solar buffs deactivate at night."),
        Map.entry("Meteor Spirit","Ghast-style fireball projectile that launches players; 12 hearts; 4s cooldown."),
        Map.entry("Destiny Spirit","Every week, grants 5 random positive effects at Level 5."),
        Map.entry("Inferno Spirit","Permanent Fire Resistance; dash attack on a 10s cooldown; dash leaves a line of fire behind the path."),
        Map.entry("Vengeance Spirit","Tracks the player who killed you and reveals their location until revenge is achieved."),
        Map.entry("Nether Spirit","Strength II, Speed II and Fire Resistance only while in the Nether; deactivates immediately outside it."),
        Map.entry("Illusionist Spirit","Spawns 3 mannequin decoys with your skin, armor and held items; each keeps its own random offset, looks where you look and copies swimming, crouching and elytra flight; any hit makes one vanish; 30s duration; 3m cooldown."),
        Map.entry("Coinflip Spirit","Once per day: 50% chance of 3 powerful random buffs/enchantments or 3 Level-3 debuffs for 24h."),
        Map.entry("Dragon Spirit","Launches Dragon's Breath and grants real controllable flight for 10s; 2m cooldown; 11 hearts."),
        Map.entry("XP Spirit","Every 10 levels: 2 random positive effects plus a random enchantment, scaling with XP milestones."),
        Map.entry("Random Spirit","Every 3 real-life days, copies a random online player's spirit and its abilities."),
        Map.entry("Lifesteal Spirit","Gain 1 heart per player kill up to 20 hearts; death resets the bonus to 10 hearts."),
        Map.entry("Weather Spirit","Can change weather at will; 13 hearts."),
        Map.entry("Oracle Spirit","Permanent Night Vision; reveal a player's location for 30s; 24h cooldown; 11 hearts."),
        Map.entry("Fire Born","Permanent Fire Resistance; held weapons receive Fire Aspect III behavior."),
        Map.entry("Titan Spirit","15 hearts; activate Resistance I for 15s; 2m cooldown."),
        Map.entry("Projection Spirit","Astral projection for 15s: your body stays behind as a decoy with your armor and items while you fly invisibly (gear hidden), fast and untouchable but unable to hurt anyone. Use again to snap back to your body, crouch + use to pull your body to you (both work across dimensions); hitting the body also snaps you back; 3m cooldown."),
        Map.entry("Invis Spirit","Become invisible for 10m, including armor; particles remain visible; 30m cooldown."),
        Map.entry("Misfortune Spirit","Applies random debuffs to nearby players for 30s; 2m cooldown."),
        Map.entry("Teleporter Spirit","Blink up to 100 blocks in the look direction; 15s cooldown."),
        Map.entry("Feather Spirit","No fall damage; launches nearby players upward; 5m cooldown."),
        Map.entry("Abyss Spirit","Below Y=-20: Strength I, Speed I and Resistance I."),
        Map.entry("Berserker Spirit","At 4 hearts or less: Strength II, Speed II and Resistance I."),
        Map.entry("Golden Spirit","Hero of the Village 255; golden apples provide 4 extra absorption hearts."),
        Map.entry("Miner Spirit","12 hearts; Haste III; tools behave as Fortune III + Efficiency III."),
        Map.entry("Magnet Spirit","Yanks nearby players directly toward the holder; 3m cooldown."),
        Map.entry("Aqua Spirit","While in water: Strength I, Speed I, Water Breathing and Resistance I."),
        Map.entry("Spider Spirit","Traps nearby players in cobwebs/Slowness IV for 5s; 30s cooldown."),
        Map.entry("Pearl Infinity Spirit","After an Ender Pearl is thrown, another pearl is restored 5s later."),
        Map.entry("TNT Spirit","Orbital strike cannon: Nuke Shot (rain of ~1100+ TNT) and Stab Shot (a column of 709 TNT) at the block you look at, Overworld only. Each has its own 2-hour cooldown."),
        Map.entry("Knockback Spirit","Permanent Knockback Resistance and Knockback II behavior on weapons."),
        Map.entry("Angel Spirit","12 hearts; permanent Regeneration I; Heavenly Heal (4m) and Thunder Clap (1m)."),
        Map.entry("Juggernaut Spirit","Knockback Resistance and a dash through blocks/players; 2m cooldown."),
        Map.entry("Colossus Spirit","Permanent Resistance II; activate Strength II for 30s; 1m cooldown."),
        Map.entry("Nightmare Spirit","Immune to Poison, Blindness and Nausea; can inflict them on nearby players for 10s; 5m cooldown."),
        Map.entry("Orbit Spirit","Protective shield granting Resistance V for 20s; 5m cooldown."),
        Map.entry("Thief Spirit","Borrow another player's spirit abilities for 1 minute; 5m cooldown."),
        Map.entry("Deep Dark Spirit","Summons a friendly Warden for 3m; 1m cooldown."),
        Map.entry("Warden Spirit","Warden Mode grants Strength I, Speed I and Regeneration; nearby other players receive Darkness; Sonic Boom; 1m activation cooldown."),
        Map.entry("Wither Spirit","12 hearts; real controllable flight; fires Wither Skulls during its timed attack; Withers are repelled; 1m cooldown."),
        Map.entry("Light Spirit","Fires a damaging Light Beam along the look path; 8 hearts damage; 30s cooldown."),
        Map.entry("Ominous Spirit","12 hearts; summons Evoker Fangs and 3 Vexes; 2m cooldown."),
        Map.entry("Ghost Spirit","9 hearts; armor and nameplate become invisible while crouching; 50% less fall damage."),
        Map.entry("Anchor Spirit","Completely immune to knockback from attacks and projectiles."),
        Map.entry("Blazing Spirit","Attacks ignite targets for 2s; immune to fire/lava; takes ticking damage in rain/water; physical attack damage is reduced 20% in water."),
        Map.entry("Aerodynamic Spirit","Permanent Speed I + Jump Boost I; moves normally through cobwebs; takes 10% increased knockback."),
        Map.entry("Vampiric Spirit","A fully charged 110% strike restores 0.5 hearts instantly; burns/ticks and takes minor direct-sun damage without a helmet."),
        Map.entry("Ravenging Spirit","11 hearts + 2 armor bars; knockback resistance; block breaking can consume hunger; configurable hunger cost; heavy-knockback dash applies Slowness II for 5s; illagers do not naturally target you; dash cooldown 5m."),
        Map.entry("Breach Spirit","11 hearts; permanent Breach II and Haste II."),
        Map.entry("Glider Spirit","Permanent glide capability that can be toggled in air; functions alongside a chestplate so chest armor is retained."),
        Map.entry("Mapper Spirit","12 hearts; Locator Bar enabled; reveals a structure location every 2 hours."),
        Map.entry("Blacksmithing Spirit","Held items do not lose durability from use; consumables still consume and totems still work normally."),
        Map.entry("Lazer Spirit","Choose Broad Beam (1s) or Pinpoint Laser (5s). Red charge/aim visuals; 30s recharge; recharge continues while firing; both can instantly break targeted blocks." )
    );
}
