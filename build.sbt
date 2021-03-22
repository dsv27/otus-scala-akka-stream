name := "akka_streams_demo"

version := "0.1"


scalaVersion := "2.13.4"

addCompilerPlugin("io.tryp" % "splain" % "0.5.8" cross CrossVersion.patch)

val AkkaVersion = "2.6.10"
libraryDependencies += "com.typesafe.akka" %% "akka-stream" % AkkaVersion

resolvers += "jitpack" at "https://jitpack.io"
resolvers += Resolver.jcenterRepo
libraryDependencies += "com.github.synesso" %% "scala-stellar-sdk" % "0.19.2"
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
  libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2"
  libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3"
  libraryDependencies += "org.mongodb.scala" %% "mongo-scala-driver" % "2.9.0"
  //libraryDependencies += "org.mongodb.scala" % "mongo-scala-bson_2.13" % "4.2.2"
  //libraryDependencies += "org.mongodb" % "mongodb-driver-reactivestreams" % "1.12.0"
  