val scala2Version = "2.13.10"

lazy val root = project
  .in(file("."))
  .settings(
    name := "TP_1_DMB",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala2Version,

    libraryDependencies += "org.scalameta" %% "munit" % "1.0.0" % Test,
    libraryDependencies += "org.apache.spark" %% "spark-core" % "3.3.1",
    libraryDependencies += "org.apache.spark" %% "spark-graphx" % "3.3.1",
    libraryDependencies += "org.apache.spark" %% "spark-sql" % "3.3.1"
  )
