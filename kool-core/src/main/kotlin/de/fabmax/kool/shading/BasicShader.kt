package de.fabmax.kool.shading

import de.fabmax.kool.RenderContext
import de.fabmax.kool.Texture
import de.fabmax.kool.scene.Mesh
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.Float32Buffer
import de.fabmax.kool.util.MutableVec3f
import de.fabmax.kool.util.MutableVec4f


fun basicShader(propsInit: ShaderProps.() -> Unit): BasicShader {
    return BasicShader(ShaderProps().apply(propsInit))
}

/**
 * Generic simple shader generated by [GlslGenerator]
 */
open class BasicShader(val props: ShaderProps, private val generator: GlslGenerator = GlslGenerator()) : Shader() {

    protected var lightColor: MutableVec3f
        get() = generator.uniformLightColor.value
        set(value) { generator.uniformLightColor.value.set(value) }
    protected var lightDirection: MutableVec3f
        get() = generator.uniformLightDirection.value
        set(value) { generator.uniformLightDirection.value.set(value) }
    protected var cameraPosition: MutableVec3f
        get() = generator.uniformCameraPosition.value
        set(value) { generator.uniformCameraPosition.value.set(value) }

    var shininess: Float
        get() = generator.uniformShininess.value
        set(value) { generator.uniformShininess.value = value }
    var specularIntensity: Float
        get() = generator.uniformSpecularIntensity.value
        set(value) { generator.uniformSpecularIntensity.value = value }

    var staticColor: MutableVec4f
        get() = generator.uniformStaticColor.value
        set(value) { generator.uniformStaticColor.value.set(value) }
    var texture: Texture?
        get() = generator.uniformTexture.value
        set(value) { generator.uniformTexture.value = value }

    var alpha: Float
        get() = generator.uniformAlpha.value
        set(value) { generator.uniformAlpha.value = value }

    var saturation: Float
        get() = generator.uniformSaturation.value
        set(value) { generator.uniformSaturation.value = value }

    var fogColor: MutableVec4f
        get() = generator.uniformFogColor.value
        set(value) { generator.uniformFogColor.value.set(value) }
    var fogRange: Float
        get() = generator.uniformFogRange.value
        set(value) { generator.uniformFogRange.value = value }

    var bones: Float32Buffer?
        get() = generator.uniformBones.value
        set(value) { generator.uniformBones.value = value }

    private var scene: Scene? = null

    init {
        // set meaningful uniform default values
        shininess = props.shininess
        specularIntensity = props.specularIntensity
        staticColor.set(props.staticColor)
        texture = props.texture
        alpha = props.alpha
        saturation = props.saturation
        fogRange = props.fogRange
        fogColor.set(props.fogColor)
    }

    override fun generateSource(ctx: RenderContext) {
        source = generator.generate(props)
    }

    override fun onLoad(ctx: RenderContext) {
        super.onLoad(ctx)
        generator.onLoad(this, ctx)
    }

    override fun onBind(ctx: RenderContext) {
        onMatrixUpdate(ctx)

        scene = null

        // fixme: if (isGlobalFog) fogColor.set(global)
        generator.uniformFogColor.bind(ctx)
        // fixme: if (isGlobalFog) fogRange = global
        generator.uniformFogRange.bind(ctx)

        // fixme: if (isGlobalSaturation) saturation = globalSaturation
        generator.uniformSaturation.bind(ctx)

        generator.uniformAlpha.bind(ctx)
        generator.uniformShininess.bind(ctx)
        generator.uniformSpecularIntensity.bind(ctx)
        generator.uniformStaticColor.bind(ctx)
        generator.uniformTexture.bind(ctx)
        generator.uniformBones.bind(ctx)
    }

    override fun bindMesh(mesh: Mesh, ctx: RenderContext) {
        if (scene != mesh.scene) {
            scene = mesh.scene
            if (scene != null) {
                cameraPosition.set(scene!!.camera.globalPos)
                generator.uniformCameraPosition.bind(ctx)

                val light = scene!!.light
                lightDirection.set(light.direction)
                generator.uniformLightDirection.bind(ctx)
                lightColor.set(light.color.r, light.color.g, light.color.b)
                generator.uniformLightColor.bind(ctx)
            }
        }

        super.bindMesh(mesh, ctx)
    }

    override fun onMatrixUpdate(ctx: RenderContext) {
        // pass current transformation matrices to shader
        generator.uniformMvpMatrix.value = ctx.mvpState.mvpMatrixBuffer
        generator.uniformMvpMatrix.bind(ctx)

        generator.uniformViewMatrix.value = ctx.mvpState.viewMatrixBuffer
        generator.uniformViewMatrix.bind(ctx)

        generator.uniformModelMatrix.value = ctx.mvpState.modelMatrixBuffer
        generator.uniformModelMatrix.bind(ctx)
    }

    override fun dispose(ctx: RenderContext) {
        super.dispose(ctx)
        texture?.dispose(ctx)
    }
}

fun basicPointShader(propsInit: ShaderProps.() -> Unit): BasicPointShader {
    val generator = GlslGenerator()
    generator.addCustomUniform(Uniform1f("uPointSz"))
    generator.injectors += object : GlslGenerator.GlslInjector {
        override fun vsAfterProj(shaderProps: ShaderProps, text: StringBuilder) {
            text.append("gl_PointSize = uPointSz;\n")
        }
    }
    return BasicPointShader(ShaderProps().apply(propsInit), generator)
}

open class BasicPointShader internal constructor(props: ShaderProps, generator: GlslGenerator) :
        BasicShader(props, generator) {

    private val uPointSz = generator.customUnitforms["uPointSz"] as Uniform1f
    var pointSize: Float
        get() = uPointSz.value
        set(value) { uPointSz.value = value }

    init {
        pointSize = 1f
    }

    override fun onBind(ctx: RenderContext) {
        super.onBind(ctx)
        uPointSz.bind(ctx)
    }
}
