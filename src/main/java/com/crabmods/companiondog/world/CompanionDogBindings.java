package com.crabmods.companiondog.world;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import com.crabmods.companiondog.entity.CompanionDogEntity;
import com.crabmods.companiondog.item.CompanionDogCollarItem;
import com.crabmods.companiondog.registry.CompanionDogRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Remembers which CompanionDog belongs to which player so the collar never creates a second one. */
public final class CompanionDogBindings extends SavedData {
    private static final String NAME = "companiondog_bindings";
    private final Map<UUID, UUID> dogByOwner = new HashMap<>();
    private final Map<UUID, Location> locations = new HashMap<>();
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Map<UUID, GlobalPos> homes = new HashMap<>();
    private final Map<UUID, Long> epochs = new HashMap<>();
    private final Map<UUID, CompoundTag> backups = new HashMap<>();
    private final Map<UUID, Boolean> homeRequests = new HashMap<>();
    private static final TicketType<UUID> RECALL = TicketType.create("companiondog_recall", UUID::compareTo, 120);

    private record Location(ResourceKey<Level> dimension, BlockPos pos) {}
    private record Pending(Location location, int expires, boolean restore) {}

    public static CompanionDogBindings get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(CompanionDogBindings::new, CompanionDogBindings::load, null), NAME);
    }

    public static CompanionDogBindings load(CompoundTag tag, HolderLookup.Provider registries) {
        CompanionDogBindings bindings = new CompanionDogBindings();
        // Released identities keep an epoch even when there is no current companion.
        CompoundTag epochs = tag.getCompound("Epochs");
        for (String key : epochs.getAllKeys()) {
            try {
                bindings.epochs.put(UUID.fromString(key), epochs.getLong(key));
            } catch (IllegalArgumentException ignored) {
                // skip malformed entries
            }
        }
        CompoundTag owners = tag.getCompound("Owners");
        for (String key : owners.getAllKeys()) {
            try {
                bindings.dogByOwner.put(UUID.fromString(key), owners.getUUID(key));
                UUID owner = UUID.fromString(key);
                GlobalPos home = CompanionDogSafety.decode(tag.getCompound("Homes").getCompound(key));
                if (home != null) bindings.homes.put(owner, home);
                if (tag.getCompound("HomeRequests").contains(key)) {
                    bindings.homeRequests.put(owner, tag.getCompound("HomeRequests").getBoolean(key));
                }
                if (tag.getCompound("Backups").contains(key)) {
                    bindings.backups.put(owner, tag.getCompound("Backups").getCompound(key).copy());
                }
                CompoundTag location = tag.getCompound("Locations").getCompound(key);
                if (location.contains("Dimension")) {
                    bindings.locations.put(UUID.fromString(key), new Location(
                            ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(location.getString("Dimension"))),
                            BlockPos.of(location.getLong("Pos"))));
                }
            } catch (IllegalArgumentException ignored) {
                // skip malformed entries
            }
        }
        return bindings;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag owners = new CompoundTag();
        dogByOwner.forEach((owner, dog) -> owners.putUUID(owner.toString(), dog));
        tag.put("Owners", owners);
        CompoundTag savedLocations = new CompoundTag();
        locations.forEach((owner, location) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("Dimension", location.dimension.location().toString());
            entry.putLong("Pos", location.pos.asLong());
            savedLocations.put(owner.toString(), entry);
        });
        tag.put("Locations", savedLocations);
        CompoundTag savedHomes = new CompoundTag(), savedEpochs = new CompoundTag(), savedBackups = new CompoundTag();
        homes.forEach((owner, home) -> savedHomes.put(owner.toString(), CompanionDogSafety.encode(home)));
        epochs.forEach((owner, epoch) -> savedEpochs.putLong(owner.toString(), epoch));
        backups.forEach((owner, backup) -> savedBackups.put(owner.toString(), backup.copy()));
        tag.put("Homes", savedHomes);
        tag.put("Epochs", savedEpochs);
        tag.put("Backups", savedBackups);
        CompoundTag savedRequests = new CompoundTag();
        homeRequests.forEach((owner, danger) -> savedRequests.putBoolean(owner.toString(), danger));
        tag.put("HomeRequests", savedRequests);
        return tag;
    }

    public void requestOwnerHome(UUID owner, boolean danger) {
        if (!dogByOwner.containsKey(owner)) return;
        homeRequests.merge(owner, danger, (previous, next) -> previous || next);
        setDirty();
    }

    @Nullable
    public Boolean takeHomeRequest(UUID owner) {
        Boolean danger = homeRequests.remove(owner);
        if (danger != null) setDirty();
        return danger;
    }

    @Nullable
    public UUID dogOf(UUID owner) {
        return dogByOwner.get(owner);
    }

    public void bind(UUID owner, UUID dog) {
        if (dogByOwner.containsKey(owner) && !dog.equals(dogByOwner.get(owner))) {
            throw new IllegalStateException("Cannot replace a bound companion without explicit recovery");
        }
        dogByOwner.put(owner, dog);
        setDirty();
    }

    public void remember(CompanionDogEntity dog) {
        UUID owner = dog.getOwnerUUID();
        if (owner == null || !dog.isBound() || !dog.getUUID().equals(dogByOwner.get(owner))
                || dog.getBindingEpoch() != epochs.getOrDefault(owner, 0L)) return;
        Location next = new Location(dog.level().dimension(), dog.blockPosition());
        if (!next.equals(locations.put(owner, next))) setDirty();
        if (!homes.containsKey(owner) && dog.getHome() != null) {
            homes.put(owner, dog.getHome());
            setDirty();
        }
    }

    @Nullable public GlobalPos homeOf(UUID owner) { return homes.get(owner); }

    public boolean acceptIdentity(CompanionDogEntity dog) {
        if (!dog.isBound()) return true;
        UUID owner = dog.getOwnerUUID();
        if (owner == null) return false;
        if (dog.getBindingEpoch() != epochOf(owner)) return false;
        if (!dogByOwner.containsKey(owner)) bind(owner, dog.getUUID());
        return dog.getUUID().equals(dogByOwner.get(owner))
                && dog.getBindingEpoch() == epochs.getOrDefault(owner, 0L);
    }

    public long epochOf(UUID owner) { return epochs.getOrDefault(owner, 0L); }

    /** An explicit vanilla /kill is removal, not accidental loss or a death event. */
    public void release(CompanionDogEntity dog) {
        UUID owner = dog.getOwnerUUID();
        if (!dog.isBound() || owner == null || !dog.getUUID().equals(dogByOwner.get(owner))
                || dog.getBindingEpoch() != epochOf(owner)) return;
        Pending request = pending.remove(owner);
        if (request != null) {
            ServerLevel level = dog.getServer().getLevel(request.location.dimension);
            if (level != null) level.getChunkSource().removeRegionTicket(RECALL, new ChunkPos(request.location.pos), 2, owner);
        }
        dogByOwner.remove(owner);
        locations.remove(owner);
        homes.remove(owner);
        backups.remove(owner);
        homeRequests.remove(owner);
        epochs.put(owner, epochOf(owner) + 1);
        setDirty();
    }

    public void checkpoint(CompanionDogEntity dog) {
        if (!dog.isBound() || !acceptIdentity(dog)) return;
        remember(dog);
        CompoundTag snapshot = new CompoundTag();
        dog.saveWithoutId(snapshot);
        // Explicit disaster recovery restores identity and appearance, never old inventory.
        snapshot.remove("Mouth");
        snapshot.remove("Leash");
        snapshot.remove("Passengers");
        snapshot.remove("HandItems");
        snapshot.remove("ArmorItems");
        backups.put(dog.getOwnerUUID(), snapshot);
        setDirty();
    }

    @Nullable
    public CompanionDogEntity findLoaded(MinecraftServer server, UUID owner) {
        UUID id = dogByOwner.get(owner);
        if (id == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof CompanionDogEntity dog && !dog.isRemoved() && acceptIdentity(dog)) return dog;
        }
        return null;
    }

    public boolean setHome(ServerPlayer owner) {
        if (dogOf(owner.getUUID()) == null) {
            owner.displayClientMessage(Component.translatable("message.companiondog.no_companion"), true);
            return false;
        }
        CompanionDogEntity dog = findLoaded(owner.getServer(), owner.getUUID());
        CompanionDogEntity probe = dog != null ? dog : CompanionDogRegistries.COMPANION_DOG.get().create(owner.serverLevel());
        if (probe == null || !probe.setHomeFrom(owner)) return false;
        homes.put(owner.getUUID(), probe.getHome());
        if (dog != null) checkpoint(dog);
        setDirty();
        if (dog == null) requestRecall(owner);
        return true;
    }

    public void requestRecall(ServerPlayer player) {
        request(player, false);
    }

    public void cancelRecall(ServerPlayer player) {
        Pending request = pending.remove(player.getUUID());
        if (request == null) return;
        ServerLevel level = player.getServer().getLevel(request.location.dimension);
        if (level != null) level.getChunkSource().removeRegionTicket(
                RECALL, new ChunkPos(request.location.pos), 2, player.getUUID());
    }

    public void requestRestore(ServerPlayer player) {
        if (findLoaded(player.getServer(), player.getUUID()) != null) {
            player.displayClientMessage(Component.translatable("message.companiondog.already_present"), true);
            return;
        }
        request(player, true);
    }

    private void request(ServerPlayer player, boolean restore) {
        UUID owner = player.getUUID();
        if (pending.containsKey(owner)) {
            player.displayClientMessage(Component.translatable("message.companiondog.searching"), true);
            return;
        }
        Location location = locations.get(owner);
        ServerLevel level = location == null ? null : player.getServer().getLevel(location.dimension);
        if (level == null) {
            player.displayClientMessage(Component.translatable("message.companiondog.unavailable"), true);
            return;
        }
        level.getChunkSource().addRegionTicket(RECALL, new ChunkPos(location.pos), 2, owner);
        pending.put(owner, new Pending(location, player.getServer().getTickCount() + 100, restore));
        player.displayClientMessage(Component.translatable("message.companiondog.searching"), true);
    }

    /** Wait for entity storage to finish loading before recalling the original UUID. */
    public void tick(MinecraftServer server) {
        // A callback may enqueue a different request; never mutate an active map iterator.
        for (var entry : java.util.List.copyOf(pending.entrySet())) {
            UUID owner = entry.getKey();
            Pending request = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(owner);
            ServerLevel level = server.getLevel(request.location.dimension);
            CompanionDogEntity foundDog = findLoaded(server, owner);
            boolean found = foundDog != null;
            boolean expired = server.getTickCount() >= request.expires;
            boolean unavailable = player == null || !player.isAlive() || player.isSpectator();
            if (found || expired || unavailable) {
                pending.remove(owner);
                if (found && !unavailable) {
                    if (request.restore) player.displayClientMessage(Component.translatable("message.companiondog.already_present"), true);
                    else CompanionDogCollarItem.summon(player);
                } else if (!unavailable) {
                    if (!(request.restore && level != null && level.areEntitiesLoaded(new ChunkPos(request.location.pos).toLong())
                            && restoreMissing(player))) {
                        player.displayClientMessage(Component.translatable("message.companiondog.unavailable"), true);
                    }
                }
                if (level != null) level.getChunkSource().removeRegionTicket(
                        RECALL, new ChunkPos(request.location.pos), 2, owner);
            }
        }
    }

    private boolean restoreMissing(ServerPlayer owner) {
        CompoundTag snapshot = backups.get(owner.getUUID());
        if (snapshot == null || findLoaded(owner.getServer(), owner.getUUID()) != null) return false;
        CompanionDogEntity dog = CompanionDogRegistries.COMPANION_DOG.get().create(owner.serverLevel());
        if (dog == null) return false;
        dog.load(snapshot.copy());
        dog.setUUID(dogByOwner.get(owner.getUUID()));
        dog.setMouthItem(net.minecraft.world.item.ItemStack.EMPTY);
        dog.setHealth(dog.getMaxHealth());
        long previous = epochs.getOrDefault(owner.getUUID(), 0L);
        epochs.put(owner.getUUID(), previous + 1);
        dog.setBindingEpoch(previous + 1);
        dog.moveTo(owner.getX(), owner.getY(), owner.getZ());
        if (homes.containsKey(owner.getUUID())) dog.setHome(homes.get(owner.getUUID()));
        if (!owner.serverLevel().addFreshEntity(dog)) {
            epochs.put(owner.getUUID(), previous);
            return false;
        }
        dog.requestHome(true);
        checkpoint(dog);
        owner.displayClientMessage(Component.translatable("message.companiondog.restored"), true);
        return true;
    }
}
