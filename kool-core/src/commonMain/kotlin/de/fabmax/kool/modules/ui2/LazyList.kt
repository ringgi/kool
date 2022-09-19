package de.fabmax.kool.modules.ui2

import de.fabmax.kool.KoolContext
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.logD
import kotlin.math.max
import kotlin.math.min

class LazyListState : ScrollState() {
    var averageElementSizeDp = 0f
    var spaceBeforeVisibleItems = 0f
    var spaceAfterVisibleItems = 0f

    var itemsSize = 0
    var itemsFrom = 0
    var itemsTo = 0
}

interface LazyListScope : UiScope {
    override val modifier: LazyListModifier

    val isHorizontal: Boolean get() = modifier.orientation == ListOrientation.Horizontal
    val isVertical: Boolean get() = modifier.orientation == ListOrientation.Vertical

    fun <T> items(items: List<T>, block: LazyListScope.(T) -> Unit)
    fun <T> itemsIndexed(items: List<T>, block: LazyListScope.(Int, T) -> Unit)
}

open class LazyListModifier(surface: UiSurface) : UiModifier(surface) {
    var orientation: ListOrientation by property(ListOrientation.Vertical)
    var extraItemsBefore: Int by property(0)
    var extraItemsAfter: Int by property(3)
}

fun <T: LazyListModifier> T.orientation(orientation: ListOrientation): T {
    this.orientation = orientation
    return this
}

enum class ListOrientation {
    Horizontal,
    Vertical
}

fun UiScope.LazyList(
    state: LazyListState,
    layout: Layout = ColumnLayout,
    width: Dimension = Grow(),
    height: Dimension = Grow(),
    withVerticalScrollbar: Boolean = true,
    withHorizontalScrollbar: Boolean = false,
    scrollbarColor: Color? = null,
    containerModifier: ((UiModifier) -> Unit)? = null,
    scrollPaneModifier: ((ScrollPaneModifier) -> Unit)? = null,
    vScrollbarModifier: ((ScrollbarModifier) -> Unit)? = null,
    hScrollbarModifier: ((ScrollbarModifier) -> Unit)? = null,
    block: LazyListScope.() -> Unit
) {
    ScrollArea(
        state,
        width, height,
        withVerticalScrollbar, withHorizontalScrollbar,
        scrollbarColor,
        containerModifier, vScrollbarModifier, hScrollbarModifier
    ) {
        modifier.width(Grow())
        scrollPaneModifier?.let { it(modifier) }

        val lazyList = uiNode.createChild(LazyListNode::class, LazyListNode.factory)
        lazyList.state = state
        lazyList.modifier
            .layout(layout)
            .margin(0.dp)
            .width(Grow())
        lazyList.block()
    }
}

class LazyListNode(parent: UiNode?, surface: UiSurface) : UiNode(parent, surface), LazyListScope {
    override val modifier = LazyListModifier(surface)

    lateinit var state: LazyListState

    override fun <T> items(items: List<T>, block: LazyListScope.(T) -> Unit) =
        iterateItems(items, block, null)

    override fun <T> itemsIndexed(items: List<T>, block: LazyListScope.(Int, T) -> Unit) =
        iterateItems(items, null, block)

    private fun <T> iterateItems(
        items: List<T>,
        block: (LazyListScope.(T) -> Unit)?,
        indexedBlock: (LazyListScope.(Int, T) -> Unit)?
    ) {
        // auto-depend on list state in case it is a MutableListState
        (items as? MutableListState)?.use()
        state.itemsSize = items.size

        // use prior knowledge from state to select the range of visible items
        if (state.averageElementSizeDp == 0f) {
            // this apparently is the first layout run, we have absolutely no knowledge about the future content
            // start by adding up to 100 items and hope for the best
            state.itemsFrom = 0
            state.itemsTo = min(items.lastIndex, 100)
            state.spaceBeforeVisibleItems = 0f
            state.spaceAfterVisibleItems = 0f

        } else {
            // use the element size seen in previous layout runs to estimate the total list dimensions
            val elemSize = state.averageElementSizeDp
            val listPos = if (isVertical) {
                state.computeSmoothScrollPosDpY(surface.deltaT)
            } else {
                state.computeSmoothScrollPosDpX(surface.deltaT)
            }
            val viewSize = if (isVertical) state.viewSizeDp.y else state.viewSizeDp.x
            val numViewItems = (viewSize / elemSize).toInt() + modifier.extraItemsAfter

            state.itemsFrom = min(items.size - numViewItems, max(0, (listPos / elemSize).toInt() - modifier.extraItemsBefore))
            state.itemsTo = min(items.lastIndex, state.itemsFrom + numViewItems)

            state.spaceBeforeVisibleItems = state.itemsFrom * elemSize
            state.spaceAfterVisibleItems = (items.lastIndex - state.itemsTo) * elemSize
        }

        // add a placeholder in front of visible items to achieve correct scroll pane dimensions
        if (state.spaceBeforeVisibleItems > 0f) {
            if (isVertical) {
                Box(CellLayout, 1.dp, state.spaceBeforeVisibleItems.dp) { }
            } else {
                Box(CellLayout, state.spaceBeforeVisibleItems.dp, 1.dp) { }
            }
        }

        for (i in state.itemsFrom..state.itemsTo) {
            val item = items[i]
            indexedBlock?.invoke(this, i, item)
            block?.invoke(this, item)
        }

        // add a placeholder behind of visible items to achieve correct scroll pane dimensions
        if (state.spaceAfterVisibleItems > 0f) {
            if (isVertical) {
                Box(CellLayout, 1.dp,  state.spaceAfterVisibleItems.dp) { }
            } else {
                Box(CellLayout,  state.spaceAfterVisibleItems.dp, 1.dp) { }
            }
        }

    }

    override fun measureContentSize(ctx: KoolContext) {
        super.measureContentSize(ctx)

        // compute average child size, exclude spacer boxes before and after visible list elements
        val from = if (state.spaceBeforeVisibleItems == 0f) 0 else 1
        val to = if (state.spaceAfterVisibleItems == 0f) children.lastIndex else children.lastIndex - 1

        var size = 0f
        var count = 0
        var prevMargin = 0f
        for (i in from..to) {
            val child = children[i]
            if (isVertical) {
                size += child.contentHeightPx + max(prevMargin, child.marginTopPx)
                prevMargin = child.marginBottomPx
            } else {
                size += child.contentWidthPx + max(prevMargin, child.marginStartPx)
                prevMargin = child.marginEndPx
            }
            count++
        }
        size = pxToDp(size)
        state.averageElementSizeDp = size / count

        val viewSize = if (isVertical) state.viewSizeDp.y else state.viewSizeDp.x
        if (size < viewSize && count < state.itemsSize) {
            // we selected too few elements re-run layout with updated average element size
            surface.triggerUpdate()
            logD { "Selected too few lazy list elements: ${viewSize - size}" }
        }
    }

    companion object {
        val factory: (UiNode, UiSurface) -> LazyListNode = { parent, surface -> LazyListNode(parent, surface) }
    }
}
