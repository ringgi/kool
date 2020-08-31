package de.fabmax.kool.demo.atmosphere

import de.fabmax.kool.AssetManager
import de.fabmax.kool.KoolContext
import de.fabmax.kool.demo.ControlUiBuilder
import de.fabmax.kool.demo.Demo
import de.fabmax.kool.demo.DemoScene
import de.fabmax.kool.demo.controlUi
import de.fabmax.kool.math.MutableVec3f
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.clamp
import de.fabmax.kool.pipeline.Attribute
import de.fabmax.kool.pipeline.RenderPass
import de.fabmax.kool.pipeline.Texture
import de.fabmax.kool.scene.*
import de.fabmax.kool.scene.ui.*
import de.fabmax.kool.toString
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.IndexedVertexList
import de.fabmax.kool.util.SimpleShadowMap
import de.fabmax.kool.util.deferred.DeferredPipeline
import de.fabmax.kool.util.deferred.DeferredPipelineConfig
import de.fabmax.kool.util.deferred.deferredPbrShader
import kotlinx.coroutines.delay
import kotlin.math.pow

class AtmosphereDemo : DemoScene("Atmosphere") {

    val sunColor = Color.WHITE
    val sun = Light().apply {
        setDirectional(Vec3f.NEG_Z_AXIS)
        setColor(sunColor, 5f)
    }
    private var sunIntensity = 1f

    private var time = 0.5f
    private var moonTime = 0f
    private var animateTime = true
    private var timeSlider: Slider? = null

    val textures = mutableMapOf<String, Texture>()
    lateinit var deferredPipeline: DeferredPipeline
    val atmoShader = AtmosphericScatteringShader()

    private lateinit var opticalDepthLutPass: OpticalDepthLutPass
    private var sceneCompositing: AtmosphereSceneCompositing? = null
    private val shadows = mutableListOf<SimpleShadowMap>()
    private val earthTransform = Group("earth")
    private val camTransform = EarthCamTransform(earthRadius)

    private lateinit var menuContainer: UiContainer
    private lateinit var loadingLabel: Label

    private var loadingComplete = false
    private var sceneSetupComplete = false

    val cameraHeight: Float
        get() {
            return mainScene.camera.globalPos.distance(Vec3f.ZERO) - earthRadius
        }

    override fun lateInit(ctx: KoolContext) {
        camTransform.apply {
            mainScene.registerDragHandler(this)
            +mainScene.camera
        }

        ctx.assetMgr.launch {
            delay(500)
            loadTex(texMilkyway, "${Demo.awsBaseUrl}/solarsystem/milkyway-dark.jpg")
            loadTex(texSun, "${Demo.awsBaseUrl}/solarsystem/sun.png")
            loadTex(texSunBg, "${Demo.awsBaseUrl}/solarsystem/sun_bg.png")
            loadTex(texMoon, "${Demo.awsBaseUrl}/solarsystem/moon.jpg")
            loadTex(EarthShader.texEarthDay, "${Demo.awsBaseUrl}/solarsystem/earth_day.jpg")
            loadTex(EarthShader.texEarthNight, "${Demo.awsBaseUrl}/solarsystem/earth_night.jpg")
            loadTex(EarthShader.texEarthNrm, "${Demo.awsBaseUrl}/solarsystem/earth_nrm.jpg")
            loadTex(EarthShader.texEarthHeight, "${Demo.awsBaseUrl}/solarsystem/earth_height.jpg")
            loadTex(EarthShader.texOceanNrm, "${Demo.awsBaseUrl}/solarsystem/oceanNrm.jpg")
            loadingLabel.text = "Initializing Scene..."
            delay(100)
            loadingComplete = true
        }
    }

    private suspend fun AssetManager.loadTex(key: String, path: String) {
        loadingLabel.text = "Loading texture \"$key\"..."
        textures[key] = loadAndPrepareTexture(path)
    }

    override fun setupMainScene(ctx: KoolContext) = scene {
        opticalDepthLutPass = OpticalDepthLutPass()
        addOffscreenPass(opticalDepthLutPass)

        lighting.lights.clear()
        lighting.lights += sun
        shadows += SimpleShadowMap(this, 0)

        val defCfg = DeferredPipelineConfig().apply {
            isWithEmissive = true
            isWithAmbientOcclusion = false
            isWithScreenSpaceReflections = false
            maxGlobalLights = 1
            shadowMaps = shadows
        }
        deferredPipeline = DeferredPipeline(this, defCfg)
        deferredPipeline.pbrPass.sceneShader.ambient = Color(0.05f, 0.05f, 0.05f).toLinear()

        atmoShader.apply {
            opticalDepthLut = opticalDepthLutPass.colorTexture
            sceneColor = deferredPipeline.pbrPass.colorTexture
            scenePos = deferredPipeline.mrtPass.positionAo
            surfaceRadius = earthRadius
            atmosphereRadius = 6500f / kmPerUnit

            scatteringCoeffs = Vec3f(0.75f, 1.15f, 1.35f)
            rayleighColor = Color(0.5f, 0.5f, 1f, 1f)
            mieColor = Color(1f, 0.35f, 0.35f, 1f)
            mieG = 0.995f
            scatteringCoeffStrength = 1.0f
        }

        shadows.forEach { shadow ->
            shadow.drawNode = deferredPipeline.contentGroup
            shadow.shadowBounds = earthTransform.bounds
            //shadow.shadowBounds = deferredPipeline.contentGroup.bounds
        }

        onUpdate += { ev ->
            if (loadingComplete) {
                if (!sceneSetupComplete) {
                    sceneSetupComplete = true
                    finalizeSceneSetup(deferredPipeline)
                }

                (mainScene.camera as PerspectiveCamera).apply {
                    val h = globalPos.length() - earthRadius
                    position.set(Vec3f.ZERO)
                    lookAt.set(Vec3f.NEG_Z_AXIS)
                    clipNear = (h * 0.5f).clamp(0.003f, 5f)
                    clipFar = clipNear * 1000f
                }

                if (animateTime) {
                    val dt = ev.deltaT / 120
                    // setting time slider value results in timer slider's onChange function being called which also sets time
                    timeSlider?.value = (time + dt) % 1f
                    moonTime = (moonTime + dt / moonT)
                }
            }
        }

        onDispose += {
            textures.values.forEach { it.dispose() }
        }
    }

    private fun finalizeSceneSetup(deferredPipeline: DeferredPipeline) {
        menuContainer.isVisible = true
        loadingLabel.isVisible = false

        deferredPipeline.contentGroup.setupContent()

        sceneCompositing = AtmosphereSceneCompositing(this)

        updateSun()
    }

    private fun Group.setupContent() {
        +earthTransform.apply {
            +camTransform

            +textureMesh(isNormalMapped = true) {
                generate {
                    icoSphere {
                        steps = 7
                        radius = earthRadius
                    }
                }
                val earthShader = EarthShader(textures).also { shader = it }
                earthShader.oceanNrmTex = textures[EarthShader.texOceanNrm]

                onUpdate += { ev ->
                    val dirToSun = MutableVec3f(sun.direction).scale(-1f)

                    earthShader.uDirToSun?.value?.let { uSunDir ->
                        uSunDir.set(dirToSun)
                        toLocalCoords(uSunDir, 0f)
                    }

                    val camHeight = cameraHeight * kmPerUnit
                    val colorMix = (camHeight / 100f).clamp()
                    earthShader.uWaterColor?.value?.set(waterColorLow.mix(waterColorHigh, colorMix))
                    earthShader.uNormalShift?.value?.set(ev.time.toFloat() * 0.0051f, ev.time.toFloat() * 0.0037f, ev.time.toFloat() * -0.0071f, ev.time.toFloat() * -0.0039f)

                    atmoShader.dirToSun = dirToSun
                }
            }

            onUpdate += {
                setIdentity()
                // earth rotation axis is tilted by 23.44°
                rotate(23.44f, Vec3f.NEG_X_AXIS)
                // rotate according to time
                rotate(time * 360, Vec3f.Y_AXIS)
            }
        }

        +group {
            isFrustumChecked = false
            +Moon()

            onUpdate += {
                setIdentity()
                rotate(moonInclination, Vec3f.X_AXIS)
                rotate(360f * moonTime, Vec3f.Y_AXIS)
                translate(0f, 0f, moonDist)
            }
        }
    }

    private inner class Moon : Mesh(IndexedVertexList(Attribute.POSITIONS, Attribute.NORMALS, Attribute.TEXTURE_COORDS), "moon") {
        init {
            isFrustumChecked = false
            generate {
                rotate(180f, Vec3f.Y_AXIS)
                icoSphere {
                    steps = 4
                    radius = moonRadius
                }
            }
            shader = deferredPbrShader {
                useAlbedoMap(textures[texMoon])
                roughness = 0.7f
            }
        }

        override fun collectDrawCommands(updateEvent: RenderPass.UpdateEvent) {
            val rpCam = updateEvent.camera

            if (rpCam is PerspectiveCamera) {
                // Use modified camera clip values when rendering moon. This can cause artifacts but works in
                // most situations and is better than moon being completely clipped away

                val clipN = rpCam.clipNear
                val clipF = rpCam.clipFar
                val d = globalCenter.distance(rpCam.globalPos) + moonRadius
                val customClip = d > clipF

                if (customClip) {
                    rpCam.clipFar = d
                    rpCam.clipNear = d / 1000f
                    rpCam.updateCamera(updateEvent.ctx, updateEvent.viewport)
                }
                super.collectDrawCommands(updateEvent)
                if (customClip) {
                    rpCam.clipNear = clipN
                    rpCam.clipFar = clipF
                    rpCam.updateCamera(updateEvent.ctx, updateEvent.viewport)
                }

            } else {
                super.collectDrawCommands(updateEvent)
            }
        }
    }

    override fun setupMenu(ctx: KoolContext) = controlUi(ctx) {
        uiRoot.apply {
            loadingLabel = label("Loading...") {
                layoutSpec.setOrigin(zero(), zero(), zero())
                layoutSpec.setSize(pcs(100f), pcs(100f), full())
                textAlignment = Gravity(Alignment.CENTER, Alignment.CENTER)
            }
            +loadingLabel
        }

//        image(opticalDepthLutPass.colorTexture).apply {
//            aspectRatio = 1f
//            relativeWidth = 0.25f
//        }

        this@AtmosphereDemo.menuContainer = menuContainer
        menuContainer.isVisible = false
        menuWidth = 380f
        section("Scattering") {
            colorSlider("R:", Color.RED, atmoShader.scatteringCoeffs.x, 0f, 2f) { updateScatteringCoeffs(x = value) }
            colorSlider("G:", Color.GREEN, atmoShader.scatteringCoeffs.y, 0f, 2f) { updateScatteringCoeffs(y = value) }
            colorSlider("B:", Color.BLUE, atmoShader.scatteringCoeffs.z, 0f, 2f) { updateScatteringCoeffs(z = value) }
            gap(8f)
            sliderWithValueSmall("Pow:", atmoShader.scatteringCoeffPow, 1f, 20f, 2, widthLabel = 10f) {
                atmoShader.scatteringCoeffPow = value
            }
            sliderWithValueSmall("Str:", atmoShader.scatteringCoeffStrength, 0.01f, 5f, 2, widthLabel = 10f) {
                atmoShader.scatteringCoeffStrength = value
            }
        }

        section("Rayleigh") {
            colorSlider("R:", Color.RED, atmoShader.rayleighColor.r, 0f, 4f) { updateRayleighColor(r = value) }
            colorSlider("G:", Color.GREEN, atmoShader.rayleighColor.g, 0f, 4f) { updateRayleighColor(g = value) }
            colorSlider("B:", Color.BLUE, atmoShader.rayleighColor.b, 0f, 4f) { updateRayleighColor(b = value) }
            gap(8f)
            sliderWithValueSmall("Str:", atmoShader.rayleighColor.a, 0f, 2f, widthLabel = 10f) { updateRayleighColor(strength = value) }
        }
        section("Mie") {
//            colorSlider("R:", Color.RED, atmoShader.mieColor.r, 0f, 4f) { updateMieColor(r = value) }
//            colorSlider("G:", Color.GREEN, atmoShader.mieColor.g, 0f, 4f) { updateMieColor(g = value) }
//            colorSlider("B:", Color.BLUE, atmoShader.mieColor.b, 0f, 4f) { updateMieColor(b = value) }
//            gap(8f)
            sliderWithValueSmall("Str:", atmoShader.mieColor.a, 0f, 2f, widthLabel = 10f) { updateMieColor(strength = value) }
            sliderWithValueSmall("g:", atmoShader.mieG, 0.5f, 0.999f, 3, widthLabel = 10f) { atmoShader.mieG = value }
        }

        section("Atmosphere") {
            val thickFmt: (Float) -> String = { "${it.toString(0)} km" }
            sliderWithValueSmall("Thickness:", (atmoShader.atmosphereRadius - earthRadius) * kmPerUnit, 10f, 1000f, textFormat = thickFmt, widthLabel = 24f) {
                updateAtmosphereThickness(value)
            }
            sliderWithValueSmall("Falloff:", opticalDepthLutPass.densityFalloff, 0f, 15f, 2, widthLabel = 24f) {
                opticalDepthLutPass.densityFalloff = value
            }
        }

        section("View") {
            sliderWithValueSmall("Sun:", atmoShader.sunColor.a, 0.1f, 5f, 2, widthLabel = 24f) {
                sunIntensity = value
                updateSun()
            }
            val timeFmt: (Float) -> String = {
                val t = it * 24
                val h = t.toInt()
                val m = ((t % 1f) * 60).toInt()
                val m0 = if (m < 10) "0" else ""
                "$h:$m0$m"
            }
            timeSlider = sliderWithValueSmall("Time:", time, 0f, 1f, textFormat = timeFmt, widthLabel = 24f) {
                time = value
                updateSun()
            }
            toggleButton("Animate Time", animateTime) { animateTime = isEnabled }
            textWithValue("Camera Height:", "").apply {
                onUpdate += {
                    val h = cameraHeight * kmPerUnit
                    text = "${h.toString(1)} km"
                }
            }
        }
    }

    private fun updateSun() {
        val lightDir = MutableVec3f(0f, 0f, -1f)
        atmoShader.dirToSun = lightDir
        atmoShader.sunColor = sunColor.withAlpha(sunIntensity)

        mainScene.lighting.lights[0].apply {
            setDirectional(MutableVec3f(lightDir).scale(-1f))
            setColor(sunColor, sunIntensity * 5)
        }
    }

    private fun updateRayleighColor(r: Float = atmoShader.rayleighColor.r, g: Float = atmoShader.rayleighColor.g, b: Float = atmoShader.rayleighColor.b, strength: Float = atmoShader.rayleighColor.a) {
        atmoShader.rayleighColor = Color(r, g, b, strength)
    }

    private fun updateMieColor(r: Float = atmoShader.mieColor.r, g: Float = atmoShader.mieColor.g, b: Float = atmoShader.mieColor.b, strength: Float = atmoShader.mieColor.a) {
        atmoShader.mieColor = Color(r, g, b, strength)
    }

    private fun updateScatteringCoeffs(x: Float = atmoShader.scatteringCoeffs.x, y: Float = atmoShader.scatteringCoeffs.y, z: Float = atmoShader.scatteringCoeffs.z) {
        atmoShader.scatteringCoeffs = Vec3f(x, y, z)
    }

    private fun updateAtmosphereThickness(newThickness: Float) {
        val atmoRadius = earthRadius + newThickness / kmPerUnit
        atmoShader.atmosphereRadius = atmoRadius
        opticalDepthLutPass.atmosphereRadius = atmoRadius
    }

    private fun ControlUiBuilder.colorSlider(label: String, color: Color, initialValue: Float, min: Float, max: Float, onChange: Slider.() -> Unit): Slider {
        val slider = sliderWithValueSmall(label, initialValue, min, max, widthLabel = 10f, onChange = onChange)
        slider.knobColor.setCustom(color)
        slider.trackColorHighlighted.setCustom(color.mix(Color.BLACK, 0.5f))
        return slider
    }

    companion object {
        const val kmPerUnit = 100f
        const val earthRadius = 6000f / kmPerUnit

        const val moonRadius = 1750f / kmPerUnit
        const val moonDistScale = 0.25f
        const val moonDist = 384400 / kmPerUnit * moonDistScale
        const val moonInclination = 5.145f

        // scaled moon orbital period (according to kepler's 3rd law)
        val keplerC = (moonDist / moonDistScale).pow(3) / 27.32f.pow(2)
        val moonT = moonDist.pow(3) / keplerC

        val waterColorLow = Color.fromHex("0D1F56").toLinear()
        val waterColorHigh = Color.fromHex("020514").toLinear()

        const val texMilkyway = "Milkyway"
        const val texSun = "Sun"
        const val texSunBg = "Sun Background"
        const val texMoon = "Moon"
    }
}