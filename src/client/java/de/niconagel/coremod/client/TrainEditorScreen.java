package de.niconagel.coremod.client;

import de.niconagel.coremod.client.Zugdaten.Fahrzeug;
import de.niconagel.coremod.client.Zugdaten.Fahrzeugtyp;
import de.niconagel.coremod.client.Zugdaten.Garnitur;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Zug-Editor (/mainmenu routes trains): globale Garnituren als Ordner, darunter ihre Fahrzeuge in Reihenfolge.
 * Fahrzeuge kommen aus dem Fahrzeugkatalog von ModernRailroading. Im Routen-Editor wählt man dann pro Route eine Garnitur.
 * Jede Änderung geht sofort an den Server.
 */
public class TrainEditorScreen extends TreeEditorScreen {

    private final List<Garnitur> garnituren;
    private final Zugdaten zugdaten;
    /** Nur um beim Löschen zu zeigen, welche Routen die Garnitur nutzen. */
    private final List<CoremodClient.Strecke> strecken;

    public TrainEditorScreen(List<CoremodClient.Strecke> strecken, Zugdaten zugdaten) {
        super("Zug-Editor");
        this.garnituren = new ArrayList<>(zugdaten.garnituren());
        this.zugdaten = zugdaten;
        this.strecken = strecken;
    }

    // ---------------------------------------------------------------- Ansicht

    @Override
    protected int folderCount() {
        return garnituren.size();
    }

    @Override
    protected int childCount(int folder) {
        return garnituren.get(folder).vehicles().size();
    }

    @Override
    protected String folderSearchText(int folder) {
        Garnitur garnitur = garnituren.get(folder);
        return garnitur.name() + " " + zugdaten.composition(garnitur);
    }

    @Override
    protected String childSearchText(int folder, int child) {
        Fahrzeug vehicle = garnituren.get(folder).vehicles().get(child);
        Fahrzeugtyp type = zugdaten.type(vehicle.type());
        return type == null ? vehicle.type() : String.join(" ", type.name(), type.id(), type.category());
    }

    @Override
    protected void renderFolder(GuiGraphics graphics, int folder, int x, int top) {
        Garnitur garnitur = garnituren.get(folder);
        graphics.drawString(font, Component.empty()
                .append(Component.literal(garnitur.name()).withStyle(ChatFormatting.BOLD))
                .append(gray("  " + zugdaten.composition(garnitur))), x, top + 4, 0xFFFFFF);
        graphics.drawString(font, zugdaten.summary(garnitur), x, top + 15, 0xFFFFFF);
    }

    @Override
    protected void renderChild(GuiGraphics graphics, int folder, int child, int x, int top) {
        Fahrzeug vehicle = garnituren.get(folder).vehicles().get(child);
        Fahrzeugtyp type = zugdaten.type(vehicle.type());

        MutableComponent first = gray((child + 1) + "  ").append(type == null
                ? Component.literal(vehicle.type()).withStyle(ChatFormatting.RED)
                : Component.literal(type.name()).withStyle(ChatFormatting.BOLD, ChatFormatting.WHITE));
        if (vehicle.reversed()) first.append(Component.literal("  ⇄ umgedreht").withStyle(ChatFormatting.YELLOW));
        graphics.drawString(font, first, x, top + 4, 0xFFFFFF);

        Component second = type == null
                ? Component.literal("Fehlt im MR-Fahrzeugkatalog (Content-Pack entfernt?)").withStyle(ChatFormatting.RED)
                : gray(details(type));
        graphics.drawString(font, second, x, top + 15, 0xFFFFFF);
    }

    /** "Lok · 23 m · 80 km/h · 95 t · locomotives/br50" */
    private static String details(Fahrzeugtyp type) {
        return type.category() + " · " + Zugdaten.number(type.length()) + " m"
                + (type.vmax() > 0 ? " · " + Zugdaten.number(type.vmax()) + " km/h" : "")
                + " · " + Zugdaten.number(type.weight()) + " t · " + type.id();
    }

    @Override
    protected List<Action> actions() {
        if (open < 0) {
            return List.of(
                    new Action("Garnitur hinzufügen", this::addGarnitur, () -> true),
                    new Action("Garnitur löschen", this::deleteGarnitur, () -> selected >= 0),
                    new Action("Garnitur bearbeiten", this::renameGarnitur, () -> selected >= 0));
        }
        return List.of(
                new Action("Fahrzeug hinzufügen", this::addVehicle, () -> true),
                new Action("Fahrzeug entfernen", this::removeVehicle, () -> selected >= 0),
                new Action("Fahrzeug umdrehen", this::reverseVehicle, () -> selected >= 0),
                new Action("▲ Nach vorne", () -> moveVehicle(-1), () -> selected > 0),
                new Action("▼ Nach hinten", () -> moveVehicle(1), () -> selected >= 0 && selected < vehicles().size() - 1));
    }

    @Override
    protected String searchHint() {
        return open < 0 ? "Garnituren suchen …" : "Fahrzeuge suchen …";
    }

    // ---------------------------------------------------------------- Garnituren

    private void addGarnitur() {
        minecraft.setScreen(new FormScreen(this, "Garnitur hinzufügen", List.of("Name"), List.of(""), values -> {
            if (values.getFirst().isEmpty()) return "Bitte einen Namen eingeben.";
            // Feste ID, damit Routen auch nach dem Umbenennen noch auf die Garnitur zeigen
            String id = UUID.randomUUID().toString().substring(0, 8);
            garnituren.add(new Garnitur(id, values.getFirst(), List.of()));
            selected = garnituren.size() - 1;
            save();
            return null;
        }));
    }

    private void renameGarnitur() {
        if (selected < 0) return;
        Garnitur garnitur = garnituren.get(selected);
        minecraft.setScreen(new FormScreen(this, "Garnitur bearbeiten", List.of("Name"), List.of(garnitur.name()), values -> {
            if (values.getFirst().isEmpty()) return "Bitte einen Namen eingeben.";
            garnituren.set(selected, new Garnitur(garnitur.id(), values.getFirst(), garnitur.vehicles()));
            save();
            return null;
        }));
    }

    private void deleteGarnitur() {
        if (selected < 0) return;
        Garnitur garnitur = garnituren.get(selected);

        List<String> users = new ArrayList<>();
        for (CoremodClient.Strecke strecke : strecken) {
            for (CoremodClient.Route route : strecke.routes()) {
                if (route.garnitur().equals(garnitur.id())) users.add(route.name() + " " + route.number());
            }
        }
        Component message = users.isEmpty()
                ? Component.literal("Keine Route nutzt diese Garnitur.")
                : Component.literal("Wird von " + (users.size() == 1 ? "1 Route" : users.size() + " Routen") + " genutzt: "
                + String.join(", ", users) + ". Sie stehen danach auf \"Keine Garnitur\".").withStyle(ChatFormatting.RED);

        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                garnituren.remove(selected);
                selected = -1;
                save();
            }
            minecraft.setScreen(this);
        }, Component.literal("Garnitur \"" + garnitur.name() + "\" löschen?"), message));
    }

    // ---------------------------------------------------------------- Fahrzeuge der aufgeklappten Garnitur

    private List<Fahrzeug> vehicles() {
        return garnituren.get(open).vehicles();
    }

    private void setVehicles(List<Fahrzeug> vehicles) {
        Garnitur garnitur = garnituren.get(open);
        garnituren.set(open, new Garnitur(garnitur.id(), garnitur.name(), vehicles));
        save();
    }

    /** Auswahl aus dem MR-Katalog, bleibt offen. Jedes Fahrzeug kommt hinter das angewählte, sonst ans Ende. */
    private void addVehicle() {
        List<PickerScreen.Option> options = zugdaten.katalog().stream()
                .map(type -> new PickerScreen.Option(type.id(), Component.literal(type.name()).withStyle(ChatFormatting.BOLD),
                        gray(details(type)), type.category()))
                .toList();

        minecraft.setScreen(new PickerScreen(this, "Fahrzeug hinzufügen", options, "Hinzufügen", true, id -> {
            List<Fahrzeug> vehicles = new ArrayList<>(vehicles());
            int at = selected < 0 ? vehicles.size() : selected + 1;
            vehicles.add(at, new Fahrzeug(id, false));
            // Das neue ist angewählt, das nächste kommt also dahinter
            selected = at;
            setVehicles(vehicles);
        }));
    }

    private void removeVehicle() {
        if (selected < 0) return;
        List<Fahrzeug> vehicles = new ArrayList<>(vehicles());
        vehicles.remove(selected);
        // Angewählt bleibt das, was nachrückt: so lassen sich mehrere hintereinander entfernen
        selected = Math.min(selected, vehicles.size() - 1);
        setVehicles(vehicles);
        refresh();
    }

    private void reverseVehicle() {
        if (selected < 0) return;
        List<Fahrzeug> vehicles = new ArrayList<>(vehicles());
        Fahrzeug vehicle = vehicles.get(selected);
        vehicles.set(selected, new Fahrzeug(vehicle.type(), !vehicle.reversed()));
        setVehicles(vehicles);
        refresh();
    }

    private void moveVehicle(int by) {
        int to = selected + by;
        if (selected < 0 || to < 0 || to >= vehicles().size()) return;
        List<Fahrzeug> vehicles = new ArrayList<>(vehicles());
        Collections.swap(vehicles, selected, to);
        selected = to;
        setVehicles(vehicles);
        refresh();
    }

    private void save() {
        PacketDistributor.sendToServer(new CoremodClient.SaveGarnituren(List.copyOf(garnituren)));
    }
}
