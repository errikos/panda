package org.codesimius.panda.datalog.element.relation

import groovy.transform.Canonical

@Canonical
class RecordType extends Type {

	List<Type> innerTypes

	String toString() { "[T]##$innerTypes" }
}
