package io.joern.ghidra2cpg

import ghidra.GhidraJarApplicationLayout
import ghidra.app.plugin.core.analysis.AutoAnalysisManager
import ghidra.app.util.importer.{AutoImporter, MessageLog}
import ghidra.framework.model.{Project, ProjectLocator}
import ghidra.framework.project.{DefaultProject, DefaultProjectManager}
import ghidra.framework.protocol.ghidra.{GhidraURLConnection, Handler}
import ghidra.framework.{Application, HeadlessGhidraApplicationConfiguration}
import ghidra.program.flatapi.FlatProgramAPI
import ghidra.program.model.listing.Program
import ghidra.program.util.GhidraProgramUtilities
import ghidra.util.exception.InvalidInputException
import ghidra.util.task.TaskMonitor
import io.joern.ghidra2cpg.passes._
import io.joern.ghidra2cpg.passes.arm.ArmFunctionPass
import io.joern.ghidra2cpg.passes.mips.{LoHiPass, MipsFunctionPass}
import io.joern.ghidra2cpg.passes.x86.{ReturnEdgesPass, X86FunctionPass}
import io.joern.x2cpg._
import io.joern.x2cpg.passes.frontend.{MetaDataPass, TypeNodePass}
import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.generated.Languages
import utilities.util.FileUtilities

import java.io.File
import scala.collection.mutable
import scala.jdk.CollectionConverters._

class Ghidra2Cpg() {

  /** Create a CPG representing the given input file. The CPG is stored at the given output file. The caller must close
    * the CPG.
    */
  def createCpg(inputFile: File, outputFile: Option[String]): Cpg = {

    if (!inputFile.isDirectory && !inputFile.isFile) {
      throw new InvalidInputException(s"$inputFile is not a valid directory or file.")
    }

    val cpg = X2Cpg.newEmptyCpg(outputFile)

    better.files.File.usingTemporaryDirectory("ghidra2cpg_tmp") { tempWorkingDir =>
      initGhidra()
      val locator = new ProjectLocator(tempWorkingDir.path.toAbsolutePath.toString, CommandLineConfig.projectName)
      var program: Program = null
      var project: Project = null

      try {
        val projectManager = new HeadlessGhidraProjectManager
        project = projectManager.createProject(locator, null, false)
        program = AutoImporter.importByUsingBestGuess(inputFile, null, this, new MessageLog, TaskMonitor.DUMMY)
        addProgramToCpg(program, inputFile.getAbsolutePath, cpg)
      } catch {
        case e: Exception =>
          e.printStackTrace()
      } finally {
        if (program != null) {

          AutoAnalysisManager.getAnalysisManager(program).dispose()
          program.release(this)
        }
        project.close()
        FileUtilities.deleteDir(locator.getProjectDir)
        locator.getMarkerFile.delete
      }
    }
    cpg
  }

  private def initGhidra(): Unit = {
    // We need this for the URL handler
    Handler.registerHandler()

    // Initialize application (if necessary and only once)
    if (!Application.isInitialized) {
      val configuration = new HeadlessGhidraApplicationConfiguration
      configuration.setInitializeLogging(false)
      Application.initializeApplication(new GhidraJarApplicationLayout, configuration)
    }
  }

  private def addProgramToCpg(program: Program, fileAbsolutePath: String, cpg: Cpg): Unit = {
    val autoAnalysisManager: AutoAnalysisManager = AutoAnalysisManager.getAnalysisManager(program)
    val transactionId: Int                       = program.startTransaction("Analysis")
    try {
      autoAnalysisManager.initializeOptions()
      autoAnalysisManager.reAnalyzeAll(null)
      autoAnalysisManager.startAnalysis(TaskMonitor.DUMMY)
      GhidraProgramUtilities.setAnalyzedFlag(program, true)
      handleProgram(program, fileAbsolutePath, cpg)
    } catch {
      case e: Exception =>
        e.printStackTrace()
    } finally {
      program.endTransaction(transactionId, true)
    }

  }

  def handleProgram(program: Program, fileAbsolutePath: String, cpg: Cpg): Unit = {

    val flatProgramAPI: FlatProgramAPI = new FlatProgramAPI(program)
    val decompiler                     = Decompiler(program).get

    // Functions
    val listing          = program.getListing
    val functionIterator = listing.getFunctions(true)
    val functions        = functionIterator.iterator.asScala.toList

    new MetaDataPass(cpg, Languages.GHIDRA).createAndApply()
    new NamespacePass(cpg, fileAbsolutePath).createAndApply()

    program.getLanguage.getLanguageDescription.getProcessor.toString match {
      case "MIPS" =>
        functions.foreach { function =>
          new MipsFunctionPass(program, fileAbsolutePath, function, cpg, decompiler)
            .createAndApply()
          new LoHiPass(cpg).createAndApply()
        }
      case "AARCH64" | "ARM" =>
        functions.foreach { function =>
          new ArmFunctionPass(program, fileAbsolutePath, function, cpg, decompiler)
            .createAndApply()
        }
      case _ =>
        new X86FunctionPass(program, fileAbsolutePath, functions, cpg, decompiler).createAndApply()
        new ReturnEdgesPass(cpg).createAndApply()

    }

    new TypeNodePass(Types.types.toList, cpg)
    new JumpPass(cpg).createAndApply()
    new LiteralPass(cpg, flatProgramAPI).createAndApply()
  }

  private class HeadlessProjectConnection(projectManager: HeadlessGhidraProjectManager, connection: GhidraURLConnection)
      extends DefaultProject(projectManager, connection) {}

  private class HeadlessGhidraProjectManager extends DefaultProjectManager {}
}

object Types {
  // Types will be added to the CPG as soon as everything
  // else is done
  val types: mutable.SortedSet[String] = mutable.SortedSet[String]()

  def registerType(typeName: String): String = {
    try {
      types += typeName
    } catch {
      case _: Exception => println(s" Error adding type: $typeName")
    }
    typeName
  }
}
