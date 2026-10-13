package com.crabmods.companiondog.client;

import com.crabmods.companiondog.entity.CompanionDogEntity;
import com.crabmods.companiondog.network.CompanionDogArrivalPayload;
import com.crabmods.companiondog.network.CompanionDogNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Do not promise that CompanionDog is here until the owner has received a visible, nearby entity. */
public final class CompanionDogArrivalFeedback {
    private CompanionDogArrivalPayload pending;
    private int age, stable;

    public CompanionDogArrivalFeedback() {
        CompanionDogNetwork.arrivalHook = payload -> {
            pending = payload;
            age = stable = 0;
        };
    }

    @SubscribeEvent
    public void tick(ClientTickEvent.Post event) {
        if (pending == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            pending = null;
            return;
        }
        if (mc.level.getEntity(pending.entityId()) instanceof CompanionDogEntity dog
                && dog.getUUID().equals(pending.uuid()) && !dog.isInvisible()
                && !dog.isRecovering() && !dog.isRemoved() && dog.distanceToSqr(mc.player) < 36) {
            if (++stable >= 3) {
                mc.player.displayClientMessage(Component.translatable(pending.first()
                        ? "message.companiondog.arrived" : "message.companiondog.called"), true);
                pending = null;
                return;
            }
        } else stable = 0;
        if (++age >= 100) {
            mc.player.displayClientMessage(Component.translatable("message.companiondog.sync_pending"), true);
            pending = null;
        }
    }
}
