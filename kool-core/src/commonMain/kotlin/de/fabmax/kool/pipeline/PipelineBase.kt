package de.fabmax.kool.pipeline

import de.fabmax.kool.util.*

/**
 * Base class for regular (graphics) and compute pipelines. A pipeline includes the shader and additional attributes
 * like the corresponding data layout, etc.
 */
abstract class PipelineBase(val name: String, val bindGroupLayouts: BindGroupLayouts) : BaseReleasable() {

    protected val pipelineHashBuilder = LongHashBuilder()

    /**
     * pipelineHash is used to determine pipeline equality. In contrast to standard java hashCode() a 64-bit hash is
     * used to make collisions less likely.
     */
    abstract val pipelineHash: LongHash

    abstract val shaderCode: ShaderCode

    val pipelineDataLayout = bindGroupLayouts[BindGroupScope.PIPELINE]
    var pipelineData = pipelineDataLayout.createData()
        set(value) {
            check(value.layout == pipelineDataLayout) {
                "Given BindGroupData does not match this pipeline's data bind group layout"
            }
            field = value
        }

    internal var pipelineBackend: PipelineBackend? = null

    init {
        pipelineHashBuilder += bindGroupLayouts.viewScope.hash
        pipelineHashBuilder += bindGroupLayouts.pipelineScope.hash
        pipelineHashBuilder += bindGroupLayouts.meshScope.hash
    }

    override fun release() {
        super.release()
        if (pipelineBackend?.isReleased == false) {
            pipelineBackend?.release()
        }
        pipelineData.release()
    }

    inline fun <reified T: BindingLayout> findBindingLayout(predicate: (T) -> Boolean): Pair<BindGroupLayout, T>? {
        for (group in bindGroupLayouts.asList) {
            group.bindings.filterIsInstance<T>().find(predicate)?.let {
                return group to it
            }
        }
        return null
    }

    fun findBindGroupItemByName(name: String): BindingLayout? {
        return bindGroupLayouts.asList.firstNotNullOfOrNull { grp -> grp.bindings.find { it.name == name } }
    }
}

interface PipelineBackend : Releasable {
    fun removeUser(user: Any)
}

class PipelineData(val scope: BindGroupScope) : BaseReleasable() {
    private val bindGroupData = mutableMapOf<LongHash, UpdateAwareBindGroupData>()

    fun getPipelineData(pipeline: PipelineBase): BindGroupData {
        val layout = pipeline.bindGroupLayouts[scope]
        val data = bindGroupData.getOrPut(layout.hash) { UpdateAwareBindGroupData(layout.createData()) }
        return data.data
    }

    fun getPipelineDataUpdating(pipeline: PipelineBase, binding: Int): BindGroupData? {
        val layout = pipeline.bindGroupLayouts[scope]
        val data = bindGroupData.getOrPut(layout.hash) { UpdateAwareBindGroupData(layout.createData()) }
        return if (data.markBindingUpdate(binding)) data.data else null
    }

    fun setPipelineData(data: BindGroupData, pipeline: PipelineBase) {
        val layout = pipeline.bindGroupLayouts[scope]
        check(layout == data.layout) {
            "Given BindGroupData does not match this pipeline's $scope data bind group layout"
        }
        bindGroupData[layout.hash] = UpdateAwareBindGroupData(layout.createData())
    }

    fun discardPipelineData(pipeline: PipelineBase) {
        val layout = pipeline.bindGroupLayouts[scope]
        bindGroupData.remove(layout.hash)?.data?.release()
    }

    override fun release() {
        super.release()
        bindGroupData.values.forEach { it.data.release() }
    }

    private class UpdateAwareBindGroupData(val data: BindGroupData) {
        val updateFrames = IntArray(data.bindings.size)

        fun markBindingUpdate(binding: Int): Boolean {
            val frame = Time.frameCount
            val lastUpdate = updateFrames[binding]
            updateFrames[binding] = frame
            return lastUpdate != frame
        }
    }
}