package com.mapter.aeroclaims.screen;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class CursorHelper {
    private static double savedX = Double.NaN;
    private static double savedY = Double.NaN;

    public static void saveCursor() {
        Minecraft mc = Minecraft.getInstance();
        savedX = mc.mouseHandler.xpos();
        savedY = mc.mouseHandler.ypos();
    }

    public static void restoreCursor() {
        if (Double.isNaN(savedX) || Double.isNaN(savedY)) return;
        Minecraft mc = Minecraft.getInstance();
        long window = mc.getWindow().getWindow();
        GLFW.glfwSetCursorPos(window, savedX, savedY);
        savedX = Double.NaN;
        savedY = Double.NaN;
    }
}
