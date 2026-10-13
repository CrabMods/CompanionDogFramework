package com.crabmods.companiondog.network;

import com.crabmods.companiondog.CompanionDogMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the owner picked something in CompanionDog's interaction menu. */
public record CompanionDogCommandPayload(int entityId, int command) implements CustomPacketPayload {
    public static final Type<CompanionDogCommandPayload> TYPE = new Type<>(CompanionDogMod.id("command"));
    public static final StreamCodec<ByteBuf, CompanionDogCommandPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CompanionDogCommandPayload::entityId,
            ByteBufCodecs.VAR_INT, CompanionDogCommandPayload::command,
            CompanionDogCommandPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
