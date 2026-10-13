package de.niconagel.coremod.client;

import de.niconagel.coremod.utils.Tablist;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Hauptmenü: erst blendet der Schriftzug ein und wieder aus (Intro), dann erscheinen Titel und Knöpfe.
 * Im Hintergrund ist die Welt an der View zu sehen, der Server hat den Spieler dorthin gesetzt.
 */
public class MainMenuScreen extends Screen {

    // Intro in Ticks (20 = 1 Sekunde)
    private static final int FADE_IN = 20;
    private static final int HOLD = 30;
    private static final int FADE_OUT = 20;
    private static final int INTRO = FADE_IN + HOLD + FADE_OUT;
    private static final int MENU_FADE = 15;

    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;

    private final List<Button> buttons = new ArrayList<>();
    private int ticks;

    public MainMenuScreen(boolean intro) {
        super(Component.literal("Hauptmenü"));
        this.ticks = intro ? 0 : INTRO + MENU_FADE;
    }

    @Override
    protected void init() {
        buttons.clear();
        int x = width / 2 - BUTTON_WIDTH / 2;
        int y = height / 2;

        buttons.add(addRenderableWidget(Button.builder(Component.literal("Spielen"), button -> MainMenuClient.play())
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build()));
        buttons.add(addRenderableWidget(Button.builder(Component.literal("Einstellungen"), button -> minecraft.setScreen(new ServerSettingsScreen(this)))
                .bounds(x, y + 24, BUTTON_WIDTH, BUTTON_HEIGHT).build()));
        buttons.add(addRenderableWidget(Button.builder(Component.literal("Beenden"), button -> MainMenuClient.quit())
                .bounds(x, y + 48, BUTTON_WIDTH, BUTTON_HEIGHT).build()));

        updateButtons(0);
    }

    @Override
    public void tick() {
        ticks++;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float time = ticks + partialTick;
        updateButtons(time);
        super.render(graphics, mouseX, mouseY, partialTick);

        if (time < INTRO) {
            drawTitle(graphics, height / 2f, 4f, introAlpha(time));
        } else {
            drawTitle(graphics, height / 2f - 40, 3f, menuAlpha(time));
        }
    }

    /** Knöpfe erst nach dem Intro zeigen und einblenden. */
    private void updateButtons(float time) {
        float alpha = time < INTRO ? 0 : menuAlpha(time);
        for (Button button : buttons) {
            button.visible = alpha > 0;
            button.setAlpha(alpha);
        }
    }

    private static float introAlpha(float time) {
        if (time < FADE_IN) return time / FADE_IN;
        if (time < FADE_IN + HOLD) return 1;
        return Math.max(0, (INTRO - time) / FADE_OUT);
    }

    private static float menuAlpha(float time) {
        return Math.min(1, Math.max(0, (time - INTRO) / MENU_FADE));
    }

    private void drawTitle(GuiGraphics graphics, float centerY, float scale, float alpha) {
        // Unter Alpha 4/255 zeichnet Minecraft Text voll deckend, deshalb dann gar nicht zeichnen
        if (alpha < 0.02f) return;

        Component title = Tablist.title();
        int color = ((int) (alpha * 255) << 24) | 0xFFFFFF;

        graphics.pose().pushPose();
        graphics.pose().translate(width / 2f, centerY, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, title, -font.width(title) / 2, -font.lineHeight / 2, color, true);
        graphics.pose().popPose();
    }

    /** Kein Blur: die Welt an der View soll klar zu sehen sein, nur unten leicht abgedunkelt für die Lesbarkeit. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderWorldBackground(graphics, width, height);
    }

    static void renderWorldBackground(GuiGraphics graphics, int width, int height) {
        graphics.fillGradient(0, 0, width, height, 0x20000000, 0x90000000);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
