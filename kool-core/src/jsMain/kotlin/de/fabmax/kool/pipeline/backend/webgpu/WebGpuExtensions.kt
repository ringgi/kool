package de.fabmax.kool.pipeline.backend.webgpu

import de.fabmax.kool.pipeline.AddressMode
import de.fabmax.kool.pipeline.FilterMethod
import de.fabmax.kool.pipeline.TexFormat

fun GPUCommandEncoder.beginRenderPass(
    colorAttachments: Array<GPURenderPassColorAttachment>,
    depthStencilAttachment: GPURenderPassDepthStencilAttachment? = null,
    label: String = ""
) = beginRenderPass(GPURenderPassDescriptor(colorAttachments, depthStencilAttachment, label))

fun GPUDevice.createBindGroup(
    layout: GPUBindGroupLayout,
    entries: Array<GPUBindGroupEntry>,
    label: String = ""
) = createBindGroup(GPUBindGroupDescriptor(layout, entries, label))

fun GPUDevice.createSampler(
    label: String = "",
    addressModeU: GPUAddressMode = GPUAddressMode.clampToEdge,
    addressModeV: GPUAddressMode = GPUAddressMode.clampToEdge,
    addressModeW: GPUAddressMode = GPUAddressMode.clampToEdge,
    magFilter: GPUFilterMode = GPUFilterMode.nearest,
    minFilter: GPUFilterMode = GPUFilterMode.nearest,
    mipmapFilter: GPUMipmapFilterMode = GPUMipmapFilterMode.nearest,
    lodMinClamp: Float = 0f,
    lodMaxClamp: Float = 32f,
    maxAnisotropy: Int = 1
): GPUSampler = createSampler(GPUSamplerDescriptor(
    label = label,
    addressModeU = addressModeU,
    addressModeV = addressModeV,
    addressModeW = addressModeW,
    magFilter = magFilter,
    minFilter = minFilter,
    mipmapFilter = mipmapFilter,
    lodMinClamp = lodMinClamp,
    lodMaxClamp = lodMaxClamp,
    maxAnisotropy = maxAnisotropy
))

fun GPUDevice.createShaderModule(code: String) = createShaderModule(GPUShaderModuleDescriptor(code))

fun GPUTexture.createView(
    label: String = "",
    format: GPUTextureFormat? = null,
    baseMipLevel: Int = 0,
    mipLevelCount: Int? = null,
    baseArrayLayer: Int = 0,
    arrayLayerCount: Int? = null
) = createView(GPUTextureViewDescriptor(label, format, baseMipLevel, mipLevelCount, baseArrayLayer, arrayLayerCount))

val AddressMode.wgpu: GPUAddressMode
    get() = when (this) {
        AddressMode.CLAMP_TO_EDGE -> GPUAddressMode.clampToEdge
        AddressMode.MIRRORED_REPEAT -> GPUAddressMode.mirrorRepeat
        AddressMode.REPEAT -> GPUAddressMode.repeat
    }

val FilterMethod.wgpu: GPUFilterMode
    get() = when (this) {
        FilterMethod.NEAREST -> GPUFilterMode.nearest
        FilterMethod.LINEAR -> GPUFilterMode.linear
    }

val TexFormat.wgpuFormat: GPUTextureFormat
    get() = when (this) {
        TexFormat.R -> GPUTextureFormat.r8unorm
        TexFormat.RG -> GPUTextureFormat.rg8unorm
        TexFormat.RGB -> GPUTextureFormat.rgba8unorm
        TexFormat.RGBA -> GPUTextureFormat.rgba8unorm
        TexFormat.R_F16 -> GPUTextureFormat.r16float
        TexFormat.RG_F16 -> GPUTextureFormat.rg16float
        TexFormat.RGB_F16 -> GPUTextureFormat.rgba16float
        TexFormat.RGBA_F16 -> GPUTextureFormat.rgba16float
        TexFormat.R_F32 -> GPUTextureFormat.r32float
        TexFormat.RG_F32 -> GPUTextureFormat.rg32float
        TexFormat.RGB_F32 -> GPUTextureFormat.rgba32float
        TexFormat.RGBA_F32 -> GPUTextureFormat.rgba32float
        TexFormat.R_I32 -> GPUTextureFormat.r32sint
        TexFormat.RG_I32 -> GPUTextureFormat.rg32sint
        TexFormat.RGB_I32 -> GPUTextureFormat.rgba32sint
        TexFormat.RGBA_I32 -> GPUTextureFormat.rgba32sint
        TexFormat.R_U32 -> GPUTextureFormat.r32uint
        TexFormat.RG_U32 -> GPUTextureFormat.rg32uint
        TexFormat.RGB_U32 -> GPUTextureFormat.rgba32uint
        TexFormat.RGBA_U32 -> GPUTextureFormat.rgba32uint
    }