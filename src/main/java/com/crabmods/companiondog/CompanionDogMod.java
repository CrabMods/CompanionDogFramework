package com.crabmods.companiondog;

import com.crabmods.companiondog.network.CompanionDogNetwork;
import com.crabmods.companiondog.registry.CompanionDogRegistries;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(CompanionDogMod.MOD_ID)
public final class CompanionDogMod {
    public static final String MOD_ID = "companiondog";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CompanionDogMod(IEventBus modBus, ModContainer container) {
        CompanionDogRegistries.register(modBus);
        modBus.addListener(CompanionDogRegistries::onEntityAttributes);
        modBus.addListener(CompanionDogNetwork::register);
        NeoForge.EVENT_BUS.register(CompanionDogGameEvents.class);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
