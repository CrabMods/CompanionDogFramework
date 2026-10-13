package com.crabmods.companiondog.world;

import com.crabmods.companiondog.entity.CompanionDogEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.ClipContext;
import org.jetbrains.annotations.Nullable;

/** Conservative landing checks shared by first arrival, recall and going home. */
public final class CompanionDogSafety {
    private CompanionDogSafety() {}

    public static boolean hazardous(BlockState state) {
        return state.is(BlockTags.FIRE) || state.is(Blocks.LAVA) || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL);
    }

    public static boolean safe(ServerLevel level, CompanionDogEntity dog, BlockPos feet) {
        if (feet.getY() <= level.getMinBuildHeight() || feet.getY() >= level.getMaxBuildHeight() - 1
                || !level.getWorldBorder().isWithinBounds(feet)
                || !level.hasChunkAt(feet)) return false;
        BlockPos floor = feet.below();
        if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) return false;
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-1, -1, -1), feet.offset(1, 1, 1))) {
            if (!level.hasChunkAt(pos) || hazardous(level.getBlockState(pos))) return false;
        }
        var box = dog.getDimensions(dog.getPose()).makeBoundingBox(Vec3.atBottomCenterOf(feet));
        return level.getWorldBorder().isWithinBounds(box)
                && !level.containsAnyLiquid(box) && level.noCollision(dog, box);
    }

    /** Recall must be visible and outside the owner, not just on any nearby floor behind a wall. */
    @Nullable
    public static Vec3 findForOwner(ServerPlayer owner, CompanionDogEntity dog) {
        ServerLevel level = owner.serverLevel();
        Vec3 best = null;
        double bestScore = Double.MAX_VALUE;
        Vec3 forward = Vec3.directionFromRotation(0, owner.getYRot());
        for (BlockPos candidate : BlockPos.betweenClosed(owner.blockPosition().offset(-4, -2, -4),
                owner.blockPosition().offset(4, 2, 4))) {
            Vec3 feet = Vec3.atBottomCenterOf(candidate);
            Vec3 offset = feet.subtract(owner.position());
            if (offset.horizontalDistanceSqr() < 1.4 * 1.4 || offset.horizontalDistanceSqr() > 16
                    || !safe(level, dog, candidate)
                    || dog.getDimensions(dog.getPose()).makeBoundingBox(feet).intersects(owner.getBoundingBox())) continue;
            Vec3 center = feet.add(0, dog.getBbHeight() * 0.6, 0);
            if (level.clip(new ClipContext(owner.getEyePosition(), center, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, owner)).getType() != HitResult.Type.MISS) continue;
            double score = offset.lengthSqr() + Math.abs(offset.y) * 8 - offset.dot(forward) * 3;
            if (score < bestScore) {
                bestScore = score;
                best = feet;
            }
        }
        return best;
    }

    @Nullable
    public static Vec3 find(ServerLevel level, CompanionDogEntity dog, BlockPos origin) {
        for (int radius = 0; radius <= 4; radius++) {
            for (int dy : new int[]{0, 1, -1, 2, -2}) {
                for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    BlockPos candidate = origin.offset(dx, dy, dz);
                    if (safe(level, dog, candidate)) return Vec3.atBottomCenterOf(candidate);
                }
            }
        }
        return null;
    }

    public static CompoundTag encode(GlobalPos home) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Dimension", home.dimension().location().toString());
        tag.putLong("Pos", home.pos().asLong());
        return tag;
    }

    @Nullable
    public static GlobalPos decode(CompoundTag tag) {
        if (!tag.contains("Dimension") || !tag.contains("Pos")) return null;
        try {
            return GlobalPos.of(ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.parse(tag.getString("Dimension"))), BlockPos.of(tag.getLong("Pos")));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
