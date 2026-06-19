package net.darkhax.attributefix.impl;

import net.darkhax.attributefix.common.impl.network.LimitSync;
import net.darkhax.attributefix.common.impl.network.SyncLimitsPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * NeoForge implementation of the limit packet transport.
 */
public class NeoForgeLimitSender implements LimitSync.Sender {

    @Override
    public void toPlayer(ServerPlayer player, SyncLimitsPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void toAll(MinecraftServer server, SyncLimitsPayload payload) {
        PacketDistributor.sendToAllPlayers(payload);
    }
}
