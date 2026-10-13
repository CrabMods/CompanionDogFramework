package com.crabmods.companiondog.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Shared catch and single-item handoff; each toy supplies its own flight and impact physics. */
public abstract class FetchToyEntity extends ThrowableItemProjectile {
    private int flightTicks;

    protected FetchToyEntity(EntityType<? extends FetchToyEntity> type, Level level) {
        super(type, level);
    }

    protected FetchToyEntity(EntityType<? extends FetchToyEntity> type, LivingEntity thrower, Level level) {
        super(type, thrower, level);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || isRemoved()) return;
        for (CompanionDogEntity dog : level().getEntitiesOfClass(CompanionDogEntity.class, getBoundingBox().inflate(0.45))) {
            if (dog.hasLineOfSight(this) && dog.tryCatch(getItem().copyWithCount(1))) {
                discard();
                return;
            }
        }
        if (++flightTicks > 300) settle();
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && !(target instanceof CompanionDogEntity);
    }

    protected final void settle() {
        if (level().isClientSide || isRemoved()) return;
        ItemStack stack = getItem().copyWithCount(1);
        ItemEntity item = new ItemEntity(level(), getX(), getY(), getZ(), stack);
        item.setDeltaMovement(getDeltaMovement().multiply(0.3, 0.0, 0.3));
        item.setPickUpDelay(20);
        if (getOwner() != null) item.setThrower(getOwner());
        if (level().addFreshEntity(item)) discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("CompanionDogFlightTicks", flightTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        flightTicks = Math.max(0, Math.min(301, tag.getInt("CompanionDogFlightTicks")));
    }
}
