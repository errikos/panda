package org.clyze.deepdoop.datalog.element

import groovy.transform.Canonical
import groovy.transform.ToString

@Canonical
@ToString(includePackage = false)
class GroupElement implements IElement {

	IElement element
}
