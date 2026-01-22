package com.gillodaby.betterportals;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

final class BetterPortalsUseSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Post> {

    private final BetterPortalsService service;

    BetterPortalsUseSystem(BetterPortalsService service) {
        super(UseBlockEvent.Post.class);
        this.service = service;
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
                @Nonnull UseBlockEvent.Post event
    ) {
        BlockType blockType = event.getBlockType();
        if (blockType == null || !service.isTeleporterBlock(blockType.getId())) {
            return;
        }

        Vector3i pos = event.getTargetBlock();
        if (pos == null) {
            return;
        }

        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        if (ref == null) {
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        String worldName = player.getWorld() != null ? player.getWorld().getName() : "world";

        boolean updatedFromWarp = service.updateFromTeleporterWarp(worldName, pos);
        if (updatedFromWarp) {
            player.sendMessage(service.text("Teleporter linked via Warp Name."));
        }

        String pending = service.pendingLinkName(player);
        if (pending != null) {
            boolean bound = service.bindSource(player, pos, worldName, pending);
            boolean applied = bound && service.applyTeleporterComponent(worldName, pos, pending);
            if (bound) {
                service.cancelLink(player);
                if (applied) {
                    player.sendMessage(service.text("Teleporter bound to '" + pending + "'."));
                } else {
                    player.sendMessage(service.text("Teleporter bound, but portal UI/collision may not update."));
                }
            } else {
                player.sendMessage(service.text("Failed to bind teleporter. Check the portal name."));
            }
            return;
        }

        PortalLink link = service.findLinkForSource(worldName, pos);
        if (link == null) {
            return;
        }

        World targetWorld = service.resolveWorld(link.world());
        if (targetWorld == null) {
            player.sendMessage(service.text("Target world not found: " + link.world()));
            return;
        }

        Vector3d targetPos = new Vector3d(link.x(), link.y(), link.z());
        Vector3f targetRot = new Vector3f(link.yaw(), link.pitch(), link.roll());
        commandBuffer.addComponent(ref, Teleport.getComponentType(), new Teleport(targetWorld, targetPos, targetRot));
        player.sendMessage(service.text("Teleported via BetterPortals."));
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }
}
