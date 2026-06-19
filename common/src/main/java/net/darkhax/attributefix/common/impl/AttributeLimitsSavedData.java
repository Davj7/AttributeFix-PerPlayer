package net.darkhax.attributefix.common.impl;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Disk persistence for the per-player limits, implemented with vanilla {@link SavedData} so the same
 * code works on every loader without touching loader-specific persistence APIs. The data lives next
 * to the overworld's other saved data and mirrors itself into {@link PlayerLimits} (the runtime view
 * the mixin reads).
 */
public class AttributeLimitsSavedData extends SavedData {

    public static final String DATA_NAME = Constants.MOD_ID + "_player_limits";

    private static final SavedData.Factory<AttributeLimitsSavedData> FACTORY = new SavedData.Factory<>(
            AttributeLimitsSavedData::new,
            AttributeLimitsSavedData::load,
            // Reusing an existing datafixer type; we have no fixers, so unknown keys pass through untouched.
            DataFixTypes.LEVEL
    );

    /**
     * Obtains (loading from disk on first access) the saved data for this server. The act of loading
     * populates {@link PlayerLimits}.
     */
    public static AttributeLimitsSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public AttributeLimitsSavedData() {}

    public static AttributeLimitsSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        final AttributeLimitsSavedData data = new AttributeLimitsSavedData();
        final Map<UUID, Map<ResourceLocation, AttributeLimit>> parsed = new HashMap<>();
        final ListTag players = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            final CompoundTag playerTag = players.getCompound(i);
            final UUID uuid = playerTag.getUUID("uuid");
            final Map<ResourceLocation, AttributeLimit> limits = new HashMap<>();
            final ListTag limitList = playerTag.getList("limits", Tag.TAG_COMPOUND);
            for (int j = 0; j < limitList.size(); j++) {
                final CompoundTag limitTag = limitList.getCompound(j);
                final ResourceLocation id = ResourceLocation.tryParse(limitTag.getString("attribute"));
                if (id != null) {
                    final Double min = limitTag.contains("min") ? limitTag.getDouble("min") : null;
                    final Double max = limitTag.contains("max") ? limitTag.getDouble("max") : null;
                    final AttributeLimit limit = new AttributeLimit(min, max);
                    if (!limit.isEmpty()) {
                        limits.put(id, limit);
                    }
                }
            }
            if (!limits.isEmpty()) {
                parsed.put(uuid, limits);
            }
        }
        PlayerLimits.loadFrom(parsed);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        final ListTag players = new ListTag();
        PlayerLimits.snapshot().forEach((uuid, limits) -> {
            if (limits.isEmpty()) {
                return;
            }
            final CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("uuid", uuid);
            final ListTag limitList = new ListTag();
            limits.forEach((id, limit) -> {
                if (limit.isEmpty()) {
                    return;
                }
                final CompoundTag limitTag = new CompoundTag();
                limitTag.putString("attribute", id.toString());
                if (limit.min() != null) {
                    limitTag.putDouble("min", limit.min());
                }
                if (limit.max() != null) {
                    limitTag.putDouble("max", limit.max());
                }
                limitList.add(limitTag);
            });
            playerTag.put("limits", limitList);
            players.add(playerTag);
        });
        tag.put("players", players);
        return tag;
    }

    /**
     * Sets a player's maximum and flags the data for saving. Updates the runtime view too.
     */
    public void setMax(UUID player, ResourceLocation attribute, double max) {
        PlayerLimits.setMax(player, attribute, max);
        this.setDirty();
    }

    /**
     * Sets a player's minimum and flags the data for saving.
     */
    public void setMin(UUID player, ResourceLocation attribute, double min) {
        PlayerLimits.setMin(player, attribute, min);
        this.setDirty();
    }

    /**
     * Clears both bounds for a player/attribute and flags the data for saving.
     */
    public void clear(UUID player, ResourceLocation attribute) {
        PlayerLimits.clear(player, attribute);
        this.setDirty();
    }
}
