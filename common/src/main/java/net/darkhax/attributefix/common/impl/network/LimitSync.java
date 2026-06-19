package net.darkhax.attributefix.common.impl.network;

import net.darkhax.attributefix.common.impl.EntityOwned;
import net.darkhax.attributefix.common.impl.PlayerLimits;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;

/**
 * Loader-agnostic glue for keeping clients in sync with the server's per-player limits. Each loader
 * registers a {@link Sender} (its packet-sending implementation); common code just calls
 * {@link #syncTo}/{@link #syncToAll}. The client side feeds received payloads into
 * {@link #handleClient}.
 */
public final class LimitSync {

    /**
     * The loader-specific transport. Set once during mod setup.
     */
    public interface Sender {
        void toPlayer(ServerPlayer player, SyncLimitsPayload payload);

        void toAll(MinecraftServer server, SyncLimitsPayload payload);
    }

    private static Sender sender;

    private LimitSync() {}

    public static void setSender(Sender sender) {
        LimitSync.sender = sender;
    }

    /**
     * Sends the current limit table to a single player (used on login).
     */
    public static void syncTo(ServerPlayer player) {
        if (sender != null) {
            sender.toPlayer(player, snapshotPayload());
        }
    }

    /**
     * Broadcasts the current limit table to everyone (used after a command changes a limit).
     */
    public static void syncToAll(MinecraftServer server) {
        if (sender != null) {
            sender.toAll(server, snapshotPayload());
        }
    }

    private static SyncLimitsPayload snapshotPayload() {
        return new SyncLimitsPayload(PlayerLimits.snapshot());
    }

    /**
     * Applies a received payload on the client: replaces the local limit view and forces the local
     * player's syncable attributes to recompute so the HUD reflects the new caps immediately.
     *
     * @param localAttributes The receiving client player's attribute map (may be null very early).
     */
    public static void handleClient(SyncLimitsPayload payload, AttributeMap localAttributes) {
        PlayerLimits.loadFrom(payload.limits());
        if (localAttributes != null) {
            for (AttributeInstance instance : localAttributes.getSyncableAttributes()) {
                if (instance instanceof EntityOwned owned) {
                    owned.attributefix$markDirty();
                }
            }
        }
    }
}
