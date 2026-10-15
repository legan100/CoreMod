package de.niconagel.coremod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Hält den Client im Hauptmenü, bis "Spielen" gedrückt wird. Öffnet das Menü erst nach dem Ladebildschirm
 * und öffnet es wieder, falls etwas anderes es geschlossen hat (z.B. ein Dimensionswechsel).
 */
@EventBusSubscriber(modid = CoremodClient.MOD_ID, value = Dist.CLIENT)
public final class MainMenuClient {

    private static boolean inMenu;
    private static boolean introShown;
    /** F1-Zustand vor dem Menü; im Menü sind HUD, Hand und Block-Umrandung ausgeblendet. */
    private static boolean previousHideGui;
    /** Vom Server: welche Jobs die Rolle wählen darf. */
    static List<CoremodClient.JobEntry> jobs = List.of();

    private MainMenuClient() {
    }

    /** Vom Server ausgelöst: beim Join und noch einmal, wenn die Rolle geladen ist (dann ändern sich ggf. die Jobs). */
    public static void open(List<CoremodClient.JobEntry> availableJobs) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!inMenu) {
            previousHideGui = minecraft.options.hideGui;
            introShown = false;
        }
        inMenu = true;
        jobs = availableJobs;

        if (minecraft.screen instanceof MainMenuScreen screen) {
            // Knöpfe neu aufbauen, ohne das Intro neu zu starten
            screen.resize(minecraft, screen.width, screen.height);
        }
    }

    static void play(CoremodClient.JobEntry job) {
        leave();
        PacketDistributor.sendToServer(new CoremodClient.Play(job.id()));
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
