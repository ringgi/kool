package de.fabmax.kool.modules.ui2

import de.fabmax.kool.util.Color

/**
 * UI colors. Somewhat based on the Material Design color system:
 *   https://material.io/design/color/the-color-system.html#color-theme-creation
 * However, primary and secondary color are replaced by a single accent color.
 *
 * - [primary]: Accent color used by UI elements.
 * - [primaryVariant]: A little less prominent than the primary accent color.
 * - [secondary]: Secondary accent color used by UI elements.
 * - [secondaryVariant]: A little less prominent than the secondary accent color.
 * - [background]: Used on surfaces of components, such as menus.
 * - [backgroundVariant]: Appears behind scrollable content.
 * - [onPrimary]: Used for icons and text displayed on top of the primary accent color.
 * - [onSecondary]: Used for icons and text displayed on top of the secondary accent color.
 * - [onBackground]: Used for icons and text displayed on top of the background color.
 * - [isLight]: Whether this color is considered as a 'light' or 'dark' set of colors.
 */
data class Colors(
    val primary: Color,
    val primaryVariant: Color,
    val secondary: Color,
    val secondaryVariant: Color,
    val background: Color,
    val backgroundVariant: Color,
    val onPrimary: Color,
    val onSecondary: Color,
    val onBackground: Color,
    val isLight: Boolean
) {
    companion object {
        fun singleColorLight(
            accent: Color,
            background: Color = Color("f3f3f3ff"),
            onAccent: Color = Color.WHITE,
            onBackground: Color = Color("343434ff"),
        ): Colors = Colors(
            primary = accent,
            primaryVariant = accent.mix(Color.BLACK, 0.3f),
            secondary = accent.mix(Color.BLACK, 0.3f),
            secondaryVariant = accent.mix(Color.BLACK, 0.5f),
            background = background,
            backgroundVariant = background.mix(Color.BLACK, 0.05f).mix(accent, 0.1f),
            onPrimary = onAccent,
            onSecondary = onAccent,
            onBackground = onBackground,
            isLight = true
        )

        fun singleColorDark(
            accent: Color,
            background: Color = Color("1a1a1aff"),
            onAccent: Color = Color.WHITE,
            onBackground: Color = Color("f2f2f2ff"),
        ): Colors = Colors(
            primary = accent,
            primaryVariant = accent.mix(Color.BLACK, 0.3f),
            secondary = accent.mix(Color.BLACK, 0.3f),
            secondaryVariant = accent.mix(Color.BLACK, 0.5f),
            background = background,
            backgroundVariant = background.mix(accent, 0.05f),
            onPrimary = onAccent,
            onSecondary = onAccent,
            onBackground = onBackground,
            isLight = false
        )

        fun darkColors(
            primary: Color = Color("ffb703ff"),
            primaryVariant: Color = Color("fb8500f0"),
            secondary: Color = Color("8ecae6ff"),
            secondaryVariant: Color = Color("219ebcff"),
            background: Color = Color("023047ff"),
            backgroundVariant: Color = Color("143d52ff"),
            onPrimary: Color = Color.WHITE,
            onSecondary: Color = Color.BLACK,
            onBackground: Color = Color("dcf7ffff"),
            isLight: Boolean = false
        ): Colors = Colors(
            primary,
            primaryVariant,
            secondary,
            secondaryVariant,
            background,
            backgroundVariant,
            onPrimary,
            onSecondary,
            onBackground,
            isLight
        )

        val neon = darkColors(
            primary = Color("b2ff00"),
            primaryVariant = Color("7cb200"),
            secondary = Color("b2ff00").withAlpha(0.6f),
            secondaryVariant = Color("7cb200").withAlpha(0.6f),
            background = Color("101010d0"),
            backgroundVariant = Color("202020d0"),
            onPrimary = Color.BLACK
        )
    }
}
