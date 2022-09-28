package de.fabmax.kool.demo.menu

import de.fabmax.kool.demo.DemoLoader
import de.fabmax.kool.demo.Settings
import de.fabmax.kool.demo.UiSizes
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.MdColor
import kotlin.math.sqrt

class DemoMenu(val demoLoader: DemoLoader) {

    private val isExpandedState = mutableStateOf(false)
    private val menuPositionAnimator = AnimationState(0.1f)

    private val drawerButton = DrawerButton(this)
    private val navDemoButton = NavDemoButton(this)
    private val navSettingsButton = NavSettingsButton(this)

    private val demoList = DemoListContent(this)
    private val settings = SettingsContent(this)

    val content = mutableStateOf(MenuContent.Demos)

    var isExpanded: Boolean
        get() = isExpandedState.value
        set(value) {
            if (value != isExpandedState.value) {
                isExpandedState.set(value)
                menuPositionAnimator.start()
            }
        }

    val ui = Ui2Scene {
        +UiSurface {
            surface.sizes = Settings.uiSize.use().sizes

            modifier
                .width(WrapContent)
                .padding(start = UiSizes.menuWidth * -1f)
                .height(Grow.Std)
                .background(background = null)

            if (isExpandedState.use() || menuPositionAnimator.isActive) {
                MenuContent()
            }

            drawerButton()
        }
    }

    private fun UiScope.MenuContent() = Row {
        val p = menuPositionAnimator.progressAndUse()
        val position = if (isExpandedState.use()) UiSizes.menuWidth * (sqrt(p) - 1f) else UiSizes.menuWidth * -p * p
        modifier
            .margin(start = position)
            .width(UiSizes.menuWidth)
            .height(Grow.Std)
            .backgroundColor(colors.background)

        NavigationBar()
        Box {
            modifier
                .height(Grow.Std)
                .width(1.dp)
                .backgroundColor(MdColor.GREY tone 800)
        }
        when (content.use()) {
            MenuContent.Demos -> demoList()
            MenuContent.Settings -> settings()
        }
    }

    private fun UiScope.NavigationBar() = Column {
        modifier
            .padding(top = UiSizes.baseElemSize)
            .width(UiSizes.baseElemSize)
            .height(Grow.Std)
            .backgroundColor(colors.backgroundVariant)

        navDemoButton()
        navSettingsButton()
    }

    enum class MenuContent {
        Demos,
        Settings
    }

    companion object {
        const val navBarButtonSelectedAlpha = 0.20f
        const val navBarButtonHoveredAlpha = 0.35f

        val titleBgMesh = TitleBgRenderer.BgMesh()
    }
}