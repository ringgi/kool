package de.fabmax.kool.pipeline

import de.fabmax.kool.KoolContext
import de.fabmax.kool.scene.Node

open class OffscreenRenderPass2dPingPong(config: Config) : OffscreenRenderPass(
    renderPassConfig {
        name = config.name
        size(1, 1)
        depthTargetRenderBuffer()
        colorTargetRenderBuffer()
    }
) {

    var pingPongPasses = 1

    val pingContent = Node()
    val pongContent = Node()

    val ping: OffscreenRenderPass2d = OffscreenRenderPass2d(pingContent, config)
    val pong: OffscreenRenderPass2d = OffscreenRenderPass2d(pongContent, config)

    var onDrawPing: ((Int) -> Unit)? = null
    var onDrawPong: ((Int) -> Unit)? = null

    override val views: List<View> = emptyList()

    override fun setSize(width: Int, height: Int, ctx: KoolContext) {
        super.setSize(width, height, ctx)
        ping.setSize(width, height, ctx)
        pong.setSize(width, height, ctx)
    }

    override fun update(ctx: KoolContext) {
        ping.update(ctx)
        pong.update(ctx)
    }

    override fun collectDrawCommands(ctx: KoolContext) {
        super.collectDrawCommands(ctx)
        ping.collectDrawCommands(ctx)
        pong.collectDrawCommands(ctx)
    }

    override fun afterDraw(ctx: KoolContext) {
        super.afterDraw(ctx)
        ping.afterDraw(ctx)
        pong.afterDraw(ctx)
    }

    override fun release() {
        super.release()
        ping.release()
        pong.release()
        pingContent.release()
        pongContent.release()
    }
}