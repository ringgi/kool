package de.fabmax.kool.modules.ui2

import de.fabmax.kool.KoolContext
import de.fabmax.kool.pipeline.RenderPass
import de.fabmax.kool.scene.Group
import de.fabmax.kool.scene.geometry.MeshBuilder
import de.fabmax.kool.scene.mesh
import de.fabmax.kool.scene.ui.Font
import de.fabmax.kool.scene.ui.FontProps
import de.fabmax.kool.util.PerfTimer
import de.fabmax.kool.util.logD

class UiSurface(
    colors: Colors = Colors.darkColors(),
    name: String = "uiSurface",
    private val uiBlock: UiScope.() -> Unit
) : Group(name) {

    val defaultPrimitives = UiPrimitiveMesh()
    private val textMeshes = mutableMapOf<FontProps, TextMesh>()

    private val inputHandler = InputHandler()
    private val viewportWidth = mutableStateOf(0f)
    private val viewportHeight = mutableStateOf(0f)
    private val viewport = BoxNode(null, this).apply { modifier.layout(CellLayout) }
    private val content = viewport.createChild(RootCell::class) { _, _ -> RootCell() }

    private val registeredState = mutableListOf<MutableState>()
    var requiresUpdate: Boolean = true
        private set

    var measuredScale = 1f
        private set
    var deltaT = 0f
        private set

    // colorsState is private and use()d internally by UiSurface
    // for all other consumers the colors value is directly exposed
    private val colorsState = mutableStateOf(colors)
    var colors: Colors by colorsState::value

    init {
        this += defaultPrimitives
        // mirror y-axis
        scale(1f, -1f, 1f)
        onUpdate += {
            viewportWidth.set(it.renderPass.viewport.width.toFloat())
            viewportHeight.set(it.renderPass.viewport.height.toFloat())
            deltaT = it.deltaT

            inputHandler.handleInput(it)
            if (requiresUpdate) {
                requiresUpdate = false
                updateUi(it)
            }
        }
    }

    private fun updateUi(updateEvent: RenderPass.UpdateEvent) {
        val pt = PerfTimer()
        registeredState.forEach { it.clearUsage() }
        registeredState.clear()
        textMeshes.values.forEach { it.clear() }
        defaultPrimitives.instances?.clear()
        val prep = pt.takeMs().also { pt.reset() }

        measuredScale = updateEvent.ctx.windowScale
        viewport.setBounds(0f, 0f, viewportWidth.use(this), viewportHeight.use(this))
        content.reset()
        content.uiBlock()
        val build = pt.takeMs().also { pt.reset() }

        measureUiNodeContent(viewport, updateEvent.ctx)
        val measure = pt.takeMs().also { pt.reset() }
        layoutUiNodeChildren(viewport, updateEvent.ctx)
        val layout = pt.takeMs().also { pt.reset() }
        if (content.isInBounds) {
            renderUiNode(content, updateEvent.ctx)
        }
        val render = pt.takeMs().also { pt.reset() }
        logD { "UI update: prep: ${(prep * 1000).toInt()} us, " +
                "build: ${(build * 1000).toInt()} us, " +
                "measure: ${(measure * 1000).toInt()} us, " +
                "layout: ${(layout * 1000).toInt()} us, " +
                "render: ${(render * 1000).toInt()} us, " }
    }

    fun registerState(state: MutableState) {
        registeredState += state
    }

    fun triggerUpdate() {
        requiresUpdate = true
    }

    fun getTextBuilder(font: Font, ctx: KoolContext): MeshBuilder {
        val textMesh =  textMeshes.getOrPut(font.fontProps) { TextMesh(font, ctx).also { this += it.mesh } }
        textMesh.used = true
        return textMesh.builder
    }

    private fun measureUiNodeContent(node: UiNode, ctx: KoolContext) {
        for (i in node.children.indices) {
            measureUiNodeContent(node.children[i], ctx)
        }
        node.measureContentSize(ctx)
    }

    private fun layoutUiNodeChildren(node: UiNode, ctx: KoolContext) {
        node.layoutChildren(ctx)
        for (i in node.children.indices) {
            if (node.children[i].isInBounds) {
                layoutUiNodeChildren(node.children[i], ctx)
            }
        }
    }

    private fun renderUiNode(node: UiNode, ctx: KoolContext) {
        node.render(ctx)
        for (i in node.children.indices) {
            if (node.children[i].isInBounds) {
                renderUiNode(node.children[i], ctx)
            }
        }
    }

    private inner class InputHandler {
        private val nodeResult = mutableListOf<UiNode>()
        private var hoveredNode: UiNode? = null
        private var wasDrag = false
        private var dragNode: UiNode? = null

        fun handleInput(updateEvent: RenderPass.UpdateEvent) {
            val ptr = updateEvent.ctx.inputMgr.pointerState.primaryPointer
            content.collectNodesAt(ptr.x.toFloat(), ptr.y.toFloat(), nodeResult, hasPointerListener)
            if (hoveredNode == null && dragNode == null && nodeResult.isEmpty()) {
                return
            }

            val ptrEv = PointerEvent(ptr, updateEvent.ctx)
            hoveredNode?.let { handleHover(it, ptrEv) }
            if (nodeResult.isNotEmpty()) {
                handlePointerEvents(nodeResult, ptrEv)
            }
            dragNode?.let { handleDrag(it, ptrEv) }
        }

        private fun handleHover(currentHover: UiNode, ptrEv: PointerEvent) {
            // check if we still hover previously hovered node
            if (currentHover in nodeResult) {
                // hovering continues, hover event can be rejected, by hoverNode to stop hovering
                if (!invokePointerCallback(currentHover, ptrEv, currentHover.modifier.onHover, true)) {
                    hoveredNode = null
                }
            } else {
                // hovering stopped, cannot be rejected...
                invokePointerCallback(currentHover, ptrEv, currentHover.modifier.onExit)
                hoveredNode = null
            }
        }

        private fun handleDrag(currentDrag: UiNode, ptrEv: PointerEvent) {
            val ptr = ptrEv.pointer
            if (ptr.isDrag) {
                // dragging continues, drag event can be rejected, by dragNode to stop dragging
                if (!invokePointerCallback(currentDrag, ptrEv, currentDrag.modifier.onDrag, true)) {
                    dragNode = null
                }
            } else {
                // dragging stopped, cannot be rejected...
                invokePointerCallback(currentDrag, ptrEv, currentDrag.modifier.onDragEnd)
                dragNode = null
            }
        }

        fun handlePointerEvents(relevantNodes: List<UiNode>, ptrEv: PointerEvent) {
            val ptr = ptrEv.pointer

            var isWheelX = ptr.deltaScrollX != 0.0
            var isWheelY = ptr.deltaScrollY != 0.0
            val isDragStart = !wasDrag && ptr.isDrag
            var isAnyClick = ptr.isLeftButtonClicked ||
                    ptr.isRightButtonClicked ||
                    ptr.isMiddleButtonClicked ||
                    ptr.isForwardButtonClicked ||
                    ptr.isBackButtonClicked

            wasDrag = ptr.isDrag

            relevantNodes.forEach { node ->
                val mod = node.modifier

                // onPointer is called for any node below pointer position
                invokePointerCallback(node, ptrEv, mod.onPointer)

                if (hoveredNode == null && mod.hasAnyHoverCallback && invokePointerCallback(node, ptrEv, mod.onEnter, true)) {
                    // no node was hovered before (or we just exited it) and we found a new one which has hover
                    // callbacks installed -> select it as new hovered node
                    hoveredNode = node
                }

                if (isDragStart && dragNode == null && mod.hasAnyDragCallback && invokePointerCallback(node, ptrEv, mod.onDragStart, true)) {
                    dragNode = node
                }

                if (isAnyClick && invokePointerCallback(node, ptrEv, mod.onClick)) {
                    // click was consumed
                    isAnyClick = false
                }
                if (isWheelX && invokePointerCallback(node, ptrEv, mod.onWheelX)) {
                    // wheel x was consumed
                    isWheelX = false
                }
                if (isWheelY && invokePointerCallback(node, ptrEv, mod.onWheelY)) {
                    // wheel y was consumed
                    isWheelY = false
                }
            }
        }

        private fun invokePointerCallback(
            uiNode: UiNode,
            ptrEvent: PointerEvent,
            cb: ((PointerEvent) -> Unit)?,
            consumedIfNull: Boolean = false
        ): Boolean {
            var wasConsumed = consumedIfNull
            if (cb != null) {
                uiNode.toLocal(ptrEvent.pointer.x, ptrEvent.pointer.y, ptrEvent.position)
                // make sure consumed flag is set by default, callback has to actively reject() the
                // event to not consume it
                ptrEvent.isConsumed = true
                cb(ptrEvent)
                wasConsumed = ptrEvent.isConsumed
            }
            return wasConsumed
        }
    }

    private inner class RootCell : BoxNode(viewport, this@UiSurface) {
        fun reset() {
            resetDefaults()
            modifier.background(colorsState.use().surface)
        }

        fun collectNodesAt(x: Float, y: Float, result: MutableList<UiNode>, predicate: (UiNode) -> Boolean) {
            result.clear()
            if (isInClip(x, y)) {
                traverseChildren(this, x, y, result, predicate)
            }
            if (result.size > 1) {
                result.sortBy { -it.layer }
            }
        }

        private fun traverseChildren(node: UiNode, x: Float, y: Float, result: MutableList<UiNode>, predicate: (UiNode) -> Boolean) {
            for (i in node.children.indices) {
                val child = node.children[i]
                if (child.isInClip(x, y)) {
                    traverseChildren(child, x, y, result, predicate)
                }
            }
            if (predicate(node)) {
                result += node
            }
        }

        fun UiNode.isInClip(x: Float, y: Float): Boolean {
            return x in clipLeftPx..clipRightPx && y in clipTopPx..clipBottomPx
        }
    }

    private class TextMesh(font: Font, ctx: KoolContext) {
        val mesh = mesh(Ui2Shader.UI_MESH_ATTRIBS) {
            shader = Ui2Shader().apply { setFont(font, ctx) }
        }

        val builder = MeshBuilder(mesh.geometry)
        var used = false

        init {
            builder.setupUiBuilder()
        }

        fun clear() {
            mesh.geometry.clear()
            used = false
        }
    }

    companion object {
        fun MeshBuilder.setupUiBuilder() {
            invertFaceOrientation = true
        }

        private val hasPointerListener: (UiNode) -> Boolean = { it.modifier.hasAnyCallback }
    }
}