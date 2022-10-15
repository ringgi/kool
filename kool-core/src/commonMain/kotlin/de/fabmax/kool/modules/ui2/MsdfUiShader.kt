package de.fabmax.kool.modules.ui2

import de.fabmax.kool.modules.ksl.KslShader
import de.fabmax.kool.modules.ksl.blocks.mvpMatrix
import de.fabmax.kool.modules.ksl.lang.*
import de.fabmax.kool.pipeline.Attribute
import de.fabmax.kool.pipeline.CullMethod
import de.fabmax.kool.pipeline.GlslType

class MsdfUiShader : KslShader(Model(), PipelineConfig().apply { cullMethod = CullMethod.NO_CULLING }) {
    var fontMap by texture2d("tFontMap")

    class Model : KslProgram("Msdf UI2 Shader") {
        init {
            val fgColor = interStageFloat4(interpolation = KslInterStageInterpolation.Flat)
            val msdfProps = interStageFloat4(interpolation = KslInterStageInterpolation.Flat)
            val clipBounds = interStageFloat4(interpolation = KslInterStageInterpolation.Flat)
            val screenPos = interStageFloat2()
            val uv = interStageFloat2()

            vertexStage {
                main {
                    fgColor.input set vertexAttribFloat4(Attribute.COLORS.name)
                    msdfProps.input set vertexAttribFloat4(ATTRIB_MSDF_PROPS.name)
                    clipBounds.input set vertexAttribFloat4(Ui2Shader.ATTRIB_CLIP.name)
                    uv.input set vertexAttribFloat2(Attribute.TEXTURE_COORDS.name)

                    val mvp = mat4Var(mvpMatrix().matrix)
                    val vertexPos = float4Var(float4Value(vertexAttribFloat3(Attribute.POSITIONS.name), 1f))
                    screenPos.input set vertexPos.xy
                    outPosition set mvp * vertexPos
                }
            }

            fragmentStage {
                val median3 = functionFloat1("median") {
                    val p = paramFloat3("p")
                    body {
                        max(min(p.x, p.y), min(max(p.x, p.y), p.z))
                    }
                }

                val computeOpacity = functionFloat1("computeOpacity") {
                    val msdf = paramFloat4("msdf")
                    val props = paramFloat4("props")

                    body {
                        val sd = float1Var(median3(msdf.rgb))
                        val dist = float1Var(sd - 0.5f.const + props.y)

                        // branch-less version of "if (dist > cutoff) dist = 2.0 * cutoff - dist"
                        val p = step(props.z, dist)
                        dist set dist + p * 2f.const * (props.z - dist)

                        val screenPxDistance = float1Var(props.x * dist)
                        clamp(screenPxDistance + 0.5f.const, 0f.const, 1f.const)
                    }
                }

                main {
                    `if` (any(screenPos.output lt clipBounds.output.xy) or
                            any(screenPos.output gt clipBounds.output.zw)) {
                        discard()

                    }.`else` {
                        val fontMap = texture2d("tFontMap")
                        val msdf = sampleTexture(fontMap, uv.output)
                        val color = float4Var(fgColor.output)
                        color.a *= computeOpacity(msdf, msdfProps.output)
                        colorOutput(color)
                    }
                }
            }
        }
    }

    companion object {
        val ATTRIB_MSDF_PROPS = Attribute("aMsdfProps", GlslType.VEC_4F)

        val MSDF_UI_MESH_ATTRIBS = listOf(ATTRIB_MSDF_PROPS, Attribute.COLORS, Ui2Shader.ATTRIB_CLIP, Attribute.POSITIONS, Attribute.TEXTURE_COORDS)
    }
}