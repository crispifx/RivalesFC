package com.rivalesfc.game.desktop;

import com.rivalesfc.game.input.Gamepads;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;

public class GlfwGamepads implements Gamepads.Provider {

    private GLFWGamepadState gs;
    private final int[] jids = new int[Gamepads.MAX];
    private int count = 0;

    @Override
    public int refresh() {
        if (gs == null) {
            gs = GLFWGamepadState.create();
        }
        int n = 0;
        for (int jid = GLFW.GLFW_JOYSTICK_1; jid <= GLFW.GLFW_JOYSTICK_LAST && n < Gamepads.MAX; jid++) {
            if (GLFW.glfwJoystickPresent(jid) && GLFW.glfwJoystickIsGamepad(jid)) {
                jids[n++] = jid;
            }
        }
        count = n;
        return n;
    }

    @Override
    public boolean read(int index, Gamepads.Raw out) {
        if (gs == null || index < 0 || index >= count) return false;
        if (!GLFW.glfwGetGamepadState(jids[index], gs)) return false;
        out.lx = gs.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_X);
        out.ly = -gs.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y);
        out.lt = gs.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER);
        out.rt = gs.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER);
        out.a = pressed(GLFW.GLFW_GAMEPAD_BUTTON_A);
        out.b = pressed(GLFW.GLFW_GAMEPAD_BUTTON_B);
        out.x = pressed(GLFW.GLFW_GAMEPAD_BUTTON_X);
        out.y = pressed(GLFW.GLFW_GAMEPAD_BUTTON_Y);
        out.lb = pressed(GLFW.GLFW_GAMEPAD_BUTTON_LEFT_BUMPER);
        out.rb = pressed(GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER);
        out.start = pressed(GLFW.GLFW_GAMEPAD_BUTTON_START);
        out.up = pressed(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_UP);
        out.down = pressed(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_DOWN);
        out.left = pressed(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_LEFT);
        out.right = pressed(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_RIGHT);
        return true;
    }

    private boolean pressed(int button) {
        return gs.buttons(button) == GLFW.GLFW_PRESS;
    }
}
