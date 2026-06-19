package net.darkhax.attributefix.common.mixin;

import net.darkhax.attributefix.common.impl.AttributeLimit;
import net.darkhax.attributefix.common.impl.EntityOwned;
import net.darkhax.attributefix.common.impl.PlayerLimits;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The actual per-player cap. After vanilla computes (and globally clamps) an attribute value in
 * {@code calculateValue}, we re-clamp it down to the owning player's personal limit when one is set.
 *
 * <p>Because the global maximum stays huge (that is what the base mod already does via RangeConfig),
 * this injection is what turns a single shared ceiling into a per-player ceiling.</p>
 */
@Mixin(AttributeInstance.class)
public abstract class MixinAttributeInstance implements EntityOwned {

    @Shadow
    public abstract Holder<Attribute> getAttribute();

    @Shadow
    protected abstract void setDirty();

    @Unique
    private LivingEntity attributefix$owner;

    @Override
    public void attributefix$setOwner(LivingEntity owner) {
        this.attributefix$owner = owner;
    }

    @Override
    public LivingEntity attributefix$getOwner() {
        return this.attributefix$owner;
    }

    @Override
    public void attributefix$markDirty() {
        this.setDirty();
    }

    // `calculateValue` returns a primitive double, so the CIR is typed <Double> and we use
    // getReturnValueD()/setReturnValue(double).
    @Inject(method = "calculateValue", at = @At("RETURN"), cancellable = true)
    private void attributefix$applyPlayerLimit(CallbackInfoReturnable<Double> cir) {
        if (!(this.attributefix$owner instanceof Player player)) {
            return;
        }
        // Early-out: skip the registry lookup entirely when this player has no overrides at all.
        if (!PlayerLimits.hasLimits(player.getUUID())) {
            return;
        }
        final ResourceLocation id = BuiltInRegistries.ATTRIBUTE.getKey(this.getAttribute().value());
        final AttributeLimit limit = PlayerLimits.get(player.getUUID(), id);
        if (limit == null) {
            return;
        }
        final double original = cir.getReturnValueD();
        double value = original;

        if (limit.max() != null && value > limit.max()) {
            value = limit.max();
        }
        if (limit.min() != null && value < limit.min()) {
            value = limit.min();
        }
        if (value != original) {
            cir.setReturnValue(value);
        }
    }
}
