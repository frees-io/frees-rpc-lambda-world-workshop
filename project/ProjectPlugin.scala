import freestyle.rpc.idlgen.IdlGenPlugin.autoImport._
import org.scalafmt.sbt.ScalafmtPlugin.autoImport._
import sbt.Keys._
import sbt._

object ProjectPlugin extends AutoPlugin {

  override def trigger: PluginTrigger = allRequirements

  object autoImport {

    lazy val V = new {
      val fs2            = "0.10.5"
      val log4cats       = "0.1.1"
      val logbackClassic = "1.2.3"
      val freestyleRPC   = "0.14.1"
      val pureconfig     = "0.9.2"
    }
  }

  import autoImport._

  private lazy val logSettings: Seq[Def.Setting[_]] = Seq(
    libraryDependencies ++= Seq(
      "ch.qos.logback"    % "logback-classic" % V.logbackClassic,
      "io.chrisdavenport" %% "log4cats-core"  % V.log4cats,
      "io.chrisdavenport" %% "log4cats-slf4j" % V.log4cats
    ))

  lazy val configSettings: Seq[Def.Setting[_]] = Seq(
    libraryDependencies ++= Seq(
      "co.fs2"                %% "fs2-core"   % V.fs2,
      "com.github.pureconfig" %% "pureconfig" % V.pureconfig))

  lazy val commonsSettings: Seq[Def.Setting[_]] = logSettings ++ configSettings

  lazy val rpcProtocolSettings: Seq[Def.Setting[_]] = Seq(
    idlType := "avro",
    srcGenSerializationType := "AvroWithSchema",
    sourceGenerators in Compile += (srcGen in Compile).taskValue,
    libraryDependencies ++= Seq(
      "io.frees" %% "frees-rpc-client-core" % V.freestyleRPC
    )
  )

  lazy val rpcClientSettings: Seq[Def.Setting[_]] = logSettings ++ Seq(
    libraryDependencies ++= Seq(
      "io.frees" %% "frees-rpc-client-netty" % V.freestyleRPC,
      "io.frees" %% "frees-rpc-client-cache" % V.freestyleRPC
    )
  )

  lazy val rpcServerSettings: Seq[Def.Setting[_]] = logSettings ++ Seq(
    libraryDependencies ++= Seq("io.frees" %% "frees-rpc-server" % V.freestyleRPC))

  override def projectSettings: Seq[Def.Setting[_]] =
    Seq(
      scalaVersion := "2.12.6",
      scalacOptions := Seq(
        "-deprecation",
        "-encoding",
        "UTF-8",
        "-feature",
        "-language:existentials",
        "-language:higherKinds",
        "-language:implicitConversions",
        "-unchecked",
        "-Xlint",
        "-Yno-adapted-args",
        "-Ywarn-dead-code",
        "-Ywarn-numeric-widen",
        "-Ywarn-value-discard",
        "-Xfuture",
        "-Ywarn-unused-import"
      ),
      scalafmtCheck := true,
      scalafmtOnCompile := true,
      addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
    )
}
