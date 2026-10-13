package com.crabmods.companiondog.network;

import com.crabmods.companiondog.CompanionDogMod;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Arrival feedback waits for the matching entity to exist on the owner's client. */
public record CompanionDogArrivalPayload(int entityId, UUID uuid, boolean first) implements CustomPacketPayload {
    public static final Type<CompanionDogArrivalPayload> TYPE = new Type<>(CompanionDogMod.id("arrival"));
    public static final StreamCodec<ByteBuf, CompanionDogArrivalPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CompanionDogArrivalPayload::entityId,
            UUIDUtil.STREAM_CODEC, CompanionDogArrivalPayload::uuid,
            ByteBufCodecs.BOOL, CompanionDogArrivalPayload::first, CompanionDogArrivalPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
