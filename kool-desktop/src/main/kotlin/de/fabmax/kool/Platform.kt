package de.fabmax.kool

import de.fabmax.kool.platform.FontMapGenerator
import de.fabmax.kool.platform.ImageTextureData
import de.fabmax.kool.platform.Lwjgl3Context
import de.fabmax.kool.platform.MonitorSpec
import de.fabmax.kool.shading.GlslGenerator
import de.fabmax.kool.util.CharMap
import de.fabmax.kool.util.FontProps
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWErrorCallback
import java.awt.Desktop
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.URI

/**
 * Desktop LWJGL3 platform implementation
 *
 * @author fabmax
 */

actual val supportsMultiContext: Boolean = true

actual val supportsUint32Indices: Boolean = true

fun createContext() = createContext(Lwjgl3Context.InitProps())

actual fun createContext(props: RenderContext.InitProps): RenderContext = DesktopImpl.createContext(props)

actual fun createCharMap(fontProps: FontProps): CharMap = DesktopImpl.fontGenerator.createCharMap(fontProps)

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun defaultGlslInjector(): GlslGenerator.GlslInjector = DesktopImpl.defaultGlslInjector

actual fun loadAsset(assetPath: String, onLoad: (ByteArray) -> Unit) {
    val file = File(assetPath)
    FileInputStream(assetPath).use { inStream ->
        val len = file.length().toInt()
        var pos = 0
        val data = ByteArray(len)
        while (pos < len) {
            val read = inStream.read(data, pos, len - pos)
            if (read < 0) {
                throw IOException("Unexpected end of file")
            }
            pos += read
        }
        onLoad(data)
    }
}

actual fun loadTextureAsset(assetPath: String): TextureData = ImageTextureData(assetPath)

actual fun openUrl(url: String) = Desktop.getDesktop().browse(URI(url))

internal object DesktopImpl {
    private const val MAX_GENERATED_TEX_WIDTH = 2048
    private const val MAX_GENERATED_TEX_HEIGHT = 2048

    val monitors: MutableList<MonitorSpec> = mutableListOf()
    val primaryMonitor: MonitorSpec
    val fontGenerator = FontMapGenerator(MAX_GENERATED_TEX_WIDTH, MAX_GENERATED_TEX_HEIGHT)
    val defaultGlslInjector = object : GlslGenerator.GlslInjector {
        override fun vsHeader(text: StringBuilder) {
            text.append("#version 330\n")
        }

        override fun fsHeader(text: StringBuilder) {
            text.append("#version 330\n")
        }
    }

    init {
        // setup an error callback
        GLFWErrorCallback.createPrint(System.err).set()

        // initialize GLFW
        if (!GLFW.glfwInit()) {
            throw KoolException("Unable to initialize GLFW")
        }

        var primMon: MonitorSpec? = null
        val primMonId = GLFW.glfwGetPrimaryMonitor()
        val mons = GLFW.glfwGetMonitors()
        for (i in 0 until mons.limit()) {
            val spec = MonitorSpec(mons[i])
            monitors += spec
            if (mons[i] == primMonId) {
                primMon = spec
            }
        }
        primaryMonitor = primMon!!
    }

    fun createContext(props: RenderContext.InitProps): RenderContext {
        if (props is Lwjgl3Context.InitProps) {
            return Lwjgl3Context(props)
        } else {
            throw IllegalArgumentException("Props must be of Lwjgl3Context.InitProps")
        }
    }
}

/**
 * AutoCloseable variant of the standard use extension function (which only works for Closeable).
 * This is mainly needed for lwjgl's MemoryStack.stackPush() to work in a try-with-resources manner.
 */
inline fun <T : AutoCloseable?, R> T.use(block: (T) -> R): R {
    var exception: Throwable? = null
    try {
        return block(this)
    } catch (e: Throwable) {
        exception = e
        throw e
    } finally {
        when {
            this == null -> {}
            exception == null -> close()
            else ->
                try {
                    close()
                } catch (closeException: Throwable) {
                    exception.addSuppressed(closeException)
                }
        }
    }
}
