package com.crabmods.companiondog.item;

import com.crabmods.companiondog.entity.FrisbeeEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class FrisbeeItem extends Item {
    public FrisbeeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            FrisbeeEntity disc = new FrisbeeEntity(level, player);
            disc.setItem(stack.copyWithCount(1));
            disc.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, 1.15F, 0.25F);
            if (!level.addFreshEntity(disc)) return InteractionResultHolder.fail(stack);
            stack.consume(1, player);
            player.awardStat(Stats.ITEM_USED.get(this));
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW,
                    SoundSource.PLAYERS, 0.4F, 0.85F);
        }
        player.getCooldowns().addCooldown(this, 12);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
