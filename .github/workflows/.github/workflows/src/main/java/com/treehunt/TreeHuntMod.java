package com.treehunt;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.SaplingBlock;
import java.util.*;

public class TreeHuntMod implements ModInitializer {
    private static final int LOGS_TO_TRIGGER = 5;
    private static final int SAPLINGS_TO_ESCAPE = 10;
    private static final Map<UUID, PlayerHunt> HUNTS = new HashMap<>();

    private static class PlayerHunt {
        int logs = 0, planted = 0, scanTick = 0;
        boolean active = false;
        final Set<BlockPos> countedSaplings = new HashSet<>();
        final Set<UUID> ghostIds = new HashSet<>();
    }

    @Override public void onInitialize() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayer sp) || world.isClientSide()) return;
            if (!isLog(state.getBlock())) return;
            PlayerHunt data = HUNTS.computeIfAbsent(sp.getUUID(), id -> new PlayerHunt());
            if (data.active) return;
            data.logs++;
            if (data.logs >= LOGS_TO_TRIGGER) startHunt(sp, (ServerLevel) world, data);
        });
        ServerTickEvents.END_SERVER_TICK.register(TreeHuntMod::tick);
    }

    private static boolean isLog(Block b) {
        return b == Blocks.OAK_LOG || b == Blocks.SPRUCE_LOG || b == Blocks.BIRCH_LOG || b == Blocks.JUNGLE_LOG ||
            b == Blocks.ACACIA_LOG || b == Blocks.DARK_OAK_LOG || b == Blocks.MANGROVE_LOG || b == Blocks.CHERRY_LOG ||
            b == Blocks.PALE_OAK_LOG || b == Blocks.CRIMSON_STEM || b == Blocks.WARPED_STEM ||
            b == Blocks.STRIPPED_OAK_LOG || b == Blocks.STRIPPED_SPRUCE_LOG || b == Blocks.STRIPPED_BIRCH_LOG ||
            b == Blocks.STRIPPED_JUNGLE_LOG || b == Blocks.STRIPPED_ACACIA_LOG || b == Blocks.STRIPPED_DARK_OAK_LOG ||
            b == Blocks.STRIPPED_MANGROVE_LOG || b == Blocks.STRIPPED_CHERRY_LOG || b == Blocks.STRIPPED_PALE_OAK_LOG ||
            b == Blocks.STRIPPED_CRIMSON_STEM || b == Blocks.STRIPPED_WARPED_STEM;
    }

    private static void startHunt(ServerPlayer player, ServerLevel level, PlayerHunt data) {
        data.active = true; data.logs = 0; data.planted = 0; data.countedSaplings.clear(); data.ghostIds.clear();
        BlockPos c = player.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-12, -5, -12), c.offset(12, 5, 12))) {
            if (level.getBlockState(p).getBlock() instanceof SaplingBlock) data.countedSaplings.add(p.immutable());
        }
        player.sendSystemMessage(Component.literal("§5§lTHE FOREST SPIRIT IS HUNTING YOU! §rPlant 10 saplings to escape."));
        Vex ghost = EntityType.VEX.create(level, EntitySpawnReason.EVENT);
        if (ghost != null) {
            ghost.snapTo(player.getX() + 5, player.getY() + 1, player.getZ() + 5, level.random.nextFloat() * 360f, 0);
            ghost.setCustomName(Component.literal("§5Tree Ghost")); ghost.setCustomNameVisible(true);
            ghost.setTarget(player); ghost.setPersistenceRequired();
            level.addFreshEntity(ghost); data.ghostIds.add(ghost.getUUID());
        }
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerHunt data = HUNTS.get(player.getUUID());
            if (data == null || !data.active) continue;
            data.scanTick++;
            if (data.scanTick % 10 == 0) {
                BlockPos center = player.blockPosition();
                for (BlockPos p : BlockPos.betweenClosed(center.offset(-12, -5, -12), center.offset(12, 5, 12))) {
                    BlockState state = player.level().getBlockState(p);
                    if (state.getBlock() instanceof SaplingBlock && data.countedSaplings.add(p.immutable())) {
                        data.planted++;
                        player.sendSystemMessage(Component.literal("§aSaplings planted: §e" + data.planted + "/10"));
                    }
                }
            }
            player.displayClientMessage(Component.literal("§aTrees planted: §e" + data.planted + "/10 §5| §cForest Spirit is hunting!"), true);
            if (data.planted >= SAPLINGS_TO_ESCAPE) finishHunt(server, player, data);
        }
    }

    private static void finishHunt(MinecraftServer server, ServerPlayer player, PlayerHunt data) {
        for (ServerLevel level : server.getAllLevels()) {
            for (UUID id : data.ghostIds) {
                Entity entity = level.getEntity(id);
                if (entity != null) entity.discard();
            }
        }
        data.active = false; data.logs = 0; data.planted = 0; data.ghostIds.clear(); data.countedSaplings.clear();
        player.sendSystemMessage(Component.literal("§a§lTHE FOREST SPIRIT HAS RELEASED YOU! §rThe forest thanks you."));
    }
          }
