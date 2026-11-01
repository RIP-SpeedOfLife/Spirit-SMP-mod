package com.soulsmp.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Gives the mod access to the protected Entity#setSharedFlag (used to make an illusion glide like an elytra flyer). */
@Mixin(Entity.class)
public interface EntityInvoker {
    @Invoker("setSharedFlag")
    void soulsmp$setSharedFlag(int flag, boolean value);
}
