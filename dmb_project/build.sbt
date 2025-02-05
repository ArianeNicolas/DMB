
// The simplest possible sbt build file is just one line:

scalaVersion := "2.13.12"
// That is, to create a valid sbt build, all you've got to do is define the
// version of Scala you'd like your project to use.

// ============================================================================

// Lines like the above defining `scalaVersion` are called "settings". Settings
// are key/value pairs. In the case of `scalaVersion`, the key is "scalaVersion"
// and the value is "2.13.12"

// It's possible to define many kinds of settings, such as:

name := "dmb_project"
organization := "ch.epfl.scala"
version := "1.0"

// Note, it's not required for you to define these three settings. These are
// mostly only necessary if you intend to publish your library's binaries on a
// place like Sonatype.


// Want to use a published library in your project?
// You can define other libraries as dependencies in your build like this:

libraryDependencies += "org.scalameta" %% "munit" % "1.0.0" % Test;
libraryDependencies += "org.apache.spark" %% "spark-core" % "3.3.1";
libraryDependencies += "org.apache.spark" %% "spark-graphx" % "3.3.1";
libraryDependencies += "org.apache.spark" %% "spark-sql" % "3.3.1";
