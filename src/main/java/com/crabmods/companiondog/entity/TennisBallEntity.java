package com.crabmods.companiondog.entity;

import com.crabmods.companiondog.registry.CompanionDogRegistries;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** A bouncy ball. CompanionDog can catch it in the air; otherwise it settles as an item to fetch. */
public class TennisBallEntity extends FetchToyEntity {
    private int bounces;

    public TennisBallEntity(EntityType<? extends TennisBallEntity> type, Level level) {
        super(type, level);
    }

    public TennisBallEntity(Level level, LivingEntity thrower) {
        super(CompanionDogRegistries.TENNIS_BALL_ENTITY.get(), thrower, level);
    }

    @Override
    protected Item getDefaultItem() {
        return CompanionDogRegistries.TENNIS_BALL.get();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        setDeltaMovement(getDeltaMovement().scale(-0.25));
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        Vec3 motion = getDeltaMovement();
        Direction face = result.getDirection();
        Vec3 bounced = switch (face.getAxis()) {
            case X -> new Vec3(-motion.x * 0.55, motion.y * 0.8, motion.z * 0.8);
            case Y -> new Vec3(motion.x * 0.7, -motion.y * 0.55, motion.z * 0.7);
            case Z -> new Vec3(motion.x * 0.8, motion.y * 0.8, -motion.z * 0.55);
        };
        Vec3 normal = Vec3.atLowerCornerOf(face.getNormal());
        setPos(result.getLocation().add(normal.scale(0.13)));
        setDeltaMovement(bounced);
        bounces++;
        if (!level().isClientSide) {
            if (motion.lengthSqr() > 0.01) {
                playSound(SoundEvents.SLIME_BLOCK_HIT, 0.4F, 1.6F + random.nextFloat() * 0.2F);
            }
            if (bounces > 10 || (face == Direction.UP && bounced.lengthSqr() < 0.01)) {
                settle();
            }
        }
    }

}
