import AssemblyKeys._

name := "GeoTrellis Tutorial Project"

scalaVersion := "2.11.2"

resolvers += Resolver.sonatypeRepo("snapshots")

resolvers += "Geotools" at "http://download.osgeo.org/webdav/geotools/"

resolvers += "spray repo" at "http://repo.spray.io/"

libraryDependencies ++= Seq(
  "com.azavea.geotrellis" %% "geotrellis" % "0.10.0-SNAPSHOT",
  "com.azavea.geotrellis" %% "geotrellis-engine" % "0.10.0-SNAPSHOT",
  "com.azavea.geotrellis" %% "geotrellis-services" % "0.10.0-SNAPSHOT",
  "io.spray" %% "spray-routing" % "1.3.2",
  "io.spray" %% "spray-can" % "1.3.2",
  "io.spray" %% "spray-http" % "1.3.2",
  "org.geotools" % "gt-main" % "11.0",
  "org.geotools" % "gt-coveragetools" % "11.0"
)

seq(Revolver.settings: _*)

assemblySettings

mergeStrategy in assembly <<= (mergeStrategy in assembly) {
  (old) => {
    case "reference.conf" => MergeStrategy.concat
    case "application.conf" => MergeStrategy.concat
    case "META-INF/MANIFEST.MF" => MergeStrategy.discard
    case "META-INF\\MANIFEST.MF" => MergeStrategy.discard
    case _ => MergeStrategy.first
  }
}

net.virtualvoid.sbt.graph.Plugin.graphSettings
