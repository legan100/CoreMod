package de.niconagel.coremod.listener;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Entfernt alle 10 Minuten herumliegende Items, mit Vorwarnung eine Minute vorher. */
@EventBusSubscriber
public final class ItemClear {

    private static final int INTERVAL = 20 * 60 * 10;
    private static final int WARNING = 20 * 60;

    private ItemClear() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int tick = server.getTickCount() % INTERVAL;

        if (tick == INTERVAL - WARNING) {
            broadcast(server, "[TFBW] Herumliegende Items werden in 60 Sekunden entfernt.");
        } else if (tick == 0) {
            int count = 0;
            for (ServerLevel level : server.getAllLevels()) {
                // getEntities liefert eine Kopie, discard() während der Schleife ist also sicher
                for (ItemEntity item : level.getEntities(EntityTypeTest.forClass(ItemEntity.class), item -> true)) {
                    item.discard();
                    count++;
                }
            }
            broadcast(server, count + " herumliegende Items wurden entfernt.");
        }
    }

    private static void broadcast(MinecraftServer server, String message) {
        server.getPlayerList().broadcastSystemMessage(
                Component.literal(message).withStyle(ChatFormatting.YELLOW), false);
    }
}
