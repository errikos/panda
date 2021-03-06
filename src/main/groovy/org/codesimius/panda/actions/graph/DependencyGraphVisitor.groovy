package org.codesimius.panda.actions.graph

import groovy.transform.Canonical
import org.codesimius.panda.actions.DefaultVisitor
import org.codesimius.panda.datalog.IVisitable
import org.codesimius.panda.datalog.block.BlockLvl1
import org.codesimius.panda.datalog.block.BlockLvl2
import org.codesimius.panda.datalog.clause.RelDeclaration
import org.codesimius.panda.datalog.clause.Rule
import org.codesimius.panda.datalog.clause.TypeDeclaration
import org.codesimius.panda.datalog.element.AggregationElement
import org.codesimius.panda.datalog.element.NegationElement
import org.codesimius.panda.datalog.element.relation.Constructor
import org.codesimius.panda.datalog.element.relation.Relation
import org.codesimius.panda.system.Compiler
import org.codesimius.panda.system.DependencyGraphDOTGenerator
import org.codesimius.panda.system.Error

import static org.codesimius.panda.datalog.Annotation.CONSTRUCTOR

@Canonical
class DependencyGraphVisitor extends DefaultVisitor<IVisitable> {

	@Delegate
	Compiler compiler
	File outDir

	// Each subgraph represents a different template
	Map<String, Graph> graphs = [:].withDefault { new Graph(it) }

	private Graph currGraph
	private boolean inNegation
	private boolean inAggregation
	private Set<RelInfo> headRelations
	private Set<RelInfo> bodyRelations
	private BlockLvl1 currTemplate
	// Indirect edges are added in the end so we can first check if the target node exists
	private Map<Node, String> pendingIndirectEdges = [:]

	static final String GLOBAL = "_"
	static final String INSTANTIATION = null

	void enter(BlockLvl2 n) { currGraph = graphs[GLOBAL] }

	IVisitable exit(BlockLvl2 n) {
		n.templates.findAll { template -> !n.instantiations.any { it.id == template.name } }.each { template ->
			def baseNode = graphs[INSTANTIATION].touch(template.name, Node.Kind.TEMPLATE)
			def superNode = graphs[INSTANTIATION].touch(template.superTemplate, Node.Kind.TEMPLATE)
			baseNode.connectTo(superNode, Edge.Kind.INHERITANCE)
		}
		n.instantiations.each { inst ->
			def templateNode = graphs[INSTANTIATION].touch(inst.template, Node.Kind.TEMPLATE)
			def instanceNode = graphs[INSTANTIATION].touch(inst.id, Node.Kind.INSTANCE)
			instanceNode.connectTo(templateNode, Edge.Kind.INSTANCE)

			inst.parameters.each {
				def paramNode = graphs[INSTANTIATION].touch(it, Node.Kind.INSTANCE)
				instanceNode.connectTo(paramNode, Edge.Kind.ACTUAL_PARAM)
			}
		}
		pendingIndirectEdges.each { relNode, complexName ->
			def (String parameter, String name) = complexName.split("\\.")
			def graphName = parameter == "_" ? GLOBAL : parameter
			if (!graphs[graphName].nodes.values().any { it.title == name })
				error(Error.REL_QUAL_UNKNOWN, name)
			def paramNode = graphs[graphName].touch(name, Node.Kind.RELATION)
			relNode.connectTo(paramNode, Edge.Kind.INDIRECT_PARAM)
		}
		new CycleDetector(compiler, graphs).with {
			checkInstantiations()
			checkIndirectEdges()
			checkNegation()
			//topologicalSort()
		}

		codeGenerator.artifacts << DependencyGraphDOTGenerator.gen(outDir, this)

		return n
	}

	void enter(BlockLvl1 n) {
		currGraph = graphs[n.name]
		currTemplate = n
	}

	IVisitable exit(BlockLvl1 n) {
		currGraph = graphs[GLOBAL]
		currTemplate = null
	}

	void enter(RelDeclaration n) {
		currGraph.touch(n.relation.name, CONSTRUCTOR in n.annotations ? Node.Kind.CONSTRUCTOR : Node.Kind.RELATION)
	}

	void enter(TypeDeclaration n) {
		def typeNode = currGraph.touch(n.type.name, Node.Kind.TYPE)
		if (n.supertype) {
			def superTypeNode = currGraph.touch(n.supertype.name, Node.Kind.TYPE)
			typeNode.connectTo(superTypeNode, Edge.Kind.SUBTYPE)
		}
	}

	void enter(Rule n) {
		headRelations = [] as Set
		bodyRelations = [] as Set
	}

	IVisitable exit(Rule n) {
		headRelations.each { headRel ->
			def headRelNode = currGraph.touch(headRel.name, headRel.isConstructor ? Node.Kind.CONSTRUCTOR : Node.Kind.RELATION)
			bodyRelations.each { bodyRel ->
				def relNode = currGraph.touch(bodyRel.name, bodyRel.isConstructor ? Node.Kind.CONSTRUCTOR : Node.Kind.RELATION)
				def edge = bodyRel.isNegated ? Edge.Kind.NEGATION : (bodyRel.isAggregated ? Edge.Kind.AGGREGATION : Edge.Kind.RELATION)
				headRelNode.connectTo(relNode, edge)
			}
		}
		null
	}

	IVisitable visit(AggregationElement n) {
		inAggregation = true
		m[n.body] = visit n.body
		inAggregation = false
		null
	}

	IVisitable visit(NegationElement n) {
		inNegation = true
		m[n.element] = visit n.element
		inNegation = false
		null
	}

	void enter(Constructor n) {
		if (inRuleHead) headRelations << new RelInfo(n.name, true)
		else if (inRuleBody) bodyRelations << new RelInfo(n.name, true, inNegation, inAggregation)
	}

	void enter(Relation n) {
		if (inDecl) return

		if (n.name.contains("."))
			pendingIndirectEdges[currGraph.touch(n.name, Node.Kind.PARAMETER)] = n.name

		if (inRuleHead) headRelations << new RelInfo(n.name)
		else if (inRuleBody) bodyRelations << new RelInfo(n.name, false, inNegation, inAggregation)
	}

	@Canonical
	static class RelInfo {
		String name
		boolean isConstructor
		boolean isNegated
		boolean isAggregated
	}
}