/* Copyright (c) 2016 AlexIIL and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.robotics.gui;

import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.robotics.ZonePlannerMapDataClient;
import buildcraft.robotics.container.ContainerZonePlanner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.apache.commons.lang3.tuple.Pair;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.util.glu.GLU;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class GuiZonePlanner extends GuiBC8<ContainerZonePlanner> {
    private static final ResourceLocation TEXTURE_BASE = new ResourceLocation("buildcraftrobotics:textures/gui/zone_planner.png");
    private static final int SIZE_X = 256, SIZE_Y = 228;
    private static final GuiIcon ICON_GUI = new GuiIcon(TEXTURE_BASE, 0, 0, SIZE_X, SIZE_Y);
    private Map<Pair<Integer, Integer>, Integer> chunkListIndexes = new HashMap<>();
    private float startMouseX = 0;
    private float startMouseY = 0;
    private float startPositionX = 0;
    private float startPositionZ = 0;
    private float camY = 0;
    private float scaleSpeed = 0;
    private float positionX = 0;
    private float positionZ = 0;

    public GuiZonePlanner(ContainerZonePlanner container) {
        super(container);
        xSize = SIZE_X;
        ySize = SIZE_Y;
    }

    private static void vertex(double x, double y, double z) {
        GL11.glVertex3d(x, y, z);
    }

    public void renderCube(double x, double y, double z) {
        double rX = 1;
        double rY = 1;
        double rZ = 1;

        GL11.glNormal3d(0, 1, 0);
        vertex(x - rX, y + rY, z + rZ);
        vertex(x + rX, y + rY, z + rZ);
        vertex(x + rX, y + rY, z - rZ);
        vertex(x - rX, y + rY, z - rZ);

        GL11.glNormal3d(0, -1, 0);
        vertex(x - rX, y - rY, z - rZ);
        vertex(x + rX, y - rY, z - rZ);
        vertex(x + rX, y - rY, z + rZ);
        vertex(x - rX, y - rY, z + rZ);

        GL11.glNormal3d(-1, 0, 0);
        vertex(x - rX, y - rY, z + rZ);
        vertex(x - rX, y + rY, z + rZ);
        vertex(x - rX, y + rY, z - rZ);
        vertex(x - rX, y - rY, z - rZ);

        GL11.glNormal3d(1, 0, 0);
        vertex(x + rX, y - rY, z - rZ);
        vertex(x + rX, y + rY, z - rZ);
        vertex(x + rX, y + rY, z + rZ);
        vertex(x + rX, y - rY, z + rZ);

        GL11.glNormal3d(0, 0, -1);
        vertex(x - rX, y - rY, z - rZ);
        vertex(x - rX, y + rY, z - rZ);
        vertex(x + rX, y + rY, z - rZ);
        vertex(x + rX, y - rY, z - rZ);

        GL11.glNormal3d(0, 0, 1);
        vertex(x + rX, y - rY, z + rZ);
        vertex(x + rX, y + rY, z + rZ);
        vertex(x - rX, y + rY, z + rZ);
        vertex(x - rX, y - rY, z + rZ);
    }

    @SuppressWarnings("PointlessBitwiseExpression")
    private int drawChunk(int chunkX, int chunkZ) {
        Pair<Integer, Integer> chunkPosPair = Pair.of(chunkX, chunkZ);
        if(chunkListIndexes.containsKey(chunkPosPair)) {
            return chunkListIndexes.get(chunkPosPair);
        }
        int listIndexEmpty = GL11.glGenLists(1);
        GL11.glNewList(listIndexEmpty, GL11.GL_COMPILE);
        // noting, wait for chunk data
        GL11.glEndList();
        chunkListIndexes.put(chunkPosPair, listIndexEmpty);
        ZonePlannerMapDataClient.instance.getChunk(container.tile.getWorld(), chunkX, chunkZ, zonePlannerMapChunk -> {
            int listIndex = GL11.glGenLists(1);
            GL11.glNewList(listIndex, GL11.GL_COMPILE);
            GL11.glBegin(GL11.GL_QUADS);
            for(BlockPos pos : zonePlannerMapChunk.data.keySet()) {
                int color = zonePlannerMapChunk.data.get(pos);
                int r = (color >> 16) & 0xFF;
                int g = (color >> 8) & 0xFF;
                int b = (color >> 0) & 0xFF;
                int a = (color >> 24) & 0xFF;
                GL11.glColor4d(r / (double)0xFF, g / (double)0xFF, b / (double)0xFF, a / (double)0xFF);
                renderCube(chunkX * 16 + pos.getX(), pos.getY(), chunkZ * 16 + pos.getZ());
            }
            GL11.glEnd();
            GL11.glEndList();
            chunkListIndexes.put(chunkPosPair, listIndex);
        });
        return listIndexEmpty;
    }

    @Override
    public void handleMouseInput() throws IOException {
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scaleSpeed -= wheel / 50F;
        }
        super.handleMouseInput();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        startPositionX = positionX;
        startPositionZ = positionZ;
        startMouseX = mouseX;
        startMouseY = mouseY;
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        float deltaX = startMouseX - mouseX;
        float deltaY = startMouseY - mouseY;
        float s = 0.3F;
        positionX = startPositionX - deltaX * s;
        positionZ = startPositionZ - deltaY * s;
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void drawBackgroundLayer(float partialTicks) {
        ICON_GUI.drawAt(rootElement);
    }

    @Override
    protected void drawForegroundLayer() {
        camY += scaleSpeed;
        scaleSpeed *= 0.7F;
        int x = guiLeft;
        int y = guiTop;
        int offsetX = 8;
        int offsetY = 9;
        int sizeX = 213;
        int sizeY = 100;
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT/* | GL11.GL_COLOR_BUFFER_BIT*/); // TODO: remove
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        ScaledResolution scaledResolution = new ScaledResolution(Minecraft.getMinecraft());
        GL11.glViewport(
                (x + offsetX) * scaledResolution.getScaleFactor(),
                Minecraft.getMinecraft().displayHeight - (sizeY + y + offsetY) * scaledResolution.getScaleFactor(),
                sizeX * scaledResolution.getScaleFactor(),
                sizeY * scaledResolution.getScaleFactor()
        );
        GL11.glScalef(scaledResolution.getScaleFactor(), scaledResolution.getScaleFactor(), 1);
        GLU.gluPerspective(70.0F, (float) sizeX / sizeY, 1F, 1000.0F);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        RenderHelper.enableStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glRotatef(90, 1, 0, 0);
        BlockPos tilePos = container.tile.getPos();
        GL11.glTranslatef(-tilePos.getX() + positionX, -camY - 256, -tilePos.getZ() + positionZ);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_BLEND); // FIXME: blending
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        for(int chunkX = (tilePos.getX() >> 4) - 8; chunkX < (tilePos.getX() >> 4) + 8; chunkX++) {
            for(int chunkZ = (tilePos.getZ() >> 4) - 8; chunkZ < (tilePos.getZ() >> 4) + 8; chunkZ++) {
                GL11.glCallList(drawChunk(chunkX, chunkZ));
            }
        }
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glViewport(0, 0, Minecraft.getMinecraft().displayWidth, Minecraft.getMinecraft().displayHeight);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        RenderHelper.disableStandardItemLighting();
    }
}
