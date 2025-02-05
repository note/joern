package io.joern.kotlin2cpg.types

import io.joern.kotlin2cpg.types.ErrorLoggingMessageCollector
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.Ignore

@Ignore // uncomment as soon as the sourceDir path is correct
class KotlinScriptFilteringTests extends AnyFreeSpec with Matchers {
  "Running type inference operation on external project with lots of KotlinScript sources" - {
    "should return an empty binding context" in {
      val sourceDir   = "src/test/resources/external_projects/kotlin-dsl"
      val environment = CompilerAPI.makeEnvironment(Seq(sourceDir), Seq(), Seq(), new ErrorLoggingMessageCollector)
      environment.getSourceFiles should not be List()

      val nameGenerator = new DefaultTypeInfoProvider(environment)
      nameGenerator.bindingContext should not be null
      nameGenerator.hasEmptyBindingContext shouldBe true
    }

    "should not return an empty binding context" in {
      val sourceDir               = "src/test/resources/external_projects/kotlin-dsl"
      val dirsForSourcesToCompile = InferenceSourcesPicker.dirsForRoot(sourceDir)
      val environment =
        CompilerAPI.makeEnvironment(dirsForSourcesToCompile, Seq(), Seq(), new ErrorLoggingMessageCollector)
      environment.getSourceFiles should not be List()

      val nameGenerator = new DefaultTypeInfoProvider(environment)
      nameGenerator.bindingContext should not be null
      nameGenerator.hasEmptyBindingContext shouldBe false
    }
  }
}
