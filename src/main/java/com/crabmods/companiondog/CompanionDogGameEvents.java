package com.crabmods.companiondog;

import com.crabmods.companiondog.registry.CompanionDogRegistries;
import com.crabmods.companiondog.entity.CompanionDogEntity;
import com.crabmods.companiondog.world.CompanionDogBindings;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.CommandEvent;

public final class CompanionDogGameEvents {
    private CompanionDogGameEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        CompanionDogBindings.get(event.getServer()).tick(event.getServer());
    }

    /** The first time a player joins a world, CompanionDog's collar is waiting for them. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.getData(CompanionDogRegistries.RECEIVED_COLLAR)) {
            return;
        }
        player.setData(CompanionDogRegistries.RECEIVED_COLLAR, true);
        ItemStack collar = new ItemStack(CompanionDogRegistries.COMPANION_DOG_COLLAR.get());
        if (!player.getInventory().add(collar)) {
            player.drop(collar, false);
        }
        player.sendSystemMessage(Component.translatable("message.companiondog.welcome").withStyle(ChatFormatting.GOLD));
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof CompanionDogEntity dog && dog.isBound()
                && event.getLevel() instanceof ServerLevel level) {
            var bindings = CompanionDogBindings.get(level.getServer());
            if ((event.loadedFromDisk() || bindings.dogOf(dog.getOwnerUUID()) != null
                    || dog.getBindingEpoch() != bindings.epochOf(dog.getOwnerUUID()))
                    && !bindings.acceptIdentity(dog)) event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() instanceof CompanionDogEntity) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sendCompanionsHome(player, false);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sendCompanionsHome(player, false);
    }

    public static void sendCompanionsHome(ServerPlayer player, boolean danger) {
        CompanionDogBindings.get(player.getServer()).requestOwnerHome(player.getUUID(), danger);
        for (ServerLevel level : player.getServer().getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (entity instanceof CompanionDogEntity dog && player.getUUID().equals(dog.getOwnerUUID())) dog.requestHome(danger);
            }
        }
    }

    @SubscribeEvent
    public static void onVanillaKill(CommandEvent event) {
        if (event.isCanceled()) return;
        var builder = event.getParseResults().getContext().getLastChild();
        if (builder.getNodes().isEmpty() || builder.getCommand() == null
                || !java.util.Set.of("kill", "minecraft:kill").contains(builder.getNodes().getFirst().getNode().getName())
                || !builder.getSource().hasPermission(2)) return;
        var original = builder.getCommand();
        // Keep Brigadier execution/return values, including execute/store, while changing only CompanionDog feedback.
        builder.withCommand(context -> {
            java.util.Collection<? extends net.minecraft.world.entity.Entity> targets = builder.getArguments().containsKey("targets")
                    ? EntityArgument.getEntities(context, "targets")
                    : java.util.List.of(context.getSource().getEntityOrException());
            long companions = targets.stream().filter(CompanionDogEntity.class::isInstance).count();
            if (companions == 0) return original.run(context);
            targets.forEach(net.minecraft.world.entity.Entity::kill);
            context.getSource().sendSuccess(() -> Component.translatable("message.companiondog.quietly_removed", companions), true);
            var others = targets.stream().filter(entity -> !(entity instanceof CompanionDogEntity)).toList();
            if (!others.isEmpty()) context.getSource().sendSuccess(() -> others.size() == 1
                    ? Component.translatable("commands.kill.success.single", others.getFirst().getDisplayName())
                    : Component.translatable("commands.kill.success.multiple", others.size()), true);
            return targets.size();
        });
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("companiondog")
                .then(Commands.literal("home").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    return CompanionDogBindings.get(player.getServer()).setHome(player) ? 1 : 0;
                }))
                .then(Commands.literal("restore").executes(context -> {
                    context.getSource().sendSuccess(() -> Component.translatable("message.companiondog.restore_confirm"), false);
                    return 0;
                }).then(Commands.literal("confirm").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    CompanionDogBindings.get(player.getServer()).requestRestore(player);
                    return 1;
                })))
                .then(Commands.literal("archive").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("target", EntityArgument.entity()).then(Commands.literal("confirm").executes(context -> {
                            var target = EntityArgument.getEntity(context, "target");
                            var server = context.getSource().getServer();
                            CompanionDogBindings bindings = CompanionDogBindings.get(server);
                            CompanionDogEntity dog = target instanceof CompanionDogEntity companion ? companion
                                    : target instanceof ServerPlayer owner ? bindings.findLoaded(server, owner.getUUID()) : null;
                            if (dog == null) return 0;
                            bindings.checkpoint(dog);
                            dog.discard();
                            context.getSource().sendSuccess(() -> Component.translatable("message.companiondog.archived"), false);
                            return 1;
                        })))));
    }
}
