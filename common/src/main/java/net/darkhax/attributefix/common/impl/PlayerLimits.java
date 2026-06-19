package net.darkhax.attributefix.common.impl;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store of per-player attribute caps, keyed by player UUID and attribute id.
 *
 * <p>This is the intentionally-simple part of the sketch. Right now the limits live only in memory
 * and are lost on server restart. For a real feature you would back this with persistent storage,
 * e.g. NeoForge Data Attachments / Fabric persistent data on the player, and add a command to edit
 * it. The mixin clamp only depends on {@link #getMax(Player, ResourceLocation)}, so swapping the
 * backing store later requires no changes to the mixins.</p>
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
     * Looks up the custom cap for a player/attribute pair.
     *
     * @return The custom maximum, or an empty optional when the player has no override (in which
     *         case the global limit applied by {@code RangeConfig} continues to govern).
     */
    public static OptionalDouble getMax(Player player, ResourceLocation attribute) {
        if (attribute == null) {
            return OptionalDouble.empty();
        }
        final Map<ResourceLocation, Double> perAttribute = LIMITS.get(player.getUUID());
        if (perAttribute == null) {
            return OptionalDouble.empty();
        }
        final Double max = perAttribute.get(attribute);
        return max == null ? OptionalDouble.empty() : OptionalDouble.of(max);
    }
}
