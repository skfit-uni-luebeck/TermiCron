package de.uzl.itcr.termicron.output.ql4mdr

@DslMarker
annotation class Ql4MdrMarker

interface GraphQlElement {
    fun render(builder: StringBuilder, indent: String)
}

@Ql4MdrMarker
abstract class GraphQlNode(val name: String?, val delimiters: Delimiters) :
    GraphQlElement {
    val children = arrayListOf<GraphQlElement>()
    override fun render(builder: StringBuilder, indent: String) {
        builder.append(indent)
        val newIndent = when (delimiters) {
            Delimiters.NONE -> indent
            else -> "$indent    "
        }
        if (delimiters != Delimiters.NONE) {
            when (name) {
                null -> builder.append(delimiters.opening)
                else -> builder.append("$name ${delimiters.opening}")
            }
            builder.append("\n")
        }
        children.forEach { it.render(builder, newIndent) }
        builder.append("\n$indent${delimiters.closing}")
    }

    enum class Delimiters(val opening: String, val closing: String) {
        CURLY("{", "}"),
        PAREN("(", ")"),
        NONE("", "")
    }

    protected fun <T : GraphQlElement> initChild(tag: T, init: T.() -> Unit): T {
        tag.init()
        children.add(tag)
        return tag
    }
}

class Ql4MdrQuery : GraphQlNode(null, Delimiters.NONE) {
    fun query(init: Query.() -> Unit) = initChild(Query(), init)
    fun mutation(init: Mutation.() -> Unit) = initChild(Mutation(), init)

    override fun toString(): String {
        val builder = StringBuilder()
        render(builder, "")
        return builder.toString()
    }
}

class Query : GraphQlNode("query", Delimiters.CURLY) {
    fun queryEntity(name: String, init: ComplexQueryEntity.() -> Unit) = initChild(ComplexQueryEntity(name), init)
    /*fun simpleQueryEntity(value: String) = initChild(StringQueryEntity(value)) {}*/
}

class QueryEntityArguments : GraphQlNode(null, Delimiters.PAREN) {
    private val arguments = arrayListOf<Pair<String, String>>()
    operator fun Pair<String, String>.unaryPlus() {
        arguments.add(this)
    }

    override fun render(builder: StringBuilder, indent: String) {
        builder.append(
            arguments.joinToString(", ") { "${it.first}: \"${it.second}\"" }
        )
    }
}

class ComplexQueryEntity(name: String) : GraphQlNode(name, Delimiters.CURLY) {

    val queryAttributes = mutableListOf<StringQueryEntity>()

    var queryArguments: QueryEntityArguments? = null

    fun queryArguments(init: QueryEntityArguments.() -> Unit) {
        this.queryArguments = QueryEntityArguments()
        this.queryArguments!!.init()
    }

    operator fun String.unaryPlus() {
        queryAttributes.add(StringQueryEntity(this))
    }

    override fun render(builder: StringBuilder, indent: String) {
        builder.append("$indent$name")
        queryArguments?.let { arg ->
            builder.append("(")
            arg.render(builder, indent)
            builder.append(")")
        }
        builder.append(" {\n")
        queryAttributes.forEach { it.render(builder, "$indent    ") }
        children.forEach {
            //builder.append(nestedIndent)
            it.render(builder, "$indent    ")
            builder.append("\n")
        }
        builder.append("$indent}")
    }

    fun queryEntity(name: String, init: ComplexQueryEntity.() -> Unit) = initChild(ComplexQueryEntity(name), init)

}

class StringQueryEntity(value: String) : QueryEntity(value) {
    override fun render(builder: StringBuilder, indent: String) {
        builder.append("$indent$name\n")
    }
}

abstract class QueryEntity(name: String) : GraphQlNode(name, Delimiters.CURLY) {

}

class Mutation : GraphQlNode("mutation", Delimiters.CURLY) {
    fun mutationNode(name: String, expectedResults: List<String>, init: MutationNode.() -> Unit) =
        initChild(MutationNode(name, expectedResults), init)
}

abstract class ElementWithAttributes(name: String? = null, delimiters: Delimiters = Delimiters.CURLY) :
    GraphQlNode(name, delimiters) {
    val attributes = arrayListOf<Pair<String, String>>()
    operator fun Pair<String, String>.unaryPlus() {
        attributes.add(this)
    }

    override fun render(builder: StringBuilder, indent: String) {
        builder.append(attributes.joinToString("\n") {
            "$indent${it.first}: \"${it.second}\""
        })
    }
}

class MutationAttributes : ElementWithAttributes() {

    //val attributes = arrayListOf<Pair<String, String>>()

    val listAttributes: MutableMap<String, MutableList<MutationChild>> = mutableMapOf()

    override fun render(builder: StringBuilder, indent: String) {
        super.render(builder, indent)
        builder.append("\n")
        listAttributes.forEach { (n, attr) ->
            builder.append("$indent$n: [")
            attr.forEach {
                builder.append("\n$indent    {\n")
                it.render(builder, "$indent        ")
                builder.append("\n$indent    }")
            }
            builder.append("\n$indent]")
        }
    }

    fun listAttribute(name: String, init: MutationChild.() -> Unit) {
        val mutationChildList = listAttributes.getOrDefault(name, mutableListOf())
        val newChild = MutationChild()
        newChild.init()
        mutationChildList.add(newChild)
        listAttributes[name] = mutationChildList
    }

}

class MutationChild : ElementWithAttributes()

class MutationNode(name: String, private val expectedResults: List<String>) : GraphQlNode(name, Delimiters.PAREN) {
    fun attributes(init: MutationAttributes.() -> Unit) = initChild(MutationAttributes(), init)

    override fun render(builder: StringBuilder, indent: String) {
        super.render(builder, indent)
        builder.append(" {\n")
        builder.append(expectedResults.joinToString("\n") {
            "" +
                    "$indent    $it"
        })
        builder.append("\n$indent}")
    }
}

fun ql4mdr(init: Ql4MdrQuery.() -> Unit): Ql4MdrQuery {
    val ql4mdr = Ql4MdrQuery()
    ql4mdr.init()
    return ql4mdr
}
