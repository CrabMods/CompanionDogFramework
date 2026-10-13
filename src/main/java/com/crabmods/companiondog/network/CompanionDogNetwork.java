package com.crabmods.companiondog.network;

import com.crabmods.companiondog.entity.CompanionDogCommand;
import com.crabmods.companiondog.entity.CompanionDogEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class CompanionDogNetwork {
    public static java.util.function.Consumer<CompanionDogArrivalPayload> arrivalHook = payload -> {};
    private CompanionDogNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("4").playToServer(CompanionDogCommandPayload.TYPE, CompanionDogCommandPayload.STREAM_CODEC,
                CompanionDogNetwork::handleCommand)
                .playToClient(CompanionDogArrivalPayload.TYPE, CompanionDogArrivalPayload.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> arrivalHook.accept(payload)));
    }

    private static void handleCommand(CompanionDogCommandPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            Entity entity = player.level().getEntity(payload.entityId());
            CompanionDogCommand command = CompanionDogCommand.byId(payload.command());
            if (entity instanceof CompanionDogEntity companiondog && command != null
                    && companiondog.distanceToSqr(player) < 16 * 16 && player.hasLineOfSight(companiondog)
                    && (companiondog.isOwnedBy(player) || companiondog.canManageUnowned(player))) {
                companiondog.applyCommand(command, player);
            }
        });
    }
}
