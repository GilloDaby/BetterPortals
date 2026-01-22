package com.gillodaby.betterportals;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

final class BetterPortalsBreakSystem extends com.hypixel.hytale.component.system.EntityEventSystem<EntityStore, BreakBlockEvent> {

    private final BetterPortalsService service;

    BetterPortalsBreakSystem(BetterPortalsService service) {
        super(BreakBlockEvent.class);
        this.service = service;
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull BreakBlockEvent event
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
        if (player == null || player.getWorld() == null) {
            return;
        }

        String worldName = player.getWorld().getName();
        service.handleTeleporterBreak(worldName, pos);
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }
}