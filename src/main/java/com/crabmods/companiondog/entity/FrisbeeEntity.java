package com.crabmods.companiondog.entity;

import com.crabmods.companiondog.registry.CompanionDogRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** A drag-limited glider: forward speed supports it, while a slow or vertical throw stalls. */
public final class FrisbeeEntity extends FetchToyEntity {
    public FrisbeeEntity(EntityType<? extends FrisbeeEntity> type, Level level) {
        super(type, level);
    }

    public FrisbeeEntity(Level level, LivingEntity thrower) {
        super(CompanionDogRegistries.FRISBEE_ENTITY.get(), thrower, level);
    }

    @Override
    protected Item getDefaultItem() {
        return CompanionDogRegistries.FRISBEE.get();
    }

    @Override
    protected double getDefaultGravity() {
        // Lift never exceeds weight, and disappears in water or near zero horizontal speed.
        double lift = isInWater() ? 0 : Math.min(0.049, getDeltaMovement().horizontalDistanceSqr() * 0.10);
        return 0.055 - lift;
    }

    @Override
    public void tick() {
        if (!isNoGravity()) {
            // Vanilla supplies collision sweeping and its own .99 air drag after this step.
            setDeltaMovement(getDeltaMovement().multiply(0.975, 0.94, 0.975));
        }
        super.tick();
        if (!level().isClientSide && !isRemoved() && isInWater()) settle();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        Vec3 normal = Vec3.atLowerCornerOf(result.getDirection().getNormal());
        setPos(result.getLocation().add(normal.scale(0.3)));
        setDeltaMovement(Vec3.ZERO);
        settle();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        // No damage or knockback. Drop on the incoming side instead of travelling through a player.
        setDeltaMovement(Vec3.ZERO);
        settle();
    }
}
