package de.niconagel.coremod.listener;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Entfernt alle 10 Minuten still herumliegende Items. */
@EventBusSubscriber(value = Dist.DEDICATED_SERVER)
public final class ItemClear {

    private static final int INTERVAL = 20 * 60 * 10;

    private ItemClear() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % INTERVAL != 0) return;

        for (ServerLevel level : event.getServer().getAllLevels()) {
            // getEntities liefert eine Kopie, discard() während der Schleife ist also sicher
            level.getEntities(EntityTypeTest.forClass(ItemEntity.class), item -> true).forEach(ItemEntity::discard);
        }
    }
}
