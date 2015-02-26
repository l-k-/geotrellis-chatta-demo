package chatta

import geotrellis.engine._
import geotrellis.raster._
import geotrellis.vector._

import com.typesafe.config.ConfigFactory

import akka.actor.Props
import akka.io.IO
import spray.can.Http

import com.vividsolutions.jts.{ geom => jts }

object Main {
  private var cachedRatios:Map[String,SeqSource[LayerRatio]] = null

  private lazy val albersRasterExtent = 
    RasterSource("albers_Wetlands").rasterExtent.get

  val weights = Map(
    "ImperviousSurfaces_Barren Lands_Open Water" -> 1,
    "DevelopedLand" -> 2,
    "Wetlands" -> 3,
    "ForestedLands" -> 4,
    "Non-workingProtectedOrPublicLands" -> 5,
    "PrimeAgriculturalSoilsNotForestedOrFarmland" -> 6,
    "PublicallyOwnedWorkingLands" -> 7,
    "PrivatelyOwnedWorkingLandsWithEasements" -> 8,
    "FarmlandWithoutPrimeAgriculturalSoils" -> 9,
    "FarmlandOrForestedLandsWithPrimeAgriculturalSoils" -> 10
  )

  def initCache(): Boolean = {     
    try {
      cachedRatios =
        (for(layer <- weights.keys) yield {
          println(s"CACHING TILE RESULT FOR RASTER $layer")
          (layer,
            RasterSource(s"albers_$layer")
              .map(LayerRatio.rasterResult(_))
              .cached
          )
        }).toMap
    } catch {
      case e:Exception =>
        GeoTrellis.shutdown()
        println(s"Could not load tile set: $e.message")
        e.printStackTrace()
        return false
    }
    true
  }


  def main(args: Array[String]): Unit = {
    if(!initCache) 
      return

    implicit val system = GeoTrellis.engine.system

    val config = ConfigFactory.load()
    val staticPath = config.getString("geotrellis.server.static-path")
    val port = config.getInt("geotrellis.port")
    val host = config.getString("geotrellis.hostname")

    // create and start our service actor
    val service = 
      system.actorOf(Props(classOf[ChattaServiceActor], staticPath), "chatta-service")

    // start a new HTTP server on the given port with our service actor as the handler
    IO(Http) ! Http.Bind(service, host, port = port)
  }

  def getRasterExtent(polygon:jts.Geometry):Op[RasterExtent] = {
    val env = polygon.getEnvelopeInternal
    val e = Extent( env.getMinX(), env.getMinY(), env.getMaxX(), env.getMaxY() )
    albersRasterExtent.createAligned(e)
  }

  def getCachedRatios(layer: String): SeqSource[LayerRatio] = {
    cachedRatios(layer)
  }
}
