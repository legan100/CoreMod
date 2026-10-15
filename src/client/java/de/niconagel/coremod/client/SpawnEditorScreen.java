package de.niconagel.coremod.client;

import de.niconagel.coremod.client.CoremodClient.Abstellung;
import de.niconagel.coremod.client.CoremodClient.Gleis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Abstellungs-Editor (/mainmenu routes spawns): Abstellungen als Ordner, darunter ihre Gleise, auf denen Züge eingesetzt
 * werden können. Ein neues Gleis setzt man in der Welt: der Editor schließt sich, per Rechtsklick kommen Anfang und Ende
 * auf eine MR-Spline (siehe {@link Weltauswahl}), danach öffnet der Server den Editor wieder.
 */
public class SpawnEditorScreen extends TreeEditorScreen {

    /** Abstellung, in der zuletzt ein Gleis gesetzt wurde: der wieder geöffnete Editor klappt sie gleich auf. */
    private static String reopenAt;

    private final List<Abstellung> abstellungen;
    private final List<String> broken;

    public SpawnEditorScreen(List<Abstellung> abstellungen, List<String> broken) {
        super("Abstellungs-Editor");
        this.abstellungen = new ArrayList<>(abstellungen);
        this.broken = broken;

        for (int i = 0; i < abstellungen.size(); i++) {
            if (abstellungen.get(i).id().equals(reopenAt)) open = i;
        }
        reopenAt = null;
    }

    // ---------------------------------------------------------------- Ansicht

    @Override
    protected int folderCount() {
        return abstellungen.size();
    }

    @Override
    protected int childCount(int folder) {
        return abstellungen.get(folder).gleise().size();
    }

    @Override
    protected String folderSearchText(int folder) {
        return abstellungen.get(folder).name();
    }

    @Override
    protected String childSearchText(int folder, int child) {
        Gleis gleis = abstellungen.get(folder).gleise().get(child);
        return gleis.name() + " " + gleis.dimension();
    }

    @Override
    protected void renderFolder(GuiGraphics graphics, int folder, int x, int top) {
        Abstellung abstellung = abstellungen.get(folder);
        int count = abstellung.gleise().size();
        double length = abstellung.gleise().stream().mapToDouble(Gleis::length).sum();
        long invalid = abstellung.gleise().stream().filter(gleis -> broken.contains(gleis.id())).count();

        graphics.drawString(font, Component.literal(abstellung.name()).withStyle(ChatFormatting.BOLD), x, top + 4, 0xFFFFFF);
        Component second = gray((count == 0 ? "Keine Gleise" : count == 1 ? "1 Gleis" : count + " Gleise")
                + (count == 0 ? "" : " · " + Zugdaten.number(length) + " m"));
        if (invalid > 0) {
            second = second.copy().append(Component.literal(" · " + invalid + " ungültig").withStyle(ChatFormatting.RED));
        }
        graphics.drawString(font, second, x, top + 15, 0xFFFFFF);
    }

    @Override
    protected void renderChild(GuiGraphics graphics, int folder, int child, int x, int top) {
        Gleis gleis = abstellungen.get(folder).gleise().get(child);
        graphics.drawString(font, Component.empty()
                .append(Component.literal(gleis.name()).withStyle(ChatFormatting.BOLD))
                .append(gray("  " + Zugdaten.number(gleis.length()) + " m")), x, top + 4, 0xFFFFFF);

        Component second = broken.contains(gleis.id())
                ? Component.literal("Die Spline gibt es nicht mehr (umgebaut?) – Gleis löschen und neu setzen").withStyle(ChatFormatting.RED)
                : gray("Anfang " + coordinates(gleis.start()) + " → Ende " + coordinates(gleis.end()) + " · " + gleis.dimension());
        graphics.drawString(font, second, x, top + 15, 0xFFFFFF);
    }

    private static String coordinates(CoremodClient.Gleispunkt punkt) {
        return Math.round(punkt.x()) + ", " + Math.round(punkt.y()) + ", " + Math.round(punkt.z());
    }

    @Override
    protected List<Action> actions() {
        if (open < 0) {
            return List.of(
                    new Action("Abstellung hinzufügen", this::addAbstellung, () -> true),
                    new Action("Abstellung löschen", this::deleteAbstellung, () -> selected >= 0),
                    new Action("Abstellung bearbeiten", this::renameAbstellung, () -> selected >= 0));
        }
        return List.of(
                new Action("Gleis hinzufügen", this::addGleis, () -> true),
                new Action("Gleis löschen", this::deleteGleis, () -> selected >= 0),
                new Action("Gleis bearbeiten", this::renameGleis, () -> selected >= 0));
    }

    @Override
    protected String searchHint() {
        return open < 0 ? "Abstellungen suchen …" : "Gleise suchen …";
    }

    // ---------------------------------------------------------------- Abstellungen

    private void addAbstellung() {
        minecraft.setScreen(new FormScreen(this, "Abstellung hinzufügen", List.of("Name"), List.of(""), values -> {
            if (values.getFirst().isEmpty()) return "Bitte einen Namen eingeben.";
            abstellungen.add(new Abstellung(UUID.randomUUID().toString().substring(0, 8), values.getFirst(), List.of()));
            selected = abstellungen.size() - 1;
            save();
            return null;
        }));
    }

    private void renameAbstellung() {
        if (selected < 0) return;
        Abstellung abstellung = abstellungen.get(selected);
        minecraft.setScreen(new FormScreen(this, "Abstellung bearbeiten", List.of("Name"), List.of(abstellung.name()), values -> {
            if (values.getFirst().isEmpty()) return "Bitte einen Namen eingeben.";
            abstellungen.set(selected, new Abstellung(abstellung.id(), values.getFirst(), abstellung.gleise()));
            save();
            return null;
        }));
    }

    private void deleteAbstellung() {
        if (selected < 0) return;
        Abstellung abstellung = abstellungen.get(selected);
        int count = abstellung.gleise().size();
        confirm(Component.literal("Abstellung \"" + abstellung.name() + "\" löschen?"),
                Component.literal(count == 0 ? "Sie hat keine Gleise." : (count == 1 ? "1 Gleis wird" : count + " Gleise werden") + " mitgelöscht."),
                () -> abstellungen.remove(selected));
    }

    // ---------------------------------------------------------------- Gleise der aufgeklappten Abstellung

    /** Name abfragen, dann Editor zu und in der Welt Anfang und Ende per Rechtsklick setzen. */
    private void addGleis() {
        Abstellung abstellung = abstellungen.get(open);
        String suggestion = "Gleis " + (abstellung.gleise().size() + 1);
        minecraft.setScreen(new FormScreen(this, "Gleis hinzufügen", List.of("Name"), List.of(suggestion), values -> {
            if (values.getFirst().isEmpty()) return "Bitte einen Namen eingeben.";
            reopenAt = abstellung.id();
            PacketDistributor.sendToServer(new CoremodClient.StartGleis(abstellung.id(), values.getFirst()));
            // Statt zurück in den Editor direkt in die Welt; der Server öffnet ihn nach dem Setzen wieder
            minecraft.tell(() -> minecraft.setScreen(null));
            return null;
        }));
    }

    private void renameGleis() {
        if (selected < 0) return;
        Gleis gleis = gleise().get(selected);
        minecraft.setScreen(new FormScreen(this, "Gleis bearbeiten", List.of("Name"), List.of(gleis.name()), values -> {
            if (values.getFirst().isEmpty()) return "Bitte einen Namen eingeben.";
            List<Gleis> gleise = new ArrayList<>(gleise());
            gleise.set(selected, new Gleis(gleis.id(), values.getFirst(), gleis.dimension(), gleis.start(), gleis.end(), gleis.length()));
            setGleise(gleise);
            save();
            return null;
        }));
    }

    private void deleteGleis() {
        if (selected < 0) return;
        Gleis gleis = gleise().get(selected);
        confirm(Component.literal("Gleis \"" + gleis.name() + "\" löschen?"),
                Component.literal(Zugdaten.number(gleis.length()) + " m, Anfang " + coordinates(gleis.start())),
                () -> {
                    List<Gleis> gleise = new ArrayList<>(gleise());
                    gleise.remove(selected);
                    setGleise(gleise);
                });
    }

    private List<Gleis> gleise() {
        return abstellungen.get(open).gleise();
    }

    private void setGleise(List<Gleis> gleise) {
        Abstellung abstellung = abstellungen.get(open);
        abstellungen.set(open, new Abstellung(abstellung.id(), abstellung.name(), gleise));
    }

    private void confirm(Component title, Component message, Runnable delete) {
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                delete.run();
                selected = -1;
                save();
            }
            minecraft.setScreen(this);
        }, title, message));
    }

    private void save() {
        PacketDistributor.sendToServer(new CoremodClient.SaveAbstellungen(List.copyOf(abstellungen)));
    }
}
