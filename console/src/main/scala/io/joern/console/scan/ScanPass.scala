package io.joern.console.scan

import io.joern.console.Query
import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.generated.nodes.AstNode
import io.shiftleft.passes.{ConcurrentWriterCpgPass, DiffGraph, KeyPoolCreator, ParallelCpgPass}

class ScanPass(cpg: Cpg, queries: List[Query]) extends ConcurrentWriterCpgPass[Query](cpg) {

  override def generateParts(): Array[Query] = queries.toArray

  override def runOnPart(diffGraph: DiffGraphBuilder, query: Query): Unit = {
    query(cpg).foreach(diffGraph.addNode)
  }

}
