package com.soulsmp.mixin;

import com.soulsmp.SoulState;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Real effect immunity: the effect is never applied (Nightmare: Poison/Blindness/Nausea, Venom: Poison). */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true, require = 0)
    private void soulsmp$immunities(MobEffectInstance effect, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ServerPlayer sp)) return;
        String s = SoulState.effective(sp);
        Holder<MobEffect> e = effect.getEffect();
        boolean poison = e.equals(MobEffects.POISON);
        if (s.equals("Nightmare Spirit") && (poison || e.equals(MobEffects.BLINDNESS) || e.equals(MobEffects.NAUSEA))) cir.setReturnValue(false);
        else if (s.equals("Venom Spirit") && poison) cir.setReturnValue(false);
    }
}
