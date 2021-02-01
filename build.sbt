name := "akka_streams_demo"

version := "0.1"


scalaVersion := "2.13.4"

addCompilerPlugin("io.tryp" % "splain" % "0.5.8" cross CrossVersion.patch)

val AkkaVersion = "2.6.10"
libraryDependencies += "com.typesafe.akka" %% "akka-stream" % AkkaVersion

resolvers += "jitpack" at "https://jitpack.io"
resolvers += Resolver.jcenterRepo
libraryDependencies += "com.github.synesso" %% "scala-stellar-sdk" % "0.17.0"
libraryDependencies ++= Seq("com.lightbend.akka" %% "akka-stream-alpakka-mongodb" % "2.0.2")
libraryDependencies += "org.typelevel" %% "cats-core" % "2.3.1"
val circeVersion = "0.12.3"

// for @JsonCodec
scalacOptions ++= Seq("-Ymacro-annotations")

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
  //,"io.circe" %% "circe-generic-extras"
  ).map(_ % circeVersion)
  
  libraryDependencies += "com.github.scopt" %% "scopt" % "4.0.0"
  