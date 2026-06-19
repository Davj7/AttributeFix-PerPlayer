package net.darkhax.attributefix.common.impl;

import net.minecraft.world.entity.LivingEntity;

/**
 * Duck interface that we implement (via mixin) on both {@code AttributeMap} and
 * {@code AttributeInstance}. Vanilla never keeps a reference back to the owning entity, so we add
 * one ourselves in order to know *whose* attribute is being calculated when we clamp the value.
 *
 * <p>The {@code attributefix$} prefix avoids any chance of clashing with vanilla or other mods.</p>
 */
public interface EntityOwned {

    /**
     * @param owner The entity that owns this object. May be null for entities that are not living
     *              (the feature only targets players, but the reference is set for any LivingEntity).
     */
    void attributefix$setOwner(LivingEntity owner);

    /**
     * @return The owning entity, or null if it was never set.
     */
    LivingEntity attributefix$getOwner();

    /**
     * Forces the implementor to recompute its cached value on next read. Only meaningful on
     * {@code AttributeInstance}; the default no-op lets {@code AttributeMap} share this interface
     * without caring about it. Used after a limit changes so the new cap applies immediately.
     */
    default void attributefix$markDirty() {}
}
