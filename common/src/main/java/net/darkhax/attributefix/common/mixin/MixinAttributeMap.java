package net.darkhax.attributefix.common.mixin;

import net.darkhax.attributefix.common.impl.EntityOwned;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes {@code AttributeMap} hold a reference to its owning entity and stamps that reference onto
 * every {@code AttributeInstance} it hands out. Instances are created lazily, so we tag them in
 * {@code getInstance} rather than trying to enumerate them up front.
 */
@Mixin(AttributeMap.class)
public abstract class MixinAttributeMap implements EntityOwned {

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

    // NOTE: verify this descriptor against your mappings. In 1.21.1 Mojmap the method is
    // `AttributeInstance getInstance(Holder<Attribute> holder)`.
    @Inject(method = "getInstance(Lnet/minecraft/core/Holder;)Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;", at = @At("RETURN"))
    private void attributefix$tagInstance(Holder<Attribute> holder, CallbackInfoReturnable<AttributeInstance> cir) {
        if (cir.getReturnValue() instanceof EntityOwned owned) {
            owned.attributefix$setOwner(this.attributefix$owner);
        }
    }
}
