package net.darkhax.attributefix.fabric.impl;

import net.darkhax.attributefix.common.impl.network.LimitSync;
import net.darkhax.attributefix.common.impl.network.SyncLimitsPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric implementation of the limit packet transport.
 */
public class FabricLimitSender implements LimitSync.Sender {

    @Override
    public void toPlayer(ServerPlayer player, SyncLimitsPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public void toAll(MinecraftServer server, SyncLimitsPayload payload) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}
