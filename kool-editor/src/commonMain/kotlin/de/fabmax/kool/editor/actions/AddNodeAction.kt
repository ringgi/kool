package de.fabmax.kool.editor.actions

import de.fabmax.kool.editor.EditorState
import de.fabmax.kool.editor.KoolEditor
import de.fabmax.kool.editor.model.SceneNodeModel
import de.fabmax.kool.util.launchOnMainThread

class AddNodeAction(
    private val addNodeModel: SceneNodeModel
) : EditorAction {

    override fun doAction() {
        launchOnMainThread {
            val needsInit = !addNodeModel.isCreated
            if (needsInit) {
                addNodeModel.createComponents()
            }

            addNodeModel.scene.addSceneNode(addNodeModel)
            KoolEditor.instance.ui.sceneBrowser.refreshSceneTree()

            if (needsInit) {
                addNodeModel.initComponents()
            }
        }
    }

    override fun undoAction() {
        if (EditorState.selectedNode.value == addNodeModel) {
            EditorState.selectedNode.set(null)
        }
        addNodeModel.scene.removeSceneNode(addNodeModel)
        KoolEditor.instance.ui.sceneBrowser.refreshSceneTree()
    }
}