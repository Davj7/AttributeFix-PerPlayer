package net.darkhax.attributefix.impl;

import net.darkhax.attributefix.common.impl.AttributeFixMod;
import net.darkhax.attributefix.common.impl.Constants;
import net.darkhax.attributefix.common.impl.network.LimitSync;
import net.darkhax.attributefix.common.impl.network.SyncLimitsPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(Constants.MOD_ID)
@EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class NeoForgeMod {

    @SubscribeEvent
    public static void onLoadComplete(FMLLoadCompleteEvent event) {
        AttributeFixMod.getInstance().init();
    }

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar(Constants.MOD_ID).playToClient(
                SyncLimitsPayload.TYPE,
                SyncLimitsPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        LimitSync.handleClient(payload, context.player().getAttributes())));
        LimitSync.setSender(new NeoForgeLimitSender());
    }
}