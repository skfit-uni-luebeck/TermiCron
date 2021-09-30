package de.uzl.itcr.termicron.output.ql4mdr

/**
 * annotation class to signify to Kotlin compiler that this class is a DSL root
 * https://kotlinlang.org/docs/type-safe-builders.html#scope-control-dslmarker
 */
@DslMarker
annotation class Ql4MdrMarker

/**
 * the indent should be stepped by n spaces every time it's increased
 */
val indentStep = " ".repeat(2)

/**
 * an element within the GraphQL DSL that can be rendered to String, using a StringBuilder
 */
interface GraphQlElement {
    /**
     * render this element to the StringBuilder, with a indent greater-equal-to the indent provided
     *
     * @param builder the StringBuilder to render for
     * @param indent the base indent to render at
     */
    fun render(builder: StringBuilder, indent: String)
}

/**
 * an abstract node within the GraphQL DSL.
 *
 * @property name the name to render
 * @property delimiters if delimiters are to be appended after the name (e.g. "query { }"), or Delimiters.NONE
 */
@Ql4MdrMarker
abstract class GraphQlNode(val name: String?, private val delimiters: Delimiters) :
    GraphQlElement {
    /**
     * the children these node has, that have to rendered under this node
     */
    val children = arrayListOf<GraphQlElement>()

    /**
     * render this element: indent first, then the name (if present), then the delimiters
     * then all the children, with larger indent, then the closing delimiter if needed
     *
     * @param builder the StringBuilder to write to
     * @param indent the base indent
     */
    override fun render(builder: StringBuilder, indent: String) {
        builder.append(indent)
        val newIndent = when (delimiters) {
            Delimiters.NONE -> indent
            else -> "$indent$indentStep"
        }
        if (delimiters != Delimiters.NONE) {
            when (name) {
                null -> builder.append(delimiters.opening)
                else -> builder.append("$name ${delimiters.opening}")
            }
            builder.append("\n")
        } else {
            builder.append(name ?: "")
        }
        children.forEach { it.render(builder, newIndent) }
        builder.append("\n$indent${delimiters.closing}")
    }

    /**
     * enum class for Delimiters: round and curly bracket, and none. Other are currently not needed
     * for the subset of GraphQL/QL4MDR implemented here.
     *
     * @property opening the opening string
     * @property closing the closing string
     */
    enum class Delimiters(val opening: String, val closing: String) {
        CURLY("{", "}"),
        PAREN("(", ")"),
        NONE("", "")
    }

    /**
     * init a child of type T: call the init function on the new node, add it to the children, and return it
     *
     * @param T the GraphQlElement subclass to initialize
     * @param child the child to initialize
     * @param init the initialization block
     * @return the initialized child as T, it's added to the children list.
     */
    protected fun <T : GraphQlElement> initChild(child: T, init: T.() -> Unit): T {
        child.init()
        children.add(child)
        return child
    }
}

/**
 * an abstract GraphQl entry point, without name and delimiters.
 * This serves to provide functions query {} and mutation {} to initialize those
 */
class Ql4MdrQuery : GraphQlNode(null, Delimiters.NONE) {
    /**
     * add a query {} block to the entire query
     *
     * @param init the initialization function
     */
    fun query(init: Query.() -> Unit) = initChild(Query(), init)

    /**
     * add a mutation {} block to the entire query
     *
     * @param init the initialization function
     */
    fun mutation(init: Mutation.() -> Unit) = initChild(Mutation(), init)

    /**
     * toString renders the entire GraphQL/QL4MDR tree to a "pretty" string
     *
     * @return the rendered GraphQL
     */
    override fun toString(): String {
        val builder = StringBuilder()
        render(builder, "")
        return builder.toString()
    }
}

/**
 * a query node with curly brackets.
 * A query has entities, each with attributes to retrieve (in curly brackets) and arguments (in parens)
 */
class Query : GraphQlNode("query", Delimiters.CURLY) {
    /**
     * add a name () {} block to the query
     *
     * @param init the initialization function
     */
    fun queryEntity(name: String, init: ComplexQueryEntity.() -> Unit) = initChild(ComplexQueryEntity(name), init)
}

/**
 * a complex query entity with attributes and arguments
 *
 * @constructor
 * a GraphQlNode with ultimately curly brackets
 *
 * @param name the name of the query entity (e.g. "conceptSystem")
 */
class ComplexQueryEntity(name: String) : GraphQlNode(name, Delimiters.CURLY) {

    /**
     * the attributes of this query
     */
    private val queryAttributes = arrayListOf<StringQueryEntity>()

    /**
     * the arguments of this query, can be null
     */
    private var queryArguments: QueryEntityArguments? = null

    /**
     * add arguments to this query node, e.g. for "uri" and "version"
     *
     * @param init the initialization function
     */
    fun queryArguments(init: QueryEntityArguments.() -> Unit) {
        this.queryArguments = QueryEntityArguments()
        this.queryArguments!!.init()
    }

    /**
     * the syntax +"foo" adds a new attribute "foo" to query for this entity
     */
    operator fun String.unaryPlus() {
        queryAttributes.add(StringQueryEntity(this))
    }

    /**
     * render the query: indent + name first, then the arguments in round parens,
     * then the attributes in curly brackets, each on a new line
     *
     * @param builder the StringBuilder to use
     * @param indent the base indent to render at
     */
    override fun render(builder: StringBuilder, indent: String) {
        builder.append("$indent$name")
        queryArguments?.let { arg ->
            builder.append("(")
            arg.render(builder, indent)
            builder.append(")")
        }
        builder.append(" {\n")
        queryAttributes.forEach { it.render(builder, "$indent$indentStep") }
        children.forEach {
            it.render(builder, "$indent$indentStep")
            builder.append("\n")
        }
        builder.append("$indent}")
    }

    /**
     * add a nested query entity to this one
     *
     * @param name the name of the query entity
     * @param init the initialization of this entity
     */
    fun queryEntity(name: String, init: ComplexQueryEntity.() -> Unit) = initChild(ComplexQueryEntity(name), init)

}

/**
 * a simple query entity, like "url" under "conceptSystem (...) { }"
 *
 * @constructor
 * implements a QueryEntity
 *
 * @param value the value to render as
 */
class StringQueryEntity(value: String) : QueryEntity(value) {
    /**
     * simply render the name of this entity
     *
     * @param builder the string builder to render to
     * @param indent the base indent
     */
    override fun render(builder: StringBuilder, indent: String) {
        builder.append("$indent$name\n")
    }
}

/**
 * an abstract entity that should be retrieved over GraphQL
 *
 * @constructor
 * a GraphQLNode with curly delimiters
 *
 * @param name the name of the entity
 */
abstract class QueryEntity(name: String) : GraphQlNode(name, Delimiters.CURLY)

/**
 * arguments are provided in round brackets after the name of the QueryEntity
 */
class QueryEntityArguments : GraphQlNode(null, Delimiters.PAREN) {
    /**
     * the list of arguments, as name-value pairs
     */
    private val arguments = arrayListOf<Pair<String, String>>()

    private val returnParams = arrayListOf<String>()

    /**
     * pairs of name and value are provided using the syntax:
     * +("name" to "value")
     * e.g. +("url" to "http://...")
     * mind the parenthesis!
     */
    operator fun Pair<String, String>.unaryPlus() {
        arguments.add(this)
    }

    operator fun String.unaryPlus() {
        returnParams.add(this)
    }

    /**
     * render the arguments as a comma-separated list
     *
     * @param builder the StringBuilder to render to
     * @param indent the base indents
     */
    override fun render(builder: StringBuilder, indent: String) {
        builder.append(
            arguments.joinToString(", ") { "${it.first}: \"${it.second}\"" }
        )
        when {
            returnParams.isNotEmpty() -> {
                if (arguments.isNotEmpty()) builder.append(",")
            }
        }
    }
}

/**
 * execute mutations via GraphQL
 */
class Mutation : GraphQlNode("mutation", Delimiters.CURLY) {
    /**
     * every mutation needs MutationNodes
     *
     * @param name the name of the mutation
     * @param expectedResults the expected results that should be returned for the mutation, e.g. "url".
     * This is written after the attributes in round brackets
     * @param init the inititialization function
     */
    fun mutationNode(name: String, expectedResults: List<String>, init: MutationNode.() -> Unit) =
        initChild(MutationNode(name, expectedResults), init)
}

/**
 * an abstract element with attributes
 *
 * @constructor
 * a GraphQl Node with specified name and delimiters
 *
 * @param name the name of the element
 * @param delimiters the delimiters of the element
 */
abstract class ElementWithAttributes(name: String? = null, delimiters: Delimiters = Delimiters.CURLY) :
    GraphQlNode(name, delimiters) {
    /**
     * the attributes of this node, as name-value pairs
     */
    private val attributes = arrayListOf<Pair<String, String>>()

    /**
     * add attributes with the syntax +("foo" to "bar")
     */
    operator fun Pair<String, String>.unaryPlus() = attributes.add(this)

    /**
     * render the attributes of this node as a \n-seperated string
     *
     * @param builder the StringBuilder to render to
     * @param indent the base indent
     */
    override fun render(builder: StringBuilder, indent: String) {
        builder.append(attributes.joinToString("\n") {
            "$indent${it.first}: \"${it.second}\""
        })
    }
}

/**
 * attributes of the mutation (given in round brackets after the name of the mutation to call)
 */
class MutationAttributes : ElementWithAttributes() {

    /**
     * attributes that are a list, keyed by the name of the attribute (e.g. "concepts")
     */
    private val listAttributes: MutableMap<String, ArrayList<MutationChild>> = mutableMapOf()

    /**
     * render the attributes. First, the normal render procedure for this element,
     * i.e. the simple argument pairs
     * Then, on a new line, render all the list attributes at a higher indent
     *
     * @param builder the StringBuilder to write to
     * @param indent the base indent to use
     */
    override fun render(builder: StringBuilder, indent: String) {
        super.render(builder, indent)
        builder.append("\n")
        listAttributes.forEach { (listName, listAttribute) ->
            builder.append("$indent$listName: [")
            listAttribute.forEach {
                builder.append("\n$indent$indentStep{\n")
                it.render(builder, "$indent${indentStep.repeat(2)}")
                builder.append("\n$indent$indentStep}")
            }
            builder.append("\n$indent]")
        }
    }

    /**
     * add a new list attribute with the respective name
     *
     * @param name the name of the list attribute, e.g. "concepts"
     * @param init the initialization function
     */
    fun listAttribute(name: String, init: MutationChild.() -> Unit) {
        val mutationChildList = listAttributes.getOrDefault(name, arrayListOf())
        val newChild = MutationChild()
        newChild.init()
        mutationChildList.add(newChild)
        listAttributes[name] = mutationChildList
    }
}

/**
 * MutationChild is a very simple ElementWithAttributes, and used within the listAttribute
 */
class MutationChild : ElementWithAttributes()

/**
 * MutationNode is a mutation with the specified name.
 * It has attributes, list attributes, and expected/returned attributes
 *
 * @property expectedResults the list of values to retrieve from the mutated element
 * @constructor
 * implements a GraphQL node with round parenthesis
 *
 * @param name the name of the node
 */
class MutationNode(name: String, private val expectedResults: List<String>) : GraphQlNode(name, Delimiters.PAREN) {
    /**
     * add an attribute to this node
     *
     * @param init the initialization function for this attribute
     */
    fun attributes(init: MutationAttributes.() -> Unit) = initChild(MutationAttributes(), init)

    /**
     * render this node: first, the normal name ( attributes ).
     * Then, append { and the expected results, each on a new line, then }
     *
     * @param builder
     * @param indent
     */
    override fun render(builder: StringBuilder, indent: String) {
        super.render(builder, indent)
        builder.append(" {\n")
        builder.append(expectedResults.joinToString("\n") {
            "" +
                    "$indent$indentStep$it"
        })
        builder.append("\n$indent}")
    }
}

/**
 * entry point for QL4MDR DSL
 *
 * @param init the initialization function with which to build the tree
 * @return the complete query. Use toString()!
 */
fun ql4mdr(init: Ql4MdrQuery.() -> Unit): Ql4MdrQuery {
    val ql4mdr = Ql4MdrQuery()
    ql4mdr.init()
    return ql4mdr
}
