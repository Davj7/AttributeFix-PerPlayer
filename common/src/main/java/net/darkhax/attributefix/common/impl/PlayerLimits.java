package net.darkhax.attributefix.common.impl;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/**
 * Runtime store of per-player attribute caps, keyed by player UUID and attribute id. Each entry is
 * an {@link AttributeLimit} holding an optional minimum and maximum. This is the map the value-clamp
 * mixin reads on every calculation, so it must be cheap to query.
 *
 * <p>This class is only the in-memory view. Loading from / saving to disk is handled by
 * {@link AttributeLimitsSavedData}, which calls {@link #loadFrom} on server start and reads
 * {@link #snapshot()} when persisting. Keeping a single static map also means the integrated server
 * and its client (same JVM) share the same limits, so single-player display stays in sync.</p>
 */
public final class PlayerLimits {

    private static final Map<UUID, Map<ResourceLocation, AttributeLimit>> LIMITS = new ConcurrentHashMap<>();

    private PlayerLimits() {}

    /**
     * Sets the maximum value a given attribute may reach for a specific player, preserving any
     * existing minimum override.
     */
    public static void setMax(UUID player, ResourceLocation attribute, double max) {
        update(player, attribute, current -> current.withMax(max));
    }

    /**
     * Sets the minimum value a given attribute may reach for a specific player, preserving any
     * existing maximum override.
     */
    public static void setMin(UUID player, ResourceLocation attribute, double min) {
        update(player, attribute, current -> current.withMin(min));
    }

    private static void update(UUID player, ResourceLocation attribute, UnaryOperator<AttributeLimit> op) {
        final Map<ResourceLocation, AttributeLimit> perAttribute = LIMITS.computeIfAbsent(player, key -> new ConcurrentHashMap<>());
        final AttributeLimit current = perAttribute.getOrDefault(attribute, new AttributeLimit(null, null));
        perAttribute.put(attribute, op.apply(current));
    }

    /**
     * Removes both bounds for a player/attribute pair, returning it to the global limit.
     */
    public static void clear(UUID player, ResourceLocation attribute) {
        final Map<ResourceLocation, AttributeLimit> perAttribute = LIMITS.get(player);
        if (perAttribute != null) {
            perAttribute.remove(attribute);
            if (perAttribute.isEmpty()) {
                LIMITS.remove(player);
            }
        }
    }

    public static OptionalDouble getMax(Player player, ResourceLocation attribute) {
        return getMax(player.getUUID(), attribute);
    }

    public static OptionalDouble getMin(Player player, ResourceLocation attribute) {
        return getMin(player.getUUID(), attribute);
    }

    public static OptionalDouble getMax(UUID player, ResourceLocation attribute) {
        final AttributeLimit limit = lookup(player, attribute);
        return limit != null && limit.max() != null ? OptionalDouble.of(limit.max()) : OptionalDouble.empty();
    }

    public static OptionalDouble getMin(UUID player, ResourceLocation attribute) {
        final AttributeLimit limit = lookup(player, attribute);
        return limit != null && limit.min() != null ? OptionalDouble.of(limit.min()) : OptionalDouble.empty();
    }

    private static AttributeLimit lookup(UUID player, ResourceLocation attribute) {
        if (attribute == null) {
            return null;
        }
        final Map<ResourceLocation, AttributeLimit> perAttribute = LIMITS.get(player);
        return perAttribute == null ? null : perAttribute.get(attribute);
    }

    /**
     * @return A detached deep copy of every stored limit, safe to iterate while persisting.
     */
    public static Map<UUID, Map<ResourceLocation, AttributeLimit>> snapshot() {
        final Map<UUID, Map<ResourceLocation, AttributeLimit>> copy = new HashMap<>();
        LIMITS.forEach((uuid, perAttribute) -> copy.put(uuid, new HashMap<>(perAttribute)));
        return copy;
    }

    /**
     * Replaces the entire in-memory state, e.g. when a world is loaded. Clears anything left over
     * from a previously loaded world.
     */
    public static void loadFrom(Map<UUID, Map<ResourceLocation, AttributeLimit>> data) {
        LIMITS.clear();
        data.forEach((uuid, perAttribute) -> LIMITS.put(uuid, new ConcurrentHashMap<>(perAttribute)));
    }

    /**
     * Drops all limits. Called when the server stops so state does not bleed across worlds.
     */
    public static void clearAll() {
        LIMITS.clear();
    }
}
