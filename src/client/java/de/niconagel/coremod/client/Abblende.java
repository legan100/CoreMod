package de.niconagel.coremod.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.function.BooleanSupplier;

/**
 * Schwarzblende für Wechsel zwischen Menü und Welt: abblenden, schwarz bleiben, bis das Ziel bereit ist, dann aufblenden.
 * In Echtzeit, damit es unabhängig von Ticks und FPS gleichmäßig wirkt.
 * <ul>
 *     <li>Job gewählt ({@link #toWorld}): erst bei Schwarz geht die Wahl an den Server.</li>
 *     <li>Fahrt zu Ende ({@link #toMenu}): bei Schwarz holt der Server den Spieler ins Menü.</li>
 * </ul>
 */
@EventBusSubscriber(modid = CoremodClient.MOD_ID, value = Dist.CLIENT)
public final class Abblende {

    /** Wie FADE in MainMenu auf dem Server: so lange, bis der Server den Spieler ins Menü holt. */
    private static final long TO_MENU = 1500;
    private static final long TO_WORLD = 800;
    private static final long FADE_OUT = 1500;
    /** Noch schwarz, nachdem das Ziel bereit ist: die Welt braucht einen Moment, bis sie gezeichnet ist. */
    private static final long SETTLE = 400;
    /** Wird das Ziel nicht bereit (z.B. Server hängt), wird trotzdem wieder aufgeblendet. */
    private static final long MAX_BLACK = 10_000;

    /** Beginn des Abblendens, -1 = keine Blende. */
    private static long start = -1;
    private static long fadeIn;
    /** Beginn des Aufblendens, -1 = noch nicht bekannt. */
    private static long fadeOutAt = -1;
    /** Läuft einmal, sobald es schwarz ist; null = schon gelaufen oder nichts zu tun. */
    private static Runnable whenBlack;
    private static BooleanSupplier ready = () -> true;

    private Abblende() {
    }

    /** Fahrt zu Ende: aufgeblendet wird, wenn das Hauptmenü offen ist. */
    static void toMenu() {
        start(TO_MENU, null, () -> Minecraft.getInstance().screen instanceof MainMenuScreen);
    }

    /** Job gewählt: {@code whenBlack} schickt die Wahl, {@code ready} sagt, wann die Welt zu sehen sein darf. */
    static void toWorld(Runnable whenBlack, BooleanSupplier ready) {
        start(TO_WORLD, whenBlack, ready);
    }

    private static void start(long duration, Runnable action, BooleanSupplier until) {
        start = Util.getMillis();
        fadeIn = duration;
        fadeOutAt = -1;
        whenBlack = action;
        ready = until;
    }

    /** Läuft gerade eine Blende? Dann nimmt das Menü keine weitere Wahl an. */
    static boolean active() {
        return start >= 0;
    }

    /** 0 = durchsichtig, 1 = schwarz. */
    private static float alpha() {
        if (start < 0) return 0;
        long now = Util.getMillis();

        if (fadeOutAt < 0) {
            float alpha = Math.min(1, (now - start) / (float) fadeIn);
            if (alpha >= 1 && whenBlack != null) {
                // Nicht mitten im Zeichnen Screens wechseln oder Pakete schicken
                Minecraft.getInstance().tell(whenBlack);
                whenBlack = null;
            }
            if (alpha >= 1 && whenBlack == null && ready.getAsBoolean() || now - start > MAX_BLACK) fadeOutAt = now + SETTLE;
            return alpha;
        }
        if (now < fadeOutAt) return 1;

        float alpha = 1 - (now - fadeOutAt) / (float) FADE_OUT;
        if (alpha <= 0) start = -1;
        return Math.max(0, alpha);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        start = -1;
        whenBlack = null;
    }

    /** Ohne Screen über der HUD … */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (Minecraft.getInstance().screen == null) draw(event.getGuiGraphics());
    }

    /** … mit Screen (das Hauptmenü) über dem Screen, sonst sähe man das Menü durch. */
    @SubscribeEvent
    public static void onRenderScreen(ScreenEvent.Render.Post event) {
        draw(event.getGuiGraphics());
    }

    private static void draw(GuiGraphics graphics) {
        float alpha = alpha();
        if (alpha <= 0) return;
        graphics.pose().pushPose();
        // Über Knöpfen und Tooltips
        graphics.pose().translate(0, 0, 500);
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (int) (alpha * 255) << 24);
        graphics.pose().popPose();
    }
}
