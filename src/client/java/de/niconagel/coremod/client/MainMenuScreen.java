package de.niconagel.coremod.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Hauptmenü: erst blendet der Schriftzug ein und wieder aus (Intro), dann erscheinen Titel und Knöpfe.
 * "Spielen" schiebt die Knöpfe nach links raus, der Titel wandert nach oben und die Jobs gleiten nacheinander von rechts herein.
 * Im Hintergrund ist die Welt an der View zu sehen, der Server hat den Spieler dorthin gesetzt.
 */
public class MainMenuScreen extends Screen {

    // Intro in Ticks (20 = 1 Sekunde)
    private static final int FADE_IN = 20;
    private static final int HOLD = 30;
    private static final int FADE_OUT = 20;
    private static final int INTRO = FADE_IN + HOLD + FADE_OUT;
    private static final int MENU_FADE = 15;

    // Wechsel Hauptmenü ↔ Job-Auswahl
    private static final int SWITCH = 16;
    private static final int SLIDE = 160;
    /** Versatz zwischen den Knöpfen, als Anteil ihrer Animation. */
    private static final float STAGGER = 0.2f;

    /** Wie Tablist.title() in der Server-Mod. */
    private static final Component TITLE = Component.empty().withStyle(ChatFormatting.BOLD)
            .append(Component.literal("TF").withStyle(ChatFormatting.RED))
            .append(Component.literal("s in der Blockwelt").withStyle(ChatFormatting.GRAY));

    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW = 24;

    private final List<Button> menuButtons = new ArrayList<>();
    private final List<Button> jobButtons = new ArrayList<>();
    private int ticks;
    private boolean jobsPage;
    /** Tick, an dem der letzte Wechsel begonnen hat. */
    private float switchedAt = -SWITCH;

    public MainMenuScreen(boolean intro) {
        super(Component.literal("Hauptmenü"));
        this.ticks = intro ? 0 : INTRO + MENU_FADE;
    }

    @Override
    protected void init() {
        menuButtons.clear();
        jobButtons.clear();
        int y = height / 2;

        menuButtons.add(button("Spielen", y, button -> showJobs(true)));
        menuButtons.add(button("Einstellungen", y + ROW, button -> minecraft.setScreen(new ServerSettingsScreen(this))));
        menuButtons.add(button("Beenden", y + 2 * ROW, button -> MainMenuClient.quit()));

        int top = jobsTop();
        List<CoremodClient.JobEntry> jobs = MainMenuClient.jobs;
        for (int i = 0; i < jobs.size(); i++) {
            CoremodClient.JobEntry job = jobs.get(i);
            Button button = button(job.name(), top + i * ROW, b -> MainMenuClient.play(job));
            button.setTooltip(Tooltip.create(Component.literal(job.description())));
            jobButtons.add(button);
        }
        jobButtons.add(button("Zurück", top + jobs.size() * ROW + 8, button -> showJobs(false)));

        updateButtons(ticks, page(ticks));
    }

    private Button button(String text, int y, Button.OnPress onPress) {
        return addRenderableWidget(Button.builder(Component.literal(text), onPress)
                .bounds(width / 2 - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    /** Oberkante der Job-Liste, so dass Titel, Jobs und "Zurück" zusammen mittig stehen. */
    private int jobsTop() {
        return height / 2 - (MainMenuClient.jobs.size() + 1) * ROW / 2 + 16;
    }

    private void showJobs(boolean show) {
        if (show == jobsPage) return;
        // Mitten in der Animation umkehren, ohne zu springen
        float raw = Mth.clamp((ticks - switchedAt) / SWITCH, 0, 1);
        switchedAt = ticks - (1 - raw) * SWITCH;
        jobsPage = show;
    }

    /** 0 = Hauptmenü, 1 = Job-Auswahl, dazwischen läuft der Wechsel. */
    private float page(float time) {
        float raw = Mth.clamp((time - switchedAt) / SWITCH, 0, 1);
        return jobsPage ? raw : 1 - raw;
    }

    @Override
    public void tick() {
        ticks++;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && jobsPage) {
            showJobs(false);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float time = ticks + partialTick;
        float page = page(time);

        // Bei der Job-Auswahl etwas dunkler, die Liste ist länger
        graphics.fill(0, 0, width, height, (int) (page * 0x50) << 24);
        updateButtons(time, page);
        super.render(graphics, mouseX, mouseY, partialTick);

        if (time < INTRO) {
            drawText(graphics, TITLE, height / 2f, 4f, introAlpha(time));
            return;
        }

        float move = easeInOutCubic(page);
        int top = jobsTop();
        drawText(graphics, TITLE, Mth.lerp(move, height / 2f - 40, top - 40), Mth.lerp(move, 3f, 2f), menuAlpha(time));

        float in = jobsIn(page);
        if (in > 0) {
            int lineWidth = (int) (BUTTON_WIDTH * easeOutCubic(in) / 2);
            graphics.fill(width / 2 - lineWidth, top - 27, width / 2 + lineWidth, top - 26, ((int) (in * 0xA0) << 24) | 0xFFFFFF);
            drawText(graphics, Component.literal("Wähle deinen Job"), top - 14 - (1 - in) * 6, 1f, in);
        }
    }

    /** Hauptmenü-Knöpfe gleiten nach links raus, danach die Jobs von rechts herein, jeweils leicht versetzt. */
    private void updateButtons(float time, float page) {
        float menuAlpha = time < INTRO ? 0 : menuAlpha(time);
        float out = Mth.clamp(page / 0.6f, 0, 1);
        for (int i = 0; i < menuButtons.size(); i++) {
            float local = stagger(out, i, menuButtons.size());
            place(menuButtons.get(i), -SLIDE * easeInCubic(local), (1 - local) * menuAlpha);
        }

        float in = jobsIn(page);
        for (int i = 0; i < jobButtons.size(); i++) {
            float local = stagger(in, i, jobButtons.size());
            place(jobButtons.get(i), SLIDE * (1 - easeOutBack(local)), local);
        }
    }

    private void place(Button button, float offset, float alpha) {
        button.setX(width / 2 - BUTTON_WIDTH / 2 + Math.round(offset));
        button.setAlpha(alpha);
        button.visible = alpha > 0.02f;
    }

    /** Die Jobs starten, wenn das Hauptmenü schon halb draußen ist. */
    private static float jobsIn(float page) {
        return Mth.clamp((page - 0.3f) / 0.7f, 0, 1);
    }

    /** Fortschritt des i-ten von n Knöpfen, wenn die ganze Gruppe bei {@code progress} steht. */
    private static float stagger(float progress, int i, int n) {
        return Mth.clamp(progress * (1 + (n - 1) * STAGGER) - i * STAGGER, 0, 1);
    }

    private static float easeInCubic(float x) {
        return x * x * x;
    }

    private static float easeOutCubic(float x) {
        return 1 - (1 - x) * (1 - x) * (1 - x);
    }

    private static float easeInOutCubic(float x) {
        return x < 0.5f ? 4 * x * x * x : 1 - (float) Math.pow(-2 * x + 2, 3) / 2;
    }

    /** Schießt leicht übers Ziel hinaus und federt zurück. */
    private static float easeOutBack(float x) {
        float c1 = 1.70158f;
        float c3 = c1 + 1;
        return 1 + c3 * (x - 1) * (x - 1) * (x - 1) + c1 * (x - 1) * (x - 1);
    }

    private static float introAlpha(float time) {
        if (time < FADE_IN) return time / FADE_IN;
        if (time < FADE_IN + HOLD) return 1;
        return Math.max(0, (INTRO - time) / FADE_OUT);
    }

    private static float menuAlpha(float time) {
        return Math.min(1, Math.max(0, (time - INTRO) / MENU_FADE));
    }

    private void drawText(GuiGraphics graphics, Component text, float centerY, float scale, float alpha) {
        // Unter Alpha 4/255 zeichnet Minecraft Text voll deckend, deshalb dann gar nicht zeichnen
        if (alpha < 0.02f) return;

        int color = ((int) (alpha * 255) << 24) | 0xFFFFFF;

        graphics.pose().pushPose();
        graphics.pose().translate(width / 2f, centerY, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, text, -font.width(text) / 2, -font.lineHeight / 2, color, true);
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
