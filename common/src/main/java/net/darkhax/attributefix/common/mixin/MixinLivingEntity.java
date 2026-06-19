package net.darkhax.attributefix.common.mixin;

import net.darkhax.attributefix.common.impl.EntityOwned;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures the owning entity into its {@code AttributeMap} as soon as the entity finishes
 * constructing. From there the map propagates the reference down to each {@code AttributeInstance}
 * (see {@link MixinAttributeMap}). This is the link that lets the value clamp know which player it
 * is dealing with.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void attributefix$captureOwner(CallbackInfo ci) {
        final LivingEntity self = (LivingEntity) (Object) this;
        // getAttributes() is assigned during the LivingEntity constructor, so it is safe at TAIL.
        if (self.getAttributes() instanceof EntityOwned owned) {
            owned.attributefix$setOwner(self);
        }
    }
}
