package net.darkhax.attributefix.common.impl.command;

import com.mojang.authlib.GameProfile;
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
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

import java.util.Collection;
import java.util.OptionalDouble;
import java.util.UUID;

/**
 * {@code /attributelimit} command. The Brigadier tree lives here in common; each loader only has to
 * forward its dispatcher and {@link CommandBuildContext} to {@link #register}.
 *
 * <pre>
 *   /attributelimit max   &lt;targets&gt; &lt;attribute&gt; &lt;value&gt;
 *   /attributelimit min   &lt;targets&gt; &lt;attribute&gt; &lt;value&gt;
 *   /attributelimit clear &lt;targets&gt; &lt;attribute&gt;
 *   /attributelimit get   &lt;target&gt;  &lt;attribute&gt;
 * </pre>
 *
 * <p>Targets are resolved as {@link GameProfile}s, so both online and offline players (by name or
 * UUID) can be edited. Online targets have their live attributes refreshed immediately.</p>
 */
public final class AttributeLimitCommand {

    private AttributeLimitCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(Commands.literal("attributelimit")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("max")
                        .then(Commands.argument("targets", GameProfileArgument.gameProfile())
                                .then(Commands.argument("attribute", ResourceArgument.resource(buildContext, Registries.ATTRIBUTE))
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                                .executes(AttributeLimitCommand::setMax)))))
                .then(Commands.literal("min")
                        .then(Commands.argument("targets", GameProfileArgument.gameProfile())
                                .then(Commands.argument("attribute", ResourceArgument.resource(buildContext, Registries.ATTRIBUTE))
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                                .executes(AttributeLimitCommand::setMin)))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("targets", GameProfileArgument.gameProfile())
                                .then(Commands.argument("attribute", ResourceArgument.resource(buildContext, Registries.ATTRIBUTE))
                                        .executes(AttributeLimitCommand::clear))))
                .then(Commands.literal("get")
                        .then(Commands.argument("target", GameProfileArgument.gameProfile())
                                .then(Commands.argument("attribute", ResourceArgument.resource(buildContext, Registries.ATTRIBUTE))
                                        .executes(AttributeLimitCommand::getLimit)))));
    }

    private static int setMax(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return applyBound(ctx, true);
    }

    private static int setMin(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return applyBound(ctx, false);
    }

    private static int applyBound(CommandContext<CommandSourceStack> ctx, boolean isMax) throws CommandSyntaxException {
        final Collection<GameProfile> targets = GameProfileArgument.getGameProfiles(ctx, "targets");
        final Holder.Reference<Attribute> attribute = ResourceArgument.getAttribute(ctx, "attribute");
        final double value = DoubleArgumentType.getDouble(ctx, "value");
        final ResourceLocation id = attribute.key().location();
        final MinecraftServer server = ctx.getSource().getServer();
        final AttributeLimitsSavedData data = AttributeLimitsSavedData.get(server);

        for (GameProfile profile : targets) {
            final UUID uuid = profile.getId();
            if (isMax) {
                data.setMax(uuid, id, value);
            } else {
                data.setMin(uuid, id, value);
            }
            refreshIfOnline(server, uuid, attribute);
        }
        LimitSync.syncToAll(server);
        final String bound = isMax ? "maximum" : "minimum";
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Set " + id + " " + bound + " to " + value + " for " + targets.size() + " player(s)."), true);
        return targets.size();
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final Collection<GameProfile> targets = GameProfileArgument.getGameProfiles(ctx, "targets");
        final Holder.Reference<Attribute> attribute = ResourceArgument.getAttribute(ctx, "attribute");
        final ResourceLocation id = attribute.key().location();
        final MinecraftServer server = ctx.getSource().getServer();
        final AttributeLimitsSavedData data = AttributeLimitsSavedData.get(server);

        for (GameProfile profile : targets) {
            data.clear(profile.getId(), id);
            refreshIfOnline(server, profile.getId(), attribute);
        }
        LimitSync.syncToAll(server);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Cleared custom " + id + " limits for " + targets.size() + " player(s)."), true);
        return targets.size();
    }

    private static int getLimit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final Collection<GameProfile> targets = GameProfileArgument.getGameProfiles(ctx, "target");
        final Holder.Reference<Attribute> attribute = ResourceArgument.getAttribute(ctx, "attribute");
        final ResourceLocation id = attribute.key().location();
        int reported = 0;

        for (GameProfile profile : targets) {
            final OptionalDouble min = PlayerLimits.getMin(profile.getId(), id);
            final OptionalDouble max = PlayerLimits.getMax(profile.getId(), id);
            final String name = profile.getName() != null ? profile.getName() : profile.getId().toString();
            if (min.isEmpty() && max.isEmpty()) {
                ctx.getSource().sendSuccess(() -> Component.literal(
                        name + " has no custom " + id + " limit (global limit applies)."), false);
            } else {
                final String minStr = min.isPresent() ? String.valueOf(min.getAsDouble()) : "default";
                final String maxStr = max.isPresent() ? String.valueOf(max.getAsDouble()) : "default";
                ctx.getSource().sendSuccess(() -> Component.literal(
                        name + " " + id + " limit -> min: " + minStr + ", max: " + maxStr + "."), false);
                reported++;
            }
        }
        return reported;
    }

    private static void refreshIfOnline(MinecraftServer server, UUID uuid, Holder<Attribute> attribute) {
        final ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online == null) {
            return;
        }
        final AttributeInstance instance = online.getAttributes().getInstance(attribute);
        if (instance instanceof EntityOwned owned) {
            owned.attributefix$markDirty();
        }
    }
}
