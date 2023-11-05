package de.fabmax.kool.physics

expect fun Material(staticFriction: Float, dynamicFriction: Float = staticFriction, restitution: Float = 0.2f): Material

interface Material : Releasable {
    val staticFriction: Float
    val dynamicFriction: Float
    val restitution: Float

    override fun release()
}
