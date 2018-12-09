package de.fabmax.kool.util

import kotlin.reflect.KProperty

/**
 * @author fabmax
 */
class Property<T>(val name: String, value: T, private val onChange: Property<T>.() -> Unit) {

    var value: T = value
        private set

    val clear: T
        get() {
            valueChanged = false
            return value
        }

    var valueChanged = true
        private set

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return value
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        if (value != this.value) {
            this.value = value
            valueChanged = true
        }
    }

    fun copy(other: Property<*>, maintainChangeFlag: Boolean) {
        valueChanged = if (maintainChangeFlag) {
            other.valueChanged
        } else {
            value != other.value
        }
        @Suppress("UNCHECKED_CAST")
        value = other.value as? T ?: value
    }

    fun applyIfChanged() {
        if (valueChanged) {
            onChange()
        }
    }
}