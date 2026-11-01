package com.soulsmp.mixin;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Aerodynamic Spirit: the server marks the player with a hidden Weaving effect (amplifier 4) while a
 * cobweb is near. Weaving is synced to the client, so both sides agree and cobwebs do not slow the player.
 * Without the mod on the client, vanilla Weaving still halves the slowdown.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "makeStuckInBlock", at = @At("HEAD"), cancellable = true, require = 0)
    private void soulsmp$ignoreCobweb(BlockState state, Vec3 multiplier, CallbackInfo ci) {
        if (state.is(Blocks.COBWEB) && (Object) this instanceof LivingEntity living) {
            MobEffectInstance e = living.getEffect(MobEffects.WEAVING);
            if (e != null && e.getAmplifier() == 4) ci.cancel();
        }
    }
}
