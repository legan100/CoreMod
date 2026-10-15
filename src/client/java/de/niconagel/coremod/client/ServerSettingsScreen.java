package de.niconagel.coremod.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Server-Einstellungen aus dem Hauptmenü. Noch ohne Inhalt, welche Einstellungen es gibt, steht noch aus. */
public class ServerSettingsScreen extends Screen {

    private final Screen parent;

    public ServerSettingsScreen(Screen parent) {
        super(Component.literal("Einstellungen"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Zurück"), button -> onClose())
                .bounds(width / 2 - 100, height - 40, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 30, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.literal("Noch keine Einstellungen verfügbar.").withStyle(ChatFormatting.GRAY),
                width / 2, height / 2 - 4, 0xFFFFFF);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        MainMenuScreen.renderWorldBackground(graphics, width, height);
    }

    /** ESC und "Zurück" führen ins Hauptmenü, nicht ins Spiel. */
    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
