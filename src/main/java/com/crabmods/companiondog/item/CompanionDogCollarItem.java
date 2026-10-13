package com.crabmods.companiondog.item;

import com.crabmods.companiondog.entity.CompanionDogAction;
import com.crabmods.companiondog.entity.CompanionDogEntity;
import com.crabmods.companiondog.entity.CompanionDogPose;
import com.crabmods.companiondog.registry.CompanionDogRegistries;
import com.crabmods.companiondog.world.CompanionDogBindings;
import com.crabmods.companiondog.world.CompanionDogSafety;
import com.crabmods.companiondog.network.CompanionDogArrivalPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** Calls CompanionDog to your side, wherever the dog is. Never creates a second CompanionDog. */
public class CompanionDogCollarItem extends Item {
    public CompanionDogCollarItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }
        if (player.isSecondaryUseActive()) {
            CompanionDogBindings.get(serverPlayer.getServer()).setHome(serverPlayer);
            return InteractionResultHolder.success(stack);
        }
        CompanionDogEntity dog = summon(serverPlayer);
        player.getCooldowns().addCooldown(this, dog == null ? 10 : 40);
        return InteractionResultHolder.success(stack);
    }

    /** Brings the bound dog to the player, or creates it the first time. */
    @Nullable
    public static CompanionDogEntity summon(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator()) return null;
        MinecraftServer server = player.getServer();
        CompanionDogBindings bindings = CompanionDogBindings.get(server);
        UUID existing = bindings.dogOf(player.getUUID());
        if (existing != null) {
            CompanionDogEntity dog = bindings.findLoaded(server, player.getUUID());
            if (dog != null && dog.isAlive()) {
                bindings.cancelRecall(player);
                CompanionDogEntity arrived = dog.bringTo(player);
                if (arrived != null) {
                    bindings.checkpoint(arrived);
                    greet(arrived);
                    announce(player, arrived, false);
                }
                return arrived;
            }
            bindings.requestRecall(player);
            return null;
        }
        CompanionDogEntity dog = CompanionDogRegistries.COMPANION_DOG.get().create(player.serverLevel());
        if (dog == null) {
            return null;
        }
        Vec3 safe = CompanionDogSafety.findForOwner(player, dog);
        if (safe == null) {
            player.displayClientMessage(Component.translatable("message.companiondog.unsafe"), true);
            return null;
        }
        dog.moveTo(safe.x, safe.y, safe.z, player.getYRot() + 180.0F, 0.0F);
        dog.finalizeSpawn(player.serverLevel(), player.serverLevel().getCurrentDifficultyAt(player.blockPosition()),
                MobSpawnType.MOB_SUMMONED, null);
        dog.tame(player);
        dog.setBound(true);
        dog.setBindingEpoch(bindings.epochOf(player.getUUID()));
        dog.setHome(GlobalPos.of(player.level().dimension(), BlockPos.containing(safe)));
        if (!player.serverLevel().addFreshEntity(dog)) return null;
        bindings.bind(player.getUUID(), dog.getUUID());
        bindings.checkpoint(dog);
        greet(dog);
        announce(player, dog, true);
        return dog;
    }

    private static void announce(ServerPlayer player, CompanionDogEntity dog, boolean first) {
        PacketDistributor.sendToPlayer(player, new CompanionDogArrivalPayload(dog.getId(), dog.getUUID(), first));
    }

    private static void greet(CompanionDogEntity dog) {
        dog.setCommandedPose(CompanionDogPose.STAND);
        dog.playSound(SoundEvents.WOLF_AMBIENT, 0.6F, 1.5F);
        if (dog.level() instanceof ServerLevel server) {
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART,
                    dog.getX(), dog.getY() + 0.85, dog.getZ(), 4, 0.3, 0.15, 0.3, 0.0);
        }
        dog.playAction(CompanionDogAction.SPIN);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.companiondog.companiondog_collar.desc").withStyle(ChatFormatting.GRAY));
    }
}
