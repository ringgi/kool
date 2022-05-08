package de.fabmax.kool.demo.physics.terrain

import de.fabmax.kool.math.*
import de.fabmax.kool.math.spatial.BoundingBox
import de.fabmax.kool.math.spatial.NearestTraverser
import de.fabmax.kool.math.spatial.OcTree
import de.fabmax.kool.math.spatial.Vec3fAdapter
import de.fabmax.kool.physics.Physics
import de.fabmax.kool.physics.RigidStatic
import de.fabmax.kool.physics.Shape
import de.fabmax.kool.physics.geometry.TriangleMesh
import de.fabmax.kool.physics.geometry.TriangleMeshGeometry
import de.fabmax.kool.pipeline.Attribute
import de.fabmax.kool.pipeline.Texture3d
import de.fabmax.kool.pipeline.ibl.EnvironmentMaps
import de.fabmax.kool.scene.Group
import de.fabmax.kool.scene.Mesh
import de.fabmax.kool.scene.MeshInstanceList
import de.fabmax.kool.scene.geometry.IndexedVertexList
import de.fabmax.kool.scene.geometry.MeshBuilder
import de.fabmax.kool.util.ShadowMap
import kotlin.math.sqrt

class Trees(val terrain: Terrain, nTrees: Int, val windDensity: Texture3d) {

    val windOffset = MutableVec3f()
    val windSpeed = MutableVec3f(10f, 0.5f, 6.7f)
    var windStrength = 1f
    var windScale = 100f

    val treeGroup = Group().apply {
        isFrustumChecked = false

        onUpdate += {
            windOffset.x += windSpeed.x * it.deltaT
            windOffset.y += windSpeed.y * it.deltaT
            windOffset.z += windSpeed.z * it.deltaT
        }
    }

    private val treeAreas = listOf(
        Vec3f(-32f, 0f, 64f) to 50f,
        Vec3f(-61f, 0f, 109f) to 50f,
        Vec3f(29f, 0f, 52f) to 50f,
        Vec3f(25f, 0f, -143f) to 25f
    )

    private val random = Random(17)
    private val treeTree = OcTree(Vec3fAdapter, bounds = BoundingBox(Vec3f(-200f), Vec3f(200f)))

    val trees = mutableListOf<Tree>()

    init {
        val treeGenerator = LowPolyTree(0x1deadb0b)

        // generate 20 different tree models
        for (i in 0 until 20) {
            val root = treeGenerator.generateNodes(Mat4f())

            val treeData = IndexedVertexList(Attribute.POSITIONS, Attribute.NORMALS, Attribute.COLORS, TreeShader.WIND_SENSITIVITY)
            val meshBuilder = MeshBuilder(treeData)
            treeGenerator.trunkMesh(root, random.randomF(0.25f, 0.75f), meshBuilder)
            treeGenerator.leafMesh(root, random.randomF(0.25f, 0.75f), meshBuilder)

            val collisionData = IndexedVertexList(Attribute.POSITIONS)
            val collisionBuilder = MeshBuilder(collisionData)
            treeGenerator.trunkMesh(root, 0f, collisionBuilder)

            treeData.splitVertices()
            treeData.generateNormals()
            trees += Tree(
                Mesh(treeData).apply {
                    isFrustumChecked = false
                    instances = MeshInstanceList(listOf(Attribute.INSTANCE_MODEL_MAT))
                },
                collisionData
            )
        }
        trees.forEach { treeGroup += it.drawMesh }

        // randomly distribute tree instances
        for (i in 0 until nTrees) {
            val (pos, likelihood) = pickTreePosition()
            if (likelihood > 0f) {
                treeTree.add(pos)
                val tree = trees[random.randomI(trees.indices)]
                val mesh = tree.drawMesh
                val size = 0.6f + likelihood.clamp(0f, 0.4f)
                val pose = Mat4f().translate(pos).rotate(random.randomF(0f, 360f), Vec3f.Y_AXIS)
                tree.instances += TreeInstance(Mat4f().set(pose), size, tree)
                mesh.instances!!.addInstance {
                    put(pose.scale(size).matrix)
                }
            }
        }
    }

    private fun pickTreePosition(): Pair<Vec3f, Float> {
        val trav = NearestTraverser<Vec3f>()
        val position = MutableVec3f()
        var tries = 0
        while (tries < 500) {
            tries++

            position.x = random.randomF(-200f, 200f)
            position.z = random.randomF(-200f, 200f)
            position.y = 0f
            val areaDistance = treeAreas.minOf { it.first.distance(position) / it.second }
            if (areaDistance > 1f) {
                // position is not in tree area
                continue
            }

            val minGreen = minOf(
                terrain.getSplatWeightsAt(position.x, position.z).y,
                terrain.getSplatWeightsAt(position.x - 3f, position.z - 3f).y,
                terrain.getSplatWeightsAt(position.x - 3f, position.z + 3f).y,
                terrain.getSplatWeightsAt(position.x + 3f, position.z - 3f).y,
                terrain.getSplatWeightsAt(position.x + 3f, position.z + 3f).y
            )
            if (minGreen < 0.9f) {
                // trees only grow on green stuff
                continue
            }

            position.y = terrain.getTerrainHeightAt(position.x, position.z) - 0.25f
            trav.setup(position, 10f).traverse(treeTree)
            val nearestTreeDistance = sqrt(trav.sqrDist).clamp(0f, 7f)
            if (nearestTreeDistance < 2f) {
                // too close to next tree
                continue
            }

            val growLikelihood = nearestTreeDistance / 7f * (1f - areaDistance)
            if (random.randomF() < growLikelihood) {
                return position to growLikelihood
            }
        }
        return Vec3f.ZERO to 0f
    }

    fun setupTreeShaders(ibl: EnvironmentMaps, shadowMap: ShadowMap) {
        val treeShader = TreeShader(ibl, shadowMap, windDensity)
        val shadowShader = TreeShader.Shadow(windDensity)

        treeGroup.onUpdate += {
            treeShader.windOffset = windOffset
            treeShader.windStrength = windStrength
            treeShader.windScale = 1f / windScale

            shadowShader.windOffset = windOffset
            shadowShader.windStrength = windStrength
            shadowShader.windScale = 1f / windScale
        }

        treeGroup.children.filterIsInstance<Mesh>().forEach {
            it.shader = treeShader
            it.depthShader = shadowShader
        }
    }

    class Tree(val drawMesh: Mesh, collisionMesh: IndexedVertexList) {
        val instances = mutableListOf<TreeInstance>()
        val physicsMesh = TriangleMesh(collisionMesh)
    }

    class TreeInstance(val pose: Mat4f, val scale: Float, tree: Tree) {
        val physicsGeometry = TriangleMeshGeometry(tree.physicsMesh, Vec3f(scale))
        val physicsBody: RigidStatic = RigidStatic().apply {
            attachShape(Shape(physicsGeometry, Physics.defaultMaterial))
            position = pose.transform(MutableVec3f())
            rotation = pose.getRotation(MutableVec4f())
        }
    }
}