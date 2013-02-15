package org.jetbrains.plugins.scala
package lang.typeInference.generated

import lang.typeInference.TypeInferenceTestBase
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

/**
 * @author Alefas
 * @since 11.12.12
 */
class TypeInferenceScalazTest extends TypeInferenceTestBase {
  //This class was generated by build script, please don't change this
  override def folderPath: String = super.folderPath + "scalaz/"

  protected override def isIncludeScalazLibrary: Boolean = true

  def testSCL3819() {doTest()}

  def testSCL4033() {doTest()}

  def testSCL4352() {doTest()}

  def testSCL4468() {doTest()}

  def testSCL4912() {doTest()}
}
