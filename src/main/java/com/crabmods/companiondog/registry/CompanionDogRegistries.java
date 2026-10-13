package com.crabmods.companiondog.registry;

import com.crabmods.companiondog.CompanionDogMod;
import com.crabmods.companiondog.entity.CompanionDogEntity;
import com.crabmods.companiondog.entity.TennisBallEntity;
import com.crabmods.companiondog.entity.FrisbeeEntity;
import com.crabmods.companiondog.item.FrisbeeItem;
import com.crabmods.companiondog.item.CompanionDogCollarItem;
import com.crabmods.companiondog.item.TennisBallItem;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class CompanionDogRegistries {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, CompanionDogMod.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CompanionDogMod.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CompanionDogMod.MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, CompanionDogMod.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<CompanionDogEntity>> COMPANION_DOG = ENTITY_TYPES.register("companiondog",
            () -> EntityType.Builder.of(CompanionDogEntity::new, MobCategory.CREATURE)
                    .sized(0.55F, 0.8F)
                    .eyeHeight(0.68F)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .build("companiondog"));

    public static final DeferredHolder<EntityType<?>, EntityType<TennisBallEntity>> TENNIS_BALL_ENTITY =
            ENTITY_TYPES.register("tennis_ball", () -> EntityType.Builder.<TennisBallEntity>of(TennisBallEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(6)
                    .updateInterval(2)
                    .build("tennis_ball"));

    public static final DeferredHolder<EntityType<?>, EntityType<FrisbeeEntity>> FRISBEE_ENTITY =
            ENTITY_TYPES.register("frisbee", () -> EntityType.Builder.<FrisbeeEntity>of(FrisbeeEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.12F).clientTrackingRange(6).updateInterval(1).build("frisbee"));

    public static final DeferredItem<FrisbeeItem> FRISBEE = ITEMS.register("frisbee",
            () -> new FrisbeeItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<CompanionDogCollarItem> COMPANION_DOG_COLLAR = ITEMS.register("companiondog_collar",
            () -> new CompanionDogCollarItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<TennisBallItem> TENNIS_BALL = ITEMS.register("tennis_ball",
            () -> new TennisBallItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<DeferredSpawnEggItem> COMPANION_DOG_SPAWN_EGG = ITEMS.register("companiondog_spawn_egg",
            () -> new DeferredSpawnEggItem(COMPANION_DOG, 0x737B86, 0xF3F4F6, new Item.Properties()));

    public static final Supplier<AttachmentType<Boolean>> RECEIVED_COLLAR = ATTACHMENTS.register("received_collar",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).copyOnDeath().build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("companiondog", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.companiondog"))
            .icon(() -> new ItemStack(COMPANION_DOG_COLLAR.get()))
            .displayItems((parameters, output) -> {
                output.accept(COMPANION_DOG_COLLAR.get());
                output.accept(TENNIS_BALL.get());
                output.accept(FRISBEE.get());
                output.accept(COMPANION_DOG_SPAWN_EGG.get());
            })
            .build());

    private CompanionDogRegistries() {
    }

    public static boolean isFetchToy(ItemStack stack) {
        return stack.is(TENNIS_BALL.get()) || stack.is(FRISBEE.get());
    }

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);
        ATTACHMENTS.register(modBus);
    }

    public static void onEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(COMPANION_DOG.get(), CompanionDogEntity.createAttributes().build());
    }
}
