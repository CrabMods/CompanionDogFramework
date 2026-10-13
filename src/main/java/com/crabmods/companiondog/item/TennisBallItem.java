package com.crabmods.companiondog.item;

import com.crabmods.companiondog.entity.TennisBallEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class TennisBallItem extends Item {
    public TennisBallItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW,
                SoundSource.PLAYERS, 0.5F, 0.6F);
        if (!level.isClientSide) {
            TennisBallEntity ball = new TennisBallEntity(level, player);
            ball.setItem(stack.copyWithCount(1));
            ball.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.1F, 0.5F);
            level.addFreshEntity(ball);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        stack.consume(1, player);
        player.getCooldowns().addCooldown(this, 6);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
