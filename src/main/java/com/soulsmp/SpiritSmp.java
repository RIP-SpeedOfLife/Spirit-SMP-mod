package com.soulsmp;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.datafixers.util.Pair;
import com.soulsmp.mixin.EntityInvoker;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Predicate;

public class SpiritSmp implements ModInitializer {
    public static final String MOD_ID = "soulsmp";
    private static final Logger LOG = LoggerFactory.getLogger(MOD_ID);
    private static final Random RNG = new Random();

    /** Area abilities also affect mobs (so they can be tested alone). Set to false for a players-only SMP. */
    private static final boolean AFFECT_MOBS = true;
    /** Random/Thief pick from the whole roster when no other player is online (solo testing). */
    private static final boolean SOLO_TEST_FALLBACK = true;
    /** Armor points added by Ravenging Spirit. */
    private static final double RAVENGING_ARMOR = 2.0;
    /** Destiny: each chosen effect is re-applied for this many ticks (320 = 16 s) and renewed while the week lasts. */
    private static final int DESTINY_REFRESH_TICKS = 320;
    private static final long DESTINY_WEEK_MS = 7L * 24 * 60 * 60 * 1000;
    /** Juggernaut dash does not break blocks harder than this (obsidian is 50). */
    private static final float JUGGERNAUT_MAX_HARDNESS = 50.0f;

    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};

    private static final List<Holder<MobEffect>> POSITIVE = List.of(MobEffects.SPEED, MobEffects.HASTE, MobEffects.STRENGTH, MobEffects.JUMP_BOOST, MobEffects.REGENERATION, MobEffects.FIRE_RESISTANCE, MobEffects.WATER_BREATHING, MobEffects.NIGHT_VISION, MobEffects.ABSORPTION, MobEffects.HEALTH_BOOST, MobEffects.LUCK, MobEffects.SLOW_FALLING, MobEffects.DOLPHINS_GRACE, MobEffects.CONDUIT_POWER);
    private static final List<Holder<MobEffect>> COIN_GOOD = List.of(MobEffects.SPEED, MobEffects.STRENGTH, MobEffects.RESISTANCE, MobEffects.HASTE, MobEffects.JUMP_BOOST, MobEffects.REGENERATION, MobEffects.ABSORPTION, MobEffects.HEALTH_BOOST, MobEffects.FIRE_RESISTANCE, MobEffects.NIGHT_VISION);
    private static final List<Holder<MobEffect>> COIN_BAD = List.of(MobEffects.SLOWNESS, MobEffects.WEAKNESS, MobEffects.MINING_FATIGUE, MobEffects.HUNGER, MobEffects.UNLUCK);
    private static final List<Holder<MobEffect>> MISFORTUNE = List.of(MobEffects.SLOWNESS, MobEffects.WEAKNESS, MobEffects.MINING_FATIGUE, MobEffects.BLINDNESS, MobEffects.NAUSEA, MobEffects.HUNGER, MobEffects.POISON, MobEffects.DARKNESS, MobEffects.UNLUCK);

    // ---------- tiny scheduler + summoned helpers ----------
    private record Task(long due, Runnable run) {}
    private static final List<Task> TASKS = new ArrayList<>();
    private record Minion(Mob mob, ServerPlayer owner, boolean warden) {}
    private static final List<Minion> MINIONS = new ArrayList<>();
    private record Dur(ItemStack ref, int damage) {}
    private static final Map<UUID, Map<Integer, Dur>> DUR = new HashMap<>();
    private static final Map<UUID, Set<UUID>> SENTINEL = new HashMap<>();
    private static boolean halving = false;

    @Override public void onInitialize() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer p = handler.getPlayer();
            applyMaxHealth(p);
            p.sendSystemMessage(Component.literal("Spirit SMP loaded. Use /spirit list, /spirit claim or hold an amethyst shard and use it to activate your spirit."));
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack held = player.getItemInHand(hand);
            if (world.isClientSide() || !(player instanceof ServerPlayer p)) return InteractionResult.PASS;
            if (held.is(Items.ENDER_PEARL) && SoulState.is(p, "Pearl Infinity Spirit") && !p.isCreative()) {
                int before = count(p, Items.ENDER_PEARL);
                runLater(lvl(p), 2, () -> {
                    if (!p.isRemoved() && count(p, Items.ENDER_PEARL) < before) runLater(lvl(p), 100, () -> {
                        if (p.isRemoved()) return;
                        ItemStack pearl = new ItemStack(Items.ENDER_PEARL);
                        if (!p.getInventory().add(pearl)) net.minecraft.world.Containers.dropItemStack(lvl(p), p.getX(), p.getY(), p.getZ(), pearl);
                    });
                });
                return InteractionResult.PASS;
            }
            if (!held.is(Items.AMETHYST_SHARD)) return InteractionResult.PASS;
            if (p.isShiftKeyDown()) cycleMode(p); else activate(p);
            return InteractionResult.SUCCESS_SERVER;
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClientSide() || !(player instanceof ServerPlayer p) || !(entity instanceof LivingEntity target)) return InteractionResult.PASS;
            String s = SoulState.effective(p);
            if (s.equals("Blazing Spirit")) ignite(target, 40);
            if (s.equals("Vampiric Spirit") && p.getAttackStrengthScale(0.5f) >= 0.99f) p.heal(1.0f);
            return InteractionResult.PASS;
        });

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer p && SoulState.is(p, "Ravenging Spirit")) {
                int cost = 1;
                if (p.getFoodData().getFoodLevel() <= cost) return false;
                p.getFoodData().setFoodLevel(Math.max(0, p.getFoodData().getFoodLevel() - cost));
            }
            return true;
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (isProjBody(entity)) {
                // the left-behind body: any real hit pulls the projection back (drowning/fall/suffocation are ignored)
                if (source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.IN_WALL)) return false;
                ProjBody b = projBodyOf(entity);
                if (b != null) endProjection(b.owner(), true);
                return false;
            }
            if (entity instanceof ServerPlayer pj && isProjecting(pj) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
            if (source.getEntity() instanceof ServerPlayer pa && isProjecting(pa)) return false;
            if (isIllusion(entity)) {
                // immune to drowning, fall and suffocation; any other hit makes the illusion vanish
                if (source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.IN_WALL)) return false;
                removeIllusion(entity);
                return false;
            }
            if (entity instanceof ServerPlayer p && source.is(DamageTypeTags.IS_FALL)) {
                String s = SoulState.effective(p);
                if (s.equals("Feather Spirit")) return false;
                if (s.equals("Ghost Spirit") && !halving) {
                    halving = true;
                    try { p.hurtServer(lvl(p), source, amount * 0.5f); } finally { halving = false; }
                    return false;
                }
            }
            return true;
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer dp && isProjecting(dp)) endProjection(dp, false);
            if (entity instanceof ServerPlayer p && SoulState.is(p, "Lifesteal Spirit")) {
                SoulState.lifestealHearts(p, 0);
                setMaxHealth(p, 20); p.setHealth(20);
            }
            if (source.getEntity() instanceof ServerPlayer killer && SoulState.is(killer, "Lifesteal Spirit") && entity instanceof ServerPlayer) {
                int bonus = Math.min(10, SoulState.lifestealHearts(killer) + 1);
                SoulState.lifestealHearts(killer, bonus);
                applyMaxHealth(killer); killer.setHealth(Math.min(killer.getMaxHealth(), killer.getHealth() + 2));
            }
            // Vengeance: remember who killed you; clear it when you kill them back.
            if (entity instanceof ServerPlayer dead && SoulState.is(dead, "Vengeance Spirit") && source.getEntity() instanceof ServerPlayer k && k != dead)
                SoulState.tag(dead, "soulsmp:vengeance=", k.getUUID().toString());
            if (entity instanceof ServerPlayer victim && source.getEntity() instanceof ServerPlayer avenger && SoulState.is(avenger, "Vengeance Spirit")
                    && victim.getUUID().toString().equals(SoulState.tag(avenger, "soulsmp:vengeance=")))
                SoulState.tag(avenger, "soulsmp:vengeance=", "");
        });

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (Compat.tags(entity).contains("soulsmp_illusion") && !isIllusion(entity) && !isProjBody(entity)) runLater(world, 1, entity::discard); // leftovers from a previous session
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (Illusion i : ILLUSIONS) i.ent().discard(); ILLUSIONS.clear();
            for (ProjBody b : new ArrayList<>(PROJECTIONS.values())) endProjection(b.owner(), true);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer dp = handler.getPlayer();
            if (isProjecting(dp)) endProjection(dp, true);
        });

        ServerTickEvents.END_SERVER_TICK.register(this::tick);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            Commands.literal("spirit")
                .then(Commands.literal("list").executes(c -> { list(c.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("claim").executes(c -> { claim(c.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("remove").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .executes(c -> { remove(c.getSource().getPlayerOrException()); return 1; })
                    .then(Commands.argument("target", net.minecraft.commands.arguments.EntityArgument.player())
                        .executes(c -> { ServerPlayer t = net.minecraft.commands.arguments.EntityArgument.getPlayer(c, "target"); remove(t); c.getSource().sendSuccess(() -> Component.literal("Spirit removed from " + t.getName().getString()), true); return 1; })))
                .then(Commands.literal("ability").executes(c -> { activate(c.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("mode").executes(c -> { cycleMode(c.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("resetcd").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException(); SoulState.resetAll(p);
                    p.sendSystemMessage(Component.literal("All spirit cooldowns/state reset (testing).")); return 1; }))
                .then(Commands.literal("set").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).then(Commands.argument("spirit", StringArgumentType.greedyString()).executes(c -> { set(c.getSource().getPlayerOrException(), StringArgumentType.getString(c,"spirit")); return 1; })))
        ));
    }

    // =====================================================================
    //  TICK
    // =====================================================================
    private void tick(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            try {
                normalizeAttributes(p);
                if (SoulState.effective(p).isBlank()) continue;
                applyPassives(p);
                updateTimers(p);
                if (p.tickCount % 20 == 0) everySecond(p);
            } catch (Exception e) { LOG.error("Spirit tick failed for {}", p.getName().getString(), e); }
        }
        tickMinions();
        tickIllusions(server);
        tickProjections(server);
        long now = server.getTickCount();
        for (int i = 0; i < TASKS.size(); ) {
            Task t = TASKS.get(i);
            if (t.due() <= now) { TASKS.remove(i); try { t.run().run(); } catch (Exception e) { LOG.error("Scheduled task failed", e); } } else i++;
        }
    }

    private static void normalizeAttributes(ServerPlayer p) {
        String s = SoulState.effective(p);
        double kb = switch (s) { case "Anchor Spirit", "Ravenging Spirit" -> 1.0; case "Knockback Spirit", "Juggernaut Spirit" -> 0.8; default -> 0.0; };
        setAttr(p, Attributes.KNOCKBACK_RESISTANCE, kb);
        setAttr(p, Attributes.ARMOR, s.equals("Ravenging Spirit") ? RAVENGING_ARMOR : 0.0);
        setAttr(p, Attributes.MAX_ABSORPTION, s.equals("Golden Spirit") ? 8.0 : 0.0);
    }
    private static void setAttr(ServerPlayer p, Holder<Attribute> attr, double v) {
        AttributeInstance a = p.getAttribute(attr);
        if (a != null && a.getBaseValue() != v) a.setBaseValue(v);
    }

    private static void updateTimers(ServerPlayer p) {
        long now = System.currentTimeMillis();
        String spirit = SoulState.effective(p);
        boolean flier = spirit.equals("Dragon Spirit") || spirit.equals("Wither Spirit");
        if (spirit.equals("Projection Spirit")) flier = true; // astral flight reuses the timed-flight machinery
        if (flier) setFlight(p, SoulState.duration(p, "flight") > now);
        else if ("1".equals(SoulState.getMode(p, "flightOn"))) setFlight(p, false);

        boolean projecting = spirit.equals("Projection Spirit") && isProjecting(p);
        if (projecting) effect(p, MobEffects.SPEED, 25, 1);
        boolean invisOn = spirit.equals("Invis Spirit") && SoulState.duration(p, "invis") > now;
        if (invisOn || projecting) {
            p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 25, 0, false, false, false));
            if (invisOn) lvl(p).sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 2, .25, .6, .25, .01); // Projection has no particles
            invisHide(p);
        } else if ("1".equals(SoulState.getMode(p, "invisHide"))) invisRestore(p);
        if (spirit.equals("Glider Spirit")) gliderTick(p);
        if (spirit.equals("Ghost Spirit")) ghost(p); else if ("1".equals(SoulState.getMode(p, "ghostHide"))) ghostRestore(p);
        if (spirit.equals("Juggernaut Spirit") && SoulState.duration(p, "juggDash") > now) juggernautTick(p);
        if (spirit.equals("Lazer Spirit") && SoulState.duration(p, "lazer") > now) renderLaser(p);
        if (spirit.equals("Blazing Spirit") && p.isInWater() && p.tickCount % 10 == 0) p.hurtServer(lvl(p), p.damageSources().drown(), 0.5f);
        if (spirit.equals("Aerodynamic Spirit")) aerodynamicWebs(p);
        if (spirit.equals("Blacksmithing Spirit")) blacksmith(p);
        if (spirit.equals("Warden Spirit") && SoulState.duration(p, "wardenMode") > now) {
            effect(p, MobEffects.STRENGTH, 25, 0); effect(p, MobEffects.SPEED, 25, 0); effect(p, MobEffects.REGENERATION, 25, 0);
            for (ServerPlayer other : lvl(p).players()) if (other != p && other.distanceTo(p) <= 32)
                other.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, false, false, false));
        }
    }

    private static void applyPassives(ServerPlayer p) {
        String s = SoulState.effective(p);
        applyMaxHealth(p);
        switch (s) {
            case "Venom Spirit" -> p.removeEffect(MobEffects.POISON);
            case "Ranger Spirit" -> { effect(p, MobEffects.SPEED, 40, 0); ensureEnchant(p, st -> st.is(Items.BOW), Enchantments.INFINITY, 1); }
            case "Sentinel Spirit" -> effect(p, MobEffects.RESISTANCE, 40, 0);
            case "Lunar Spirit" -> { if (isNight(p)) { effect(p, MobEffects.SPEED, 40, 0); effect(p, MobEffects.STRENGTH, 40, 0); effect(p, MobEffects.RESISTANCE, 40, 0); effect(p, MobEffects.NIGHT_VISION, 220, 0); } }
            case "Solar Spirit" -> { if (!isNight(p)) { effect(p, MobEffects.SPEED, 40, 0); effect(p, MobEffects.STRENGTH, 40, 0); effect(p, MobEffects.RESISTANCE, 40, 0); effect(p, MobEffects.FIRE_RESISTANCE, 40, 0); } }
            case "Nether Spirit" -> { if (p.level().dimension() == Level.NETHER) { effect(p, MobEffects.STRENGTH, 40, 1); effect(p, MobEffects.SPEED, 40, 1); effect(p, MobEffects.FIRE_RESISTANCE, 40, 0); } }
            case "Inferno Spirit" -> effect(p, MobEffects.FIRE_RESISTANCE, 40, 0);
            case "Fire Born" -> {
                effect(p, MobEffects.FIRE_RESISTANCE, 40, 0);
                ensureEnchant(p, SpiritSmp::isMelee, Enchantments.FIRE_ASPECT, 3);
            }
            case "Knockback Spirit" -> ensureEnchant(p, SpiritSmp::isMelee, Enchantments.KNOCKBACK, 2);
            case "Miner Spirit" -> {
                effect(p, MobEffects.HASTE, 40, 2);
                Predicate<ItemStack> tool = st -> st.is(ItemTags.PICKAXES) || st.is(ItemTags.AXES) || st.is(ItemTags.SHOVELS) || st.is(ItemTags.HOES);
                ensureEnchant(p, tool, Enchantments.FORTUNE, 3); ensureEnchant(p, tool, Enchantments.EFFICIENCY, 3);
            }
            case "Abyss Spirit" -> { if (p.getY() < -20) { effect(p, MobEffects.STRENGTH, 40, 0); effect(p, MobEffects.SPEED, 40, 0); effect(p, MobEffects.RESISTANCE, 40, 0); } }
            case "Berserker Spirit" -> { if (p.getHealth() <= 8) { effect(p, MobEffects.STRENGTH, 40, 1); effect(p, MobEffects.SPEED, 40, 1); effect(p, MobEffects.RESISTANCE, 40, 0); } }
            case "Feather Spirit" -> effect(p, MobEffects.SLOW_FALLING, 40, 0);
            case "Aqua Spirit" -> { if (p.isInWater()) { effect(p, MobEffects.STRENGTH, 40, 0); effect(p, MobEffects.SPEED, 40, 0); effect(p, MobEffects.WATER_BREATHING, 40, 0); effect(p, MobEffects.RESISTANCE, 40, 0); } }
            case "Colossus Spirit" -> effect(p, MobEffects.RESISTANCE, 40, 1);
            case "Angel Spirit" -> effect(p, MobEffects.REGENERATION, 40, 0);
            case "Nightmare Spirit" -> { p.removeEffect(MobEffects.POISON); p.removeEffect(MobEffects.BLINDNESS); p.removeEffect(MobEffects.NAUSEA); }
            case "Oracle Spirit" -> effect(p, MobEffects.NIGHT_VISION, 220, 0);
            case "Wither Spirit" -> effect(p, MobEffects.FIRE_RESISTANCE, 40, 0);
            case "Ghost Spirit" -> { if (p.isCrouching()) effect(p, MobEffects.INVISIBILITY, 25, 0); }
            case "Blazing Spirit" -> effect(p, MobEffects.FIRE_RESISTANCE, 40, 0);
            case "Aerodynamic Spirit" -> { effect(p, MobEffects.SPEED, 40, 0); effect(p, MobEffects.JUMP_BOOST, 40, 0); }
            case "Breach Spirit" -> { effect(p, MobEffects.HASTE, 40, 1); ensureEnchant(p, SpiritSmp::isWeapon, Enchantments.BREACH, 2); }
            case "Golden Spirit" -> { effect(p, MobEffects.HERO_OF_THE_VILLAGE, 40, 254); goldenApples(p); }
            case "Vampiric Spirit" -> vampiricSun(p);
            default -> {}
        }
    }

    /** Things that only need to run once a second. */
    private static void everySecond(ServerPlayer p) {
        String s = SoulState.effective(p);
        String base = SoulState.spirit(p);
        if (base.equals("Random Spirit") && SoulState.ready(p, "Random Spirit")) rollRandom(p, true);
        switch (s) {
            case "Sentinel Spirit" -> sentinel(p);
            case "Destiny Spirit" -> destiny(p);
            case "XP Spirit" -> xpSpirit(p);
            case "Vengeance Spirit" -> vengeance(p);
            default -> {}
        }
    }

    // =====================================================================
    //  ACTIVATION
    // =====================================================================
    private static void activate(ServerPlayer p) {
        String s = SoulState.effective(p);
        if (s.isBlank()) { p.sendSystemMessage(Component.literal("You do not have a spirit.")); return; }
        switch (s) {
            case "Venom Spirit" -> pulsePoison(p);
            case "Meteor Spirit" -> meteor(p);
            case "Illusionist Spirit" -> illusions(p);
            case "Inferno Spirit" -> infernoDash(p);
            case "Coinflip Spirit" -> coinflip(p);
            case "Dragon Spirit" -> dragonBreath(p);
            case "Random Spirit" -> rollRandom(p, false);
            case "Weather Spirit" -> weather(p);
            case "Oracle Spirit" -> oracle(p);
            case "Titan Spirit" -> buff(p, "Titan Spirit", 300, MobEffects.RESISTANCE, 0);
            case "Invis Spirit" -> invis(p);
            case "Projection Spirit" -> projection(p);
            case "Misfortune Spirit" -> misfortune(p);
            case "Teleporter Spirit" -> teleport(p);
            case "Feather Spirit" -> launchNearby(p);
            case "Magnet Spirit" -> magnet(p);
            case "Spider Spirit" -> spider(p);
            case "TNT Spirit" -> tnt(p);
            case "Angel Spirit" -> angelHeal(p);
            case "Juggernaut Spirit" -> juggernaut(p);
            case "Colossus Spirit" -> buff(p, "Colossus Spirit", 600, MobEffects.STRENGTH, 1);
            case "Nightmare Spirit" -> nightmare(p);
            case "Orbit Spirit" -> buff(p, "Orbit Spirit", 400, MobEffects.RESISTANCE, 4);
            case "Thief Spirit" -> thief(p);
            case "Deep Dark Spirit" -> friendlyWarden(p);
            case "Warden Spirit" -> wardenMode(p);
            case "Wither Spirit" -> witherFlight(p);
            case "Light Spirit" -> lightBeam(p);
            case "Ominous Spirit" -> ominous(p);
            case "Ravenging Spirit" -> ravengingDash(p);
            case "Glider Spirit" -> toggleGlider(p);
            case "Mapper Spirit" -> mapper(p);
            case "Lazer Spirit" -> lazer(p);
            case "Destiny Spirit" -> destinyInfo(p);
            case "XP Spirit" -> p.sendSystemMessage(Component.literal("XP Spirit is automatic: it triggers every 10 XP levels."));
            case "Blazing Spirit" -> p.sendSystemMessage(Component.literal("Blazing Spirit is passive; attacks ignite targets and water is hazardous."));
            default -> p.sendSystemMessage(Component.literal("That spirit has no manual activation."));
        }
    }

    private static boolean start(ServerPlayer p, String key) {
        long cd = SoulData.COOLDOWNS.getOrDefault(key, 0L);
        if (!SoulState.ready(p, key)) { cdMsg(p, key); return false; }
        SoulState.startCd(p, key, cd); return true;
    }
    private static void cdMsg(ServerPlayer p, String key) {
        long sec = Math.max(1, (SoulState.getCd(p, key) - System.currentTimeMillis() + 999) / 1000);
        String t = sec >= 3600 ? (sec / 3600) + "h " + ((sec % 3600) / 60) + "m" : sec >= 60 ? (sec / 60) + "m " + (sec % 60) + "s" : sec + "s";
        p.sendSystemMessage(Component.literal("Cooldown: " + t));
    }
    private static void cdInfo(ServerPlayer p, String key, String text) {
        p.sendSystemMessage(Component.literal(text));
        if (!SoulState.ready(p, key)) cdMsg(p, key);
    }

    private static void cycleMode(ServerPlayer p) {
        switch (SoulState.effective(p)) {
            case "Lazer Spirit" -> {
                String m = SoulState.getMode(p, "mode:lazer").equals("broad") ? "pinpoint" : "broad";
                SoulState.setMode(p, "mode:lazer", m);
                p.sendSystemMessage(Component.literal("Lazer mode: " + (m.equals("broad") ? "Broad Beam (1s)" : "Pinpoint Laser (5s)")));
            }
            case "TNT Spirit" -> {
                String m = SoulState.getMode(p, "mode:tnt").equals("stab") ? "shot" : "stab";
                SoulState.setMode(p, "mode:tnt", m);
                p.sendSystemMessage(Component.literal("TNT mode: " + (m.equals("stab") ? "Stab Shot" : "Nuke Shot")));
            }
            case "Projection Spirit" -> pullBody(p);
            default -> p.sendSystemMessage(Component.literal("Your spirit has no selectable modes."));
        }
    }

    // =====================================================================
    //  ABILITIES
    // =====================================================================
    private static void pulsePoison(ServerPlayer p) {
        if (!start(p, "Venom Spirit")) return;
        for (LivingEntity q : targets(p, 8)) effect(q, MobEffects.POISON, 300, 1);
    }

    private static void launchFireball(ServerPlayer p, int power) {
        Vec3 v = p.getLookAngle();
        LargeFireball fb = new LargeFireball(p.level(), p, new Vec3(v.x, v.y, v.z), power);
        fb.setPos(p.getX() + v.x * 2, p.getEyeY() + v.y * 2, p.getZ() + v.z * 2);
        p.level().addFreshEntity(fb);
    }
    private static void meteor(ServerPlayer p) {
        if (!start(p, "Meteor Spirit")) return;
        launchFireball(p, 2);
    }

    // ----- Illusionist: 3 mannequins with your skin that keep a fixed random offset and copy your look direction, pose, armor and held items -----
    private record Illusion(LivingEntity ent, ServerPlayer owner, Vec3 offset, long expires, boolean[] swing) {}
    private static final List<Illusion> ILLUSIONS = new ArrayList<>();
    private static final int ILLUSION_COUNT = 3;
    private static final int ILLUSION_TICKS = 20 * 30;
    private static final EquipmentSlot[] COPY_SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};

    private static boolean isIllusion(Entity e) { for (Illusion i : ILLUSIONS) if (i.ent() == e) return true; return false; }
    private static void removeIllusion(Entity e) {
        for (Iterator<Illusion> it = ILLUSIONS.iterator(); it.hasNext(); ) {
            Illusion i = it.next();
            if (i.ent() == e) { it.remove(); poof(i.ent()); i.ent().discard(); return; }
        }
    }
    private static void poof(Entity e) {
        if (e.level() instanceof ServerLevel l) l.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 1, e.getZ(), 12, .3, .5, .3, .02);
    }

    /** Calls a public no-arg method by name (authlib accessors changed names between versions, so try each). */
    private static Object call(Object o, String... names) {
        if (o == null) return null;
        for (String n : names) { try { return o.getClass().getMethod(n).invoke(o); } catch (Exception ignored) {} }
        return null;
    }
    /** The player's real skin textures (name/value/signature), so the mannequin does not need to look the skin up. */
    private static ListTag skinProperties(ServerPlayer p) {
        ListTag list = new ListTag();
        try {
            Object props = call(p.getGameProfile(), "properties", "getProperties");
            Object vals = call(props, "values");
            if (vals instanceof Collection<?> c) for (Object pr : c) {
                Object n = call(pr, "name", "getName"), v = call(pr, "value", "getValue"), sg = call(pr, "signature", "getSignature");
                if (!(n instanceof String ns) || !(v instanceof String vs)) continue;
                CompoundTag t = new CompoundTag();
                t.putString("name", ns); t.putString("value", vs);
                if (sg instanceof String ss) t.putString("signature", ss);
                list.add(t);
            }
        } catch (Exception e) { LOG.warn("Could not read skin textures", e); }
        return list;
    }

    /** A mannequin wearing the player's skin, tagged so leftovers are cleaned up after a crash. Not added to the world yet. */
    private static LivingEntity mannequin(ServerPlayer p, ServerLevel l) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:mannequin");
        ListTag skin = skinProperties(p);
        if (!skin.isEmpty()) {
            CompoundTag profile = new CompoundTag();
            profile.putString("name", p.getName().getString());
            profile.putIntArray("id", UUIDUtil.uuidToIntArray(p.getUUID()));
            profile.put("properties", skin);
            tag.put("profile", profile);
        } else {
            tag.putString("profile", p.getName().getString()); // name only => the game resolves the skin itself
        }
        tag.putBoolean("immovable", true);
        tag.putBoolean("hide_description", true);
        tag.putString("main_hand", p.getMainArm() == HumanoidArm.LEFT ? "left" : "right");
        Entity e = EntityType.loadEntityRecursive(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, l.registryAccess(), tag), l, EntitySpawnReason.COMMAND, x -> x);
        if (!(e instanceof LivingEntity m)) { LOG.error("Could not create a mannequin"); if (e != null) e.discard(); return null; }
        m.addTag("soulsmp_illusion");
        m.setNoGravity(true);
        m.setSilent(true);
        m.setCustomName(Component.literal(p.getName().getString()));
        m.setCustomNameVisible(true);
        return m;
    }

    private static void illusions(ServerPlayer p) {
        if (!start(p, "Illusionist Spirit")) return;
        ServerLevel l = lvl(p);
        List<Vec3> used = new ArrayList<>();
        int made = 0;
        for (int i = 0; i < ILLUSION_COUNT; i++) {
            Vec3 off = null;
            for (int t = 0; t < 30 && off == null; t++) {
                double a = RNG.nextDouble() * Math.PI * 2, r = 1.5 + RNG.nextDouble() * 2.0;
                Vec3 c = new Vec3(Math.cos(a) * r, 0, Math.sin(a) * r);
                boolean ok = true; for (Vec3 u : used) if (u.distanceTo(c) < 1.2) ok = false;
                if (ok) off = c;
            }
            if (off == null) off = new Vec3(RNG.nextDouble() * 4 - 2, 0, RNG.nextDouble() * 4 - 2);
            used.add(off);

            LivingEntity m = mannequin(p, l);
            if (m == null) continue;
            Vec3 pos = p.position().add(off);
            m.setPos(pos.x, pos.y, pos.z);
            ILLUSIONS.add(new Illusion(m, p, off, l.getServer().getTickCount() + ILLUSION_TICKS, new boolean[]{false}));
            l.addFreshEntity(m);
            made++;
        }
        if (made == 0) { SoulState.setCd(p, "Illusionist Spirit", 0); p.sendSystemMessage(Component.literal("Could not create the illusions.")); }
    }

    private static void tickIllusions(MinecraftServer server) {
        long now = server.getTickCount();
        for (Iterator<Illusion> it = ILLUSIONS.iterator(); it.hasNext(); ) {
            Illusion i = it.next();
            LivingEntity m = i.ent(); ServerPlayer o = i.owner();
            if (m.isRemoved()) { it.remove(); continue; }
            if (o.isRemoved() || !o.isAlive() || now >= i.expires() || o.level() != m.level()) { poof(m); m.discard(); it.remove(); continue; }
            Vec3 pos = o.position().add(i.offset());
            m.setPos(pos.x, pos.y, pos.z);
            m.setYRot(o.getYRot()); m.setXRot(o.getXRot());
            m.setYHeadRot(o.getYHeadRot()); m.setYBodyRot(o.yBodyRot);
            Pose pose = o.getPose() == Pose.SLEEPING ? Pose.STANDING : o.getPose();
            m.setPose(pose);
            m.setShiftKeyDown(o.isShiftKeyDown());
            m.setSprinting(o.isSprinting());
            m.setSwimming(o.isSwimming());
            m.setInvisible(o.isInvisible());
            boolean glide = o.isFallFlying();
            if (glide != m.isFallFlying()) ((EntityInvoker) m).soulsmp$setSharedFlag(7, glide); // shared flag 7 = fall flying
            for (EquipmentSlot es : COPY_SLOTS) {
                ItemStack src = o.getItemBySlot(es);
                if (!ItemStack.matches(m.getItemBySlot(es), src)) m.setItemSlot(es, src.copy());
            }
            // attack / jab / place: replay the swing when yours starts (the held item decides if it is a swing or a spear jab)
            boolean sw = Compat.swinging(o);
            if (sw && !i.swing()[0]) Compat.swing(m, o);
            i.swing()[0] = sw;
            // eating, drinking, drawing a bow, blocking...
            if (o.isUsingItem()) { if (!m.isUsingItem() || m.getUsedItemHand() != o.getUsedItemHand()) m.startUsingItem(o.getUsedItemHand()); }
            else if (m.isUsingItem()) m.stopUsingItem();
        }
    }


    // ----- Projection: your body stays behind as a decoy while you roam as an invisible, untouchable astral form -----
    private record ProjBody(LivingEntity body, ServerPlayer owner, ServerLevel level, Vec3 pos, float yRot, float xRot) {}
    private static final Map<UUID, ProjBody> PROJECTIONS = new HashMap<>();
    private static final long PROJECTION_MS = 15_000;

    private static boolean isProjecting(ServerPlayer p) { return PROJECTIONS.containsKey(p.getUUID()); }
    private static boolean isProjBody(Entity e) { return projBodyOf(e) != null; }
    private static ProjBody projBodyOf(Entity e) { for (ProjBody b : PROJECTIONS.values()) if (b.body() == e) return b; return null; }

    private static void projection(ServerPlayer p) {
        if (isProjecting(p)) { // using the ability again ends it: crouched pulls your body to you, otherwise you snap back to your body
            if (p.isShiftKeyDown()) pullBody(p); else endProjection(p, true);
            return;
        }
        if (!start(p, "Projection Spirit")) return;
        ServerLevel l = lvl(p);
        LivingEntity body = mannequin(p, l);
        if (body == null) { SoulState.setCd(p, "Projection Spirit", 0); p.sendSystemMessage(Component.literal("Could not create your body.")); return; }
        body.setPos(p.getX(), p.getY(), p.getZ());
        body.setYRot(p.getYRot()); body.setXRot(p.getXRot());
        body.setYHeadRot(p.getYHeadRot()); body.setYBodyRot(p.yBodyRot);
        body.setPose(p.getPose() == Pose.SLEEPING ? Pose.STANDING : p.getPose());
        body.setShiftKeyDown(p.isShiftKeyDown());
        for (EquipmentSlot es : COPY_SLOTS) body.setItemSlot(es, p.getItemBySlot(es).copy());
        PROJECTIONS.put(p.getUUID(), new ProjBody(body, p, l, p.position(), p.getYRot(), p.getXRot()));
        l.addFreshEntity(body);
        long until = System.currentTimeMillis() + PROJECTION_MS;
        SoulState.setDuration(p, "projection", until);
        SoulState.setDuration(p, "flight", until);
        setFlight(p, true);
        p.sendSystemMessage(Component.literal("Astral projection: 15s. Use the ability again to return to your body, or crouch + use to pull your body to you. If your body is hit you snap back.").withStyle(st -> st.withColor(0xFF55FF)));
    }

    /** Ends the projection: removes the body decoy and (optionally) puts the player back where the body stood. */
    private static void endProjection(ServerPlayer p, boolean teleportBack) {
        ProjBody b = PROJECTIONS.remove(p.getUUID());
        SoulState.setDuration(p, "projection", 0);
        SoulState.setDuration(p, "flight", 0);
        if (b == null) return;
        if (!b.body().isRemoved()) b.body().discard();
        if (teleportBack && !p.isRemoved() && p.isAlive()) {
            Vec3 t = b.pos();
            try {
                if (p.level() == b.level()) p.connection.teleport(t.x, t.y, t.z, b.yRot(), b.xRot());
                else p.teleport(new TeleportTransition(b.level(), t, Vec3.ZERO, b.yRot(), b.xRot(), TeleportTransition.DO_NOTHING)); // body is in another dimension
            } catch (Exception e) { LOG.warn("Could not return the projection to its body", e); }
        }
        if (!p.isRemoved()) {
            p.resetFallDistance();
            setFlight(p, false);
            p.removeEffect(MobEffects.INVISIBILITY);
            if ("1".equals(SoulState.getMode(p, "invisHide"))) invisRestore(p);
        }
    }

    /** Crouch + use while projecting: the body is pulled to where the projection is (nobody is teleported back). */
    private static void pullBody(ServerPlayer p) {
        if (!isProjecting(p)) { p.sendSystemMessage(Component.literal("You are not projecting.")); return; }
        endProjection(p, false);
        p.sendSystemMessage(Component.literal("Your body was pulled to you.").withStyle(st -> st.withColor(0xFF55FF)));
    }

    private static void tickProjections(MinecraftServer server) {
        long now = System.currentTimeMillis();
        for (ProjBody b : new ArrayList<>(PROJECTIONS.values())) {
            ServerPlayer o = b.owner();
            if (o.isRemoved() || !o.isAlive()) endProjection(o, false);
            else if ((b.body().isRemoved() && b.body().getRemovalReason() != null && b.body().getRemovalReason().shouldDestroy()) || !SoulState.is(o, "Projection Spirit") || SoulState.duration(o, "projection") <= now) endProjection(o, true);
        }
    }

    private static void doDash(ServerPlayer p, double speed) {
        Vec3 v = p.getLookAngle();
        p.setDeltaMovement(v.x * speed, v.y * 0.5 + 0.15, v.z * speed);
        Compat.sync(p);
    }
    private static void infernoDash(ServerPlayer p) {
        if (!start(p, "Inferno Spirit")) return;
        doDash(p, 2.8);
        ServerLevel l = lvl(p);
        Vec3 look = p.getLookAngle(); Vec3 h = new Vec3(look.x, 0, look.z);
        if (h.lengthSqr() < 1.0E-4) return; h = h.normalize();
        for (int i = 1; i <= 10; i++) {
            BlockPos pos = BlockPos.containing(p.getX() + h.x * i, p.getY(), p.getZ() + h.z * i);
            if (l.getBlockState(pos).isAir()) { BlockState f = Blocks.FIRE.defaultBlockState(); if (f.canSurvive(l, pos)) l.setBlockAndUpdate(pos, f); }
        }
    }
    private static final Map<UUID, Vec3> JUGG = new HashMap<>();
    /** Juggernaut: a 0.5s charge that smashes every solid block (up to JUGGERNAUT_MAX_HARDNESS) and shoves whoever it hits. */
    private static void juggernaut(ServerPlayer p) {
        if (!start(p, "Juggernaut Spirit")) return;
        JUGG.put(p.getUUID(), p.getLookAngle());
        SoulState.setDuration(p, "juggDash", System.currentTimeMillis() + 500);
    }
    private static void juggernautTick(ServerPlayer p) {
        Vec3 d = JUGG.get(p.getUUID());
        if (d == null) return;
        ServerLevel l = lvl(p);
        p.setDeltaMovement(d.x * 1.4, d.y * 1.4 + 0.05, d.z * 1.4);
        Compat.sync(p);
        AABB bb = p.getBoundingBox();
        AABB box = new AABB(bb.minX, bb.minY + 0.2, bb.minZ, bb.maxX, bb.maxY, bb.maxZ).expandTowards(d.scale(3.0));
        int broken = 0;
        for (BlockPos raw : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (broken >= 200) break;
            BlockPos pos = raw.immutable();
            BlockState st = l.getBlockState(pos);
            if (st.isAir() || st.getCollisionShape(l, pos).isEmpty()) continue;
            float h = st.getDestroySpeed(l, pos);
            if (h < 0 || h >= JUGGERNAUT_MAX_HARDNESS) continue;
            l.destroyBlock(pos, true, p);
            broken++;
        }
        for (LivingEntity t : targets(p, 2.5)) shove(t, new Vec3(d.x * 0.8, 0.25, d.z * 0.8));
    }

    private static void dragonBreath(ServerPlayer p) {
        if (!start(p, "Dragon Spirit")) return;
        Vec3 v = p.getLookAngle();
        DragonFireball f = new DragonFireball(p.level(), p, new Vec3(v.x * 1.5, v.y * 1.5, v.z * 1.5));
        f.setPos(p.getX() + v.x * 2, p.getEyeY() + v.y * 2, p.getZ() + v.z * 2);
        p.level().addFreshEntity(f);
        SoulState.setDuration(p, "flight", System.currentTimeMillis() + 10_000);
        setFlight(p, true);
    }
    private static void witherFlight(ServerPlayer p) {
        if (!start(p, "Wither Spirit")) return;
        SoulState.setDuration(p, "flight", System.currentTimeMillis() + 15_000);
        setFlight(p, true);
    }
    /** Only touches abilities for survival/adventure players, so creative flight is left alone. */
    private static void setFlight(ServerPlayer p, boolean on) {
        if (p.isCreative() || p.isSpectator()) return;
        boolean was = "1".equals(SoulState.getMode(p, "flightOn"));
        if (on == was) return;
        SoulState.setMode(p, "flightOn", on ? "1" : "0");
        p.getAbilities().mayfly = on;
        if (!on) p.getAbilities().flying = false;
        p.onUpdateAbilities();
    }

    private static void invis(ServerPlayer p) {
        if (!start(p, "Invis Spirit")) return;
        SoulState.setDuration(p, "invis", System.currentTimeMillis() + 600_000);
    }

    private static void misfortune(ServerPlayer p) {
        if (!start(p, "Misfortune Spirit")) return;
        for (LivingEntity q : targets(p, 8))
            for (Holder<MobEffect> e : pickDistinct(MISFORTUNE, 3)) effect(q, e, 600, 1);
    }

    private static void teleport(ServerPlayer p) {
        if (!start(p, "Teleporter Spirit")) return;
        Vec3 eye = p.getEyePosition(); Vec3 look = p.getLookAngle(); Vec3 end = eye.add(look.scale(100));
        BlockHitResult hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 target = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation().subtract(look.scale(0.6));
        Vec3 delta = target.subtract(0, p.getEyeHeight(), 0).subtract(p.position());
        boolean ok = p.level().noCollision(p, p.getBoundingBox().move(delta));
        for (int i = 0; i < 4 && !ok; i++) { delta = delta.add(0, 0.4, 0); ok = p.level().noCollision(p, p.getBoundingBox().move(delta)); }
        for (int i = 0; i < 12 && !ok; i++) { delta = delta.subtract(look.scale(0.5)); ok = p.level().noCollision(p, p.getBoundingBox().move(delta)); }
        if (!ok) { SoulState.setCd(p, "Teleporter Spirit", 0); p.sendSystemMessage(Component.literal("No room to teleport there.")); return; }
        Vec3 dest = p.position().add(delta);
        p.connection.teleport(dest.x, dest.y, dest.z, p.getYRot(), p.getXRot());
    }

    private static void launchNearby(ServerPlayer p) {
        if (!start(p, "Feather Spirit")) return;
        for (LivingEntity q : targets(p, 6)) { Vec3 d = q.position().subtract(p.position()).normalize(); shove(q, new Vec3(d.x, 1.2, d.z)); }
    }
    private static void magnet(ServerPlayer p) {
        if (!start(p, "Magnet Spirit")) return;
        for (LivingEntity q : targets(p, 10)) { Vec3 d = p.position().subtract(q.position()).normalize(); shove(q, new Vec3(d.x * 1.4, .15, d.z * 1.4)); }
    }
    private static void spider(ServerPlayer p) {
        if (!start(p, "Spider Spirit")) return;
        ServerLevel l = lvl(p);
        for (LivingEntity q : targets(p, 7)) {
            effect(q, MobEffects.SLOWNESS, 100, 3);
            BlockPos feet = q.blockPosition();
            for (BlockPos pos : new BlockPos[]{feet, feet.above()}) {
                if (l.getBlockState(pos).isAir()) {
                    l.setBlockAndUpdate(pos, Blocks.COBWEB.defaultBlockState());
                    runLater(l, 100, () -> { if (l.getBlockState(pos).is(Blocks.COBWEB)) l.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()); });
                }
            }
        }
    }
    private static void angelHeal(ServerPlayer p) {
        if (!start(p, "Angel Heal")) return;
        p.setHealth(p.getMaxHealth()); effect(p, MobEffects.REGENERATION, 200, 1);
    }
    private static void nightmare(ServerPlayer p) {
        if (!start(p, "Nightmare Spirit")) return;
        for (LivingEntity q : targets(p, 8)) { effect(q, MobEffects.POISON, 200, 0); effect(q, MobEffects.BLINDNESS, 200, 0); effect(q, MobEffects.NAUSEA, 200, 0); }
    }

    // ----- TNT Spirit = Orbital Strike Cannon (patterns come from the datapack: soulsmp_nuke_patterns.txt) -----
    private static List<List<double[]>> NUKE_PATTERNS;
    private static int nukeCycle = RNG.nextInt(27);
    private static synchronized List<List<double[]>> nukePatterns() {
        if (NUKE_PATTERNS != null) return NUKE_PATTERNS;
        List<List<double[]>> out = new ArrayList<>();
        try (var in = SpiritSmp.class.getResourceAsStream("/soulsmp_nuke_patterns.txt")) {
            if (in != null) {
                var r = new java.io.BufferedReader(new java.io.InputStreamReader(in));
                String line; List<double[]> cur = null;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    if (line.startsWith("#")) { cur = new ArrayList<>(); out.add(cur); continue; }
                    String[] a = line.split(" ");
                    if (cur != null) cur.add(new double[]{Double.parseDouble(a[0]), Double.parseDouble(a[1]), Double.parseDouble(a[2])});
                }
            }
        } catch (Exception e) { LOG.error("Could not load nuke patterns", e); }
        NUKE_PATTERNS = out;
        return out;
    }

    private static void tnt(ServerPlayer p) {
        boolean stab = "stab".equals(SoulState.getMode(p, "mode:tnt"));
        String key = stab ? "TNT Nuke Stab" : "TNT Nuke Shot";
        if (!SoulState.ready(p, key)) { cdMsg(p, key); return; }
        if (p.level().dimension() != Level.OVERWORLD) { p.sendSystemMessage(Component.literal("The orbital cannon only works in the Overworld.")); return; }
        double range = Math.min(256, lvl(p).getServer().getPlayerList().getViewDistance() * 16);
        Vec3 eye = p.getEyePosition();
        BlockHitResult hit = p.level().clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(range)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        if (hit.getType() == HitResult.Type.MISS) { p.sendSystemMessage(Component.literal("No block in sight (range " + (int) range + ").")); return; }
        Vec3 t = hit.getLocation();
        ServerLevel l = lvl(p);
        if (stab) {
            // Stab Shot (as in the datapack): TNT spawned at Y=319 over the target, falling at 10 blocks/tick
            final double y = 319;
            for (int i = 0; i < 400; i++) spawnTnt(l, p, t.x, y, t.z, 11, 0, -10, 0);
            for (int i = 0; i < 300; i++) spawnTnt(l, p, t.x, y, t.z, 10, 0, -10, 0);
            for (int f = 1; f <= 9; f++) spawnTnt(l, p, t.x, y, t.z, f, 0, -10, 0);
        } else {
            // Nuke Shot: ~1100-1500 TNT raining from 70 blocks above, one of 27 patterns in rotation
            List<List<double[]>> pats = nukePatterns();
            if (pats.isEmpty()) { p.sendSystemMessage(Component.literal("Nuke patterns missing from the jar.")); return; }
            List<double[]> pat = pats.get(nukeCycle % pats.size());
            nukeCycle++;
            for (double[] m : pat) spawnTnt(l, p, t.x, t.y + 70, t.z, 80, m[0], m[1], m[2]);
        }
        SoulState.startCd(p, key, SoulData.COOLDOWNS.get(key));
        p.sendSystemMessage(Component.literal((stab ? "Stab Shot" : "Nuke Shot") + " fired at " + (int) t.x + " " + (int) t.y + " " + (int) t.z).withStyle(st -> st.withColor(0xFF5555)));
    }
    private static void spawnTnt(ServerLevel l, ServerPlayer owner, double x, double y, double z, int fuse, double mx, double my, double mz) {
        PrimedTnt tnt = new PrimedTnt(l, x, y, z, owner);
        tnt.setFuse(fuse);
        tnt.setDeltaMovement(mx, my, mz);
        l.addFreshEntity(tnt);
    }

    private static void thief(ServerPlayer p) {
        if (!SoulState.ready(p, "Thief Spirit")) { cdMsg(p, "Thief Spirit"); return; }
        List<String> pool = new ArrayList<>();
        for (ServerPlayer q : players(p, 16)) { String s = SoulState.spirit(q); if (q != p && !s.isBlank() && !s.equals("Thief Spirit") && !s.equals("Random Spirit")) pool.add(s); }
        if (pool.isEmpty() && SOLO_TEST_FALLBACK) { pool.addAll(rosterWithoutCopiers()); p.sendSystemMessage(Component.literal("(solo test: no player nearby, picking from the roster)")); }
        if (pool.isEmpty()) { p.sendSystemMessage(Component.literal("No other player with a spirit within 16 blocks.")); return; }
        String s = pool.get(RNG.nextInt(pool.size()));
        SoulState.tag(p, "soulsmp:borrow=", s);
        SoulState.tag(p, "soulsmp:borrowuntil=", Long.toString(System.currentTimeMillis() + 60_000));
        SoulState.startCd(p, "Thief Spirit", SoulData.COOLDOWNS.get("Thief Spirit"));
        p.sendSystemMessage(Component.literal("Borrowed " + s + " for 60 seconds. Use your ability to activate it."));
    }

    private static void rollRandom(ServerPlayer p, boolean quiet) {
        if (!SoulState.ready(p, "Random Spirit")) { if (!quiet) cdMsg(p, "Random Spirit"); return; }
        List<String> pool = new ArrayList<>();
        for (ServerPlayer q : lvl(p).getServer().getPlayerList().getPlayers()) {
            String s = SoulState.spirit(q);
            if (q != p && !s.isBlank() && !s.equals("Random Spirit") && !s.equals("Thief Spirit")) pool.add(s);
        }
        boolean fallback = false;
        if (pool.isEmpty() && SOLO_TEST_FALLBACK) { pool.addAll(rosterWithoutCopiers()); fallback = true; }
        if (pool.isEmpty()) { if (!quiet) p.sendSystemMessage(Component.literal("No online player with a spirit to copy.")); return; }
        String pick = pool.get(RNG.nextInt(pool.size()));
        SoulState.tag(p, "soulsmp:copy=", pick);
        SoulState.startCd(p, "Random Spirit", SoulData.COOLDOWNS.get("Random Spirit"));
        applyMaxHealth(p);
        p.sendSystemMessage(Component.literal("Random Spirit copied: " + pick + (fallback ? " (solo test: picked from the roster)" : "") + ". Next change in 3 days.").withStyle(st -> st.withColor(0xFF55FF)));
    }
    private static List<String> rosterWithoutCopiers() {
        List<String> l = new ArrayList<>(SoulData.ALL); l.remove("Random Spirit"); l.remove("Thief Spirit"); return l;
    }

    private static void coinflip(ServerPlayer p) {
        if (!start(p, "Coinflip Spirit")) return;
        boolean heads = RNG.nextBoolean();
        StringBuilder sb = new StringBuilder();
        for (Holder<MobEffect> e : pickDistinct(heads ? COIN_GOOD : COIN_BAD, 3)) {
            p.addEffect(new MobEffectInstance(e, 1_728_000, 2, false, true, true));
            sb.append(e.value().getDisplayName().getString()).append(" III, ");
        }
        p.sendSystemMessage(Component.literal((heads ? "HEADS! Buffs for 24h: " : "TAILS! Debuffs for 24h: ") + sb.substring(0, sb.length() - 2))
            .withStyle(st -> st.withColor(heads ? 0x55FF55 : 0xFF5555)));
    }

    private static void oracle(ServerPlayer p) {
        if (!SoulState.ready(p, "Oracle Spirit")) { cdMsg(p, "Oracle Spirit"); return; }
        LivingEntity best = null; double bd = Double.MAX_VALUE;
        for (ServerPlayer q : lvl(p).getServer().getPlayerList().getPlayers()) {
            if (q == p || q.isSpectator()) continue;
            double d = q.level() == p.level() ? q.distanceToSqr(p) : 1.0E12;
            if (d < bd) { bd = d; best = q; }
        }
        if (best == null && AFFECT_MOBS) for (LivingEntity e : targets(p, 64)) { double d = e.distanceToSqr(p); if (d < bd) { bd = d; best = e; } }
        if (best == null) { p.sendSystemMessage(Component.literal("Oracle: no other player is online.")); return; }
        SoulState.startCd(p, "Oracle Spirit", SoulData.COOLDOWNS.get("Oracle Spirit"));
        effect(best, MobEffects.GLOWING, 600, 0);
        BlockPos b = best.blockPosition();
        p.sendSystemMessage(Component.literal("Oracle: " + best.getName().getString() + " is at " + b.getX() + " " + b.getY() + " " + b.getZ() + " (glowing for 30s)").withStyle(st -> st.withColor(0x55FFFF)));
    }

    private static void weather(ServerPlayer p) {
        ServerLevel l = lvl(p);
        String next = switch (SoulState.getMode(p, "weather")) { case "clear" -> "rain"; case "rain" -> "thunder"; default -> "clear"; };
        SoulState.setMode(p, "weather", next);
        Compat.setWeather(l, next);
        p.sendSystemMessage(Component.literal("Weather: " + next));
    }

    /** Destiny: 5 random positive effects (Level 5) are chosen once per week; they stay on (renewed every 16 s) until the week ends, then new ones are chosen. */
    private static void destiny(ServerPlayer p) {
        long now = System.currentTimeMillis();
        String chosen = SoulState.tag(p, "soulsmp:destiny=");
        boolean fresh = false;
        if (chosen.isEmpty() || now >= SoulState.tagLong(p, "soulsmp:destinyuntil=")) {
            List<Integer> idx = new ArrayList<>();
            for (int i = 0; i < POSITIVE.size(); i++) idx.add(i);
            Collections.shuffle(idx, RNG);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 5; i++) { if (i > 0) sb.append(','); sb.append(idx.get(i)); }
            chosen = sb.toString();
            SoulState.tag(p, "soulsmp:destiny=", chosen);
            SoulState.tag(p, "soulsmp:destinyuntil=", Long.toString(now + DESTINY_WEEK_MS));
            fresh = true;
        }
        StringBuilder names = new StringBuilder();
        for (String part : chosen.split(",")) {
            try {
                Holder<MobEffect> h = POSITIVE.get(Integer.parseInt(part));
                MobEffectInstance cur = p.getEffect(h);
                if (cur == null || cur.getAmplifier() < 4 || cur.getDuration() < 40)
                    p.addEffect(new MobEffectInstance(h, DESTINY_REFRESH_TICKS, 4, false, false, true));
                names.append(h.value().getDisplayName().getString()).append(" V, ");
            } catch (Exception ignored) {}
        }
        if (fresh && names.length() > 2) p.sendSystemMessage(Component.literal("Destiny for this week: " + names.substring(0, names.length() - 2)).withStyle(st -> st.withColor(0xFFAA00)));
    }
    private static void destinyInfo(ServerPlayer p) {
        long left = SoulState.tagLong(p, "soulsmp:destinyuntil=") - System.currentTimeMillis();
        if (left <= 0) { p.sendSystemMessage(Component.literal("Destiny is automatic: new effects are chosen every week.")); return; }
        long h = left / 3_600_000L;
        p.sendSystemMessage(Component.literal("Destiny effects renew automatically. New effects in " + (h / 24) + "d " + (h % 24) + "h."));
    }

    private static void xpSpirit(ServerPlayer p) {
        int m = p.experienceLevel / 10;
        int last = (int) SoulState.tagLong(p, "soulsmp:xpm=");
        if (m < last) { SoulState.tag(p, "soulsmp:xpm=", Integer.toString(m)); return; }
        if (m == last || m <= 0) return;
        SoulState.tag(p, "soulsmp:xpm=", Integer.toString(m));
        int amp = Math.min(4, m - 1);
        StringBuilder sb = new StringBuilder();
        for (Holder<MobEffect> e : pickDistinct(POSITIVE, 2)) {
            p.addEffect(new MobEffectInstance(e, 12_000, amp, false, true, true));
            sb.append(e.value().getDisplayName().getString()).append(" ").append(amp + 1).append(", ");
        }
        String ench = randomEnchant(p, p.getMainHandItem(), m);
        p.sendSystemMessage(Component.literal("XP milestone " + (m * 10) + ": " + sb.substring(0, sb.length() - 2) + " (10 min)" + (ench == null ? "" : " + " + ench + " on your held item")).withStyle(st -> st.withColor(0x55FF55)));
    }
    private static String randomEnchant(ServerPlayer p, ItemStack stack, int milestone) {
        if (stack.isEmpty() || !stack.isEnchantable()) return null;
        List<Holder.Reference<Enchantment>> opts = new ArrayList<>(lvl(p).registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements()
            .filter(h -> !h.is(EnchantmentTags.CURSE) && h.value().canEnchant(stack)).toList());
        if (opts.isEmpty()) return null;
        Holder.Reference<Enchantment> h = opts.get(RNG.nextInt(opts.size()));
        int level = Math.max(1, Math.min(h.value().getMaxLevel(), milestone));
        stack.enchant(h, level);
        return Enchantment.getFullname(h, level).getString();
    }

    private static void sentinel(ServerPlayer p) {
        int range = lvl(p).getServer().getPlayerList().getViewDistance() * 16;
        Set<UUID> seen = SENTINEL.computeIfAbsent(p.getUUID(), k -> new HashSet<>());
        Set<UUID> now = new HashSet<>();
        for (ServerPlayer o : lvl(p).players()) {
            if (o == p || o.isSpectator()) continue;
            double d = o.distanceTo(p);
            if (d <= range) {
                now.add(o.getUUID());
                if (!seen.contains(o.getUUID()))
                    p.sendSystemMessage(Component.literal("[Sentinel] " + o.getName().getString() + " entered your render distance (" + (int) d + " blocks)").withStyle(st -> st.withColor(0xFFFF55)));
            }
        }
        seen.clear(); seen.addAll(now);
    }

    private static void vengeance(ServerPlayer p) {
        String id = SoulState.tag(p, "soulsmp:vengeance=");
        if (id.isEmpty()) return;
        for (ServerPlayer o : lvl(p).getServer().getPlayerList().getPlayers()) {
            if (!o.getUUID().toString().equals(id)) continue;
            BlockPos b = o.blockPosition();
            String dist = o.level() == p.level() ? (int) o.distanceTo(p) + " blocks" : "other dimension";
            p.sendSystemMessage(Component.literal("Vengeance: " + o.getName().getString() + " at " + b.getX() + " " + b.getY() + " " + b.getZ() + " (" + dist + ")").withStyle(st -> st.withColor(0xAA0000)), true);
            return;
        }
    }

    private static void goldenApples(ServerPlayer p) {
        ItemStack use = p.getUseItem();
        boolean eating = p.isUsingItem() && (use.is(Items.GOLDEN_APPLE) || use.is(Items.ENCHANTED_GOLDEN_APPLE));
        if (eating && p.getUseItemRemainingTicks() <= 2) SoulState.setMode(p, "gapple", "1");
        else if (!eating && "1".equals(SoulState.getMode(p, "gapple"))) {
            SoulState.setMode(p, "gapple", "0");
            p.setAbsorptionAmount(p.getAbsorptionAmount() + 8.0f); // +4 hearts
        }
    }

    private static void vampiricSun(ServerPlayer p) {
        if (p.tickCount % 20 != 0 || isNight(p) || p.isInWater() || lvl(p).isRaining()) return;
        if (!p.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) return;
        if (lvl(p).canSeeSky(p.blockPosition().above())) ignite(p, 40);
    }

    private static void wardenMode(ServerPlayer p) {
        if (!start(p, "Warden Spirit")) return;
        SoulState.setDuration(p, "wardenMode", System.currentTimeMillis() + 30_000);
        p.sendSystemMessage(Component.literal("Warden Mode active for 30s.").withStyle(st -> st.withColor(0x00AAAA)));
        sonicBoom(p);
    }
    private static void sonicBoom(ServerPlayer p) {
        ServerLevel l = lvl(p);
        Vec3 eye = p.getEyePosition(); Vec3 look = p.getLookAngle();
        Set<LivingEntity> hit = new HashSet<>();
        for (int i = 1; i <= 15; i++) {
            Vec3 pos = eye.add(look.scale(i));
            l.sendParticles(ParticleTypes.SONIC_BOOM, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            hit.addAll(l.getEntitiesOfClass(LivingEntity.class, new AABB(pos, pos).inflate(1.0), e -> e != p && e.isAlive() && isTarget(e)));
        }
        l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3.0f, 1.0f);
        for (LivingEntity e : hit) { e.hurtServer(l, p.damageSources().sonicBoom(p), 10.0f); shove(e, new Vec3(look.x * 1.2, 0.3, look.z * 1.2)); }
    }

    private static void friendlyWarden(ServerPlayer p) {
        if (!start(p, "Deep Dark Spirit")) return;
        ServerLevel l = lvl(p);
        Warden w = new Warden(Compat.<Warden>type("warden"), l);
        w.setPos(p.getX() + 2, p.getY(), p.getZ() + 2); w.setYRot(p.getYRot());
        w.setPersistenceRequired();
        l.addFreshEntity(w);
        MINIONS.add(new Minion(w, p, true));
        runLater(l, 3600, () -> { if (!w.isRemoved()) w.discard(); });
        p.sendSystemMessage(Component.literal("A friendly Warden answers your call (3 min).").withStyle(st -> st.withColor(0x00AAAA)));
    }

    private static void lightBeam(ServerPlayer p) {
        if (!start(p, "Light Spirit")) return;
        ServerLevel l = lvl(p);
        Vec3 v = p.getLookAngle(); Vec3 eye = p.getEyePosition();
        Set<LivingEntity> hit = new HashSet<>();
        for (int i = 1; i <= 24; i++) {
            Vec3 pos = eye.add(v.scale(i));
            BlockPos bp = BlockPos.containing(pos);
            if (!l.getBlockState(bp).getCollisionShape(l, bp).isEmpty()) break;
            l.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 4, .05, .05, .05, 0);
            hit.addAll(l.getEntitiesOfClass(LivingEntity.class, new AABB(pos, pos).inflate(1.0), e -> e != p && e.isAlive() && isTarget(e)));
        }
        for (LivingEntity e : hit) e.hurtServer(l, p.damageSources().indirectMagic(p, p), 16.0f);
    }

    private static void ominous(ServerPlayer p) {
        if (!start(p, "Ominous Spirit")) return;
        ServerLevel l = lvl(p);
        for (int i = 0; i < 3; i++) {
            Vex v = new Vex(Compat.<Vex>type("vex"), l);
            v.setPos(p.getX() + i - 1, p.getY() + 1.5, p.getZ() + 1);
            v.setLimitedLife(20 * 20);
            l.addFreshEntity(v);
            MINIONS.add(new Minion(v, p, false));
        }
        Vec3 look = p.getLookAngle(); Vec3 h = new Vec3(look.x, 0, look.z);
        h = h.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : h.normalize();
        float angle = (float) Math.atan2(h.z, h.x);
        for (int i = 1; i <= 16; i++) fang(l, p, p.getX() + h.x * i, p.getY(), p.getZ() + h.z * i, angle, i);          // line, like an Evoker
        for (int i = 0; i < 5; i++) { double a = i * Math.PI * 2 / 5; fang(l, p, p.getX() + Math.cos(a) * 1.5, p.getY(), p.getZ() + Math.sin(a) * 1.5, a2f(a), 0); }
        for (int i = 0; i < 8; i++) { double a = i * Math.PI * 2 / 8; fang(l, p, p.getX() + Math.cos(a) * 2.5, p.getY(), p.getZ() + Math.sin(a) * 2.5, a2f(a), 3); }
    }
    private static float a2f(double a) { return (float) a; }
    private static void fang(ServerLevel l, ServerPlayer owner, double x, double y, double z, float angle, int delay) {
        l.addFreshEntity(new EvokerFangs(l, x, groundY(l, x, y, z), z, angle, delay, owner));
    }
    private static double groundY(ServerLevel l, double x, double y, double z) {
        BlockPos b = BlockPos.containing(x, y + 1, z);
        for (int i = 0; i < 6; i++) {
            BlockPos below = b.below();
            if (!l.getBlockState(below).getCollisionShape(l, below).isEmpty()) return b.getY();
            b = below;
        }
        return y;
    }

    /** Aerodynamic: hidden Weaving (amplifier 4) while a cobweb is near; EntityMixin then ignores the slowdown. */
    private static void aerodynamicWebs(ServerPlayer p) {
        AABB box = p.getBoundingBox().inflate(0.75);
        boolean near = false;
        for (BlockPos bp : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ)))
            if (p.level().getBlockState(bp).is(Blocks.COBWEB)) { near = true; break; }
        MobEffectInstance cur = p.getEffect(MobEffects.WEAVING);
        boolean ours = cur != null && cur.getAmplifier() == 4;
        if (near) { if (!ours || cur.getDuration() < 10) p.addEffect(new MobEffectInstance(MobEffects.WEAVING, 20, 4, false, false, false)); }
        else if (ours) p.removeEffect(MobEffects.WEAVING);
    }

    private static void renderLaser(ServerPlayer p) {
        Vec3 v = p.getLookAngle();
        for (int i = 1; i <= 20; i++) {
            Vec3 pos = p.getEyePosition().add(v.scale(i));
            lvl(p).sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 3, .03, .03, .03, 0);
            BlockPos bp = BlockPos.containing(pos);
            if (!p.level().getBlockState(bp).isAir() && p.level().getBlockState(bp).getDestroySpeed(p.level(), bp) >= 0) p.level().destroyBlock(bp, true, p);
        }
    }
    private static void lazer(ServerPlayer p) {
        String mode = SoulState.getMode(p, "mode:lazer"); if (mode.isEmpty()) mode = "pinpoint";
        String key = mode.equals("broad") ? "Lazer Broad" : "Lazer Pinpoint";
        if (!start(p, key)) return;
        long duration = mode.equals("broad") ? 1000L : 5000L;
        SoulState.setDuration(p, "lazer", System.currentTimeMillis() + duration);
        p.sendSystemMessage(Component.literal("Lazer: " + mode + " (sneak + shard, or /spirit mode, switches mode)"));
    }

    private static void ravengingDash(ServerPlayer p) {
        if (!start(p, "Ravenging Dash")) return;
        Vec3 v = p.getLookAngle();
        p.setDeltaMovement(v.x * 2.6, v.y * .35, v.z * 2.6); Compat.sync(p);
        effect(p, MobEffects.SLOWNESS, 100, 1);
        for (LivingEntity q : targets(p, 5)) { Vec3 d = q.position().subtract(p.position()).normalize(); shove(q, new Vec3(d.x * 2.2, 0.8, d.z * 2.2)); effect(q, MobEffects.SLOWNESS, 100, 1); }
    }

    // ----- Glider: while active, the worn chestplate gets the vanilla glider component (works like an elytra) -----
    private static void toggleGlider(ServerPlayer p) {
        boolean active = SoulState.duration(p, "glider") > System.currentTimeMillis();
        SoulState.setDuration(p, "glider", active ? 0 : System.currentTimeMillis() + 86_400_000L);
        if (active) stripGlider(p);
        p.sendSystemMessage(Component.literal(active ? "Glider deactivated" : "Glider activated: jump, then jump again in the air (needs a chestplate)"));
    }
    private static void gliderTick(ServerPlayer p) {
        boolean active = SoulState.duration(p, "glider") > System.currentTimeMillis();
        ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty() || st.is(Items.ELYTRA)) continue;
            if (st == chest) { if (active && !st.has(DataComponents.GLIDER)) st.set(DataComponents.GLIDER, Unit.INSTANCE); else if (!active && st.has(DataComponents.GLIDER)) st.remove(DataComponents.GLIDER); }
            else if (st.has(DataComponents.GLIDER)) st.remove(DataComponents.GLIDER);
        }
    }
    private static void stripGlider(ServerPlayer p) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty() && !st.is(Items.ELYTRA) && st.has(DataComponents.GLIDER)) st.remove(DataComponents.GLIDER);
        }
    }

    private static void mapper(ServerPlayer p) {
        if (!start(p, "Mapper Spirit")) return;
        p.sendSystemMessage(Component.literal("Mapper: use /locate structure <structure> to choose a structure. Your Mapper cooldown is now active."));
    }

    /** Blacksmithing: restore durability lost by the items in your hands since the previous tick. */
    private static void blacksmith(ServerPlayer p) {
        Map<Integer, Dur> m = DUR.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
        ItemStack[] hands = {p.getMainHandItem(), p.getOffhandItem()};
        for (int i = 0; i < 2; i++) {
            ItemStack s = hands[i];
            if (s.isEmpty() || !s.isDamageableItem()) { m.remove(i); continue; }
            Dur d = m.get(i);
            int cur = s.getDamageValue();
            if (d != null && d.ref() == s && cur > d.damage()) { s.setDamageValue(d.damage()); cur = d.damage(); }
            m.put(i, new Dur(s, cur));
        }
    }

    // =====================================================================
    //  HELPERS
    // =====================================================================
    private static boolean isMinion(Entity e) { for (Minion m : MINIONS) if (m.mob() == e) return true; return false; }
    private static boolean isSpear(ItemStack st) { return BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().endsWith("_spear"); }
    private static boolean isMelee(ItemStack st) { return st.is(ItemTags.SWORDS) || st.is(ItemTags.AXES) || st.is(Items.MACE) || st.is(Items.TRIDENT) || isSpear(st); }
    private static boolean isWeapon(ItemStack st) { return isMelee(st) || st.is(Items.BOW) || st.is(Items.CROSSBOW); }

    // ----- Ghost: while crouching, others see no body, armor, held items or nameplate -----
    private static final EquipmentSlot[] VISIBLE_SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
    private static void ghost(ServerPlayer p) {
        boolean hide = p.isCrouching();
        boolean was = "1".equals(SoulState.getMode(p, "ghostHide"));
        if (hide) {
            if (!was) ghostTeam(p, true);
            if (!was || p.tickCount % 2 == 0) sendEquipment(p, true);
            SoulState.setMode(p, "ghostHide", "1");
        } else if (was) ghostRestore(p);
    }
    private static void ghostRestore(ServerPlayer p) {
        SoulState.setMode(p, "ghostHide", "0");
        sendEquipment(p, false);
        ghostTeam(p, false);
    }
    // ----- Invis: while the ability is active, others see no armor or held items (same trick as Ghost) -----
    private static void invisHide(ServerPlayer p) {
        boolean was = "1".equals(SoulState.getMode(p, "invisHide"));
        if (!was || p.tickCount % 2 == 0) sendEquipment(p, true);
        if (!was) SoulState.setMode(p, "invisHide", "1");
    }
    private static void invisRestore(ServerPlayer p) {
        SoulState.setMode(p, "invisHide", "0");
        sendEquipment(p, false);
    }
    private static void sendEquipment(ServerPlayer p, boolean empty) {
        List<Pair<EquipmentSlot, ItemStack>> list = new ArrayList<>();
        for (EquipmentSlot s : VISIBLE_SLOTS) list.add(Pair.of(s, empty ? ItemStack.EMPTY : p.getItemBySlot(s).copy()));
        ClientboundSetEquipmentPacket pk = new ClientboundSetEquipmentPacket(p.getId(), list);
        for (ServerPlayer o : lvl(p).players()) if (o != p && o.distanceToSqr(p) < 128 * 128) o.connection.send(pk);
    }
    private static void ghostTeam(ServerPlayer p, boolean on) {
        var sb = lvl(p).getServer().getScoreboard();
        PlayerTeam t = sb.getPlayerTeam("soulsmp_ghost");
        if (on) {
            if (p.getTeam() != null) return;
            if (t == null) { t = sb.addPlayerTeam("soulsmp_ghost"); t.setNameTagVisibility(Team.Visibility.NEVER); }
            sb.addPlayerToTeam(p.getScoreboardName(), t);
        } else if (t != null && p.getTeam() == t) sb.removePlayerFromTeam(p.getScoreboardName(), t);
    }

    private static boolean isTarget(LivingEntity e) {
        if (e.isSpectator() || isIllusion(e) || isProjBody(e)) return false;
        if (e instanceof ServerPlayer) return true;
        if (!AFFECT_MOBS || e instanceof ArmorStand) return false;
        for (Minion m : MINIONS) if (m.mob() == e) return false;
        return true;
    }
    private static List<LivingEntity> targets(ServerPlayer p, double r) {
        return new ArrayList<>(lvl(p).getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r), e -> e != p && e.isAlive() && e.distanceTo(p) <= r && isTarget(e)));
    }
    private static void shove(Entity e, Vec3 add) {
        e.setDeltaMovement(e.getDeltaMovement().add(add));
        Compat.sync(e); // makes the server send the new velocity to players
    }
    private static void ignite(LivingEntity e, int ticks) {
        if (!e.fireImmune() && e.getRemainingFireTicks() < ticks) e.setRemainingFireTicks(ticks);
    }
    private static int count(ServerPlayer p, net.minecraft.world.item.Item item) {
        int n = 0; Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) { ItemStack s = inv.getItem(i); if (s.is(item)) n += s.getCount(); }
        return n;
    }
    private static <T> List<T> pickDistinct(List<T> src, int n) {
        List<T> c = new ArrayList<>(src); Collections.shuffle(c, RNG); return new ArrayList<>(c.subList(0, Math.min(n, c.size())));
    }
    private static void runLater(ServerLevel l, int ticks, Runnable r) { TASKS.add(new Task(l.getServer().getTickCount() + ticks, r)); }

    private static void tickMinions() {
        MINIONS.removeIf(m -> m.mob().isRemoved() || !m.mob().isAlive() || m.owner().isRemoved());
        for (Minion m : MINIONS) {
            Mob mob = m.mob(); ServerPlayer o = m.owner();
            if (mob.getTarget() == o) mob.setTarget(null);
            if (mob instanceof Warden w) {
                w.clearAnger(o);
                w.getBrain().setMemoryWithExpiry(MemoryModuleType.DIG_COOLDOWN, Unit.INSTANCE, 1200L); // never dig back into the ground
            }
            if (mob.tickCount % 20 != 0) continue;
            if (mob instanceof Warden w2) {
                LivingEntity foe = null; double best = Double.MAX_VALUE;
                for (LivingEntity e : ((ServerLevel) mob.level()).getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(24),
                        x -> x != o && x != mob && x.isAlive() && !x.isSpectator() && !isMinion(x) && ((x instanceof ServerPlayer sp && !sp.isCreative()) || x instanceof Enemy))) {
                    double d = e.distanceToSqr(mob); if (d < best) { best = d; foe = e; }
                }
                if (foe != null) w2.increaseAngerAt(foe, 200, false);
            }
            LivingEntity want = o.getLastHurtByMob();
            if (want == null || !want.isAlive() || want == mob) want = o.getLastHurtMob();
            if (want == null || !want.isAlive() || want == o || want == mob) continue;
            if (mob instanceof Warden w) w.increaseAngerAt(want, 100, false);
            else if (mob.getTarget() == null) mob.setTarget(want);
        }
    }

    private static void ensureEnchant(ServerPlayer p, Predicate<ItemStack> match, ResourceKey<Enchantment> key, int level) {
        Holder<Enchantment> h = lvl(p).registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || !match.test(s)) continue;
            if (EnchantmentHelper.getItemEnchantmentLevel(h, s) < level) s.enchant(h, level);
        }
    }

    private static void cooldownOnly(ServerPlayer p, String key, String message) { if (start(p, key)) p.sendSystemMessage(Component.literal(message)); }
    private static void buff(ServerPlayer p, String key, int ticks, Holder<MobEffect> effect, int amp) { if (!start(p, key)) return; effect(p, effect, ticks, amp); }
    private static void effect(LivingEntity e, Holder<MobEffect> effect, int ticks, int amp) { e.addEffect(new MobEffectInstance(effect, ticks, amp, false, false, true)); }
    private static boolean isNight(ServerPlayer p) { return Compat.isNight(p); }
    private static ServerLevel lvl(ServerPlayer p) { return (ServerLevel) p.level(); }
    private static List<ServerPlayer> players(ServerPlayer p, double r) { return new ArrayList<>(lvl(p).getPlayers(x -> x.distanceTo(p) <= r)); }

    private static void applyMaxHealth(ServerPlayer p) {
        int hp = SoulData.MAX_HEALTH.getOrDefault(SoulState.effective(p), 20);
        if (SoulState.is(p, "Lifesteal Spirit")) { int b = SoulState.lifestealHearts(p); hp = Math.min(40, 20 + b * 2); }
        setMaxHealth(p, hp);
    }
    private static void setMaxHealth(ServerPlayer p, double hp) {
        AttributeInstance a = p.getAttribute(Attributes.MAX_HEALTH);
        if (a != null) { if (a.getBaseValue() != hp) a.setBaseValue(hp); if (p.getHealth() > hp) p.setHealth((float) hp); }
    }

    private static void claim(ServerPlayer p) {
        if (!SoulState.spirit(p).isBlank()) { p.sendSystemMessage(Component.literal("You already have " + SoulState.spirit(p))); return; }
        List<String> pool = new ArrayList<>(SoulData.ALL);
        for (ServerPlayer q : lvl(p).getServer().getPlayerList().getPlayers()) pool.remove(SoulState.spirit(q));
        pool.removeIf(Objects::isNull);
        if (pool.isEmpty()) { p.sendSystemMessage(Component.literal("No unclaimed spirits remain.")); return; }
        set(p, pool.get(RNG.nextInt(pool.size())));
    }
    /** Takes the spirit away (and everything it changed) so /spirit claim works again. */
    private static void remove(ServerPlayer p) {
        String had = SoulState.spirit(p);
        if (isProjecting(p)) endProjection(p, true);
        stripGlider(p); setFlight(p, false);
        if ("1".equals(SoulState.getMode(p, "ghostHide"))) ghostRestore(p);
        if ("1".equals(SoulState.getMode(p, "invisHide"))) invisRestore(p);
        SoulState.resetAll(p);
        SoulState.spirit(p, "");
        SoulState.tag(p, "soulsmp:lifesteal=", "");
        normalizeAttributes(p);
        setMaxHealth(p, 20);
        p.sendSystemMessage(Component.literal(had.isBlank() ? "You had no spirit. Use /spirit claim." : "Spirit removed (" + had + "). Use /spirit claim to get a new one.").withStyle(st -> st.withColor(0xFF55FF)));
    }
    private static void set(ServerPlayer p, String s) {
        String match = SoulData.ALL.stream().filter(x -> x.equalsIgnoreCase(s)).findFirst().orElse(null);
        if (match == null) { p.sendSystemMessage(Component.literal("Unknown spirit.")); return; }
        if (isProjecting(p)) endProjection(p, true);
        stripGlider(p); setFlight(p, false);
        if ("1".equals(SoulState.getMode(p, "ghostHide"))) ghostRestore(p);
        if ("1".equals(SoulState.getMode(p, "invisHide"))) invisRestore(p);
        SoulState.spirit(p, match);
        applyMaxHealth(p); p.setHealth(p.getMaxHealth());
        p.sendSystemMessage(Component.literal("Spirit bound: " + match).withStyle(st -> st.withColor(0xFF55FF)));
        String d = SoulData.DESCRIPTIONS.get(match);
        if (d != null) p.sendSystemMessage(Component.literal(d));
    }
    private static void list(ServerPlayer p) {
        p.sendSystemMessage(Component.literal("Available spirits:").withStyle(st -> st.withColor(0xFF55FF)));
        for (String s : SoulData.ALL) p.sendSystemMessage(Component.literal("- " + s));
    }
}
