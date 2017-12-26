package de.fabmax.kool.demo.earth

import de.fabmax.kool.httpTexture
import de.fabmax.kool.platform.Math
import de.fabmax.kool.platform.RenderContext
import de.fabmax.kool.scene.Mesh
import de.fabmax.kool.scene.MeshData
import de.fabmax.kool.shading.BasicShader
import de.fabmax.kool.shading.ColorModel
import de.fabmax.kool.shading.LightModel
import de.fabmax.kool.shading.basicShader
import de.fabmax.kool.util.MutableVec3f
import de.fabmax.kool.util.Vec2f
import de.fabmax.kool.util.Vec3f

class TileMesh(val earth: Earth, val tx: Int, val ty: Int, val tz: Int) :
        Mesh(MeshData(true, false, true), "$tz/$tx/$ty") {

    val key = tileKey(tx, ty, tz)

    val isCurrentlyVisible get() = isRendered

    private var tileShader: BasicShader

    private var centerNormal = Vec3f(0f)

    private var tmpVec = MutableVec3f()
    private var tmpBndsMin = MutableVec3f()
    private var tmpBndsMax = MutableVec3f()

    var isFadingOut = false
    var isLoaded = false
        private set
    var isTexLoaded = false
        private set

    init {
        generator = {
            val lonW = tx / (1 shl tz).toDouble() * 2 * Math.PI - Math.PI
            val lonE = (tx + 1) / (1 shl tz).toDouble() * 2 * Math.PI - Math.PI

            val stepsExp = 4
            val steps = 1 shl stepsExp
            val tysFac = 1.0 / (1 shl (tz + stepsExp)).toDouble() * 2 * Math.PI
            var prevIndices = IntArray(steps + 1)
            var rowIndices = IntArray(steps + 1)
            for (row in 0..steps) {
                val tmp = prevIndices
                prevIndices = rowIndices
                rowIndices = tmp

                val tys = (ty+1) * steps - row
                val lat = Math.PI * 0.5 - Math.atan(Math.sinh(Math.PI - tys * tysFac))
                val r = Math.sin(lat) * Earth.EARTH_R
                val y = Math.cos(lat) * Earth.EARTH_R
                for (i in 0..steps) {
                    val phi = lonW + (lonE - lonW) * i / steps
                    val x = Math.sin(phi) * r
                    val z = Math.cos(phi) * r
                    val uv = Vec2f(i.toFloat() / steps, 1f - row.toFloat() / steps)

                    val fx = x.toFloat()
                    val fy = y.toFloat()
                    val fz = z.toFloat()
                    val nrm = MutableVec3f(fx, fy, fz).norm()
                    rowIndices[i] = vertex(Vec3f(fx, fy, fz), nrm, uv)

                    if (row == steps / 2 && i == steps / 2) {
                        // center vertex
                        centerNormal = Vec3f(nrm)
                    }

                    if (i > 0 && row > 0) {
                        meshData.addTriIndices(prevIndices[i - 1], rowIndices[i], rowIndices[i - 1])
                        meshData.addTriIndices(prevIndices[i - 1], prevIndices[i], rowIndices[i])
                    }
                }
            }
        }
        tileShader = basicShader {
            colorModel = ColorModel.TEXTURE_COLOR
            //colorModel = if ((tx xor ty) % 2 == 0) ColorModel.STATIC_COLOR else ColorModel.TEXTURE_COLOR
            lightModel = LightModel.NO_LIGHTING
            isAlpha = true
        }
        tileShader.alpha = 0f
        shader = tileShader
        //tileShader.staticColor.set(Color(tz / 10f, 1 - tz / 10f, 0f))
        loadTileTex(tx, ty, tz)

        generateGeometry()
    }

    private fun loadTileTex(x: Int, y: Int, z:Int) {
        tileShader.texture = httpTexture("http://tile.openstreetmap.org/$z/$x/$y.png", "mapnik/$z/$x/$y.png")
    }

    override fun render(ctx: RenderContext) {
        val targetAlpha = 1f
        if (isTexLoaded && !isFadingOut && tileShader.alpha < targetAlpha) {
            // increase alpha as soon as texture is available (but mesh doesn't have to be visible)
            tileShader.alpha += ctx.deltaT * 2
            if (tileShader.alpha >= targetAlpha) {
                tileShader.alpha = targetAlpha
                isLoaded = true
                earth.tileLoaded(this)
            }

        } else if (isFadingOut && tileShader.alpha > 0f) {
            tileShader.alpha -= ctx.deltaT * 2
            if (tileShader.alpha <= 0f) {
                tileShader.alpha = 0f
                earth.tileFadedOut(this)
            }
        }

        super.render(ctx)
    }

    override fun checkIsVisible(ctx: RenderContext): Boolean {
        val tex = tileShader.texture ?: return false
        isTexLoaded = tex.res?.isLoaded ?: false
        val visible = isTexLoaded && super.checkIsVisible(ctx)
        if (visible) {
            toGlobalCoords(tmpVec.set(centerNormal), 0f)
            val cos = scene?.camera?.globalLookDir?.dot(tmpVec) ?: 0f
            return cos < 0.1f

        } else if (!isTexLoaded) {
            // trigger / poll texture loading
            ctx.textureMgr.bindTexture(tex, ctx)
        }
        return false
    }

    companion object {
        fun tileKey(tx: Int, ty: Int, tz: Int): Long = (tz.toLong() shl 58) or
                ((tx and 0x1fffffff).toLong().shl(29)) or
                (ty and 0x1fffffff).toLong()
    }
}