package de.niconagel.coremod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/**
 * Spawn und Bereich eines Stellwerks in der Welt setzen (Gegenstück zu StellwerkAuswahl auf dem Server). Man geht hin
 * und drückt Enter: Spawn, erste Ecke, gegenüberliegende Ecke. Dabei steht der Quader von der ersten Ecke bis zum Block,
 * auf dem man steht, mit einem Raster aus ganzen Blöcken in der Welt. Rücktaste geht einen Schritt zurück, Schleichen +
 * Enter bricht ab.
 */
@EventBusSubscriber(modid = CoremodClient.MOD_ID, value = Dist.CLIENT)
public final class BereichAuswahl {

    // wie StellwerkAuswahl in der Server-Mod
    private static final int SET = 0;
    private static final int UNDO = 1;
    private static final int CANCEL = 2;
    private static final int SPAWN = 1;
    private static final int CORNER_A = 2;

    /** Mehr Rasterlinien je Achse werden zu viele; dann nur jede n-te. */
    private static final int MAX_GRID = 64;

    private static CoremodClient.BereichState state = CoremodClient.BereichState.INACTIVE;

    private BereichAuswahl() {
    }

    static void update(CoremodClient.BereichState newState) {
        state = newState;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        state = CoremodClient.BereichState.INACTIVE;
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (state.step() == 0 || minecraft.screen != null || minecraft.player == null || event.getAction() != GLFW.GLFW_PRESS) return;

        if (event.getKey() == GLFW.GLFW_KEY_ENTER || event.getKey() == GLFW.GLFW_KEY_KP_ENTER) {
            send(minecraft.player.isShiftKeyDown() ? CANCEL : SET);
        } else if (event.getKey() == GLFW.GLFW_KEY_BACKSPACE) {
            send(UNDO);
        }
    }

    private static void send(int action) {
        PacketDistributor.sendToServer(new CoremodClient.BereichKey(action));
    }

    /** Der Block, auf dem der Spieler steht; so nimmt ihn auch der Server. */
    private static BlockPos here() {
        return Minecraft.getInstance().player.blockPosition();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (state.step() == 0 || minecraft.player == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Vec3 eye = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-eye.x, -eye.y, -eye.z);
        PoseStack.Pose pose = poseStack.last();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        if (!state.spawn().isEmpty()) Weltauswahl.marker(lines, pose, state.spawn().getFirst(), 0x55FF55);

        // Ab der ersten Ecke der ganze Quader bis hierher, davor nur der Block, der die erste Ecke würde
        if (state.step() >= CORNER_A) {
            BlockPos here = here();
            BlockPos a = state.corner().isEmpty() ? here
                    : BlockPos.containing(state.corner().getFirst().x(), state.corner().getFirst().y(), state.corner().getFirst().z());
            box(lines, pose, Math.min(a.getX(), here.getX()), Math.min(a.getY(), here.getY()), Math.min(a.getZ(), here.getZ()),
                    Math.max(a.getX(), here.getX()) + 1, Math.max(a.getY(), here.getY()) + 1, Math.max(a.getZ(), here.getZ()) + 1);
        }

        poseStack.popPose();
        buffers.endBatch(RenderType.lines());
    }

    @SubscribeEvent
    public static void onRenderLabels(RenderLevelStageEvent event) {
        if (state.step() == 0 || state.spawn().isEmpty() || event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        Weltauswahl.label(Weltauswahl.labelPose(event), buffers, event.getCamera(), state.spawn().getFirst(),
                Component.literal("Spawn"), 3.0f, 1);
        buffers.endBatch();
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (state.step() == 0 || minecraft.player == null) return;
        GuiGraphics graphics = event.getGuiGraphics();
        int x = graphics.guiWidth() / 2;

        String step = switch (state.step()) {
            case SPAWN -> "Stell dich an den Spawn, schau in die Startrichtung und drück Enter";
            case CORNER_A -> "Geh zur ersten Ecke des begehbaren Bereichs und drück Enter";
            default -> {
                Vector3f a = state.corner().getFirst();
                BlockPos here = here();
                yield "Geh zur gegenüberliegenden Ecke und drück Enter  ("
                        + (Math.abs(here.getX() - (int) a.x()) + 1) + " × " + (Math.abs(here.getY() - (int) a.y()) + 1) + " × "
                        + (Math.abs(here.getZ() - (int) a.z()) + 1) + " Blöcke)";
            }
        };
        String keys = (state.step() > SPAWN ? "Rücktaste: Schritt zurück · " : "") + "Schleichen + Enter: abbrechen";

        graphics.drawCenteredString(minecraft.font, Component.literal("Stellwerk \"" + state.name() + "\" einrichten")
                .withStyle(ChatFormatting.BOLD), x, 12, 0xFFFFFF);
        graphics.drawCenteredString(minecraft.font, Component.literal(step).withStyle(ChatFormatting.YELLOW), x, 24, 0xFFFFFF);
        graphics.drawCenteredString(minecraft.font, Component.literal(keys).withStyle(ChatFormatting.GRAY), x, 36, 0xFFFFFF);
    }

    /** Quader von (x0, y0, z0) bis (x1, y1, z1), Raster auf allen sechs Seiten, Kanten kräftiger. */
    private static void box(VertexConsumer lines, PoseStack.Pose pose, int x0, int y0, int z0, int x1, int y1, int z1) {
        int grid = 0x55FFFF;
        int sx = Math.max(1, (x1 - x0) / MAX_GRID);
        int sy = Math.max(1, (y1 - y0) / MAX_GRID);
        int sz = Math.max(1, (z1 - z0) / MAX_GRID);

        // Linien in X-Richtung: auf Boden/Decke je Z, auf den Seiten in Z je Y
        for (int z = z0; z <= z1; z += sz) {
            line(lines, pose, x0, y0, z, x1, y0, z, grid, 90);
            line(lines, pose, x0, y1, z, x1, y1, z, grid, 90);
        }
        for (int y = y0; y <= y1; y += sy) {
            line(lines, pose, x0, y, z0, x1, y, z0, grid, 90);
            line(lines, pose, x0, y, z1, x1, y, z1, grid, 90);
        }
        // in Y-Richtung: auf den Seiten in Z je X, auf den Seiten in X je Z
        for (int x = x0; x <= x1; x += sx) {
            line(lines, pose, x, y0, z0, x, y1, z0, grid, 90);
            line(lines, pose, x, y0, z1, x, y1, z1, grid, 90);
        }
        for (int z = z0; z <= z1; z += sz) {
            line(lines, pose, x0, y0, z, x0, y1, z, grid, 90);
            line(lines, pose, x1, y0, z, x1, y1, z, grid, 90);
        }
        // in Z-Richtung: auf Boden/Decke je X, auf den Seiten in X je Y
        for (int x = x0; x <= x1; x += sx) {
            line(lines, pose, x, y0, z0, x, y0, z1, grid, 90);
            line(lines, pose, x, y1, z0, x, y1, z1, grid, 90);
        }
        for (int y = y0; y <= y1; y += sy) {
            line(lines, pose, x0, y, z0, x0, y, z1, grid, 90);
            line(lines, pose, x1, y, z0, x1, y, z1, grid, 90);
        }

        // Die zwölf Kanten
        int edge = 0xFFFF55;
        for (int y : new int[]{y0, y1}) {
            line(lines, pose, x0, y, z0, x1, y, z0, edge, 255);
            line(lines, pose, x0, y, z1, x1, y, z1, edge, 255);
            line(lines, pose, x0, y, z0, x0, y, z1, edge, 255);
            line(lines, pose, x1, y, z0, x1, y, z1, edge, 255);
        }
        for (int x : new int[]{x0, x1}) {
            for (int z : new int[]{z0, z1}) line(lines, pose, x, y0, z, x, y1, z, edge, 255);
        }
    }

    private static void line(VertexConsumer lines, PoseStack.Pose pose, float ax, float ay, float az, float bx, float by, float bz, int rgb, int alpha) {
        Vector3f normal = new Vector3f(bx - ax, by - ay, bz - az);
        if (normal.lengthSquared() < 1.0E-8f) return;
        normal.normalize();
        lines.addVertex(pose, ax, ay, az).setColor(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, alpha).setNormal(pose, normal.x(), normal.y(), normal.z());
        lines.addVertex(pose, bx, by, bz).setColor(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, alpha).setNormal(pose, normal.x(), normal.y(), normal.z());
    }
}
