package bloop.config

import java.nio.charset.Charset
import java.nio.file.{Files, Path, Paths}

object Config {
  private final val emptyPath = Paths.get("")
  case class Java(options: Array[String])
  object Java { private[bloop] val empty = Java(Array()) }

  case class Jvm(home: Option[Path], options: Array[String])
  object Jvm { private[bloop] val empty = Jvm(None, Array()) }

  case class TestFramework(names: List[String])
  object TestFramework { private[bloop] val empty = TestFramework(Nil) }
  case class TestArgument(args: Array[String], framework: Option[TestFramework])
  object TestArgument { private[bloop] val empty = TestArgument(Array(), None) }
  case class TestOptions(excludes: List[String], arguments: List[TestArgument])
  object TestOptions { private[bloop] val empty = TestOptions(Nil, Nil) }

  case class Test(frameworks: Array[TestFramework], options: TestOptions)
  object Test { private[bloop] val empty = Test(Array(), TestOptions.empty) }

  case class ClasspathOptions(
      bootLibrary: Boolean,
      compiler: Boolean,
      extra: Boolean,
      autoBoot: Boolean,
      filterLibrary: Boolean
  )

  object ClasspathOptions {
    private[bloop] val empty: ClasspathOptions = ClasspathOptions(true, false, false, true, true)
  }

  sealed abstract class CompileOrder(val id: String)
  case object Mixed extends CompileOrder("mixed")
  case object JavaThenScala extends CompileOrder("java->scala")
  case object ScalaThenJava extends CompileOrder("scala->java")

  object CompileOrder {
    final val All: List[String] = List(Mixed.id, JavaThenScala.id, ScalaThenJava.id)
  }

  // TODO(jvican): Move the classpath options to this field before 1.0.0. Holding off of this breaking change for now.
  case class CompileOptions(
      order: CompileOrder
  )

  object CompileOptions {
    private[bloop] val empty: CompileOptions = CompileOptions(Mixed)
  }

  case class Scala(
      organization: String,
      name: String,
      version: String,
      options: Array[String],
      jars: Array[Path]
  )

  object Scala {
    private[bloop] val empty: Scala = Scala("", "", "", Array(), Array())
  }

  sealed abstract class Platform(val name: String)
  object Platform {
    private[bloop] val default: Platform = JVM

    case object JS extends Platform("js")
    case object JVM extends Platform("jvm")
    case object Native extends Platform("native")

    final val All: List[String] = List(JVM.name, JS.name, Native.name)
  }

  /**
   * Configures how to start and use the Scala Native toolchain, if needed.
   * For the description of these fields, see:
   * http://static.javadoc.io/org.scala-native/tools_2.10/0.3.7/index.html#scala.scalanative.build.Config
   */
  case class NativeConfig(
      toolchainClasspath: Array[Path],
      gc: String,
      clang: Path,
      clangPP: Path,
      linkingOptions: Array[String],
      compileOptions: Array[String],
      targetTriple: String,
      nativelib: Path,
      linkStubs: Boolean
  )

  object NativeConfig {
    // FORMAT: OFF
    private[bloop] val empty: NativeConfig =
      NativeConfig(Array.empty, "", emptyPath, emptyPath, Array.empty, Array.empty, "", emptyPath, false)
    // FORMAT: ON
  }

  case class JsConfig(toolchainClasspath: Array[Path])

  object JsConfig {
    private[bloop] val empty: JsConfig = JsConfig(Array.empty)
  }

  case class Checksum(
      digest: String,
      `type`: String
  )

  object Checksum {
    private[bloop] val empty: Checksum = Checksum("", "")
  }

  case class Artifact(
      name: String,
      `type`: String,
      extension: String,
      classifier: Option[String],
      checksum: Option[Checksum],
      path: Path
  )

  object Artifact {
    private[bloop] val empty: Artifact = Artifact("", "", "", None, None, emptyPath)
  }

  case class Module(
      organization: String,
      name: String,
      version: String,
      configurations: Option[String],
      direct: Boolean,
      artifacts: List[Artifact]
  )

  object Module {
    private[bloop] val empty: Module = Module("", "", "", None, true, Nil)
  }

  case class Resolution(
      modules: List[Module]
  )

  object Resolution {
    private[bloop] val empty: Resolution = Resolution(Nil)
  }

  case class Project(
      name: String,
      directory: Path,
      sources: Array[Path],
      dependencies: Array[String],
      classpath: Array[Path],
      classpathOptions: ClasspathOptions,
      compileOptions: CompileOptions,
      out: Path,
      analysisOut: Path,
      classesDir: Path,
      `scala`: Scala,
      jvm: Jvm,
      java: Java,
      test: Test,
      platform: Platform,
      nativeConfig: Option[NativeConfig],
      jsConfig: Option[JsConfig],
      resolution: Resolution
  )

  object Project {
    // FORMAT: OFF
    private[bloop] val empty: Project =
      Project("", emptyPath, Array(), Array(), Array(), ClasspathOptions.empty,
        CompileOptions.empty, emptyPath, emptyPath, emptyPath, Scala.empty, Jvm.empty, Java.empty,
        Test.empty, Platform.default, None, None, Resolution.empty)
    // FORMAT: ON

    def analysisFileName(projectName: String) = s"$projectName-analysis.bin"
  }

  case class File(version: String, project: Project)
  object File {
    final val LatestVersion = "1.0.0"

    private[bloop] val empty = File(LatestVersion, Project.empty)

    private[bloop] def dummyForTests: File = {
      val workingDirectory = Paths.get(System.getProperty("user.dir"))
      val sourceFile = Files.createTempFile("Foo", ".scala")
      sourceFile.toFile.deleteOnExit()

      // Just add one classpath with the scala library in it
      val scalaLibraryJar = Files.createTempFile("scala-library", ".jar")
      scalaLibraryJar.toFile.deleteOnExit()

      // This is like `target` in sbt.
      val outDir = Files.createTempFile("out", "test")
      outDir.toFile.deleteOnExit()

      val outAnalysisFile = outDir.resolve("out-analysis.bin")
      outAnalysisFile.toFile.deleteOnExit()

      val classesDir = Files.createTempFile("classes", "test")
      classesDir.toFile.deleteOnExit()

      val project = Project(
        "dummy-project",
        workingDirectory,
        Array(sourceFile),
        Array("dummy-2"),
        Array(scalaLibraryJar),
        ClasspathOptions.empty,
        CompileOptions.empty,
        classesDir,
        outDir,
        outAnalysisFile,
        Scala("org.scala-lang", "scala-compiler", "2.12.4", Array("-warn"), Array()),
        Jvm(Some(Paths.get("/usr/lib/jvm/java-8-jdk")), Array()),
        Java(Array("-version")),
        Test(Array(), TestOptions(Nil, Nil)),
        Platform.default,
        None,
        None,
        Resolution.empty
      )

      File(LatestVersion, project)
    }
  }
}