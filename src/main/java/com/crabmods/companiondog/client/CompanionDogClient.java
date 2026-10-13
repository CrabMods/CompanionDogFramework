package com.crabmods.companiondog.client;

import com.crabmods.companiondog.CompanionDogMod;
import com.crabmods.companiondog.entity.CompanionDogEntity;
import com.crabmods.companiondog.registry.CompanionDogRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = CompanionDogMod.MOD_ID, dist = Dist.CLIENT)
public final class CompanionDogClient {
    public CompanionDogClient(IEventBus bus) {
        bus.addListener(this::renderers);
        CompanionDogEntity.openMenuHook = dog -> Minecraft.getInstance().setScreen(new CompanionDogInteractScreen(dog));
        NeoForge.EVENT_BUS.register(new CompanionDogArrivalFeedback());
    }

    private void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(CompanionDogRegistries.COMPANION_DOG.get(), CompanionDogRenderer::new);
        event.registerEntityRenderer(CompanionDogRegistries.TENNIS_BALL_ENTITY.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(CompanionDogRegistries.FRISBEE_ENTITY.get(), FrisbeeRenderer::new);
    }
}
