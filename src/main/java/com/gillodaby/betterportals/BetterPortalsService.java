package com.gillodaby.betterportals;

import com.hypixel.hytale.builtin.adventure.teleporter.component.Teleporter;
import com.hypixel.hytale.builtin.adventure.teleporter.system.CreateWarpWhenTeleporterPlacedSystem;
import com.hypixel.hytale.builtin.teleport.TeleportPlugin;
import com.hypixel.hytale.builtin.teleport.Warp;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class BetterPortalsService {

    private final BetterPortalsConfig config;
    private final Map<String, PortalLink> linksByName = new HashMap<>();
    private final Map<SourceKey, String> sourceToName = new HashMap<>();
    private final Map<UUID, String> pendingLink = new HashMap<>();

    BetterPortalsService(Path dataDir) {
        this.config = BetterPortalsConfig.load(dataDir);
        rebuildCache();
    }

    Message text(String raw) {
        return Message.raw(raw);
    }

    void save() {
        config.save();
    }

    void reload() {
        BetterPortalsConfig fresh = BetterPortalsConfig.load(config.dataDir());
        config.links().clear();
        config.links().addAll(fresh.links());
        config.sources().clear();
        config.sources().addAll(fresh.sources());
        rebuildCache();
        syncLinksFromWarps();
    }

    boolean setTarget(Player player, String name) {
        if (player == null || name == null || name.isEmpty()) {
            return false;
        }
        if (player.getTransformComponent() == null || player.getTransformComponent().getPosition() == null) {
            return false;
        }
        String worldName = safeWorld(player);
        double x = player.getTransformComponent().getPosition().getX();
        double y = player.getTransformComponent().getPosition().getY();
        double z = player.getTransformComponent().getPosition().getZ();
        float yaw = player.getTransformComponent().getRotation() != null
                ? player.getTransformComponent().getRotation().getX()
                : 0f;
        float pitch = player.getTransformComponent().getRotation() != null
                ? player.getTransformComponent().getRotation().getY()
                : 0f;
        float roll = player.getTransformComponent().getRotation() != null
                ? player.getTransformComponent().getRotation().getZ()
                : 0f;

        PortalLink link = new PortalLink(name, worldName, x, y, z, yaw, pitch, roll);
        upsertLink(link);
        config.save();
        return true;
    }

    boolean beginLink(Player player, String name) {
        if (player == null || name == null || name.isEmpty()) {
            return false;
        }
        PortalLink link = linksByName.get(normalize(name));
        if (link == null) {
            return false;
        }
        pendingLink.put(player.getUuid(), link.name());
        return true;
    }

    boolean cancelLink(Player player) {
        if (player == null) {
            return false;
        }
        return pendingLink.remove(player.getUuid()) != null;
    }

    String pendingLinkName(Player player) {
        if (player == null) {
            return null;
        }
        return pendingLink.get(player.getUuid());
    }

    boolean bindSource(Player player, Vector3i pos, String worldName, String linkName) {
        if (player == null || pos == null || worldName == null || linkName == null) {
            return false;
        }
        return bindSourceInternal(worldName, pos, linkName, true);
    }

    boolean handleTeleporterBreak(String worldName, Vector3i pos) {
        if (worldName == null || pos == null) {
            return false;
        }
        World world = resolveWorld(worldName);
        if (world == null || world.getChunkStore() == null) {
            return false;
        }
        ChunkStore chunkStore = world.getChunkStore();
        Ref<ChunkStore> blockRef = resolveBlockRef(chunkStore, pos);
        if (blockRef == null || !blockRef.isValid()) {
            return false;
        }
        Teleporter teleporter = chunkStore.getStore().getComponent(blockRef, Teleporter.getComponentType());
        if (teleporter == null) {
            return false;
        }

        String ownedWarp = teleporter.getOwnedWarp();
        if (ownedWarp != null && !ownedWarp.isEmpty()) {
            Map<String, Warp> warps = getWarps();
            if (warps != null) {
                String key = ownedWarp.toLowerCase(Locale.ROOT);
                Warp warp = warps.get(key);
                if (warp != null && "*Teleporter".equalsIgnoreCase(warp.getCreator())) {
                    warps.remove(key);
                    TeleportPlugin.get().saveWarps();
                }
            }
        }

        sourceToName.remove(new SourceKey(worldName, pos.x, pos.y, pos.z));
        rebuildConfigSources();
        config.save();
        syncLinksFromWarps();
        return true;
    }

    boolean applyTeleporterComponent(String sourceWorldName, Vector3i pos, String linkName) {
        if (sourceWorldName == null || pos == null || linkName == null) {
            return false;
        }
        PortalLink link = linksByName.get(normalize(linkName));
        if (link == null) {
            return false;
        }
        World sourceWorld = resolveWorld(sourceWorldName);
        World targetWorld = resolveWorld(link.world());
        if (sourceWorld == null || targetWorld == null) {
            return false;
        }
        ChunkStore chunkStore = sourceWorld.getChunkStore();
        if (chunkStore == null) {
            return false;
        }
        Ref<ChunkStore> blockRef = resolveBlockRef(chunkStore, pos);
        if (blockRef == null || !blockRef.isValid()) {
            return false;
        }
        Teleporter teleporter = chunkStore.getStore().getComponent(blockRef, Teleporter.getComponentType());
        if (teleporter == null) {
            return false;
        }

        UUID worldUuid = targetWorld.getWorldConfig() != null
                ? targetWorld.getWorldConfig().getUuid()
                : null;
        Transform transform = new Transform(
                new Vector3d(link.x(), link.y(), link.z()),
                new Vector3f(link.yaw(), link.pitch(), link.roll())
        );

        boolean updated = true;
        updated &= setField(teleporter, "worldUuid", worldUuid);
        updated &= setField(teleporter, "transform", transform);
        updated &= setField(teleporter, "relativeMask", (byte) 0);
        updated &= setField(teleporter, "warp", null);
        return updated;
    }

    boolean updateFromTeleporterWarp(String sourceWorldName, Vector3i pos) {
        if (sourceWorldName == null || pos == null) {
            return false;
        }
        World sourceWorld = resolveWorld(sourceWorldName);
        if (sourceWorld == null || sourceWorld.getChunkStore() == null) {
            return false;
        }
        ChunkStore chunkStore = sourceWorld.getChunkStore();
        Ref<ChunkStore> blockRef = resolveBlockRef(chunkStore, pos);
        if (blockRef == null || !blockRef.isValid()) {
            return false;
        }
        Teleporter teleporter = chunkStore.getStore().getComponent(blockRef, Teleporter.getComponentType());
        if (teleporter == null) {
            return false;
        }
        String ownedWarp = teleporter.getOwnedWarp() == null ? "" : teleporter.getOwnedWarp().trim();
        if (!ownedWarp.isEmpty()) {
            ensureOwnedWarpExists(chunkStore, blockRef, ownedWarp);
        }
        syncLinksFromWarps();
        String warp = teleporter.getWarp() == null ? "" : teleporter.getWarp().trim();
        if (warp.isEmpty()) {
            return false;
        }
        Warp warpEntry = findWarp(warp);
        if (warpEntry == null) {
            return false;
        }
        return bindSourceInternal(sourceWorldName, pos, warpEntry.getId(), true);
    }

    PortalLink findLinkForSource(String worldName, Vector3i pos) {
        if (worldName == null || pos == null) {
            return null;
        }
        SourceKey key = new SourceKey(worldName, pos.x, pos.y, pos.z);
        String name = sourceToName.get(key);
        if (name == null) {
            return null;
        }
        return linksByName.get(normalize(name));
    }

    boolean removeLink(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        PortalLink removed = linksByName.remove(normalize(name));
        if (removed == null) {
            return false;
        }
        config.links().removeIf(link -> normalize(link.name()).equals(normalize(name)));
        sourceToName.entrySet().removeIf(entry -> normalize(entry.getValue()).equals(normalize(name)));
        rebuildConfigSources();
        config.save();
        return true;
    }

    Collection<PortalLink> listLinks() {
        return new ArrayList<>(linksByName.values());
    }

    int syncLinksFromWarps() {
        Map<String, Warp> warps = getWarps();
        if (warps == null) {
            return 0;
        }
        linksByName.clear();
        config.links().clear();
        int count = 0;
        for (Warp warp : warps.values()) {
            PortalLink link = buildLinkFromWarp(warp);
            if (link == null) {
                continue;
            }
            linksByName.put(normalize(link.name()), link);
            config.links().add(link);
            count++;
        }

        sourceToName.entrySet().removeIf(entry -> !linksByName.containsKey(normalize(entry.getValue())));
        rebuildConfigSources();
        config.save();
        return count;
    }

    World resolveWorld(String worldName) {
        if (worldName == null || worldName.isEmpty()) {
            return null;
        }
        Universe universe = Universe.get();
        if (universe == null || universe.getWorlds() == null) {
            return null;
        }
        for (World world : universe.getWorlds().values()) {
            if (world == null || world.getName() == null) {
                continue;
            }
            if (world.getName().equalsIgnoreCase(worldName)) {
                return world;
            }
        }
        return null;
    }

    boolean isTeleporterBlock(String blockId) {
        if (blockId == null) {
            return false;
        }
        String lower = blockId.toLowerCase(Locale.ROOT);
        return lower.contains("teleporter");
    }

    private void upsertLink(PortalLink link) {
        if (link == null) {
            return;
        }
        String key = normalize(link.name());
        linksByName.put(key, link);
        config.links().removeIf(existing -> normalize(existing.name()).equals(key));
        config.links().add(link);
    }

    private void rebuildCache() {
        linksByName.clear();
        sourceToName.clear();
        for (PortalLink link : config.links()) {
            if (link == null) {
                continue;
            }
            linksByName.put(normalize(link.name()), link);
        }
        for (PortalSource source : config.sources()) {
            if (source == null) {
                continue;
            }
            sourceToName.put(new SourceKey(source.world(), source.x(), source.y(), source.z()), source.name());
        }
    }

    private void rebuildConfigSources() {
        config.sources().clear();
        for (Map.Entry<SourceKey, String> entry : sourceToName.entrySet()) {
            SourceKey key = entry.getKey();
            config.sources().add(new PortalSource(key.world, key.x, key.y, key.z, entry.getValue()));
        }
    }

    private boolean bindSourceInternal(String worldName, Vector3i pos, String linkName, boolean persist) {
        if (pos == null || worldName == null || linkName == null) {
            return false;
        }
        SourceKey key = new SourceKey(worldName, pos.x, pos.y, pos.z);
        String normalized = normalize(linkName);
        PortalLink link = linksByName.get(normalized);
        if (link == null) {
            return false;
        }
        sourceToName.put(key, link.name());
        if (persist) {
            rebuildConfigSources();
            config.save();
        }
        return true;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private Ref<ChunkStore> resolveBlockRef(ChunkStore chunkStore, Vector3i pos) {
        if (chunkStore == null || pos == null) {
            return null;
        }
        try {
            Method method = chunkStore.getClass().getMethod("getBlockReference", Vector3i.class);
            Object ref = method.invoke(chunkStore, pos);
            if (ref instanceof Ref<?>) {
                @SuppressWarnings("unchecked")
                Ref<ChunkStore> cast = (Ref<ChunkStore>) ref;
                return cast;
            }
        } catch (Throwable ignored) {
        }
        try {
            Method method = chunkStore.getClass().getMethod("getBlockReference", int.class, int.class, int.class);
            Object ref = method.invoke(chunkStore, pos.x, pos.y, pos.z);
            if (ref instanceof Ref<?>) {
                @SuppressWarnings("unchecked")
                Ref<ChunkStore> cast = (Ref<ChunkStore>) ref;
                return cast;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private boolean setField(Object target, String fieldName, Object value) {
        if (target == null || fieldName == null) {
            return false;
        }
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void ensureOwnedWarpExists(ChunkStore chunkStore, Ref<ChunkStore> blockRef, String ownedWarp) {
        if (chunkStore == null || blockRef == null || ownedWarp == null || ownedWarp.isEmpty()) {
            return;
        }
        Map<String, Warp> warps = getWarps();
        if (warps == null || warps.containsKey(ownedWarp.toLowerCase(Locale.ROOT))) {
            return;
        }
        BlockModule.BlockStateInfo blockState = chunkStore.getStore()
                .getComponent(blockRef, BlockModule.BlockStateInfo.getComponentType());
        if (blockState == null) {
            return;
        }
        Ref<ChunkStore> chunkRef = blockState.getChunkRef();
        if (chunkRef == null || !chunkRef.isValid()) {
            return;
        }
        WorldChunk worldChunk = chunkStore.getStore().getComponent(chunkRef, WorldChunk.getComponentType());
        if (worldChunk == null) {
            return;
        }
        CreateWarpWhenTeleporterPlacedSystem.createWarp(worldChunk, blockState, ownedWarp);
    }

    private Warp findWarp(String warpName) {
        if (warpName == null || warpName.isEmpty()) {
            return null;
        }
        Map<String, Warp> warps = getWarps();
        if (warps == null) {
            return null;
        }
        return warps.get(warpName.toLowerCase(Locale.ROOT));
    }

    private Map<String, Warp> getWarps() {
        TeleportPlugin plugin = TeleportPlugin.get();
        if (plugin == null) {
            return null;
        }
        if (!plugin.isWarpsLoaded()) {
            plugin.loadWarps();
        }
        return plugin.getWarps();
    }

    private PortalLink buildLinkFromWarp(Warp warp) {
        if (warp == null || warp.getTransform() == null) {
            return null;
        }
        Transform transform = warp.getTransform();
        Vector3d pos = transform.getPosition();
        Vector3f rot = transform.getRotation();
        if (pos == null) {
            return null;
        }
        float yaw = rot != null ? rot.getYaw() : 0f;
        float pitch = rot != null ? rot.getPitch() : 0f;
        float roll = rot != null ? rot.getRoll() : 0f;
        return new PortalLink(warp.getId(), warp.getWorld(), pos.getX(), pos.getY(), pos.getZ(), yaw, pitch, roll);
    }

    private String safeWorld(Player player) {
        if (player == null || player.getWorld() == null || player.getWorld().getName() == null) {
            return "world";
        }
        return player.getWorld().getName();
    }

    private static final class SourceKey {
        private final String world;
        private final int x;
        private final int y;
        private final int z;

        private SourceKey(String world, int x, int y, int z) {
            this.world = world == null ? "world" : world.trim().toLowerCase(Locale.ROOT);
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public int hashCode() {
            int result = world.hashCode();
            result = 31 * result + x;
            result = 31 * result + y;
            result = 31 * result + z;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof SourceKey other)) return false;
            return x == other.x && y == other.y && z == other.z && world.equals(other.world);
        }
    }
}
