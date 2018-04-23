package org.codesimius.panda.actions.tranform

import groovy.transform.Canonical
import org.codesimius.panda.actions.symbol.SymbolTable
import org.codesimius.panda.datalog.IVisitable
import org.codesimius.panda.datalog.block.BlockLvl0
import org.codesimius.panda.datalog.clause.RelDeclaration
import org.codesimius.panda.datalog.clause.TypeDeclaration
import org.codesimius.panda.datalog.element.ComparisonElement
import org.codesimius.panda.datalog.element.ConstructionElement
import org.codesimius.panda.datalog.element.relation.Constructor
import org.codesimius.panda.datalog.element.relation.Relation
import org.codesimius.panda.datalog.element.relation.Type
import org.codesimius.panda.datalog.expr.BinaryExpr
import org.codesimius.panda.datalog.expr.GroupExpr

import static org.codesimius.panda.datalog.Annotation.CONSTRUCTOR
import static org.codesimius.panda.datalog.Annotation.TYPE
import static org.codesimius.panda.datalog.element.relation.Type.TYPE_STRING

@Canonical
class TypesOptimizer extends DefaultTransformer {

	SymbolTable symbolTable

	Set<Type> typesToOptimize = [] as Set

	void enter(BlockLvl0 n) {
		n.typeDeclarations.each { decl ->
			if (decl.annotations.find { it == TYPE }.args["opt"]) {
				// assert to type higher
				typesToOptimize += ([decl.type] + symbolTable.subTypes[decl.type])
			}
		}
		typesToOptimize.each { t ->
			symbolTable.superTypesOrdered.remove t
			symbolTable.subTypes.remove t
			symbolTable.typeToRootType.remove t
		}
	}

	IVisitable exit(RelDeclaration n) {
		(CONSTRUCTOR in n.annotations && n.types.last() in typesToOptimize) ? null : super.exit(n)
	}

	IVisitable exit(TypeDeclaration n) {
		if (n.type in typesToOptimize) {
			extraRelDecls << new RelDeclaration(new Relation(n.type.name), [TYPE_STRING])
			return null
		} else
			return n
	}

	IVisitable exit(ComparisonElement n) { n }

	IVisitable exit(ConstructionElement n) {
		def baseT = symbolTable.constructorBaseType[n.constructor.name]
		(n.type in typesToOptimize && n.constructor.name == baseT.defaultConName) ? new Relation(n.type.name, [n.constructor.keyExprs.first()]) : n
	}

	IVisitable exit(Constructor n) { n }

	IVisitable exit(Relation n) { n }

	IVisitable exit(Type n) { n in typesToOptimize ? TYPE_STRING : n }

	IVisitable exit(BinaryExpr n) { n }

	IVisitable exit(GroupExpr n) { n }
}
