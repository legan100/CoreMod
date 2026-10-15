package de.niconagel.coremod.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Hauptmenü: erst blendet der Schriftzug ein und wieder aus (Intro), dann erscheinen Titel und Knöpfe.
 * Dahinter liegen Unterseiten als Stapel (Jobs → Strecken → Routen → Routendetails). Beim Wechsel gleitet die alte Seite
 * hinaus und die neue versetzt herein, vorwärts nach links, zurück nach rechts. Vom Hauptmenü aus wandert der Titel nach oben.
 * Im Hintergrund ist die Welt an der View zu sehen, der Server hat den Spieler dorthin gesetzt.
 */
public class MainMenuScreen extends Screen {

    // Intro in Ticks (20 = 1 Sekunde)
    private static final int FADE_IN = 20;
    private static final int HOLD = 30;
    private static final int FADE_OUT = 20;
    private static final int INTRO = FADE_IN + HOLD + FADE_OUT;
    private static final int MENU_FADE = 15;

    // Seitenwechsel
    private static final int SWITCH = 16;
    private static final int SLIDE = 160;
    /** Versatz zwischen den Elementen einer Seite, als Anteil ihrer Animation. */
    private static final float STAGGER = 0.2f;

    /** Wie Tablist.title() in der Server-Mod. */
    private static final Component TITLE = Component.empty().withStyle(ChatFormatting.BOLD)
            .append(Component.literal("TF").withStyle(ChatFormatting.RED))
            .append(Component.literal("s in der Blockwelt").withStyle(ChatFormatting.GRAY));

    /** Job-Id des Triebfahrzeugführers (Job.TF in der Server-Mod): statt direkt zu starten geht es zur Streckenwahl. */
    private static final String TF = "TF";

    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW = 24;
    private static final int LINE = 12;
    // Unterseiten: Titel oben, darunter die Überschrift, ab CONTENT_TOP der Inhalt
    private static final int TITLE_Y = 28;
    private static final int HEADING_Y = 54;
    private static final int CONTENT_TOP = 70;

    /** Eine Seite: Überschrift (im Hauptmenü null), Textzeilen ab {@code linesTop} und Knöpfe. */
    private record Page(Component heading, List<Component> lines, int linesTop, List<Button> buttons) {
    }

    /** Wie die Seiten gebaut werden, damit sie nach Größenänderungen neu entstehen. Unten liegt das Hauptmenü. */
    private final List<Supplier<Page>> stack = new ArrayList<>();
    private Page current;
    /** Die Seite, die gerade hinausgleitet, null wenn kein Wechsel läuft. */
    private Page previous;
    private boolean forward = true;
    private int ticks;
    /** Tick, an dem der letzte Wechsel begonnen hat. */
    private float switchedAt = -SWITCH;

    public MainMenuScreen(boolean intro) {
        super(Component.literal("Hauptmenü"));
        this.ticks = intro ? 0 : INTRO + MENU_FADE;
        stack.add(this::menuPage);
    }

    @Override
    protected void init() {
        // Neu aufbauen (erstes Öffnen, Größenänderung, neue Daten vom Server): ein laufender Wechsel ist dann fertig
        current = stack.getLast().get();
        previous = null;
        switchedAt = ticks - SWITCH;
        layout(ticks);
    }

    // ---------------------------------------------------------------- Seiten

    private Page menuPage() {
        List<Button> buttons = List.of(
                button("Spielen", b -> open(this::jobsPage)),
                button("Einstellungen", b -> minecraft.setScreen(new ServerSettingsScreen(this))),
                button("Beenden", b -> MainMenuClient.quit()));
        for (int i = 0; i < buttons.size(); i++) {
            buttons.get(i).setY(height / 2 + i * ROW);
        }
        return new Page(null, List.of(), 0, buttons);
    }

    private Page jobsPage() {
        List<Button> buttons = new ArrayList<>();
        for (CoremodClient.JobEntry job : MainMenuClient.jobs) {
            Button button = button(job.name(), b -> {
                if (job.id().equals(TF)) {
                    open(() -> streckenPage(job));
                } else {
                    MainMenuClient.play(job, "");
                }
            });
            button.setTooltip(Tooltip.create(Component.literal(job.description())));
            buttons.add(button);
        }
        return subPage(Component.literal("Wähle deinen Job"), List.of(), buttons);
    }

    private Page streckenPage(CoremodClient.JobEntry tf) {
        List<Button> buttons = new ArrayList<>();
        for (CoremodClient.Strecke strecke : MainMenuClient.strecken) {
            buttons.add(button(strecke.name(), b -> open(() -> routenPage(tf, strecke))));
        }
        List<Component> lines = buttons.isEmpty() ? List.of(gray("Noch keine Strecken eingerichtet.")) : List.of();
        return subPage(Component.literal("Wähle eine Strecke"), lines, buttons);
    }

    private Page routenPage(CoremodClient.JobEntry tf, CoremodClient.Strecke strecke) {
        List<Button> buttons = new ArrayList<>();
        for (CoremodClient.Route route : strecke.routes()) {
            String to = route.stops().isEmpty() ? "" : " nach " + route.stops().getLast().name();
            Button button = button(route.name() + to, b -> open(() -> detailsPage(tf, route)));
            button.setTooltip(Tooltip.create(Component.literal("TrainOS-Route " + route.number())));
            buttons.add(button);
        }
        List<Component> lines = buttons.isEmpty() ? List.of(gray("Auf dieser Strecke gibt es noch keine Routen.")) : List.of();
        return subPage(Component.literal(strecke.name()), lines, buttons);
    }

    private Page detailsPage(CoremodClient.JobEntry tf, CoremodClient.Route route) {
        List<Component> lines = new ArrayList<>();
        MutableComponent title = Component.empty().append(Component.literal(route.name()).withStyle(ChatFormatting.BOLD));
        if (!route.stops().isEmpty()) {
            title.append(gray(" von ")).append(route.stops().getFirst().name()).append(gray(" nach ")).append(route.stops().getLast().name());
        }
        lines.add(title);
        if (route.stops().size() > 2) {
            // Unterwegshalte, ohne Von und Nach
            List<String> between = route.stops().subList(1, route.stops().size() - 1).stream().map(CoremodClient.Haltepunkt::name).toList();
            lines.add(gray("über " + String.join(", ", between)));
        }
        lines.add(garniturLine(route));
        Zugdaten.Garnitur garnitur = MainMenuClient.zugdaten.garnitur(route.garnitur());
        if (garnitur != null) {
            lines.add(Component.literal(MainMenuClient.zugdaten.composition(garnitur)));
            lines.add(MainMenuClient.zugdaten.summary(garnitur));
        }
        String abstellung = CoremodClient.Abstellung.name(MainMenuClient.abstellungen, route.abstellung());
        lines.add(abstellung == null
                ? Component.literal("Keine Abstellung").withStyle(ChatFormatting.RED)
                : gray("Bereitstellung in ").append(Component.literal(abstellung).withStyle(ChatFormatting.WHITE)));
        lines.add(gray("TrainOS-Route ").append(Component.literal(route.number()).withStyle(ChatFormatting.WHITE)));
        Button start = button("Starten", b -> MainMenuClient.play(tf, route.number()));
        return subPage(Component.literal("Routendetails"), lines, List.of(start));
    }

    /**
     * Unterseite mit "Zurück" am Ende, Inhalt mittig unter der Überschrift.
     * ponytail: kein Scrollen, ab etwa 8 Einträgen läuft die Liste unten aus dem Bild; dann eine scrollbare Liste nehmen
     */
    private Page subPage(Component heading, List<Component> lines, List<Button> buttons) {
        List<Button> all = new ArrayList<>(buttons);
        all.add(button("Zurück", b -> back()));

        int textHeight = lines.isEmpty() ? 0 : lines.size() * LINE + 8;
        int total = textHeight + all.size() * ROW + 8;
        int top = Math.max(CONTENT_TOP, (height + CONTENT_TOP) / 2 - total / 2);

        int y = top + textHeight;
        for (int i = 0; i < all.size(); i++) {
            // "Zurück" etwas abgesetzt
            all.get(i).setY(y + i * ROW + (i == all.size() - 1 ? 8 : 0));
        }
        return new Page(heading, lines, top, all);
    }

    /** Nur für Eingaben registriert, gezeichnet wird in {@link #drawButtons} mit genauem Versatz. */
    private Button button(String text, Button.OnPress onPress) {
        return addWidget(Button.builder(Component.literal(text), onPress)
                .bounds(width / 2 - BUTTON_WIDTH / 2, 0, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    private static Component garniturLine(CoremodClient.Route route) {
        Zugdaten.Garnitur garnitur = MainMenuClient.zugdaten.garnitur(route.garnitur());
        return garnitur == null
                ? Component.literal("Keine Garnitur").withStyle(ChatFormatting.RED)
                : gray("Garnitur ").append(Component.literal(garnitur.name()).withStyle(ChatFormatting.WHITE));
    }

    private static MutableComponent gray(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GRAY);
    }

    // ---------------------------------------------------------------- Navigation

    private void open(Supplier<Page> page) {
        stack.add(page);
        switchTo(page.get(), true);
    }

    private void back() {
        if (stack.size() <= 1) return;
        stack.removeLast();
        switchTo(stack.getLast().get(), false);
    }

    private void switchTo(Page next, boolean forward) {
        // Läuft noch ein Wechsel, verschwindet dessen alte Seite sofort
        if (previous != null) previous.buttons().forEach(this::removeWidget);
        previous = current;
        current = next;
        this.forward = forward;
        switchedAt = ticks;
        layout(ticks);
    }

    @Override
    public void tick() {
        ticks++;
        if (previous != null && progress(ticks) >= 1) {
            previous.buttons().forEach(this::removeWidget);
            previous = null;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && stack.size() > 1) {
            back();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ---------------------------------------------------------------- Animation

    /** Fortschritt des laufenden Wechsels, 0 bis 1. */
    private float progress(float time) {
        return Mth.clamp((time - switchedAt) / SWITCH, 0, 1);
    }

    /** 0 = Hauptmenü, 1 = Unterseite, dazwischen wandert der Titel. */
    private float sub(float p) {
        float now = stack.size() > 1 ? 1 : 0;
        if (previous == null) return now;
        float before = forward ? (stack.size() > 2 ? 1 : 0) : 1;
        return Mth.lerp(easeInOutCubic(p), before, now);
    }

    /** Fade-in nach dem Intro, gilt für alles außer dem Intro-Schriftzug. */
    private static float fade(float time) {
        return time < INTRO ? 0 : Math.min(1, (time - INTRO) / MENU_FADE);
    }

    private record Motion(float offset, float alpha) {
    }

    /**
     * Wo das i-te von n Elementen einer Seite gerade ist. Die alte Seite gleitet beschleunigend hinaus, die neue startet,
     * wenn die alte schon halb draußen ist, und federt leicht übers Ziel hinaus.
     */
    private Motion motion(boolean incoming, int i, int n, float p) {
        float direction = forward ? 1 : -1;
        if (incoming) {
            float local = stagger(Mth.clamp((p - 0.3f) / 0.7f, 0, 1), i, n);
            return new Motion(direction * SLIDE * (1 - easeOutBack(local)), local);
        }
        float local = stagger(Mth.clamp(p / 0.6f, 0, 1), i, n);
        return new Motion(-direction * SLIDE * easeInCubic(local), 1 - local);
    }

    private void layout(float time) {
        float p = progress(time);
        float fade = fade(time);
        place(current, true, p, fade);
        if (previous != null) place(previous, false, p, fade);
    }

    private void place(Page page, boolean incoming, float p, float fade) {
        int n = page.lines().size() + page.buttons().size();
        for (int i = 0; i < page.buttons().size(); i++) {
            Button button = page.buttons().get(i);
            Motion motion = motion(incoming, page.lines().size() + i, n, p);
            float alpha = motion.alpha() * fade;
            // Ganze Pixel nur für Mausklicks, gezeichnet wird genau
            button.setX(Math.round(buttonX(motion)));
            button.setAlpha(alpha);
            button.visible = alpha > 0.02f;
            // Die alte Seite ist nur noch Animation
            button.active = incoming;
        }
    }

    private float buttonX(Motion motion) {
        return width / 2f - BUTTON_WIDTH / 2f + motion.offset();
    }

    /**
     * Knöpfe mit Versatz unter einem GUI-Pixel zeichnen. Nur auf ganze GUI-Pixel gesetzt (bei GUI-Größe 4 = 4 echte Pixel)
     * ruckeln sie am langsamen Ende der Animation sichtbar in Stufen.
     */
    private void drawButtons(GuiGraphics graphics, Page page, boolean incoming, float p, int mouseX, int mouseY, float partialTick) {
        int n = page.lines().size() + page.buttons().size();
        for (int i = 0; i < page.buttons().size(); i++) {
            Button button = page.buttons().get(i);
            if (!button.visible) continue;

            Motion motion = motion(incoming, page.lines().size() + i, n, p);
            graphics.pose().pushPose();
            graphics.pose().translate(buttonX(motion) - button.getX(), 0, 0);
            button.render(graphics, mouseX, mouseY, partialTick);
            graphics.pose().popPose();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Nicht partialTick: NeoForge gibt Screens seit 21.1.2xx die Dauer des Frames statt des Anteils am Tick (MC-273464),
        // damit sprang die Zeit und die Animation ruckelte
        float time = ticks + minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        float p = progress(time);
        float sub = sub(p);
        float fade = fade(time);

        // Auf den Unterseiten etwas dunkler, dort steht mehr Text
        graphics.fill(0, 0, width, height, (int) (sub * 0x50) << 24);
        layout(time);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (previous != null) drawButtons(graphics, previous, false, p, mouseX, mouseY, partialTick);
        drawButtons(graphics, current, true, p, mouseX, mouseY, partialTick);

        if (time < INTRO) {
            drawText(graphics, TITLE, 0, height / 2f, 4f, introAlpha(time));
            return;
        }

        drawText(graphics, TITLE, 0, Mth.lerp(sub, height / 2f - 40, TITLE_Y), Mth.lerp(sub, 3f, 2f), fade);
        if (sub > 0) {
            int lineWidth = (int) (BUTTON_WIDTH * easeOutCubic(sub) / 2);
            graphics.fill(width / 2 - lineWidth, TITLE_Y + 13, width / 2 + lineWidth, TITLE_Y + 14, ((int) (sub * fade * 0xA0) << 24) | 0xFFFFFF);
        }

        drawPageText(graphics, current, true, p, fade);
        if (previous != null) drawPageText(graphics, previous, false, p, fade);
    }

    private void drawPageText(GuiGraphics graphics, Page page, boolean incoming, float p, float fade) {
        int n = page.lines().size() + page.buttons().size();
        if (page.heading() != null) {
            // Überschrift blendet nur über, sie steht auf allen Unterseiten an derselben Stelle
            float alpha = incoming ? Mth.clamp((p - 0.3f) / 0.7f, 0, 1) : 1 - Mth.clamp(p / 0.6f, 0, 1);
            drawText(graphics, page.heading(), 0, HEADING_Y, 1f, alpha * fade);
        }
        for (int i = 0; i < page.lines().size(); i++) {
            Motion motion = motion(incoming, i, n, p);
            drawText(graphics, page.lines().get(i), motion.offset(), page.linesTop() + i * LINE + LINE / 2f, 1f, motion.alpha() * fade);
        }
    }

    /** Fortschritt des i-ten von n Elementen, wenn die ganze Seite bei {@code progress} steht. */
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

    /** Text mittig um ({@code width / 2 + offsetX}, {@code centerY}). */
    private void drawText(GuiGraphics graphics, Component text, float offsetX, float centerY, float scale, float alpha) {
        // Unter Alpha 4/255 zeichnet Minecraft Text voll deckend, deshalb dann gar nicht zeichnen
        if (alpha < 0.02f) return;

        int color = ((int) (alpha * 255) << 24) | 0xFFFFFF;

        graphics.pose().pushPose();
        graphics.pose().translate(width / 2f + offsetX, centerY, 0);
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
