package de.niconagel.coremod.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Auswahlliste für die Editoren: Suche über alles, was eine Zeile zeigt, und Reiter, sobald die Einträge mehr als eine
 * Kategorie haben. Normal schließt die Auswahl sich danach; mit {@code stayOpen} bleibt sie offen, damit man z.B. viermal
 * denselben Wagen anhängen kann.
 */
class PickerScreen extends Screen {

    private static final int ROW_HEIGHT = 26;

    /** @param category Reiter, "" wenn es keine gibt */
    record Option(String id, Component title, Component detail, String category) {
    }

    private final Screen parent;
    private final List<Option> options;
    private final boolean stayOpen;
    private final String actionLabel;
    private final Consumer<String> onPick;
    /** "" = alle */
    private String category = "";
    private String query = "";
    private Component status = Component.empty();

    private OptionList list;
    private Button pick;

    PickerScreen(Screen parent, String title, List<Option> options, String actionLabel, boolean stayOpen, Consumer<String> onPick) {
        super(Component.literal(title));
        this.parent = parent;
        this.options = options;
        this.actionLabel = actionLabel;
        this.stayOpen = stayOpen;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        int contentWidth = Math.min(width - 40, 380);
        int left = width / 2 - contentWidth / 2;
        int y = 30;

        Set<String> categories = new LinkedHashSet<>();
        options.forEach(option -> categories.add(option.category()));
        if (categories.size() > 1) {
            List<String> tabs = new ArrayList<>(List.of(""));
            tabs.addAll(categories);
            int tabWidth = (contentWidth - (tabs.size() - 1) * 2) / tabs.size();
            for (int i = 0; i < tabs.size(); i++) {
                String tab = tabs.get(i);
                Button button = addRenderableWidget(Button.builder(Component.literal(tab.isEmpty() ? "Alle" : tab), b -> {
                    category = tab;
                    rebuildWidgets();
                }).bounds(left + i * (tabWidth + 2), y, tabWidth, 20).build());
                // Der gewählte Reiter ist der ausgegraute
                button.active = !tab.equals(category);
            }
            y += 24;
        }

        EditBox search = new EditBox(font, left, y, contentWidth, 20, Component.literal("Suche"));
        search.setHint(Component.literal("Suchen …").withStyle(ChatFormatting.GRAY));
        search.setValue(query);
        search.setResponder(text -> {
            query = text;
            list.rebuild();
        });
        addRenderableWidget(search);
        y += 26;

        list = addRenderableWidget(new OptionList(contentWidth, height - y - 34, y));
        list.setX(left);

        pick = addRenderableWidget(Button.builder(Component.literal(actionLabel), b -> pick())
                .bounds(width / 2 - 102, height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal(stayOpen ? "Fertig" : "Abbrechen"), b -> onClose())
                .bounds(width / 2 + 2, height - 28, 100, 20).build());

        list.rebuild();
        setInitialFocus(search);
    }

    private void pick() {
        OptionRow row = list.getSelected();
        if (row == null) return;

        onPick.accept(row.option.id());
        if (stayOpen) {
            status = Component.literal("Hinzugefügt: ").append(row.option.title()).withStyle(ChatFormatting.GREEN);
        } else {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        graphics.drawString(font, status, list.getX(), height - 40, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private class OptionList extends ObjectSelectionList<OptionRow> {

        OptionList(int width, int height, int y) {
            super(PickerScreen.this.minecraft, width, height, y, ROW_HEIGHT);
        }

        void rebuild() {
            String search = query.toLowerCase(Locale.ROOT).strip();
            List<OptionRow> rows = new ArrayList<>();
            for (Option option : options) {
                String text = (option.id() + " " + option.title().getString() + " " + option.detail().getString()).toLowerCase(Locale.ROOT);
                if ((category.isEmpty() || option.category().equals(category)) && text.contains(search)) {
                    rows.add(new OptionRow(option));
                }
            }
            replaceEntries(rows);
            setSelected(null);
        }

        @Override
        public void setSelected(OptionRow selected) {
            super.setSelected(selected);
            if (pick != null) pick.active = selected != null;
        }

        @Override
        public int getRowWidth() {
            // Platz für die Scrollleiste
            return width - 20;
        }
    }

    private class OptionRow extends ObjectSelectionList.Entry<OptionRow> {
        private final Option option;

        OptionRow(Option option) {
            this.option = option;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            graphics.drawString(font, option.title(), left + 6, top + 3, 0xFFFFFF);
            graphics.drawString(font, option.detail(), left + 6, top + 13, 0xFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return true;
        }

        @Override
        public Component getNarration() {
            return option.title();
        }
    }
}
