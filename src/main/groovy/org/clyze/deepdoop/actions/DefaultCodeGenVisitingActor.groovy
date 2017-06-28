package org.clyze.deepdoop.actions

import org.clyze.deepdoop.datalog.element.ComparisonElement
import org.clyze.deepdoop.datalog.element.GroupElement
import org.clyze.deepdoop.datalog.element.LogicalElement
import org.clyze.deepdoop.datalog.element.NegationElement
import org.clyze.deepdoop.datalog.element.relation.Primitive
import org.clyze.deepdoop.datalog.expr.BinaryExpr
import org.clyze.deepdoop.datalog.expr.ConstantExpr
import org.clyze.deepdoop.datalog.expr.GroupExpr
import org.clyze.deepdoop.datalog.expr.VariableExpr
import org.clyze.deepdoop.system.Result

import java.nio.file.Files
import java.nio.file.Paths

import static org.clyze.deepdoop.datalog.element.LogicalElement.LogicType.AND

class DefaultCodeGenVisitingActor extends PostOrderVisitor<String> implements IActor<String>, TDummyActor<String> {

	File outDir
	File currentFile

	InfoCollectionVisitingActor infoActor = new InfoCollectionVisitingActor()
	TypeInferenceVisitingActor inferenceActor = new TypeInferenceVisitingActor(infoActor)
	List<Result> results = []

	DefaultCodeGenVisitingActor(File outDir) {
		actor = this
		this.outDir = outDir
	}

	DefaultCodeGenVisitingActor(String outDir) { this(new File(outDir)) }

	String exit(ComparisonElement n, Map<IVisitable, String> m) { m[n.expr] }

	String exit(GroupElement n, Map<IVisitable, String> m) { "(${m[n.element]})" }

	String exit(LogicalElement n, Map<IVisitable, String> m) {
		n.elements.findAll { m[it] }.collect { m[it] }.join(n.type == AND ? ", " : "; ")
	}

	String exit(NegationElement n, Map<IVisitable, String> m) { "!${m[n.element]}" }

	String exit(Primitive n, Map<IVisitable, String> m) { "${n.name}(${m[n.var]})" }

	String exit(BinaryExpr n, Map<IVisitable, String> m) { "${m[n.left]} ${n.op} ${m[n.right]}" }

	String exit(ConstantExpr n, Map<IVisitable, String> m) { n.value as String }

	String exit(GroupExpr n, Map<IVisitable, String> m) { "(${m[n.expr]})" }

	String exit(VariableExpr n, Map<IVisitable, String> m) { n.name }

	protected def createUniqueFile(String prefix, String suffix) {
		Files.createTempFile(Paths.get(outDir.name), prefix, suffix).toFile()
	}

	protected void emit(String data) { write currentFile, data }

	protected void emit(List<String> data) { write currentFile, data }

	protected static void write(File file, String data) { file << data << "\n" }

	protected static void write(File file, List<String> data) { data.each { write file, it } }
}
