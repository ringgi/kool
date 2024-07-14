package de.fabmax.kool.pipeline.backend.gl

import de.fabmax.kool.pipeline.*
import de.fabmax.kool.pipeline.backend.stats.BackendStats
import de.fabmax.kool.util.BaseReleasable
import de.fabmax.kool.util.Float32Buffer
import de.fabmax.kool.util.releaseWith

abstract class GlRenderPass(val backend: RenderBackendGl): BaseReleasable() {
    protected val gl: GlApi = backend.gl

    private val colorBufferClearVal = Float32Buffer(4)

    private val timeQuery: TimeQuery by lazy { TimeQuery(gl).also { it.releaseWith(this) } }

    protected fun renderViews(renderPass: RenderPass) {
        val q = if (renderPass.isProfileTimes) timeQuery else null
        q?.let {
            if (it.isAvailable) {
                renderPass.tGpu = it.getQueryResultMillis()
            }
            it.begin()
        }

        for (mipLevel in 0 until renderPass.numRenderMipLevels) {
            renderPass.setupMipLevel(mipLevel)

            when (renderPass.viewRenderMode) {
                RenderPass.ViewRenderMode.SINGLE_RENDER_PASS -> {
                    setupFramebuffer(0, mipLevel)
                    clear(renderPass)
                    for (viewIndex in renderPass.views.indices) {
                        renderPass.setupView(viewIndex)
                        renderView(renderPass.views[viewIndex], viewIndex, mipLevel)
                    }
                }

                RenderPass.ViewRenderMode.MULTI_RENDER_PASS -> {
                    for (viewIndex in renderPass.views.indices) {
                        setupFramebuffer(viewIndex, mipLevel)
                        clear(renderPass)
                        renderPass.setupView(viewIndex)
                        renderView(renderPass.views[viewIndex], viewIndex, mipLevel)
                    }
                }
            }
        }

        var anySingleShots = false
        for (i in renderPass.frameCopies.indices) {
            copy(renderPass.frameCopies[i])
            anySingleShots = anySingleShots || renderPass.frameCopies[i].isSingleShot
        }
        if (anySingleShots) {
            renderPass.frameCopies.removeAll { it.isSingleShot }
        }

        q?.end()
    }

    protected fun renderView(view: RenderPass.View, viewIndex: Int, mipLevel: Int) {
        val viewport = view.viewport
        val x = viewport.x shr mipLevel
        val y = viewport.y shr mipLevel
        val w = viewport.width shr mipLevel
        val h = viewport.height shr mipLevel

        // kool viewport coordinate origin is top left, while OpenGL's is bottom left
        val windowHeight = view.renderPass.size.y shr mipLevel
        gl.viewport(x, windowHeight - y - h, w, h)

        // only do copy when last mip-level is rendered
        val isLastMipLevel = mipLevel == view.renderPass.numRenderMipLevels - 1
        var nextFrameCopyI = 0
        var nextFrameCopy = if (isLastMipLevel) view.frameCopies.getOrNull(nextFrameCopyI++) else null
        var anySingleShots = false

        view.drawQueue.forEach { cmd ->
            nextFrameCopy?.let { frameCopy ->
                if (cmd.drawGroupId > frameCopy.drawGroupId) {
                    copy(frameCopy)
                    anySingleShots = anySingleShots || frameCopy.isSingleShot
                    nextFrameCopy = view.frameCopies.getOrNull(nextFrameCopyI++)
                    // copy messes up the currently bound frame buffer, restore the correct one
                    setupFramebuffer(viewIndex, mipLevel)
                }
            }

            if (cmd.isActive) {
                val drawInfo = backend.shaderMgr.bindDrawShader(cmd)
                val isValid = cmd.geometry.numIndices > 0  &&  drawInfo.isValid && drawInfo.numIndices > 0

                if (isValid) {
                    GlState.setupPipelineAttribs(cmd.pipeline, view.renderPass.isReverseDepth, gl)

                    val insts = cmd.instances
                    if (insts == null) {
                        gl.drawElements(drawInfo.primitiveType, drawInfo.numIndices, drawInfo.indexType)
                        BackendStats.addDrawCommands(1, cmd.geometry.numPrimitives)
                    } else if (insts.numInstances > 0) {
                        gl.drawElementsInstanced(drawInfo.primitiveType, drawInfo.numIndices, drawInfo.indexType, insts.numInstances)
                        BackendStats.addDrawCommands(1, cmd.geometry.numPrimitives * insts.numInstances)
                    }
                }
            }
        }

        nextFrameCopy?.let {
            copy(it)
            anySingleShots = anySingleShots || it.isSingleShot
        }
        if (anySingleShots) {
            view.frameCopies.removeAll { it.isSingleShot }
        }
    }

    protected abstract fun setupFramebuffer(viewIndex: Int, mipLevel: Int)

    protected abstract fun copy(frameCopy: FrameCopy)

    protected fun FrameCopy.setupCopyTargets(width: Int, height: Int, mipLevels: Int, glTarget: Int) {
        val layers = if (glTarget == gl.TEXTURE_CUBE_MAP) 6 else 1

        if (isCopyColor) {
            for (i in colorCopy.indices) {
                val dst = colorCopy[i]
                var loaded = dst.gpuTexture as LoadedTextureGl?
                if (loaded == null || loaded.width != width || loaded.height != height) {
                    dst.gpuTexture?.release()
                    createColorAttachmentTexture(width, height, mipLevels, dst, glTarget)
                    loaded = dst.gpuTexture as LoadedTextureGl
                    loaded.bind()
                    loaded.setSize(width, height, layers)
                    loaded.applySamplerSettings(dst.props.defaultSamplerSettings)
                    dst.gpuTexture = loaded
                }
            }
        }

        if (isCopyDepth) {
            val dst = depthCopy2d
            var loaded = dst.gpuTexture as LoadedTextureGl?
            if (loaded == null || loaded.width != width || loaded.height != height) {
                dst.gpuTexture?.release()
                createDepthAttachmentTexture(width, height, mipLevels, dst, glTarget)
                loaded = dst.gpuTexture as LoadedTextureGl
                loaded.bind()
                loaded.setSize(width, height, layers)
                loaded.applySamplerSettings(dst.props.defaultSamplerSettings)
                dst.gpuTexture = loaded
            }
        }
    }

    fun clear(renderPass: RenderPass) {
        for (i in renderPass.clearColors.indices) {
            renderPass.clearColors[i]?.let { color ->
                colorBufferClearVal.clear()
                color.putTo(colorBufferClearVal)
                gl.clearBufferfv(gl.COLOR, i, colorBufferClearVal)
            }
        }
        if (renderPass.clearDepth) {
            GlState.setWriteDepth(true, gl)
            gl.clearDepth(if (renderPass.isReverseDepth) 0f else 1f)
            gl.clear(gl.DEPTH_BUFFER_BIT)
        }
    }

    protected fun createAndAttachDepthRenderBuffer(pass: OffscreenRenderPass, mipLevel: Int): GlRenderbuffer {
        val rbo = gl.createRenderbuffer()
        val mipWidth = pass.width shr mipLevel
        val mipHeight = pass.height shr mipLevel
        gl.bindRenderbuffer(gl.RENDERBUFFER, rbo)
        gl.renderbufferStorage(gl.RENDERBUFFER, gl.DEPTH_COMPONENT32F, mipWidth, mipHeight)
        gl.framebufferRenderbuffer(gl.FRAMEBUFFER, gl.DEPTH_ATTACHMENT, gl.RENDERBUFFER, rbo)
        return rbo
    }

    protected fun createColorAttachmentTexture(
        width: Int,
        height: Int,
        mipLevels: Int,
        colorTexture: Texture,
        texTarget: Int
    ): GlTexture {
        val format = colorTexture.props.format
        val intFormat = format.glInternalFormat(gl)
        val layers = if (texTarget == gl.TEXTURE_CUBE_MAP) 6 else 1

        val estSize = Texture.estimatedTexSize(width, height, layers, mipLevels, format.pxSize).toLong()
        val tex = LoadedTextureGl(texTarget, gl.createTexture(), backend, colorTexture, estSize)
        tex.setSize(width, height, layers)
        tex.bind()
        tex.applySamplerSettings(colorTexture.props.defaultSamplerSettings)
        gl.texStorage2D(texTarget, mipLevels, intFormat, width, height)

        val glColorTexture = tex.glTexture
        colorTexture.gpuTexture = tex
        colorTexture.loadingState = Texture.LoadingState.LOADED
        return glColorTexture
    }

    protected fun createDepthAttachmentTexture(
        width: Int,
        height: Int,
        mipLevels: Int,
        depthTexture: Texture,
        texTarget: Int
    ): GlTexture {
        val intFormat = gl.DEPTH_COMPONENT32F
        val layers = if (texTarget == gl.TEXTURE_CUBE_MAP) 6 else 1

        val estSize = Texture.estimatedTexSize(width, height, layers, mipLevels, 4).toLong()
        val tex = LoadedTextureGl(texTarget, gl.createTexture(), backend, depthTexture, estSize)
        tex.setSize(width, height, layers)
        tex.bind()
        tex.applySamplerSettings(depthTexture.props.defaultSamplerSettings)
        gl.texStorage2D(texTarget, mipLevels, intFormat, width, height)

        val glDepthTexture = tex.glTexture
        depthTexture.gpuTexture = tex
        depthTexture.loadingState = Texture.LoadingState.LOADED
        return glDepthTexture
    }

    private object GlState {
        var actIsWriteDepth = true
        var actDepthTest: DepthCompareOp? = null
        var actCullMethod: CullMethod? = null
        var lineWidth = 0f

        fun setupPipelineAttribs(pipeline: DrawPipeline, isReversedDepth: Boolean, gl: GlApi) {
            setBlendMode(pipeline.blendMode, gl)
            setDepthTest(pipeline, isReversedDepth, gl)
            setWriteDepth(pipeline.isWriteDepth, gl)
            setCullMethod(pipeline.cullMethod, gl)
            if (lineWidth != pipeline.lineWidth) {
                lineWidth = pipeline.lineWidth
                gl.lineWidth(pipeline.lineWidth)
            }
        }

        private fun setCullMethod(cullMethod: CullMethod, gl: GlApi) {
            if (this.actCullMethod != cullMethod) {
                this.actCullMethod = cullMethod
                when (cullMethod) {
                    CullMethod.CULL_BACK_FACES -> {
                        gl.enable(gl.CULL_FACE)
                        gl.cullFace(gl.BACK)
                    }
                    CullMethod.CULL_FRONT_FACES -> {
                        gl.enable(gl.CULL_FACE)
                        gl.cullFace(gl.FRONT)
                    }
                    CullMethod.NO_CULLING -> gl.disable(gl.CULL_FACE)
                }
            }
        }

        fun setWriteDepth(enabled: Boolean, gl: GlApi) {
            if (actIsWriteDepth != enabled) {
                actIsWriteDepth = enabled
                gl.depthMask(enabled)
            }
        }

        private fun setDepthTest(pipeline: DrawPipeline, isReversedDepth: Boolean, gl: GlApi) {
            val depthCompareOp = if (isReversedDepth && pipeline.autoReverseDepthFunc) {
                when (pipeline.depthCompareOp) {
                    DepthCompareOp.LESS -> DepthCompareOp.GREATER
                    DepthCompareOp.LESS_EQUAL -> DepthCompareOp.GREATER_EQUAL
                    DepthCompareOp.GREATER -> DepthCompareOp.LESS
                    DepthCompareOp.GREATER_EQUAL -> DepthCompareOp.LESS_EQUAL
                    else -> pipeline.depthCompareOp
                }
            } else {
                pipeline.depthCompareOp
            }

            if (actDepthTest != depthCompareOp) {
                actDepthTest = depthCompareOp
                if (depthCompareOp == DepthCompareOp.ALWAYS && !pipeline.isWriteDepth) {
                    gl.disable(gl.DEPTH_TEST)
                } else {
                    gl.enable(gl.DEPTH_TEST)
                    gl.depthFunc(depthCompareOp.glOp(gl))
                }
            }
        }

        private fun setBlendMode(blendMode: BlendMode, gl: GlApi) {
            when (blendMode) {
                BlendMode.DISABLED -> gl.disable(gl.BLEND)
                BlendMode.BLEND_ADDITIVE -> {
                    gl.blendFunc(gl.ONE, gl.ONE)
                    gl.enable(gl.BLEND)
                }
                BlendMode.BLEND_MULTIPLY_ALPHA -> {
                    gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA)
                    gl.enable(gl.BLEND)
                }
                BlendMode.BLEND_PREMULTIPLIED_ALPHA -> {
                    gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA)
                    gl.enable(gl.BLEND)
                }
            }
        }
    }

    private val DepthCompareOp.glOp: Int
        get() = when(this) {
            DepthCompareOp.ALWAYS -> gl.ALWAYS
            DepthCompareOp.NEVER -> gl.NEVER
            DepthCompareOp.LESS -> gl.LESS
            DepthCompareOp.LESS_EQUAL -> gl.LEQUAL
            DepthCompareOp.GREATER -> gl.GREATER
            DepthCompareOp.GREATER_EQUAL -> gl.GEQUAL
            DepthCompareOp.EQUAL -> gl.EQUAL
            DepthCompareOp.NOT_EQUAL -> gl.NOTEQUAL
        }
}