package net.darkhax.attributefix.fabric.impl;

import net.darkhax.attributefix.common.impl.AttributeFixMod;
import net.darkhax.attributefix.common.impl.command.AttributeLimitCommand;
import net.darkhax.attributefix.common.impl.network.LimitSync;
import net.darkhax.attributefix.common.impl.network.SyncLimitsPayload;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/**
 * Shared Fabric event wiring registered from both the client and dedicated-server initializers.
 * Exactly one of those runs per environment, so {@link #register()} fires once either way.
 */
public final class FabricCommonEvents {

    private FabricCommonEvents() {}

    public static void register() {
        PayloadTypeRegistry.playS2C().register(SyncLimitsPayload.TYPE, SyncLimitsPayload.STREAM_CODEC);
        LimitSync.setSender(new FabricLimitSender());

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                AttributeLimitCommand.register(dispatcher, registryAccess));
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                AttributeFixMod.getInstance().onServerStarted(server));
        ServerLifecycleEvents.SERVER_STOPPED.register(server ->
                AttributeFixMod.getInstance().onServerStopped());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                LimitSync.syncTo(handler.player));
    }
}
