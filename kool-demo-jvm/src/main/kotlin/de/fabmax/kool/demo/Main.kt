package de.fabmax.kool.demo

import de.fabmax.kool.createContext
import de.fabmax.kool.util.serialization.MeshConverter
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.FileOutputStream

/**
 * @author fabmax
 */
fun main(args: Array<String>) {
    Demo(createContext(),"treeDemo")

//    val ctx = createContext()
//    //ctx.scenes += simpleShapesScene(ctx)
//    //ctx.scenes += modelScene(ctx)
//    ctx.scenes += uiDemoScene()
//    ctx.run()
}

fun convertMesh() {
    val meshes = MeshConverter.convertMeshes("player.fbx")
    FileOutputStream("player.kmf").use { out ->
        out.write(ProtoBuf.dump(meshes[0]))
    }
}