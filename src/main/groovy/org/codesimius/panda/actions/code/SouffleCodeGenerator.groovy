package org.codesimius.panda.actions.code

import groovy.transform.InheritConstructors
import org.codesimius.panda.actions.tranform.souffle.AssignTransformer
import org.codesimius.panda.actions.tranform.souffle.ConstructorTransformer
import org.codesimius.panda.datalog.block.BlockLvl0
import org.codesimius.panda.datalog.block.BlockLvl2
import org.codesimius.panda.datalog.clause.RelDeclaration
import org.codesimius.panda.datalog.clause.Rule
import org.codesimius.panda.datalog.clause.TypeDeclaration
import org.codesimius.panda.datalog.element.AggregationElement
import org.codesimius.panda.datalog.element.ConstructionElement
import org.codesimius.panda.datalog.element.LogicalElement
import org.codesimius.panda.datalog.element.relation.Constructor
import org.codesimius.panda.datalog.element.relation.RecordType
import org.codesimius.panda.datalog.element.relation.Relation
import org.codesimius.panda.datalog.element.relation.Type
import org.codesimius.panda.datalog.expr.RecordExpr

import static org.codesimius.panda.datalog.Annotation.*
import static org.codesimius.panda.datalog.expr.VariableExpr.gen1 as var1

@InheritConstructors
class SouffleCodeGenerator extends DefaultCodeGenerator {

	private BlockLvl0 currDatalog
	private Rule currRule

	String visit(BlockLvl2 p) {
		createUniqueFile("out_", ".dl")

		def steps = transformations + [
				new ConstructorTransformer(compiler, typeInferenceTransformer),
				new AssignTransformer(compiler)
		]

		def n = steps.inject(p) { prog, step -> prog.accept(step) }
		super.visit(n)
	}

	void enter(BlockLvl0 n) { currDatalog = n }

	String visit(RelDeclaration n) {
		def relName = fix(n.relation.name)
		def params = n.types.withIndex().collect { t, int i -> "${var1(i)}:${tr(fix(t.name))}" }
		def meta = n.annotations[METADATA]?.args?.get("types")
		emit ".decl $relName(${params.join(", ")})${meta ? " // $meta" : ""}"

		if (INPUT in n.annotations) {
			def an = n.annotations[INPUT]
			def filename = an["filename"] ?: "${n.relation.name}.facts"
			def delimiter = an["delimiter"] ?: "\\t"
			emit """.input $relName(filename="$filename", delimiter="$delimiter")"""
		}
		if (OUTPUT in n.annotations)
			emit ".output $relName"
		null
	}

	String visit(TypeDeclaration n) {
		def params = (n.supertype as RecordType).innerTypes.withIndex().collect { t, int i -> "${var1(i)}:${tr(fix(t.name))}" }
		emit ".type ${tr(fix(n.type.name))} = [${params.join(", ")}]"
		null
	}

	void enter(Rule n) { currRule = n }

	String exit(Rule n) {
		def head = m[n.head]
		def body = m[n.body]
		if (!body && (n.head instanceof LogicalElement || n.head instanceof ConstructionElement))
			emit "$head :- 1=1."
		else if (!body)
			emit "$head."
		else
			emit "$head :- $body."

		if (PLAN in n.annotations) emit ".plan ${n.annotations[PLAN]["plan"].value}"
		null
	}

	String exit(AggregationElement n) {
		def pred = n.relation.name
		def soufflePred = n.relation.exprs ? "$pred(${m[n.relation.exprs.first()]})" : pred
		def headVars = currDatalog.getHeadVars(currRule)
		def extraBody = currDatalog.getBoundBodyVars(currRule).any { it in headVars } ? "${m[n.body]}, " : ""
		"${extraBody}${m[n.var]} = $soufflePred : { ${m[n.body]} }"
	}

	String exit(Constructor n) { exit(n as Relation) }

	String exit(Relation n) { "${fix(n.name)}(${n.exprs.collect { m[it] }.join(", ")})" }

	String exit(Type n) { fix n.name }

	// Must override since the default implementation throws an exception
	String visit(RecordExpr n) {
		n.exprs.each { m[it] = visit it }
		"[${n.exprs.collect { visit(it) }.join(", ")}]"
	}

	static def fix(def s) { s.replace ":", "_" }

	static def tr(def name) {
		if (name == "string") return "symbol"
		else if (name == "int") return "number"
		else return "__SYS_TYPE_$name"
	}
}
