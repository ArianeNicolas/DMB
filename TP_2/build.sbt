
// The simplest possible sbt build file is just one line:

scalaVersion := "2.13.12"


name := "hello-world"
organization := "ch.epfl.scala"
version := "1.0"

libraryDependencies += "org.scalameta" %% "munit" % "1.0.0" % Test;
libraryDependencies += "org.apache.spark" %% "spark-core" % "3.3.1";
libraryDependencies += "org.apache.spark" %% "spark-graphx" % "3.3.1";
libraryDependencies += "org.apache.spark" %% "spark-sql" % "3.3.1"
