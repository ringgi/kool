package de.fabmax.kool.platform.vk

import de.fabmax.kool.scene.Mesh
import de.fabmax.kool.util.Float32BufferImpl
import de.fabmax.kool.util.MeshInstanceList
import de.fabmax.kool.util.Uint32BufferImpl
import org.lwjgl.util.vma.Vma.*
import org.lwjgl.vulkan.VK10.*

class IndexedMesh(val sys: VkSystem, val mesh: Mesh) : VkResource() {

    val numVertices = mesh.geometry.numVertices
    val numIndices = mesh.geometry.indices.position

    val vertexBuffer = createVertexBuffer()
    val indexBuffer = createIndexBuffer()

    var instanceBuffer: Buffer?
        private set

    init {
        addDependingResource(vertexBuffer)
        addDependingResource(indexBuffer)

        instanceBuffer = mesh.instances?.let { createInstanceBuffer(it) }
        instanceBuffer?.let { addDependingResource(it) }
    }

    fun updateInstanceBuffer() {
        mesh.instances?.let {
            val buf = instanceBuffer ?: createInstanceBuffer(it)
            instanceBuffer = buf

            buf.mappedFloats {
                it.dataF.flip()
                put((it.dataF as Float32BufferImpl).buffer)
            }
        }
    }

    private fun createVertexBuffer(): Buffer {
        memStack {
            val bufferSize = numVertices * mesh.geometry.strideBytesF.toLong()
            val stagingAllocUsage = VMA_MEMORY_USAGE_CPU_ONLY
            val stagingBuffer = Buffer(sys, bufferSize, VK_BUFFER_USAGE_TRANSFER_SRC_BIT, stagingAllocUsage)
            stagingBuffer.mappedFloats {
                mesh.geometry.dataF.flip()
                put((mesh.geometry.dataF as Float32BufferImpl).buffer)
            }

            val usage = VK_BUFFER_USAGE_TRANSFER_DST_BIT or VK_BUFFER_USAGE_VERTEX_BUFFER_BIT
            val allocUsage = VMA_MEMORY_USAGE_GPU_ONLY
            val buffer = Buffer(sys, bufferSize, usage, allocUsage)
            buffer.put(stagingBuffer)
            stagingBuffer.destroy()
            return buffer
        }
    }

    private fun createIndexBuffer(): Buffer {
        memStack {
            val bufferSize = numIndices * 4L
            val stagingAllocUsage = VMA_MEMORY_USAGE_CPU_ONLY
            val stagingBuffer = Buffer(sys, bufferSize, VK_BUFFER_USAGE_TRANSFER_SRC_BIT, stagingAllocUsage)
            stagingBuffer.mappedInts {
                mesh.geometry.indices.flip()
                put((mesh.geometry.indices as Uint32BufferImpl).buffer)
            }

            val usage = VK_BUFFER_USAGE_TRANSFER_DST_BIT or VK_BUFFER_USAGE_INDEX_BUFFER_BIT
            val allocUsage = VMA_MEMORY_USAGE_GPU_ONLY
            val buffer = Buffer(sys, bufferSize, usage, allocUsage)
            buffer.put(stagingBuffer)
            stagingBuffer.destroy()
            return buffer
        }
    }

    private fun createInstanceBuffer(instances: MeshInstanceList): Buffer {
        memStack {
            val bufferSize = instances.maxInstances * instances.strideBytesF.toLong()
            val stagingAllocUsage = VMA_MEMORY_USAGE_CPU_TO_GPU
            val instanceBuffer = Buffer(sys, bufferSize, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, stagingAllocUsage)
            instanceBuffer.mappedFloats {
                instances.dataF.flip()
                put((instances.dataF as Float32BufferImpl).buffer)
            }
            return instanceBuffer
        }
    }

    override fun freeResources() {
        //logD { "Destroyed IndexedMesh" }
    }
}