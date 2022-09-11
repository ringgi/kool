package de.fabmax.kool.modules.ui2

@DslMarker
annotation class UiScopeMarker

@UiScopeMarker
interface UiScope {
    val surface: UiSurface
    val uiNode: UiNode
    val modifier: UiModifier

    val Int.dp: Dp
        get() = Dp(this.toFloat())

    val Float.dp: Dp
        get() = Dp(this)

    fun <T: Any?> MutableValueState<T>.use(): T = use(surface)
    fun <T> MutableListState<T>.use(): MutableListState<T> = use(surface)
}
