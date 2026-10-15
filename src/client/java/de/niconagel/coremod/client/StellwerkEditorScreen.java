package de.niconagel.coremod.client;

import de.niconagel.coremod.client.CoremodClient.Stellwerk;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Stellwerks-Editor (/mainmenu stellwerk): flache Liste der Stellwerke für Fahrdienstleiter. Spawn und begehbaren Bereich
 * setzt man in der Welt ({@link BereichAuswahl}): der Editor schließt sich, danach öffnet der Server ihn wieder.
 */
public class StellwerkEditorScreen extends TreeEditorScreen {

    private final List<Stellwerk> stellwerke;

    public StellwerkEditorScreen(List<Stellwerk> stellwerke) {
        super("Stellwerks-Editor");
        this.stellwerke = new ArrayList<>(stellwerke);
    }

    @Override
    protected boolean expandable() {
        return false;
    }

    @Override
    protected int folderCount() {
        return stellwerke.size();
    }

    @Override
    protected int childCount(int folder) {
        return 0;
    }

    @Override
    protected String folderSearchText(int folder) {
        return stellwerke.get(folder).name();
    }

    @Override
    protected String childSearchText(int folder, int child) {
        return "";
    }

    @Override
    protected void renderFolder(GuiGraphics graphics, int folder, int x, int top) {
        Stellwerk stellwerk = stellwerke.get(folder);
        CoremodClient.Spawn spawn = stellwerk.spawn();
        CoremodClient.Bereich b = stellwerk.bereich();
        graphics.drawString(font, Component.literal(stellwerk.name()).withStyle(ChatFormatting.BOLD), x, top + 4, 0xFFFFFF);
        graphics.drawString(font, gray("Spawn " + Math.round(spawn.x()) + ", " + Math.round(spawn.y()) + ", " + Math.round(spawn.z())
                + "  ·  Bereich " + b.size() + " Blöcke (" + b.minX() + ", " + b.minY() + ", " + b.minZ()
                + " → " + b.maxX() + ", " + b.maxY() + ", " + b.maxZ() + ")  ·  " + stellwerk.dimension()), x, top + 15, 0xFFFFFF);
    }

    @Override
    protected void renderChild(GuiGraphics graphics, int folder, int child, int x, int top) {
        // Stellwerke haben keinen Inhalt
    }

    @Override
    protected List<Action> actions() {
        return List.of(
                new Action("Stellwerk hinzufügen", this::add, () -> true),
                new Action("Stellwerk löschen", this::delete, () -> selected >= 0),
                new Action("Stellwerk bearbeiten", this::rename, () -> selected >= 0),
                new Action("Spawn & Bereich neu", this::redo, () -> selected >= 0));
    }

    @Override
    protected String searchHint() {
        return "Stellwerke suchen …";
    }

    /** Name abfragen, dann Editor zu und in der Welt Spawn und Bereich setzen; angelegt wird es erst danach. */
    private void add() {
        minecraft.setScreen(new FormScreen(this, "Stellwerk hinzufügen", List.of("Name"), List.of(""), values -> {
            if (values.getFirst().isEmpty()) return "Bitte einen Namen eingeben.";
            startInWorld("", values.getFirst());
            return null;
        }));
    }

    private void redo() {
        if (selected < 0) return;
        startInWorld(stellwerke.get(selected).id(), "");
    }

    private void startInWorld(String id, String name) {
        PacketDistributor.sendToServer(new CoremodClient.StartStellwerk(id, name));
        // Statt zurück in den Editor direkt in die Welt; der Server öffnet ihn nach dem Setzen wieder
        minecraft.tell(() -> minecraft.setScreen(null));
    }

    private void rename() {
        if (selected < 0) return;
        Stellwerk stellwerk = stellwerke.get(selected);
        minecraft.setScreen(new FormScreen(this, "Stellwerk bearbeiten", List.of("Name"), List.of(stellwerk.name()), values -> {
            if (values.getFirst().isEmpty()) return "Bitte einen Namen eingeben.";
            stellwerke.set(selected, new Stellwerk(stellwerk.id(), values.getFirst(), stellwerk.dimension(), stellwerk.spawn(), stellwerk.bereich()));
            save();
            return null;
        }));
    }

    private void delete() {
        if (selected < 0) return;
        Stellwerk stellwerk = stellwerke.get(selected);
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                stellwerke.remove(selected);
                selected = -1;
                save();
            }
            minecraft.setScreen(this);
        }, Component.literal("Stellwerk \"" + stellwerk.name() + "\" löschen?"),
                Component.literal("Fahrdienstleiter können es danach nicht mehr wählen.")));
    }

    private void save() {
        PacketDistributor.sendToServer(new CoremodClient.SaveStellwerke(List.copyOf(stellwerke)));
    }
}
