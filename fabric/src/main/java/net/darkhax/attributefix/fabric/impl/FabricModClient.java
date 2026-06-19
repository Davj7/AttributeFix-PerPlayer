package net.darkhax.attributefix.fabric.impl;

import net.darkhax.attributefix.common.impl.AttributeFixMod;
import net.darkhax.attributefix.common.impl.network.LimitSync;
import net.darkhax.attributefix.common.impl.network.SyncLimitsPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class FabricModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        AttributeFixMod.getInstance().init();
        FabricCommonEvents.register();
        ClientPlayNetworking.registerGlobalReceiver(SyncLimitsPayload.TYPE, (payload, context) ->
                context.client().execute(() ->
                        LimitSync.handleClient(payload, context.player().getAttributes())));
    }
}
