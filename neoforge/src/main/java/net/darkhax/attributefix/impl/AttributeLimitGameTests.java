package net.darkhax.attributefix.impl;

import com.mojang.authlib.GameProfile;
import net.darkhax.attributefix.common.impl.AttributeLimit;
import net.darkhax.attributefix.common.impl.AttributeLimitsSavedData;
import net.darkhax.attributefix.common.impl.PlayerLimits;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Headless regression tests for the per-player limit feature. They use a NeoForge {@link FakePlayer}
 * (which is a real {@code ServerPlayer}, so the clamp mixin treats it as a player) and assert the
 * computed attribute value directly.
 *
 * <p>Run them with {@code runClient} and the in-game command {@code /test runall} (an empty structure
 * template named {@code empty} must exist under {@code run/gameteststructures/empty.snbt}).</p>
 */
@GameTestHolder(net.darkhax.attributefix.common.impl.Constants.MOD_ID)
@PrefixGameTestTemplate(false)
public class AttributeLimitGameTests {

    private static final ResourceLocation ARMOR = ResourceLocation.withDefaultNamespace("generic.armor");
    private static final ResourceLocation MAX_HEALTH = ResourceLocation.withDefaultNamespace("generic.max_health");

    @GameTest(template = "empty")
    public static void maxClampLowersValue(GameTestHelper helper) {
        final FakePlayer player = freshPlayer(helper.getLevel(), "afix-max");
        final AttributeInstance armor = instance(player, ARMOR);

        PlayerLimits.setMax(player.getUUID(), ARMOR, 30.0D);
        armor.setBaseValue(100.0D);
        final double value = armor.getValue();
        PlayerLimits.clear(player.getUUID(), ARMOR);

        if (value != 30.0D) {
            throw new GameTestAssertException("max clamp: expected 30.0 but got " + value);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void minClampRaisesValue(GameTestHelper helper) {
        final FakePlayer player = freshPlayer(helper.getLevel(), "afix-min");
        final AttributeInstance armor = instance(player, ARMOR);

        PlayerLimits.setMin(player.getUUID(), ARMOR, 10.0D);
        armor.setBaseValue(0.0D);
        final double value = armor.getValue();
        PlayerLimits.clear(player.getUUID(), ARMOR);

        if (value != 10.0D) {
            throw new GameTestAssertException("min clamp: expected 10.0 but got " + value);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void noLimitLeavesValueUntouched(GameTestHelper helper) {
        final FakePlayer player = freshPlayer(helper.getLevel(), "afix-none");
        final AttributeInstance health = instance(player, MAX_HEALTH);

        // No override for this player: the early-out path must leave the value exactly as computed.
        health.setBaseValue(40.0D);
        final double value = health.getValue();

        if (value != 40.0D) {
            throw new GameTestAssertException("no-limit: expected 40.0 but got " + value);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void persistenceRoundTrips(GameTestHelper helper) {
        final UUID id = UUID.randomUUID();
        PlayerLimits.clearAll();
        PlayerLimits.setMin(id, ARMOR, 10.0D);
        PlayerLimits.setMax(id, ARMOR, 30.0D);

        final AttributeLimitsSavedData data = new AttributeLimitsSavedData();
        final CompoundTag tag = data.save(new CompoundTag(), helper.getLevel().registryAccess());

        PlayerLimits.clearAll();
        AttributeLimitsSavedData.load(tag, helper.getLevel().registryAccess());
        final AttributeLimit limit = PlayerLimits.get(id, ARMOR);
        PlayerLimits.clearAll();

        if (limit == null || limit.min() == null || limit.max() == null
                || limit.min() != 10.0D || limit.max() != 30.0D) {
            throw new GameTestAssertException("persistence: round-trip lost data, got " + limit);
        }
        helper.succeed();
    }

    private static FakePlayer freshPlayer(ServerLevel level, String name) {
        return FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), name));
    }

    private static AttributeInstance instance(FakePlayer player, ResourceLocation attributeId) {
        final Holder<Attribute> attribute = BuiltInRegistries.ATTRIBUTE.getHolder(attributeId).orElseThrow();
        final AttributeInstance instance = player.getAttributes().getInstance(attribute);
        if (instance == null) {
            throw new GameTestAssertException("player has no instance for attribute " + attributeId);
        }
        return instance;
    }
}
