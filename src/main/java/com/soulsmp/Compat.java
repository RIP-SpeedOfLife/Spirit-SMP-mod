package com.soulsmp;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.Set;

/** Every API that changed between 1.21.11 and 26.3 lives here, so a future update only touches this file. */
public final class Compat {
    private Compat() {}

    /** Entity command tags (was Entity#getTags). */
    public static Set<String> tags(Entity e) { return e.entityTags(); }

    /** Make the server send the new velocity to clients (was entity.hurtMarked = true). */
    public static void sync(Entity e) {
        e.needsSync = true;
        // players move client-side, so the new velocity has to be pushed to them explicitly
        if (e instanceof ServerPlayer sp && sp.connection != null) sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(sp));
    }

    /** Entity type looked up by registry name (EntityType.WARDEN / VEX constants are gone). */
    @SuppressWarnings("unchecked")
    public static <T extends Entity> EntityType<T> type(String name) {
        return (EntityType<T>) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace(name));
    }

    /** Weather through the vanilla /weather command (setWeatherParameters is gone). */
    public static void setWeather(ServerLevel l, String mode) {
        var server = l.getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "weather " + mode + " 12000");
    }

    /** Night = 13000..23000 of the overworld day. */
    public static boolean isNight(ServerPlayer p) { return p.level().isDarkOutside(); }

    // TODO(26.3): swing state moved; run diagnose-api.ps1 and fill these two in.
    public static boolean swinging(ServerPlayer o) { return false; }
    public static void swing(LivingEntity m, ServerPlayer o) { }
}
