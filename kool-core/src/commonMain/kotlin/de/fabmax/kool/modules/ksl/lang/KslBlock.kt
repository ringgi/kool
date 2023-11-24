package de.fabmax.kool.modules.ksl.lang

import de.fabmax.kool.modules.ksl.generator.KslGenerator
import de.fabmax.kool.modules.ksl.model.KslMutatedState

abstract class KslBlock(blockName: String, parentScope: KslScopeBuilder) : KslStatement(blockName, parentScope) {

    private val inputDependencies = mutableMapOf<BlockInput<*, *>, Set<KslMutatedState>>()
    private val outputs = mutableListOf<KslValue<*>>()

    val body = KslScopeBuilder(this, parentScope, parentScope.parentStage).apply { scopeName = blockName }

    init {
        childScopes += body
    }

    private fun nextName(suffix: String): String = parentScopeBuilder.parentStage.program.nextName("${opName}_$suffix")

    protected fun inFloat1(name: String? = null, defaultValue: KslScalarExpression<KslFloat1>? = null): ScalarInput<KslFloat1> =
        ScalarInput(name ?: nextName("inF1"), KslFloat1, defaultValue).also {
            updateDependencies(it, defaultValue)
        }
    protected fun inFloat2(name: String? = null, defaultValue: KslVectorExpression<KslFloat2, KslFloat1>? = null): VectorInput<KslFloat2, KslFloat1> =
        VectorInput(name ?: nextName("inF2"), KslFloat2, defaultValue).also {
            updateDependencies(it, defaultValue)
        }
    protected fun inFloat3(name: String? = null, defaultValue: KslVectorExpression<KslFloat3, KslFloat1>? = null): VectorInput<KslFloat3, KslFloat1> =
        VectorInput(name ?: nextName("inF3"), KslFloat3, defaultValue).also {
            updateDependencies(it, defaultValue)
        }
    protected fun inFloat4(name: String? = null, defaultValue: KslVectorExpression<KslFloat4, KslFloat1>? = null): VectorInput<KslFloat4, KslFloat1> =
        VectorInput(name ?: nextName("inF4"), KslFloat4, defaultValue).also {
            updateDependencies(it, defaultValue)
        }

    protected fun inInt1(name: String? = null, defaultValue: KslScalarExpression<KslInt1>? = null): ScalarInput<KslInt1> =
        ScalarInput(name ?: nextName("inI1"), KslInt1, defaultValue).also {
            updateDependencies(it, defaultValue)
        }
    protected fun inInt2(name: String? = null, defaultValue: KslVectorExpression<KslInt2, KslInt1>? = null): VectorInput<KslInt2, KslInt1> =
        VectorInput(name ?: nextName("inI2"), KslInt2, defaultValue).also {
            updateDependencies(it, defaultValue)
        }
    protected fun inInt3(name: String? = null, defaultValue: KslVectorExpression<KslInt3, KslInt1>? = null): VectorInput<KslInt3, KslInt1> =
        VectorInput(name ?: nextName("inI3"), KslInt3, defaultValue).also {
            updateDependencies(it, defaultValue)
        }
    protected fun inInt4(name: String? = null, defaultValue: KslVectorExpression<KslInt4, KslInt1>? = null): VectorInput<KslInt4, KslInt1> =
        VectorInput(name ?: nextName("inI4"), KslInt4, defaultValue).also {
            updateDependencies(it, defaultValue)
        }

    protected fun inMat2(name: String? = null, defaultValue: KslMatrixExpression<KslMat2, KslFloat2>? = null): MatrixInput<KslMat2, KslFloat2> =
        MatrixInput(name ?: nextName("inM2"), KslMat2, defaultValue).also {
            updateDependencies(it, defaultValue)
        }
    protected fun inMat3(name: String? = null, defaultValue: KslMatrixExpression<KslMat3, KslFloat3>? = null): MatrixInput<KslMat3, KslFloat3> =
        MatrixInput(name ?: nextName("inM3"), KslMat3, defaultValue).also {
            updateDependencies(it, defaultValue)
        }
    protected fun inMat4(name: String? = null, defaultValue: KslMatrixExpression<KslMat4, KslFloat4>? = null): MatrixInput<KslMat4, KslFloat4> =
        MatrixInput(name ?: nextName("inM4"), KslMat4, defaultValue).also {
            updateDependencies(it, defaultValue)
        }

    protected fun inFloat1Array(arraySize: Int, name: String? = null, defaultValue: KslScalarArrayExpression<KslFloat1>? = null): ScalarArrayInput<KslFloat1> =
        ScalarArrayInput(name ?: nextName("inArrF1"), arraySize, KslFloat1).also {
            updateDependencies(it, defaultValue)
        }
    protected fun inFloat2Array(arraySize: Int, name: String? = null, defaultValue: KslVectorArrayExpression<KslFloat2, KslFloat1>? = null): VectorArrayInput<KslFloat2, KslFloat1> =
        VectorArrayInput(name ?: nextName("inArrF2"), arraySize, KslFloat2).also {
            updateDependencies(it, defaultValue)
        }
    protected fun inFloat3Array(arraySize: Int, name: String? = null, defaultValue: KslVectorArrayExpression<KslFloat3, KslFloat1>? = null): VectorArrayInput<KslFloat3, KslFloat1> =
        VectorArrayInput(name ?: nextName("inArrF3"), arraySize, KslFloat3).also {
            updateDependencies(it, defaultValue)
        }
    protected fun inFloat4Array(arraySize: Int, name: String? = null, defaultValue: KslVectorArrayExpression<KslFloat4, KslFloat1>? = null): VectorArrayInput<KslFloat4, KslFloat1> =
        VectorArrayInput(name ?: nextName("inArrF4"), arraySize, KslFloat4).also {
            updateDependencies(it, defaultValue)
        }
    protected fun inMat2Array(arraySize: Int, name: String? = null, defaultValue: KslMatrixArrayExpression<KslMat2, KslFloat2>? = null): MatrixArrayInput<KslMat2, KslFloat2> =
        MatrixArrayInput(name ?: nextName("inArrMat2"), arraySize, KslMat2).also {
            updateDependencies(it, defaultValue)
        }
    protected fun inMat3Array(arraySize: Int, name: String? = null, defaultValue: KslMatrixArrayExpression<KslMat3, KslFloat3>? = null): MatrixArrayInput<KslMat3, KslFloat3> =
        MatrixArrayInput(name ?: nextName("inArrMat3"), arraySize, KslMat3).also {
            updateDependencies(it, defaultValue)
        }
    protected fun inMat4Array(arraySize: Int, name: String? = null, defaultValue: KslMatrixArrayExpression<KslMat4, KslFloat4>? = null): MatrixArrayInput<KslMat4, KslFloat4> =
        MatrixArrayInput(name ?: nextName("inArrMat4"), arraySize, KslMat4).also {
            updateDependencies(it, defaultValue)
        }

    protected fun outFloat1(name: String? = null): KslVarScalar<KslFloat1> = parentScopeBuilder.float1Var(name = nextName(name ?: "outF1")).also { outputs += it }
    protected fun outFloat2(name: String? = null): KslVarVector<KslFloat2, KslFloat1> = parentScopeBuilder.float2Var(name = nextName(name ?: "outF2")).also { outputs += it }
    protected fun outFloat3(name: String? = null): KslVarVector<KslFloat3, KslFloat1> = parentScopeBuilder.float3Var(name = nextName(name ?: "outF3")).also { outputs += it }
    protected fun outFloat4(name: String? = null): KslVarVector<KslFloat4, KslFloat1> = parentScopeBuilder.float4Var(name = nextName(name ?: "outF4")).also { outputs += it }

    protected fun outInt1(name: String? = null): KslVarScalar<KslInt1> = parentScopeBuilder.int1Var(name = nextName(name ?: "outI1")).also { outputs += it }
    protected fun outInt2(name: String? = null): KslVarVector<KslInt2, KslInt1> = parentScopeBuilder.int2Var(name = nextName(name ?: "outI2")).also { outputs += it }
    protected fun outInt3(name: String? = null): KslVarVector<KslInt3, KslInt1> = parentScopeBuilder.int3Var(name = nextName(name ?: "outI3")).also { outputs += it }
    protected fun outInt4(name: String? = null): KslVarVector<KslInt4, KslInt1> = parentScopeBuilder.int4Var(name = nextName(name ?: "outI4")).also { outputs += it }

    protected fun outMat2(name: String? = null): KslVarMatrix<KslMat2, KslFloat2> = parentScopeBuilder.mat2Var(name = nextName(name ?: "outM2")).also { outputs += it }
    protected fun outMat3(name: String? = null): KslVarMatrix<KslMat3, KslFloat3> = parentScopeBuilder.mat3Var(name = nextName(name ?: "outM3")).also { outputs += it }
    protected fun outMat4(name: String? = null): KslVarMatrix<KslMat4, KslFloat4> = parentScopeBuilder.mat4Var(name = nextName(name ?: "outM4")).also { outputs += it }

    private fun updateDependencies(input: BlockInput<*, *>, newExpression: KslExpression<*>?) {
        // collect dependencies of new input expression
        inputDependencies[input] = newExpression?.collectStateDependencies() ?: emptySet()

        // update dependencies of block
        dependencies.clear()
        inputDependencies.values.forEach { deps ->
            deps.forEach {
                dependencies[it.state] = it
            }
        }
    }

    override fun validate() {
        super.validate()
        inputDependencies.keys.forEach {
            if (it.input == null) {
                throw IllegalStateException("Missing input value for input ${it.name} of block $opName")
            }
        }
    }

    abstract inner class BlockInput<T: KslType, E: KslExpression<T>>(
        val name: String,
        override val expressionType: T,
        defaultValue: E?
    ) : KslExpression<T> {

        var input: E? = defaultValue
            set(value) {
                updateDependencies(this, value)
                field = value
            }

        operator fun invoke(assignExpression: E) {
            input = assignExpression
        }

        // return empty dependencies here - actual dependencies to input expression are managed by outer block statement
        override fun collectStateDependencies(): Set<KslMutatedState> = emptySet()

        override fun generateExpression(generator: KslGenerator): String {
            return input?.generateExpression(generator)
                ?: throw IllegalStateException("Missing input value for input $name of block $opName")
        }

        override fun toPseudoCode(): String {
            return input?.toPseudoCode()
                ?: throw IllegalStateException("Missing input value for input $name of block $opName")
        }
    }

    inner class ScalarInput<S>(name: String, expressionType: S, defaultValue: KslScalarExpression<S>?) :
        BlockInput<S, KslScalarExpression<S>>(name, expressionType, defaultValue), KslScalarExpression<S>
            where S : KslType, S : KslScalar

    inner class VectorInput<V, S>(name: String, expressionType: V, defaultValue: KslVectorExpression<V, S>?) :
        BlockInput<V, KslVectorExpression<V, S>>(name, expressionType, defaultValue), KslVectorExpression<V, S>
            where V : KslType, V : KslVector<S>, S : KslType, S : KslScalar

    inner class MatrixInput<M, V>(name: String, expressionType: M, defaultValue: KslMatrixExpression<M, V>?) :
        BlockInput<M, KslMatrixExpression<M, V>>(name, expressionType, defaultValue), KslMatrixExpression<M, V>
            where M : KslType, M : KslMatrix<V>, V : KslType, V : KslVector<*>

    inner class ScalarArrayInput<S>(name: String, arraySize: Int, expressionType: S) :
        BlockInput<KslArrayType<S>, KslScalarArrayExpression<S>>(name, KslArrayType(expressionType, arraySize), null), KslScalarArrayExpression<S>
            where S : KslType, S : KslScalar

    inner class VectorArrayInput<V, S>(name: String, arraySize: Int, expressionType: V) :
        BlockInput<KslArrayType<V>, KslVectorArrayExpression<V, S>>(name, KslArrayType(expressionType, arraySize), null), KslVectorArrayExpression<V, S>
            where V : KslType, V : KslVector<S>, S : KslType, S : KslScalar

    inner class MatrixArrayInput<M, V>(name: String, arraySize: Int, expressionType: M) :
        BlockInput<KslArrayType<M>, KslMatrixArrayExpression<M, V>>(name, KslArrayType(expressionType, arraySize), null), KslMatrixArrayExpression<M, V>
            where M : KslType, M : KslMatrix<V>, V : KslType, V : KslVector<*>

}
