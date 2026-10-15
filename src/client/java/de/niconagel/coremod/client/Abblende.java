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

/**
 * Schwarzblende am Ende einer Fahrt: abblenden, schwarz bleiben, bis der Server den Spieler ins Menü geholt hat und das
 * Hauptmenü offen ist, dann aufblenden. In Echtzeit, damit es unabhängig von Ticks und FPS gleichmäßig wirkt.
 */
@EventBusSubscriber(modid = CoremodClient.MOD_ID, value = Dist.CLIENT)
public final class Abblende {

    /** Wie FADE in MainMenu auf dem Server: so lange, bis der Wechsel passiert. */
    private static final long FADE_IN = 1500;
    private static final long FADE_OUT = 1500;
    /** Schwarz, nachdem das Menü offen ist: die Welt an der View braucht einen Moment zum Laden. */
    private static final long SETTLE = 400;
    /** Kommt das Menü nicht (z.B. Server hängt), wird trotzdem wieder aufgeblendet. */
    private static final long MAX_BLACK = 10_000;

    /** Beginn des Abblendens, -1 = keine Blende. */
    private static long start = -1;
    /** Beginn des Aufblendens, -1 = noch nicht bekannt. */
    private static long fadeOutAt = -1;

    private Abblende() {
    }

    static void start() {
        start = Util.getMillis();
        fadeOutAt = -1;
    }

    /** 0 = durchsichtig, 1 = schwarz. */
    private static float alpha() {
        if (start < 0) return 0;
        long now = Util.getMillis();

        if (fadeOutAt < 0) {
            boolean menuOpen = Minecraft.getInstance().screen instanceof MainMenuScreen && now - start >= FADE_IN;
            if (menuOpen || now - start > MAX_BLACK) fadeOutAt = now + SETTLE;
            return Math.min(1, (now - start) / (float) FADE_IN);
        }
        if (now < fadeOutAt) return 1;

        float alpha = 1 - (now - fadeOutAt) / (float) FADE_OUT;
        if (alpha <= 0) start = -1;
        return Math.max(0, alpha);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        start = -1;
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
