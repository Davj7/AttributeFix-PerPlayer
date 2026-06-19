package net.darkhax.attributefix.common.impl.network;

import net.darkhax.attributefix.common.impl.AttributeLimit;
import net.darkhax.attributefix.common.impl.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-to-client packet carrying the full per-player limit table. The payload is small (a handful
 * of players and attributes), so we always send the complete snapshot rather than deltas: trivially
 * correct and the bandwidth cost is negligible.
 */
public record SyncLimitsPayload(Map<UUID, Map<ResourceLocation, AttributeLimit>> limits) implements CustomPacketPayload {

    public static final Type<SyncLimitsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "sync_limits"));

    public static final StreamCodec<FriendlyByteBuf, SyncLimitsPayload> STREAM_CODEC =
            StreamCodec.of(SyncLimitsPayload::write, SyncLimitsPayload::read);

    @Override
    public Type<SyncLimitsPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, SyncLimitsPayload payload) {
        buf.writeVarInt(payload.limits.size());
        payload.limits.forEach((uuid, perAttribute) -> {
            buf.writeUUID(uuid);
            buf.writeVarInt(perAttribute.size());
            perAttribute.forEach((id, limit) -> {
                buf.writeResourceLocation(id);
                writeNullableDouble(buf, limit.min());
                writeNullableDouble(buf, limit.max());
            });
        });
    }

    private static SyncLimitsPayload read(FriendlyByteBuf buf) {
        final int playerCount = buf.readVarInt();
        final Map<UUID, Map<ResourceLocation, AttributeLimit>> limits = new HashMap<>(playerCount);
        for (int i = 0; i < playerCount; i++) {
            final UUID uuid = buf.readUUID();
            final int attributeCount = buf.readVarInt();
            final Map<ResourceLocation, AttributeLimit> perAttribute = new HashMap<>(attributeCount);
            for (int j = 0; j < attributeCount; j++) {
                final ResourceLocation id = buf.readResourceLocation();
                final Double min = readNullableDouble(buf);
                final Double max = readNullableDouble(buf);
                perAttribute.put(id, new AttributeLimit(min, max));
            }
            limits.put(uuid, perAttribute);
        }
        return new SyncLimitsPayload(limits);
    }

    private static void writeNullableDouble(FriendlyByteBuf buf, Double value) {
        buf.writeBoolean(value != null);
        if (value != null) {
            buf.writeDouble(value);
        }
    }

    private static Double readNullableDouble(FriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readDouble() : null;
    }
}
