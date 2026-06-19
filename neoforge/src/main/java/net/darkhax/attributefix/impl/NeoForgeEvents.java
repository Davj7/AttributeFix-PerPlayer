package net.darkhax.attributefix.impl;

import net.darkhax.attributefix.common.impl.AttributeFixMod;
import net.darkhax.attributefix.common.impl.Constants;
import net.darkhax.attributefix.common.impl.command.AttributeLimitCommand;
import net.darkhax.attributefix.common.impl.network.LimitSync;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Game-bus event subscriptions for NeoForge (the existing {@link NeoForgeMod} handles the mod bus).
 */
@EventBusSubscriber(modid = Constants.MOD_ID)
public class NeoForgeEvents {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        AttributeLimitCommand.register(event.getDispatcher(), event.getBuildContext());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        AttributeFixMod.getInstance().onServerStarted(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        AttributeFixMod.getInstance().onServerStopped();
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LimitSync.syncTo(player);
        }
    }
}
