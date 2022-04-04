package de.fabmax.kool.demo.physics.terrain

import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.clamp
import de.fabmax.kool.pipeline.shading.unlitShader
import de.fabmax.kool.scene.Group
import de.fabmax.kool.scene.LineMesh
import de.fabmax.kool.scene.Model
import de.fabmax.kool.util.MdColor
import kotlin.math.*

class PlayerModel(val model: Model, val playerController: PlayerController) : Group("player-model") {

    private val controllerShapeOutline: LineMesh
    var isDrawShapeOutline: Boolean
        get() = controllerShapeOutline.isVisible
        set(value) { controllerShapeOutline.isVisible = value }

    init {
        // set correct player model position (relative to player controller origin)
        model.translate(0f, -0.9f, 0f)
        model.rotate(180f, Vec3f.Y_AXIS)
        +model

        controllerShapeOutline = makeShapeOutline()
        +controllerShapeOutline

        onUpdate += {
            updateAnimation(it.deltaT)
        }
    }

    private fun makeShapeOutline() = LineMesh().apply {
        isVisible = false
        isCastingShadow = false
        val cr = MdColor.RED
        val cg = MdColor.GREEN
        val cb = MdColor.BLUE

        // currently, player size is hardcoded...
        val r = 0.3f // controller.radius
        val h = 0.6f // controller.height / 2f
        for (i in 0 until 40) {
            val a0 = (i / 40f) * 2 * PI.toFloat()
            val a1 = ((i + 1) / 40f) * 2 * PI.toFloat()
            addLine(Vec3f(cos(a0) * r, h, sin(a0) * r), Vec3f(cos(a1) * r, h, sin(a1) * r), cg)
            addLine(Vec3f(cos(a0) * r, -h, sin(a0) * r), Vec3f(cos(a1) * r, -h, sin(a1) * r), cg)
        }

        for (i in 0 until 20) {
            val a0 = (i / 40f) * 2 * PI.toFloat()
            val a1 = ((i + 1) / 40f) * 2 * PI.toFloat()
            addLine(Vec3f(cos(a0) * r, sin(a0) * r + h, 0f), Vec3f(cos(a1) * r, sin(a1) * r + h, 0f), cr)
            addLine(Vec3f(cos(a0) * r, -sin(a0) * r - h, 0f), Vec3f(cos(a1) * r, -sin(a1) * r - h, 0f), cr)

            addLine(Vec3f(0f, sin(a0) * r + h, cos(a0) * r), Vec3f(0f, sin(a1) * r + h, cos(a1) * r), cb)
            addLine(Vec3f(0f, -sin(a0) * r - h, cos(a0) * r), Vec3f(0f, -sin(a1) * r - h, cos(a1) * r), cb)
        }

        addLine(Vec3f(-r, h, 0f), Vec3f(-r, -h, 0f), cr)
        addLine(Vec3f(r, h, 0f), Vec3f(r, -h, 0f), cr)
        addLine(Vec3f(0f, h, -r), Vec3f(0f, -h, -r), cb)
        addLine(Vec3f(0f, h, r), Vec3f(0f, -h, r), cb)

        shader = unlitShader {
            lineWidth = 2f
        }
    }

    private fun updateAnimation(timeStep: Float) {
        // set transform group / model transform according to character position
        setIdentity()
        translate(playerController.position)
        rotate(playerController.moveHeading, Vec3f.Y_AXIS)

        // determine which animation to use based on speed
        if (abs(playerController.moveSpeed) <= PlayerController.walkSpeed) {
            val w = (abs(playerController.moveSpeed) / PlayerController.walkSpeed).clamp(0f, 1f)
            model.setAnimationWeight(walkAnimation, w)
            model.setAnimationWeight(idleAnimation, 1f - w)
            model.setAnimationWeight(runAnimation, 0f)

        } else {
            val w = ((abs(playerController.moveSpeed) - PlayerController.walkSpeed) / (PlayerController.runSpeed - PlayerController.walkSpeed)).clamp(0f, 1f)
            model.setAnimationWeight(runAnimation, w)
            model.setAnimationWeight(walkAnimation, 1f - w)
            model.setAnimationWeight(idleAnimation, 0f)
        }
        model.applyAnimation(timeStep * sign(playerController.moveSpeed))
    }

    companion object {
        // model animation indices, depend on the actual model
        private const val idleAnimation = 0
        private const val runAnimation = 1
        private const val walkAnimation = 2
    }
}