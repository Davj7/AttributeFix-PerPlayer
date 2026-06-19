package net.darkhax.attributefix.common.impl.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.darkhax.attributefix.common.impl.AttributeLimitsSavedData;
import net.darkhax.attributefix.common.impl.EntityOwned;
import net.darkhax.attributefix.common.impl.PlayerLimits;
import net.darkhax.attributefix.common.impl.network.LimitSync;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

import java.util.Collection;
import java.util.OptionalDouble;

/**
 * {@code /attributelimit} command. The Brigadier tree lives here in common; each loader only has to
 * forward its dispatcher and {@link CommandBuildContext} to {@link #register}.
 *
 * <pre>
 *   /attributelimit set   &lt;players&gt; &lt;attribute&gt; &lt;max&gt;
 *   /attributelimit clear &lt;players&gt; &lt;attribute&gt;
 *   /attributelimit get   &lt;player&gt;  &lt;attribute&gt;
 * </pre>
 */
public final class AttributeLimitCommand {

    private AttributeLimitCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(Commands.literal("attributelimit")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("set")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("attribute", ResourceArgument.resource(buildContext, Registries.ATTRIBUTE))
                                        .then(Commands.argument("max", DoubleArgumentType.doubleArg())
                                                .executes(AttributeLimitCommand::setLimit)))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("attribute", ResourceArgument.resource(buildContext, Registries.ATTRIBUTE))
                                        .executes(AttributeLimitCommand::clearLimit))))
                .then(Commands.literal("get")
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("attribute", ResourceArgument.resource(buildContext, Registries.ATTRIBUTE))
                                        .executes(AttributeLimitCommand::getLimit)))));
    }

    private static int setLimit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        final Holder.Reference<Attribute> attribute = ResourceArgument.getAttribute(ctx, "attribute");
        final double max = DoubleArgumentType.getDouble(ctx, "max");
        final ResourceLocation id = attribute.key().location();
        final AttributeLimitsSavedData data = AttributeLimitsSavedData.get(ctx.getSource().getServer());

        for (ServerPlayer player : targets) {
            data.setLimit(player.getUUID(), id, max);
            refresh(player, attribute);
        }
        LimitSync.syncToAll(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Set " + id + " limit to " + max + " for " + targets.size() + " player(s)."), true);
        return targets.size();
    }

    private static int clearLimit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        final Holder.Reference<Attribute> attribute = ResourceArgument.getAttribute(ctx, "attribute");
        final ResourceLocation id = attribute.key().location();
        final AttributeLimitsSavedData data = AttributeLimitsSavedData.get(ctx.getSource().getServer());

        for (ServerPlayer player : targets) {
            data.clearLimit(player.getUUID(), id);
            refresh(player, attribute);
        }
        LimitSync.syncToAll(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Cleared custom " + id + " limit for " + targets.size() + " player(s)."), true);
        return targets.size();
    }

    private static int getLimit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final ServerPlayer player = EntityArgument.getPlayer(ctx, "target");
        final Holder.Reference<Attribute> attribute = ResourceArgument.getAttribute(ctx, "attribute");
        final ResourceLocation id = attribute.key().location();
        final OptionalDouble limit = PlayerLimits.getMax(player.getUUID(), id);

        if (limit.isPresent()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    player.getName().getString() + " has a " + id + " limit of " + limit.getAsDouble() + "."), false);
            return 1;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                player.getName().getString() + " has no custom " + id + " limit (global limit applies)."), false);
        return 0;
    }

    /**
     * Marks the player's attribute instance dirty so the new cap is recalculated immediately instead
     * of only after the next modifier change.
     */
    private static void refresh(ServerPlayer player, Holder<Attribute> attribute) {
        final AttributeInstance instance = player.getAttributes().getInstance(attribute);
        if (instance instanceof EntityOwned owned) {
            owned.attributefix$markDirty();
        }
    }
}
