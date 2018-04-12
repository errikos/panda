package org.codesimius.panda.actions.graph

import groovy.transform.Canonical
import groovy.transform.ToString

@Canonical
@ToString(includePackage = false)
class Node {

	enum Kind {
		TEMPLATE, INSTANCE, PARAMETER, RELATION, CONSTRUCTOR
	}

	String id
	String name
	Kind kind
	Set<Edge> outEdges = [] as Set
}