package net.darkhax.attributefix.fabric.impl;

import net.darkhax.attributefix.common.impl.AttributeFixMod;
import net.darkhax.attributefix.common.impl.command.AttributeLimitCommand;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * Shared Fabric event wiring registered from both the client and dedicated-server initializers.
 * Exactly one of those runs per environment, so {@link #register()} fires once either way.
 */
public final class FabricCommonEvents {

    private FabricCommonEvents() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                AttributeLimitCommand.register(dispatcher, registryAccess));
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                AttributeFixMod.getInstance().onServerStarted(server));
        ServerLifecycleEvents.SERVER_STOPPED.register(server ->
                AttributeFixMod.getInstance().onServerStopped());
    }
}
