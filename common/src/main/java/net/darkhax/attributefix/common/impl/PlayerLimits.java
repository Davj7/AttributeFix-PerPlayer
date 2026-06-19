package net.darkhax.attributefix.common.impl;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime store of per-player attribute caps, keyed by player UUID and attribute id. This is the
 * map the value-clamp mixin reads on every calculation, so it must be cheap to query.
 *
 * <p>This class is only the in-memory view. Loading from / saving to disk is handled by
 * {@link AttributeLimitsSavedData}, which calls {@link #loadFrom} on server start and reads
 * {@link #snapshot()} when persisting. Keeping a single static map also means the integrated server
 * and its client (same JVM) share the same limits, so single-player display stays in sync.</p>
 */
public final class PlayerLimits {

    private static final Map<UUID, Map<ResourceLocation, Double>> LIMITS = new ConcurrentHashMap<>();

    private PlayerLimits() {}

    /**
     * Sets the maximum value a given attribute may reach for a specific player.
     */
    public static void setMax(UUID player, ResourceLocation attribute, double max) {
        LIMITS.computeIfAbsent(player, key -> new ConcurrentHashMap<>()).put(attribute, max);
    }

    /**
     * Removes a custom cap, returning the player to the global (mod-wide) limit for that attribute.
     */
    public static void clearMax(UUID player, ResourceLocation attribute) {
        final Map<ResourceLocation, Double> perAttribute = LIMITS.get(player);
        if (perAttribute != null) {
            perAttribute.remove(attribute);
            if (perAttribute.isEmpty()) {
                LIMITS.remove(player);
            }
        }
    }

    /**
     * Convenience overload used by the value-clamp mixin.
     */
    public static OptionalDouble getMax(Player player, ResourceLocation attribute) {
        return getMax(player.getUUID(), attribute);
    }

    /**
     * Looks up the custom cap for a player/attribute pair.
     *
     * @return The custom maximum, or an empty optional when the player has no override (in which
     *         case the global limit applied by {@code RangeConfig} continues to govern).
     */
    public static OptionalDouble getMax(UUID player, ResourceLocation attribute) {
        if (attribute == null) {
            return OptionalDouble.empty();
        }
        final Map<ResourceLocation, Double> perAttribute = LIMITS.get(player);
        if (perAttribute == null) {
            return OptionalDouble.empty();
        }
        final Double max = perAttribute.get(attribute);
        return max == null ? OptionalDouble.empty() : OptionalDouble.of(max);
    }

    /**
     * @return A detached deep copy of every stored limit, safe to iterate while persisting.
     */
    public static Map<UUID, Map<ResourceLocation, Double>> snapshot() {
        final Map<UUID, Map<ResourceLocation, Double>> copy = new HashMap<>();
        LIMITS.forEach((uuid, perAttribute) -> copy.put(uuid, new HashMap<>(perAttribute)));
        return copy;
    }

    /**
     * Replaces the entire in-memory state, e.g. when a world is loaded. Clears anything left over
     * from a previously loaded world.
     */
    public static void loadFrom(Map<UUID, Map<ResourceLocation, Double>> data) {
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
