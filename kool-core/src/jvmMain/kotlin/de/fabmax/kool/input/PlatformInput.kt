package de.fabmax.kool.input

import de.fabmax.kool.KoolSystem
import de.fabmax.kool.platform.Lwjgl3Context
import de.fabmax.kool.util.logD
import org.lwjgl.glfw.GLFW

internal actual object PlatformInput {

    var isMouseOverWindow = false
        private set

    private val localCharKeyCodes = mutableMapOf<Int, Int>()
    private val cursorShapes = mutableMapOf<CursorShape, Long>()
    private var currentCursorShape = CursorShape.DEFAULT

    actual fun setCursorMode(cursorMode: CursorMode) {
        val ctx = KoolSystem.getContextOrNull() as Lwjgl3Context? ?: return
        val windowHandle = ctx.renderBackend.glfwWindow.windowPtr

        if (cursorMode == CursorMode.NORMAL || ctx.isWindowFocused) {
            GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_CURSOR, cursorMode.glfwMode)
        }
    }

    actual fun applyCursorShape(cursorShape: CursorShape) {
        val ctx = KoolSystem.requireContext() as Lwjgl3Context? ?: return
        val windowHandle = ctx.renderBackend.glfwWindow.windowPtr

        if (cursorShape != currentCursorShape) {
            GLFW.glfwSetCursor(windowHandle, cursorShapes[cursorShape] ?: 0L)
            currentCursorShape = cursorShape
        }
    }

    fun onContextCreated(ctx: Lwjgl3Context) {
        deriveLocalKeyCodes()
        createStandardCursors()

        val windowHandle = ctx.renderBackend.glfwWindow.windowPtr
        installInputHandlers(windowHandle)

        ctx.onWindowFocusChanged += {
            if (PointerInput.cursorMode == CursorMode.LOCKED) {
                if (!it.isWindowFocused) {
                    logD { "Switching to normal cursor mode because of focus loss" }
                    GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL)
                } else {
                    logD { "Re-engaging cursor-lock because of focus gain" }
                    GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED)
                }
            }
        }
    }

    private fun deriveLocalKeyCodes() {
        val printableKeys = mutableListOf<Int>()
        for (c in GLFW.GLFW_KEY_0..GLFW.GLFW_KEY_9) { printableKeys += c }
        for (c in GLFW.GLFW_KEY_A..GLFW.GLFW_KEY_Z) { printableKeys += c }
        for (c in GLFW.GLFW_KEY_KP_0..GLFW.GLFW_KEY_KP_9) { printableKeys += c }
        printableKeys += GLFW.GLFW_KEY_APOSTROPHE
        printableKeys += GLFW.GLFW_KEY_COMMA
        printableKeys += GLFW.GLFW_KEY_MINUS
        printableKeys += GLFW.GLFW_KEY_PERIOD
        printableKeys += GLFW.GLFW_KEY_SLASH
        printableKeys += GLFW.GLFW_KEY_SEMICOLON
        printableKeys += GLFW.GLFW_KEY_EQUAL
        printableKeys += GLFW.GLFW_KEY_LEFT_BRACKET
        printableKeys += GLFW.GLFW_KEY_RIGHT_BRACKET
        printableKeys += GLFW.GLFW_KEY_BACKSLASH
        //printableKeys += GLFW.GLFW_KEY_WORLD_1
        printableKeys += GLFW.GLFW_KEY_WORLD_2
        printableKeys += GLFW.GLFW_KEY_KP_DECIMAL
        printableKeys += GLFW.GLFW_KEY_KP_DIVIDE
        printableKeys += GLFW.GLFW_KEY_KP_MULTIPLY
        printableKeys += GLFW.GLFW_KEY_KP_SUBTRACT
        printableKeys += GLFW.GLFW_KEY_KP_ADD
        printableKeys += GLFW.GLFW_KEY_KP_EQUAL

        printableKeys.forEach { c ->
            val localName = GLFW.glfwGetKeyName(c, 0) ?: ""
            if (localName.isNotBlank()) {
                val localChar = localName[0].uppercaseChar()
                localCharKeyCodes[c] = localChar.code
            }
        }
    }

    private fun createStandardCursors() {
        cursorShapes[CursorShape.DEFAULT] = 0
        cursorShapes[CursorShape.TEXT] = GLFW.glfwCreateStandardCursor(GLFW.GLFW_IBEAM_CURSOR)
        cursorShapes[CursorShape.CROSSHAIR] = GLFW.glfwCreateStandardCursor(GLFW.GLFW_CROSSHAIR_CURSOR)
        cursorShapes[CursorShape.HAND] = GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR)
        cursorShapes[CursorShape.H_RESIZE] = GLFW.glfwCreateStandardCursor(GLFW.GLFW_HRESIZE_CURSOR)
        cursorShapes[CursorShape.V_RESIZE] = GLFW.glfwCreateStandardCursor(GLFW.GLFW_VRESIZE_CURSOR)
    }

    private fun installInputHandlers(windowHandle: Long) {
        // install mouse callbacks
        GLFW.glfwSetMouseButtonCallback(windowHandle) { _, btn, act, _ ->
            PointerInput.handleMouseButtonEvent(btn, act == GLFW.GLFW_PRESS)
        }
        GLFW.glfwSetCursorPosCallback(windowHandle) { _, x, y ->
            PointerInput.handleMouseMove(x, y)
        }
        GLFW.glfwSetCursorEnterCallback(windowHandle) { _, entered ->
            if (!entered) {
                isMouseOverWindow = false
                PointerInput.handleMouseExit()
            } else {
                isMouseOverWindow = true
            }
        }
        GLFW.glfwSetScrollCallback(windowHandle) { _, xOff, yOff ->
            PointerInput.handleMouseScroll(xOff, yOff)
        }

        // install keyboard callbacks
        GLFW.glfwSetKeyCallback(windowHandle) { _, key, _, action, mods ->
            val event = when (action) {
                GLFW.GLFW_PRESS -> KeyboardInput.KEY_EV_DOWN
                GLFW.GLFW_REPEAT -> KeyboardInput.KEY_EV_DOWN or KeyboardInput.KEY_EV_REPEATED
                GLFW.GLFW_RELEASE -> KeyboardInput.KEY_EV_UP
                else -> -1
            }
            if (event != -1) {
                val keyCode = KEY_CODE_MAP[key] ?: UniversalKeyCode(key)
                val localKeyCode = LocalKeyCode(localCharKeyCodes[keyCode.code] ?: keyCode.code)
                var keyMod = 0
                if (mods and GLFW.GLFW_MOD_ALT != 0) {
                    keyMod = keyMod or KeyboardInput.KEY_MOD_ALT
                }
                if (mods and GLFW.GLFW_MOD_CONTROL != 0) {
                    keyMod = keyMod or KeyboardInput.KEY_MOD_CTRL
                }
                if (mods and GLFW.GLFW_MOD_SHIFT != 0) {
                    keyMod = keyMod or KeyboardInput.KEY_MOD_SHIFT
                }
                if (mods and GLFW.GLFW_MOD_SUPER != 0) {
                    keyMod = keyMod or KeyboardInput.KEY_MOD_SUPER
                }
                KeyboardInput.handleKeyEvent(KeyEvent(keyCode, localKeyCode, event, keyMod))
            }
        }
        GLFW.glfwSetCharCallback(windowHandle) { _, codepoint ->
            KeyboardInput.handleCharTyped(codepoint.toChar())
        }
    }

    private val CursorMode.glfwMode: Int
        get() = when (this) {
            CursorMode.NORMAL -> GLFW.GLFW_CURSOR_NORMAL
            CursorMode.LOCKED -> GLFW.GLFW_CURSOR_DISABLED
        }


    val KEY_CODE_MAP: Map<Int, KeyCode> = mutableMapOf(
        GLFW.GLFW_KEY_LEFT_CONTROL to KeyboardInput.KEY_CTRL_LEFT,
        GLFW.GLFW_KEY_RIGHT_CONTROL to KeyboardInput.KEY_CTRL_RIGHT,
        GLFW.GLFW_KEY_LEFT_SHIFT to KeyboardInput.KEY_SHIFT_LEFT,
        GLFW.GLFW_KEY_RIGHT_SHIFT to KeyboardInput.KEY_SHIFT_RIGHT,
        GLFW.GLFW_KEY_LEFT_ALT to KeyboardInput.KEY_ALT_LEFT,
        GLFW.GLFW_KEY_RIGHT_ALT to KeyboardInput.KEY_ALT_RIGHT,
        GLFW.GLFW_KEY_LEFT_SUPER to KeyboardInput.KEY_SUPER_LEFT,
        GLFW.GLFW_KEY_RIGHT_SUPER to KeyboardInput.KEY_SUPER_RIGHT,
        GLFW.GLFW_KEY_ESCAPE to KeyboardInput.KEY_ESC,
        GLFW.GLFW_KEY_MENU to KeyboardInput.KEY_MENU,
        GLFW.GLFW_KEY_ENTER to KeyboardInput.KEY_ENTER,
        GLFW.GLFW_KEY_KP_ENTER to KeyboardInput.KEY_NP_ENTER,
        GLFW.GLFW_KEY_KP_DIVIDE to KeyboardInput.KEY_NP_DIV,
        GLFW.GLFW_KEY_KP_MULTIPLY to KeyboardInput.KEY_NP_MUL,
        GLFW.GLFW_KEY_KP_ADD to KeyboardInput.KEY_NP_PLUS,
        GLFW.GLFW_KEY_KP_SUBTRACT to KeyboardInput.KEY_NP_MINUS,
        GLFW.GLFW_KEY_KP_DECIMAL to KeyboardInput.KEY_NP_DECIMAL,
        GLFW.GLFW_KEY_BACKSPACE to KeyboardInput.KEY_BACKSPACE,
        GLFW.GLFW_KEY_TAB to KeyboardInput.KEY_TAB,
        GLFW.GLFW_KEY_DELETE to KeyboardInput.KEY_DEL,
        GLFW.GLFW_KEY_INSERT to KeyboardInput.KEY_INSERT,
        GLFW.GLFW_KEY_HOME to KeyboardInput.KEY_HOME,
        GLFW.GLFW_KEY_END to KeyboardInput.KEY_END,
        GLFW.GLFW_KEY_PAGE_UP to KeyboardInput.KEY_PAGE_UP,
        GLFW.GLFW_KEY_PAGE_DOWN to KeyboardInput.KEY_PAGE_DOWN,
        GLFW.GLFW_KEY_LEFT to KeyboardInput.KEY_CURSOR_LEFT,
        GLFW.GLFW_KEY_RIGHT to KeyboardInput.KEY_CURSOR_RIGHT,
        GLFW.GLFW_KEY_UP to KeyboardInput.KEY_CURSOR_UP,
        GLFW.GLFW_KEY_DOWN to KeyboardInput.KEY_CURSOR_DOWN,
        GLFW.GLFW_KEY_F1 to KeyboardInput.KEY_F1,
        GLFW.GLFW_KEY_F2 to KeyboardInput.KEY_F2,
        GLFW.GLFW_KEY_F3 to KeyboardInput.KEY_F3,
        GLFW.GLFW_KEY_F4 to KeyboardInput.KEY_F4,
        GLFW.GLFW_KEY_F5 to KeyboardInput.KEY_F5,
        GLFW.GLFW_KEY_F6 to KeyboardInput.KEY_F6,
        GLFW.GLFW_KEY_F7 to KeyboardInput.KEY_F7,
        GLFW.GLFW_KEY_F8 to KeyboardInput.KEY_F8,
        GLFW.GLFW_KEY_F9 to KeyboardInput.KEY_F9,
        GLFW.GLFW_KEY_F10 to KeyboardInput.KEY_F10,
        GLFW.GLFW_KEY_F11 to KeyboardInput.KEY_F11,
        GLFW.GLFW_KEY_F12 to KeyboardInput.KEY_F12
    )
}