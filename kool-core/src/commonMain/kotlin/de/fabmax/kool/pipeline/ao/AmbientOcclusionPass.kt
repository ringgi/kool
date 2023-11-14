package de.fabmax.kool.pipeline.ao

import de.fabmax.kool.math.MutableVec2f
import de.fabmax.kool.math.MutableVec3f
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.math.Vec4f
import de.fabmax.kool.modules.ksl.KslShader
import de.fabmax.kool.modules.ksl.lang.*
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.pipeline.FullscreenShaderUtil.fullscreenQuadVertexStage
import de.fabmax.kool.pipeline.FullscreenShaderUtil.fullscreenShaderPipelineCfg
import de.fabmax.kool.pipeline.FullscreenShaderUtil.generateFullscreenQuad
import de.fabmax.kool.scene.Camera
import de.fabmax.kool.scene.Node
import de.fabmax.kool.scene.addMesh
import de.fabmax.kool.util.Uint8Buffer
import kotlin.math.*
import kotlin.random.Random

class AmbientOcclusionPass(val aoSetup: AoSetup, width: Int, height: Int) :
    OffscreenRenderPass2d(Node(), renderPassConfig {
        name = "AmbientOcclusionPass"
        setSize(width, height)
        clearDepthTexture()
        addColorTexture(TexFormat.R)
    }) {

    var sceneCam: Camera? = null

    private val aoPassShader = AoPassShader()

    var fwdNormalDepth: Texture2d? by aoPassShader::depthTex
    var deferredPosition: Texture2d? by aoPassShader::depthTex
    var deferredNormal: Texture2d? by aoPassShader::normalTex

    var radius: Float by aoPassShader::uRadius
    var strength: Float by aoPassShader::uStrength
    var power: Float by aoPassShader::uPower
    var bias: Float by aoPassShader::uBias

    var kernelSz = 16
        get() = aoPassShader.uKernelSize
        set(value) {
            if (value != field) {
                field = value
                generateKernels(value)
            }
        }

    init {
        clearColor = null

        drawNode.apply {
            addMesh(Attribute.POSITIONS, Attribute.TEXTURE_COORDS) {
                generateFullscreenQuad()

                shader = aoPassShader

                val tmpVec2f = MutableVec2f()
                onUpdate += {
                    sceneCam?.let {
                        aoPassShader.uProj.set(it.proj)
                        aoPassShader.uInvProj.set(it.invProj)
                    }
                    aoPassShader.uNoiseScale = tmpVec2f.set(
                        this@AmbientOcclusionPass.width / NOISE_TEX_SIZE.toFloat(),
                        this@AmbientOcclusionPass.height / NOISE_TEX_SIZE.toFloat()
                    )
                }
            }
        }

        fwdNormalDepth = aoSetup.linearDepthPass?.colorTexture
        generateKernels(16)
    }

    private fun generateKernels(nKernels: Int) {
        val n = min(nKernels, MAX_KERNEL_SIZE)

        val scales = (0 until n)
            .map { lerp(0.1f, 1f, (it.toFloat() / n).pow(2)) }
            .shuffled(Random(17))

        for (i in 0 until n) {
            val xi = hammersley(i, n)
            val phi = 2f * PI.toFloat() * xi.x
            val cosTheta = sqrt((1f - xi.y))
            val sinTheta = sqrt(1f - cosTheta * cosTheta)

            val k = MutableVec3f(
                sinTheta * cos(phi),
                sinTheta * sin(phi),
                cosTheta
            )
            aoPassShader.uKernel[i] = k.norm().mul(scales[i])
        }
        aoPassShader.uKernelSize = n
    }

    private fun radicalInverse(pBits: Int): Float {
        var bits = pBits.toLong()
        bits = (bits shl 16) or (bits shr 16)
        bits = ((bits and 0x55555555) shl 1) or ((bits and 0xAAAAAAAA) shr 1)
        bits = ((bits and 0x33333333) shl 2) or ((bits and 0xCCCCCCCC) shr 2)
        bits = ((bits and 0x0F0F0F0F) shl 4) or ((bits and 0xF0F0F0F0) shr 4)
        bits = ((bits and 0x00FF00FF) shl 8) or ((bits and 0xFF00FF00) shr 8)
        return bits.toFloat() / 0x100000000
    }

    private fun hammersley(i: Int, n: Int): Vec2f {
        return Vec2f(i.toFloat() / n.toFloat(), radicalInverse(i))
    }

    private fun lerp(a: Float, b: Float, f: Float): Float {
        return a + f * (b - a)
    }

    override fun release() {
        drawNode.release()
        super.release()
    }

    private fun aoPassProg() = KslProgram("Ambient Occlusion Pass").apply {
        val uv = interStageFloat2("uv")

        fullscreenQuadVertexStage(uv)

        fragmentStage {
            val noiseTex = texture2d("noiseTex")
            val depthTex = texture2d("depthTex")

            val uProj = uniformMat4("uProj")
            val uInvProj = uniformMat4("uInvProj")
            val uKernel = uniformFloat3Array("uKernel", MAX_KERNEL_SIZE)
            val uNoiseScale = uniformFloat2("uNoiseScale")
            val uKernelSize = uniformInt1("uKernelRange")
            val uRadius = uniformFloat1("uRadius")
            val uStrength = uniformFloat1("uStrength")
            val uPower = uniformFloat1("uPower")
            val uBias = uniformFloat1("uBias")

            main {
                val normal: KslVectorExpression<KslTypeFloat3, KslTypeFloat1>
                val origin: KslVectorExpression<KslTypeFloat3, KslTypeFloat1>
                val depthComponent: String

                if (aoSetup.isDeferred) {
                    depthComponent = "z"
                    normal = float3Var(sampleTexture(texture2d("normalTex"), uv.output).xyz)
                    origin = float3Var(sampleTexture(depthTex, uv.output).xyz)

                } else {
                    depthComponent = "a"
                    val normalDepth = float4Var(sampleTexture(depthTex, uv.output))
                    normal = normalDepth.xyz

                    val projPos = float4Var(Vec4f(0f, 0f, 1f, 1f).const)
                    projPos.xy set uv.output * 2f.const - 1f.const
                    projPos set uInvProj * projPos
                    origin = float3Var(projPos.xyz / projPos.w)
                    origin set origin * (normalDepth.w / origin.z)
                }

                val occlFac = float1Var(1f.const)
                val linDistance = float1Var(-origin.z)
                `if`(linDistance gt 0f.const) {
                    val sampleR = float1Var(uRadius)
                    `if`(sampleR lt 0f.const) {
                        sampleR *= -linDistance
                    }

                    `if`(linDistance lt sampleR * 200f.const) {
                        // compute kernel rotation
                        val noiseCoord = float2Var(uv.output * uNoiseScale)
                        val rotVec = float3Var(sampleTexture(noiseTex, noiseCoord).xyz * 2f.const - 1f.const)
                        val tangent = float3Var(normalize(rotVec - normal * dot(rotVec, normal)))
                        val bitangent = float3Var(cross(normal, tangent))
                        val tbn = mat3Var(mat3Value(tangent, bitangent, normal))

                        val occlusion = float1Var(0f.const)
                        val occlusionDiv = float1Var(0f.const)
                        fori(0.const, uKernelSize) { i ->
                            val kernel = float3Var(tbn * uKernel[i])
                            val samplePos = float3Var(origin + kernel * sampleR)
                            val sampleProj = float4Var(uProj * float4Value(samplePos, 1f.const))
                            sampleProj.xyz set sampleProj.xyz / sampleProj.w

                            `if`((sampleProj.x gt (-1f).const) and (sampleProj.x lt 1f.const) and
                                    (sampleProj.y gt (-1f).const) and (sampleProj.y lt 1f.const)) {

                                val sampleUv = float2Var(sampleProj.xy * 0.5f.const + 0.5f.const)
                                val sampleDepth = sampleTexture(depthTex, sampleUv).float1(depthComponent)
                                val rangeCheck = float1Var(1f.const - smoothStep(0f.const, 1f.const, abs(origin.z - sampleDepth) / (4f.const * sampleR)))
                                val occlusionInc = float1Var(clamp((sampleDepth - (samplePos.z + uBias)) * 10f.const, 0f.const, 1f.const))
                                occlusion += occlusionInc * rangeCheck
                                occlusionDiv += 1f.const
                            }
                        }
                        occlusion /= occlusionDiv
                        val distFac = float1Var(1f.const - smoothStep(sampleR * 150f.const, sampleR * 200f.const, linDistance))
                        occlFac set pow(clamp(1f.const - occlusion * distFac * uStrength, 0f.const, 1f.const), uPower)
                    }
                }
                colorOutput(float4Value(occlFac, 0f.const, 0f.const, 1f.const))
            }
        }
    }

    private inner class AoPassShader : KslShader(aoPassProg(), fullscreenShaderPipelineCfg) {
        var noiseTex by texture2d("noiseTex", aoNoiseTex)
        var depthTex by texture2d("depthTex")
        var normalTex by texture2d("normalTex")

        val uProj by uniformMat4f("uProj")
        val uInvProj by uniformMat4f("uInvProj")
        val uKernel by uniform3fv("uKernel", MAX_KERNEL_SIZE)
        var uNoiseScale by uniform2f("uNoiseScale")
        var uKernelSize by uniform1i("uKernelRange", 16)
        var uRadius by uniform1f("uRadius", 1f)
        var uStrength by uniform1f("uStrength", 1.25f)
        var uPower by uniform1f("uPower", 1.5f)
        var uBias by uniform1f("uBias", 0.05f)
    }

    companion object {
        const val MAX_KERNEL_SIZE = 64
        const val NOISE_TEX_SIZE = 4

        private fun generateNoiseTex(): Texture2d {
            val noiseLen = NOISE_TEX_SIZE * NOISE_TEX_SIZE
            val buf = Uint8Buffer(4 * noiseLen)
            val rotAngles = (0 until noiseLen).map { 2f * PI.toFloat() * it / noiseLen }.shuffled()

            for (i in 0 until (NOISE_TEX_SIZE * NOISE_TEX_SIZE)) {
                val ang = rotAngles[i]
                val x = cos(ang)
                val y = sin(ang)
                buf[i*4+0] = ((x * 0.5f + 0.5f) * 255).toInt().toUByte()
                buf[i*4+1] = ((y * 0.5f + 0.5f) * 255).toInt().toUByte()
                buf[i*4+2] = 0u
                buf[i*4+3] = 1u
            }

            val data = TextureData2d(buf, NOISE_TEX_SIZE, NOISE_TEX_SIZE, TexFormat.RGBA)
            val texProps = TextureProps(TexFormat.RGBA, AddressMode.REPEAT, AddressMode.REPEAT,
                minFilter = FilterMethod.NEAREST, magFilter = FilterMethod.NEAREST,
                mipMapping = false, maxAnisotropy = 1)
            return Texture2d(texProps, "ao_noise_tex") { data }
        }

        private val aoNoiseTex: Texture2d by lazy { generateNoiseTex() }
    }
}

class AoSetup private constructor(val linearDepthPass: NormalLinearDepthMapPass?) {
    val isDeferred: Boolean
        get() = linearDepthPass == null
    val isForward: Boolean
        get() = linearDepthPass != null

    companion object {
        fun deferred() = AoSetup(null)
        fun forward(linearDepthPass: NormalLinearDepthMapPass) = AoSetup(linearDepthPass)
    }
}
