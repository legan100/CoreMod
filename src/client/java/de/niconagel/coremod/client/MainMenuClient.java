package de.niconagel.coremod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
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
    /** Die Job-Wahl ist beim Server, das Menü zu. */
    private static boolean playSent;
    /** Der Server hat den Start bestätigt. */
    private static boolean started;
    /** Vom Server: welche Jobs die Rolle wählen darf. */
    static List<CoremodClient.JobEntry> jobs = List.of();
    /** Vom Server: Strecken und Routen für Triebfahrzeugführer. */
    static List<CoremodClient.Strecke> strecken = List.of();
    /** Vom Server: Garnituren und MR-Fahrzeuge für die Routendetails. */
    static Zugdaten zugdaten = Zugdaten.NONE;
    /** Vom Server: Abstellungen, für die Routendetails. */
    static List<CoremodClient.Abstellung> abstellungen = List.of();

    private MainMenuClient() {
    }

    /**
     * Vom Server ausgelöst: beim Join, noch einmal, wenn die Rolle geladen ist (dann ändern sich ggf. die Jobs), und wenn
     * ein Start fehlgeschlagen ist (dann ohne Intro, das gibt es nur einmal pro Verbindung).
     */
    public static void open(List<CoremodClient.JobEntry> availableJobs, List<CoremodClient.Strecke> availableStrecken, Zugdaten trains,
                            List<CoremodClient.Abstellung> availableAbstellungen) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!inMenu) {
            previousHideGui = minecraft.options.hideGui;
        }
        inMenu = true;
        jobs = availableJobs;
        strecken = availableStrecken;
        zugdaten = trains;
        abstellungen = availableAbstellungen;

        if (minecraft.screen instanceof MainMenuScreen screen) {
            // Knöpfe neu aufbauen, ohne das Intro neu zu starten
            screen.resize(minecraft, screen.width, screen.height);
        }
    }

    /**
     * Job gewählt: abblenden, erst bei Schwarz die Wahl schicken und das Menü schließen. Aufgeblendet wird, wenn der
     * Server den Start bestätigt und die Welt am Ziel gebaut ist, oder wenn er das Menü zurückschickt (Start fehlgeschlagen).
     *
     * @param route Routennummer beim Triebfahrzeugführer, sonst ""
     */
    static void play(CoremodClient.JobEntry job, String route) {
        // Doppelklick oder zweiter Job während des Abblendens
        if (Abblende.active()) return;
        playSent = false;
        started = false;
        Abblende.toWorld(() -> {
            leave();
            PacketDistributor.sendToServer(new CoremodClient.Play(job.id(), route));
            Minecraft.getInstance().setScreen(null);
            playSent = true;
        }, () -> playSent && (started && worldReady() || inMenu && Minecraft.getInstance().screen instanceof MainMenuScreen));
    }

    /** Vom Server: der Spieler ist am Ziel (Teleport und ggf. Zug sind durch). */
    static void started() {
        started = true;
    }

    /** Ist der Abschnitt der Welt gebaut, in dem der Spieler steht? Vorher sähe man nach dem Teleport ins Leere. */
    private static boolean worldReady() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.levelRenderer.isSectionCompiled(minecraft.player.blockPosition());
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
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        introShown = false;
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
