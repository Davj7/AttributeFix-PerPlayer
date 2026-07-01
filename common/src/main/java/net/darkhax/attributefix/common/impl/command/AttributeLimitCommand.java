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
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

import java.util.Collection;
import java.util.OptionalDouble;
import java.util.UUID;

/**
 * {@code /attributelimit} command. The Brigadier tree lives here in common; each loader only has to
 * forward its dispatcher and {@link CommandBuildContext} to {@link #register}.
 *
 * <pre>
 *   /attributelimit max     &lt;targets&gt; &lt;attribute&gt; &lt;value&gt;
 *   /attributelimit min     &lt;targets&gt; &lt;attribute&gt; &lt;value&gt;
 *   /attributelimit add max &lt;targets&gt; &lt;attribute&gt; &lt;delta&gt;
 *   /attributelimit add min &lt;targets&gt; &lt;attribute&gt; &lt;delta&gt;
 *   /attributelimit clear   &lt;targets&gt; &lt;attribute&gt;
 *   /attributelimit get     &lt;target&gt;  &lt;attribute&gt;
 * </pre>
 *
 * <p>Targets are resolved as {@link GameProfile}s, so both online and offline players (by name or
 * UUID) can be edited. Online targets have their live attributes refreshed immediately.</p>
 *
 * <p>The {@code add} form adjusts a bound relative to its current value. When a player has no custom
 * bound yet, the attribute's <em>global</em> limit (the value it is capped at server-wide, expanded by
 * the base mod) is used as the base, so {@code add max ... -5} on a fresh player yields
 * {@code global - 5}. This composes across skill nodes and lets a respec revert by applying the
 * opposite delta.</p>
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
                .then(Commands.literal("add")
                        .then(Commands.literal("max")
                                .then(Commands.argument("targets", GameProfileArgument.gameProfile())
                                        .then(Commands.argument("attribute", ResourceArgument.resource(buildContext, Registries.ATTRIBUTE))
                                                .then(Commands.argument("delta", DoubleArgumentType.doubleArg())
                                                        .executes(AttributeLimitCommand::addMax)))))
                        .then(Commands.literal("min")
                                .then(Commands.argument("targets", GameProfileArgument.gameProfile())
                                        .then(Commands.argument("attribute", ResourceArgument.resource(buildContext, Registries.ATTRIBUTE))
                                                .then(Commands.argument("delta", DoubleArgumentType.doubleArg())
                                                        .executes(AttributeLimitCommand::addMin))))))
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

    private static int addMax(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return applyRelative(ctx, true);
    }

    private static int addMin(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return applyRelative(ctx, false);
    }

    /**
     * Shifts a bound by {@code delta} relative to its current value. Falls back to the attribute's
     * global limit as the base when the player has no custom bound for it yet.
     */
    private static int applyRelative(CommandContext<CommandSourceStack> ctx, boolean isMax) throws CommandSyntaxException {
        final Collection<GameProfile> targets = GameProfileArgument.getGameProfiles(ctx, "targets");
        final Holder.Reference<Attribute> attribute = ResourceArgument.getAttribute(ctx, "attribute");
        final double delta = DoubleArgumentType.getDouble(ctx, "delta");
        final ResourceLocation id = attribute.key().location();
        final MinecraftServer server = ctx.getSource().getServer();
        final AttributeLimitsSavedData data = AttributeLimitsSavedData.get(server);
        final double globalBase = globalBound(attribute, isMax);

        for (GameProfile profile : targets) {
            final UUID uuid = profile.getId();
            final OptionalDouble current = isMax ? PlayerLimits.getMax(uuid, id) : PlayerLimits.getMin(uuid, id);
            final double base = current.orElse(globalBase);
            final double value = base + delta;
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
                "Adjusted " + id + " " + bound + " by " + delta + " for " + targets.size() + " player(s)."), true);
        return targets.size();
    }

    /**
     * The server-wide limit used as the base for a relative adjustment when a player has none of their
     * own. For a {@link RangedAttribute} this is its (possibly base-mod-expanded) min/max; other
     * attribute types have no range, so their default value is used as a sane fallback.
     */
    private static double globalBound(Holder.Reference<Attribute> attribute, boolean isMax) {
        if (attribute.value() instanceof RangedAttribute ranged) {
            return isMax ? ranged.getMaxValue() : ranged.getMinValue();
        }
        return attribute.value().getDefaultValue();
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
