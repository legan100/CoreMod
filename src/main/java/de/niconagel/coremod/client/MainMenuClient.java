package de.niconagel.coremod.client;

import de.niconagel.coremod.Coremod;
import de.niconagel.coremod.mainmenu.MainMenuNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Hält den Client im Hauptmenü, bis "Spielen" gedrückt wird. Öffnet das Menü erst nach dem Ladebildschirm
 * und öffnet es wieder, falls etwas anderes es geschlossen hat (z.B. ein Dimensionswechsel).
 */
@EventBusSubscriber(modid = Coremod.MOD_ID, value = Dist.CLIENT)
public final class MainMenuClient {

    private static boolean inMenu;
    private static boolean introShown;
    /** F1-Zustand vor dem Menü; im Menü sind HUD, Hand und Block-Umrandung ausgeblendet. */
    private static boolean previousHideGui;

    private MainMenuClient() {
    }

    /** Vom Server ausgelöst, beim Join. */
    public static void open() {
        if (!inMenu) {
            previousHideGui = Minecraft.getInstance().options.hideGui;
        }
        inMenu = true;
        introShown = false;
    }

    static void play() {
        leave();
        PacketDistributor.sendToServer(new MainMenuNetwork.Play());
        Minecraft.getInstance().setScreen(null);
    }

    static void quit() {
        Minecraft minecraft = Minecraft.getInstance();
        leave();
        if (minecraft.level != null) {
            minecraft.level.disconnect();
        }
        minecraft.disconnect();
        minecraft.stop();
    }

    private static void leave() {
        inMenu = false;
        Minecraft.getInstance().options.hideGui = previousHideGui;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!inMenu) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            // Verbindung getrennt
            leave();
            return;
        }

        minecraft.options.hideGui = true;

        if (minecraft.screen instanceof MainMenuScreen
                || minecraft.screen instanceof ServerSettingsScreen
                || minecraft.screen instanceof ReceivingLevelScreen) {
            return;
        }

        minecraft.setScreen(new MainMenuScreen(!introShown));
        introShown = true;
    }
}
