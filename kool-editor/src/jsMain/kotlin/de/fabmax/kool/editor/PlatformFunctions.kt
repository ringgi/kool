package de.fabmax.kool.editor

import de.fabmax.kool.KoolContext
import de.fabmax.kool.editor.model.EditorProject
import de.fabmax.kool.util.logE

actual object PlatformFunctions {
    actual fun onEditorStarted(ctx: KoolContext) { }

    actual fun onWindowCloseRequest(ctx: KoolContext): Boolean = true

    actual fun editBehavior(behaviorSourcePath: String) {
        logE { "Source editing is not available on JS. Download project and us the JVM variant." }
    }

    actual fun loadProjectModel(path: String): EditorProject = EditorState.newProject()

    actual fun saveProjectModel(path: String) {
        logE { "saveProjectModel not yet implemented" }
    }

    actual suspend fun chooseFilePath(): String? {
        logE { "chooseFilePath() not available on JS" }
        return null
    }
}