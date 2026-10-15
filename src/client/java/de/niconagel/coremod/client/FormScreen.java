package de.niconagel.coremod.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Formular aus beschrifteten Textfeldern für den Routen-Editor. "Speichern" (oder Enter) gibt die Werte an {@code onSave}:
 * Fehlertext = bleibt offen und zeigt ihn an, null = gespeichert, zurück zum Editor. Ab 4 Feldern zweispaltig.
 */
class FormScreen extends Screen {

    private static final int ROW = 36;
    private static final int GAP = 10;

    private final Screen parent;
    private final List<String> labels;
    private final List<String> values;
    private final Function<List<String>, String> onSave;
    private final List<EditBox> boxes = new ArrayList<>();
    private Component error = Component.empty();

    // Optionales Auswahlfeld unter den Textfeldern, sein Wert (die ID) kommt als letzter in onSave an
    private String choiceLabel;
    private List<PickerScreen.Option> choiceOptions;
    private String choiceId;
    private Button choiceButton;

    FormScreen(Screen parent, String title, List<String> labels, List<String> values, Function<List<String>, String> onSave) {
        super(Component.literal(title));
        this.parent = parent;
        this.labels = labels;
        this.values = new ArrayList<>(values);
        this.onSave = onSave;
    }

    /** Auswahlfeld, das die Auswahlliste öffnet; {@code selectedId} muss eine der Optionen sein. */
    FormScreen choice(String label, List<PickerScreen.Option> options, String selectedId) {
        this.choiceLabel = label;
        this.choiceOptions = options;
        this.choiceId = selectedId;
        return this;
    }

    @Override
    protected void init() {
        // Bei Größenänderung das schon Getippte behalten
        for (int i = 0; i < boxes.size(); i++) {
            values.set(i, boxes.get(i).getValue());
        }
        boxes.clear();

        int columns = labels.size() > 3 ? 2 : 1;
        int boxWidth = columns == 1 ? 200 : 150;
        int left = width / 2 - (columns * boxWidth + (columns - 1) * GAP) / 2;
        int rows = (labels.size() + columns - 1) / columns + (choiceLabel == null ? 0 : 1);
        int top = Math.max(40, height / 2 - (rows * ROW + 30) / 2);

        for (int i = 0; i < labels.size(); i++) {
            EditBox box = new EditBox(font, left + (i % columns) * (boxWidth + GAP), top + (i / columns) * ROW + 10,
                    boxWidth, 20, Component.literal(labels.get(i)));
            box.setMaxLength(64);
            box.setValue(values.get(i));
            boxes.add(addRenderableWidget(box));
        }

        if (choiceLabel != null) {
            PickerScreen.Option current = choiceOptions.stream().filter(option -> option.id().equals(choiceId)).findFirst().orElseThrow();
            choiceButton = addRenderableWidget(Button.builder(current.title(), b -> minecraft.setScreen(
                            new PickerScreen(this, choiceLabel + " wählen", choiceOptions, "Auswählen", false, id -> choiceId = id)))
                    .bounds(left, top + (rows - 1) * ROW + 10, columns * boxWidth + (columns - 1) * GAP, 20).build());
        }

        int buttonsY = top + rows * ROW + 10;
        addRenderableWidget(Button.builder(Component.literal("Speichern"), b -> save())
                .bounds(width / 2 - 102, buttonsY, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Abbrechen"), b -> onClose())
                .bounds(width / 2 + 2, buttonsY, 100, 20).build());

        setInitialFocus(boxes.getFirst());
    }

    private void save() {
        List<String> result = new ArrayList<>(boxes.stream().map(box -> box.getValue().strip()).toList());
        if (choiceLabel != null) result.add(choiceId);
        String message = onSave.apply(result);
        if (message == null) {
            minecraft.setScreen(parent);
        } else {
            error = Component.literal(message).withStyle(ChatFormatting.RED);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            save();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 15, 0xFFFFFF);
        for (int i = 0; i < boxes.size(); i++) {
            EditBox box = boxes.get(i);
            graphics.drawString(font, labels.get(i), box.getX(), box.getY() - 10, 0xA0A0A0);
        }
        if (choiceButton != null) {
            graphics.drawString(font, choiceLabel, choiceButton.getX(), choiceButton.getY() - 10, 0xA0A0A0);
        }
        graphics.drawCenteredString(font, error, width / 2, height - 20, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
