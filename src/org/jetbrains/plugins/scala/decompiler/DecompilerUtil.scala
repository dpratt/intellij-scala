package org.jetbrains.plugins.scala.decompiler

import _root_.scala.runtime.RichBoolean
import _root_.scala.tools.scalap.scalax.rules.scalasig.ByteCode
import _root_.scala.tools.scalap.scalax.rules.scalasig.SourceFileInfo
import _root_.scala.tools.scalap.scalax.rules.scalasig.SourceFileAttributeParser
import _root_.scala.tools.scalap.scalax.rules.scalasig.ScalaSigPrinter
import _root_.scala.tools.scalap.scalax.rules.scalasig.ScalaSigAttributeParsers
import _root_.scala.tools.scalap.scalax.rules.scalasig.ClassFileParser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.{Project, ProjectManager}
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.{VirtualFileWithId, CharsetToolkit, VirtualFile}
import com.intellij.testFramework.LightVirtualFile
import java.io.{PrintStream, IOException, ByteArrayOutputStream, FileNotFoundException}
import java.nio.ByteBuffer

/**
 * @author ilyas
 */

object DecompilerUtil {
  protected val LOG: Logger = Logger.getInstance("#org.jetbrains.plugins.groovy.lang.psi.impl.statements.arguments.GrArgumentListImpl");

  val DECOMPILER_VERSION = 46
  private val decompiledTextAttribute = new FileAttribute("_file_decompiled_text_", DECOMPILER_VERSION)
  private val isScalaCompiledAttribute = new FileAttribute("_is_scala_compiled_", DECOMPILER_VERSION)
  private val sourceFileAttribute = new FileAttribute("_scala_source_file_", DECOMPILER_VERSION)

  def isScalaFile(file: VirtualFile): Boolean = try {
    isScalaFile(file, file.contentsToByteArray)
  }
  catch {
    case e: IOException => false
  }

  private val SCALA_FILE = new Key[java.lang.Boolean]("Is Scala File Key")

  def isScalaFile(file: VirtualFile, bytes: => Array[Byte]): Boolean = {
    def inner: Boolean = {
      if (file.getFileType != StdFileTypes.CLASS) return false
      if (!file.isInstanceOf[VirtualFileWithId]) return false
      val read = isScalaCompiledAttribute.readAttribute(file)
      if (read != null) try {read.readBoolean} finally {read.close} else {
        val byteCode = ByteCode(bytes)
        val isScala = try {
          val classFile = ClassFileParser.parse(byteCode)
          classFile.attribute("ScalaSig") match {case Some(_) => true; case None => false}
        } catch {
          case e => false
        }
        val write = isScalaCompiledAttribute.writeAttribute(file)
        write.writeBoolean(isScala)
        write.close
        isScala
      }
    }
    val b = file.getUserData(SCALA_FILE)
    if (b != null) return java.lang.Boolean.TRUE == b
    val res = inner
    file.putUserData(SCALA_FILE, new java.lang.Boolean(res))
    res
  }

  def obtainProject: Project = {
    val manager = ProjectManager.getInstance
    if (ApplicationManager.getApplication.isUnitTestMode) {
      manager.asInstanceOf[ProjectManagerEx].getCurrentTestProject
    } else {
      val projects = manager.getOpenProjects();
      if (projects.length == 0) manager.getDefaultProject else projects(0)
    }
  }

  val SOURCE_FILE = "SourceFile"
  val SCALA_SIG = "ScalaSig"

  def decompile(bytes: Array[Byte], file: VirtualFile) = {
    val byteCode = ByteCode(bytes)
    val ba = decompiledTextAttribute.readAttributeBytes(file)
    val sf = sourceFileAttribute.readAttributeBytes(file)
    val (bts, sourceFile) = if (ba != null && sf != null) (ba, sf) else {
      val classFile = ClassFileParser.parse(byteCode)
      classFile.attribute(SCALA_SIG).map(_.byteCode).map(ScalaSigAttributeParsers.parse) match {
        case Some(scalaSig) => {
          val baos = new ByteArrayOutputStream
          val stream = new PrintStream(baos)
          val syms = scalaSig.topLevelClasses ::: scalaSig.topLevelObjects
          syms.first.parent match {
            case Some(p) if (p.name != "<empty>") => {
              stream.print("package ");
              stream.print(p.path);
              stream.print("\n")
            }
            case _ =>
          }
          // Print classes
          val printer = new ScalaSigPrinter(stream, true)
          for (c <- syms) {
            printer.printSymbol(c)
          }
          val bs = baos.toByteArray
          decompiledTextAttribute.writeAttributeBytes(file, bs, 0, bs.length)

          // Obtain source file name
          val Some(SourceFileInfo(index)) = classFile.attribute(SOURCE_FILE).map(_.byteCode).map(SourceFileAttributeParser.parse)
          val source: String = classFile.header.constants(index) match {
            case s: String => s
            case _ => ""
          }
          val sBytes = source.getBytes(CharsetToolkit.UTF8)
          sourceFileAttribute.writeAttributeBytes(file, sBytes, 0, sBytes.length)
          (bs, sBytes)
        }
      }
    }
    (new String(bts, CharsetToolkit.UTF8), new String(sourceFile, CharsetToolkit.UTF8))
  }
}