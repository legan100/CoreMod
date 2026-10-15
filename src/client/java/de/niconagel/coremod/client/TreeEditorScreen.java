package de.niconagel.coremod.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * Gemeinsamer Aufbau von Routen- und Zug-Editor, fürs Team und bewusst ohne Animationen.
 * Rechts Ordner (Strecken bzw. Garnituren): der Pfeil rechts klappt einen auf, dann sind nur noch er und sein Inhalt
 * (eingerückt) zu sehen. Links die Knöpfe der Unterklasse passend zur Ansicht, darunter die Suche.
 */
abstract class TreeEditorScreen extends Screen {

    private static final int MARGIN = 10;
    private static final int TOP = 32;
    private static final int SIDE_WIDTH = 150;
    private static final int ROW_HEIGHT = 30;
    private static final int ARROW_WIDTH = 20;
    private static final int INDENT = 18;

    /** Knopf links. {@code enabled} wird nach jeder Auswahl neu geprüft. */
    protected record Action(String label, Runnable run, BooleanSupplier enabled) {
    }

    /** Aufgeklappter Ordner, -1 = Ordneransicht. */
    protected int open = -1;
    /** Angewählter Ordner (Ordneransicht) bzw. Eintrag des aufgeklappten Ordners, -1 = nichts. */
    protected int selected = -1;
    private String query = "";

    private final List<Button> buttons = new ArrayList<>();
    private List<Action> actions = List.of();
    private TreeList list;

    protected TreeEditorScreen(String title) {
        super(Component.literal(title));
    }

    // ---------------------------------------------------------------- Unterklassen

    protected abstract int folderCount();

    protected abstract int childCount(int folder);

    /** Wonach die Suche in der Ordneransicht sucht. */
    protected abstract String folderSearchText(int folder);

    /** Wonach die Suche im aufgeklappten Ordner sucht. */
    protected abstract String childSearchText(int folder, int child);

    /** Zwei Zeilen ab (x, top), rechts davon ist noch der Pfeil. */
    protected abstract void renderFolder(GuiGraphics graphics, int folder, int x, int top);

    /** Zwei Zeilen ab (x, top), schon eingerückt. */
    protected abstract void renderChild(GuiGraphics graphics, int folder, int child, int x, int top);

    /** Knöpfe links für die aktuelle Ansicht ({@link #open} &lt; 0: Ordner, sonst Inhalt des Ordners). */
    protected abstract List<Action> actions();

    protected abstract String searchHint();

    /** false = flache Liste ohne Pfeil und ohne Inhalt (z.B. Stellwerke). */
    protected boolean expandable() {
        return true;
    }

    // ---------------------------------------------------------------- Aufbau

    @Override
    protected void init() {
        buttons.clear();
        actions = actions();
        int y = TOP;
        for (Action action : actions) {
            buttons.add(addRenderableWidget(Button.builder(Component.literal(action.label()), b -> action.run().run())
                    .bounds(MARGIN, y, SIDE_WIDTH, 20).build()));
            y += 24;
        }

        EditBox search = new EditBox(font, MARGIN, y + 8, SIDE_WIDTH, 20, Component.literal("Suche"));
        search.setHint(Component.literal(searchHint()).withStyle(ChatFormatting.GRAY));
        search.setValue(query);
        search.setResponder(text -> {
            query = text;
            refresh();
        });
        addRenderableWidget(search);

        int listX = MARGIN * 2 + SIDE_WIDTH;
        list = addRenderableWidget(new TreeList(width - listX - MARGIN, height - TOP - MARGIN));
        list.setX(listX);

        refresh();
    }

    /** Liste neu aufbauen und Knöpfe anpassen, nach jeder Änderung an den Daten. */
    protected void refresh() {
        list.rebuild();
        updateButtons();
    }

    private void updateButtons() {
        for (int i = 0; i < buttons.size(); i++) {
            buttons.get(i).active = actions.get(i).enabled().getAsBoolean();
        }
    }

    /** Beim Anklicken: die Liste markiert die Zeile selbst, nur die Knöpfe ändern sich. */
    private void select(int index) {
        selected = index;
        updateButtons();
    }

    /** Pfeil: auf-/zuklappen. Danach gelten andere Knöpfe und die Suche gilt der anderen Ebene, also leeren. */
    private void toggle(int folder) {
        open = open == folder ? -1 : folder;
        selected = -1;
        query = "";
        // Erst nach dem Klick: Vanilla geht gerade noch über die Widgets
        minecraft.tell(this::rebuildWidgets);
    }

    protected static MutableComponent gray(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GRAY);
    }

    private static boolean matches(String text, String query) {
        return text.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT).strip());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
    }

    // ---------------------------------------------------------------- Liste

    private class TreeList extends ObjectSelectionList<Row> {

        TreeList(int width, int height) {
            super(TreeEditorScreen.this.minecraft, width, height, TOP, ROW_HEIGHT);
        }

        void rebuild() {
            List<Row> rows = new ArrayList<>();
            if (open < 0) {
                for (int i = 0; i < folderCount(); i++) {
                    if (matches(folderSearchText(i), query)) rows.add(new FolderRow(i));
                }
            } else {
                rows.add(new FolderRow(open));
                for (int i = 0; i < childCount(open); i++) {
                    if (matches(childSearchText(open, i), query)) rows.add(new ChildRow(i));
                }
            }
            replaceEntries(rows);

            // Was die Suche ausblendet, ist nicht mehr angewählt
            Row chosen = rows.stream().filter(Row::isChosen).findFirst().orElse(null);
            if (chosen == null) selected = -1;
            setSelected(chosen);
        }

        @Override
        public int getRowWidth() {
            // Platz für die Scrollleiste
            return width - 20;
        }
    }

    private abstract class Row extends ObjectSelectionList.Entry<Row> {
        /** Zuletzt gezeichnete Position, für Klicks auf den Pfeil. */
        int rowLeft;
        int rowWidth;

        abstract boolean isChosen();
    }

    /** Ordner: links der Inhalt der Unterklasse (Auswahl), rechts der Pfeil zum Auf-/Zuklappen. */
    private class FolderRow extends Row {
        private final int index;

        FolderRow(int index) {
            this.index = index;
        }

        @Override
        boolean isChosen() {
            return open < 0 && selected == index;
        }

        @Override
        public void render(GuiGraphics graphics, int i, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            rowLeft = left;
            rowWidth = width;
            renderFolder(graphics, index, left + 6, top);

            if (!expandable()) return;
            int arrowX = left + width - ARROW_WIDTH;
            graphics.fill(arrowX, top, arrowX + 1, top + height, 0x40FFFFFF);
            if (hovering && mouseX >= arrowX) {
                graphics.fill(arrowX + 1, top, left + width, top + height, 0x30FFFFFF);
            }
            graphics.drawCenteredString(font, index == open ? "▲" : "▼", arrowX + ARROW_WIDTH / 2, top + height / 2 - 4, 0xFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (expandable() && mouseX >= rowLeft + rowWidth - ARROW_WIDTH) {
                toggle(index);
                return false;
            }
            // Im aufgeklappten Ordner ist er nur Überschrift
            if (open >= 0) return false;
            select(index);
            return true;
        }

        @Override
        public Component getNarration() {
            return Component.literal(folderSearchText(index));
        }
    }

    /** Eintrag, eingerückt unter seinem Ordner. */
    private class ChildRow extends Row {
        private final int index;

        ChildRow(int index) {
            this.index = index;
        }

        @Override
        boolean isChosen() {
            return open >= 0 && selected == index;
        }

        @Override
        public void render(GuiGraphics graphics, int i, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            rowLeft = left;
            rowWidth = width;

            // Ordner-Linie: senkrecht vom Ordner herunter (durch den Zeilenabstand), waagrecht zum Eintrag
            int lineX = left + 8;
            int middle = top + height / 2;
            graphics.fill(lineX, top - 4, lineX + 1, middle + 1, 0x60FFFFFF);
            graphics.fill(lineX, middle, left + INDENT - 3, middle + 1, 0x60FFFFFF);

            renderChild(graphics, open, index, left + INDENT, top);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            select(index);
            return true;
        }

        @Override
        public Component getNarration() {
            return Component.literal(childSearchText(open, index));
        }
    }
}
