package de.niconagel.coremod.client;

import de.niconagel.coremod.client.CoremodClient.Route;
import de.niconagel.coremod.client.CoremodClient.Strecke;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Routen-Editor (/mainmenu routes): Strecken als Ordner, darunter ihre Routen. Eine neue Route entsteht in drei Schritten:
 * Formular (Name, TrainOS-Nummer, Garnitur), Haltepunkte in der Welt setzen ({@link Weltauswahl}), dann eine Abstellung
 * wählen, von der der Zug den ersten Halt erreicht. Danach öffnet der Server den Editor wieder.
 */
public class RouteEditorScreen extends TreeEditorScreen {

    private static final List<String> ROUTE_FIELDS = List.of("Name (z.B. RE10)", "Routennummer (TrainOS)");

    /** Strecke, auf der zuletzt eine Route angelegt wurde: der wieder geöffnete Editor klappt sie gleich auf. */
    private static String reopenAt;

    private final List<Strecke> strecken;
    private final Zugdaten zugdaten;
    private final List<CoremodClient.Abstellung> abstellungen;

    public RouteEditorScreen(List<Strecke> strecken, Zugdaten zugdaten, List<CoremodClient.Abstellung> abstellungen) {
        super("Routen-Editor");
        this.strecken = new ArrayList<>(strecken);
        this.zugdaten = zugdaten;
        this.abstellungen = abstellungen;

        for (int i = 0; i < strecken.size(); i++) {
            if (strecken.get(i).name().equals(reopenAt)) open = i;
        }
        reopenAt = null;
    }

    // ---------------------------------------------------------------- Ansicht

    @Override
    protected int folderCount() {
        return strecken.size();
    }

    @Override
    protected int childCount(int folder) {
        return strecken.get(folder).routes().size();
    }

    @Override
    protected String folderSearchText(int folder) {
        return strecken.get(folder).name();
    }

    @Override
    protected String childSearchText(int folder, int child) {
        Route r = strecken.get(folder).routes().get(child);
        Zugdaten.Garnitur garnitur = zugdaten.garnitur(r.garnitur());
        String abstellung = CoremodClient.Abstellung.name(abstellungen, r.abstellung());
        List<String> parts = new ArrayList<>(List.of(r.number(), r.name(), garnitur == null ? "" : garnitur.name(), abstellung == null ? "" : abstellung));
        r.stops().forEach(stop -> parts.add(stop.name()));
        return String.join(" ", parts);
    }

    @Override
    protected void renderFolder(GuiGraphics graphics, int folder, int x, int top) {
        Strecke strecke = strecken.get(folder);
        graphics.drawString(font, Component.literal(strecke.name()).withStyle(ChatFormatting.BOLD), x, top + 4, 0xFFFFFF);
        graphics.drawString(font, gray(routeCount(strecke.routes().size())), x, top + 15, 0xFFFFFF);
    }

    @Override
    protected void renderChild(GuiGraphics graphics, int folder, int child, int x, int top) {
        Route route = strecken.get(folder).routes().get(child);
        int stops = route.stops().size();
        graphics.drawString(font, Component.empty()
                .append(Component.literal(route.name()).withStyle(ChatFormatting.BOLD))
                .append("  " + route.fromTo())
                .append(gray("  (" + (stops == 1 ? "1 Halt" : stops + " Halte") + ")")), x, top + 4, 0xFFFFFF);

        Zugdaten.Garnitur garnitur = zugdaten.garnitur(route.garnitur());
        String abstellung = CoremodClient.Abstellung.name(abstellungen, route.abstellung());
        MutableComponent second = gray("TrainOS " + route.number() + "  ·  ")
                .append(garnitur == null ? missing("Keine Garnitur") : white(garnitur.name()))
                .append(gray("  ·  ab "))
                .append(abstellung == null ? missing("keine Abstellung") : white(abstellung));
        graphics.drawString(font, second, x, top + 15, 0xFFFFFF);
    }

    private static Component white(String text) {
        return Component.literal(text).withStyle(ChatFormatting.WHITE);
    }

    private static Component missing(String text) {
        return Component.literal(text).withStyle(ChatFormatting.RED);
    }

    @Override
    protected List<Action> actions() {
        if (open < 0) {
            return List.of(
                    new Action("Strecke hinzufügen", this::add, () -> true),
                    new Action("Strecke löschen", this::delete, () -> selected >= 0),
                    new Action("Strecke bearbeiten", this::edit, () -> selected >= 0));
        }
        return List.of(
                new Action("Route hinzufügen", this::add, () -> true),
                new Action("Route löschen", this::delete, () -> selected >= 0),
                new Action("Route bearbeiten", this::edit, () -> selected >= 0));
    }

    @Override
    protected String searchHint() {
        return open < 0 ? "Strecken suchen …" : "Routen suchen …";
    }

    // ---------------------------------------------------------------- Aktionen

    private void add() {
        if (open < 0) {
            minecraft.setScreen(new FormScreen(this, "Strecke hinzufügen", List.of("Name"), List.of(""), values -> {
                String error = streckeError(values.getFirst(), -1);
                if (error != null) return error;
                strecken.add(new Strecke(values.getFirst(), List.of()));
                selected = strecken.size() - 1;
                save();
                return null;
            }));
            return;
        }

        // Schritt 1: Formular; Haltepunkte und Abstellung folgen in der Welt, der Server legt die Route dann an
        String strecke = strecken.get(open).name();
        minecraft.setScreen(new FormScreen(this, "Route hinzufügen", ROUTE_FIELDS, List.of("", ""), values -> {
            String error = routeError(values, -1);
            if (error != null) return error;
            reopenAt = strecke;
            PacketDistributor.sendToServer(new CoremodClient.StartRoute(strecke, values.get(0), values.get(1), values.get(2)));
            minecraft.tell(() -> minecraft.setScreen(null));
            return null;
        }).choice("Garnitur", garniturOptions(), ""));
    }

    private void edit() {
        if (selected < 0) return;
        if (open < 0) {
            Strecke strecke = strecken.get(selected);
            minecraft.setScreen(new FormScreen(this, "Strecke bearbeiten", List.of("Name"), List.of(strecke.name()), values -> {
                String error = streckeError(values.getFirst(), selected);
                if (error != null) return error;
                strecken.set(selected, new Strecke(values.getFirst(), strecke.routes()));
                save();
                return null;
            }));
            return;
        }

        // Haltepunkte und Abstellung bleiben; sie neu zu setzen heißt: Route löschen und neu anlegen
        Route route = strecken.get(open).routes().get(selected);
        String garnitur = zugdaten.garnitur(route.garnitur()) == null ? "" : route.garnitur();
        minecraft.setScreen(new FormScreen(this, "Route bearbeiten", ROUTE_FIELDS, List.of(route.name(), route.number()), values -> {
            String error = routeError(values, selected);
            if (error != null) return error;
            List<Route> routes = new ArrayList<>(strecken.get(open).routes());
            routes.set(selected, new Route(values.get(1), values.get(0), values.get(2), route.abstellung(), route.stops()));
            strecken.set(open, new Strecke(strecken.get(open).name(), routes));
            save();
            return null;
        }).choice("Garnitur", garniturOptions(), garnitur));
    }

    private List<PickerScreen.Option> garniturOptions() {
        List<PickerScreen.Option> options = new ArrayList<>();
        options.add(new PickerScreen.Option("", Component.literal("Keine Garnitur"), gray("Kann später gewählt werden"), ""));
        for (Zugdaten.Garnitur garnitur : zugdaten.garnituren()) {
            options.add(new PickerScreen.Option(garnitur.id(),
                    Component.literal(garnitur.name()).withStyle(ChatFormatting.BOLD),
                    gray(zugdaten.composition(garnitur) + " · ").append(zugdaten.summary(garnitur)), ""));
        }
        return options;
    }

    private void delete() {
        if (selected < 0) return;
        Component title;
        Component message;
        if (open < 0) {
            Strecke strecke = strecken.get(selected);
            title = Component.literal("Strecke \"" + strecke.name() + "\" löschen?");
            message = Component.literal(routeCount(strecke.routes().size()) + " werden mitgelöscht.");
        } else {
            Route route = strecken.get(open).routes().get(selected);
            title = Component.literal("Route " + route.name() + " (" + route.number() + ") löschen?");
            message = Component.literal(route.fromTo());
        }

        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                if (open < 0) {
                    strecken.remove(selected);
                } else {
                    List<Route> routes = new ArrayList<>(strecken.get(open).routes());
                    routes.remove(selected);
                    strecken.set(open, new Strecke(strecken.get(open).name(), routes));
                }
                selected = -1;
                save();
            }
            minecraft.setScreen(this);
        }, title, message));
    }

    private void save() {
        PacketDistributor.sendToServer(new CoremodClient.SaveStrecken(List.copyOf(strecken)));
    }

    /** Streckennamen sind eindeutig: darüber findet der Server die Strecke einer neuen Route wieder. */
    private String streckeError(String name, int except) {
        if (name.isEmpty()) return "Bitte einen Namen eingeben.";
        for (int i = 0; i < strecken.size(); i++) {
            if (i != except && strecken.get(i).name().equals(name)) return "Die Strecke \"" + name + "\" gibt es schon.";
        }
        return null;
    }

    /** Fehlertext fürs Formular oder null. {@code except} = bearbeitete Route, die ihre eigene Nummer behalten darf. */
    private String routeError(List<String> values, int except) {
        if (values.get(0).isEmpty()) return "Bitte einen Namen eingeben.";
        String number = values.get(1);
        // Die Zugnummer beim Start ist diese Nummer plus Abfahrtsminute, zusammen fünfstellig
        if (!number.matches("\\d{3}")) return "Die TrainOS-Routennummer ist dreistellig, z.B. 384.";

        for (int i = 0; i < strecken.size(); i++) {
            List<Route> routes = strecken.get(i).routes();
            for (int j = 0; j < routes.size(); j++) {
                if ((i != open || j != except) && routes.get(j).number().equals(number)) {
                    return "Route " + number + " gibt es schon (" + strecken.get(i).name() + ").";
                }
            }
        }
        return null;
    }

    private static String routeCount(int count) {
        return count == 0 ? "Keine Routen" : count == 1 ? "1 Route" : count + " Routen";
    }
}
