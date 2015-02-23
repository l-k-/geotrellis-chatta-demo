package chatta

import geotrellis.engine._
import geotrellis.engine.op.local._
import geotrellis.engine.render._
import geotrellis.engine.stats._
import geotrellis.raster._
import geotrellis.raster.render._
import geotrellis.services._
import geotrellis.vector._
import geotrellis.vector.json._

import akka.actor._
import spray.routing.HttpService
import spray.http._

import com.vividsolutions.jts.{ geom => jts }

class ChattaServiceActor(val staticPath:String) extends Actor with ChattaService {
  def actorRefFactory = context
  def receive = runRoute(serviceRoute)
}

trait ChattaService extends HttpService {
  implicit def executionContext = actorRefFactory.dispatcher

  val staticPath:String

  val serviceRoute = {
    get {
      pathSingleSlash {
        redirect("index.html",StatusCodes.Found)
      } ~
      pathPrefix("gt") {
        path("colors") {
          complete(ColorRampMap.getJson)
        } ~
        path("breaks") {
          parameters('layers,
                     'weights,
                     'numBreaks.as[Int],
                     'mask ? "") {
            (layersParam,weightsParam,numBreaks,mask) => {
              val extent = Extent(-9634947.090,4030964.877,-9359277.090,4300664.877)
              val re = RasterExtent(extent,256,256)

              val layers = layersParam.split(",")
              val weights = weightsParam.split(",").map(_.toInt)

              Model.weightedOverlay(layers,weights,re)
                .classBreaks(numBreaks)
                .run match {
                  case Complete(breaks, _) =>
                    complete(Json.classBreaks(breaks))
                  case Error(message,trace) =>
                    failWith(new RuntimeException(message))
              }
            }
          }
        } ~
        path("wo") {
          parameters('service,
                     'request,
                     'version,
                     'format,
                     'bbox,
                     'height.as[Int],
                     'width.as[Int],
                     'layers,
                     'weights,
                     'palette ? "ff0000,ffff00,00ff00,0000ff",
                     'colors.as[Int] ? 4,
                     'breaks,
                     'colorRamp ? "blue-to-red",
                     'mask ? "") {
            (_,_,_,_,bbox,cols,rows,layersString,weightsString,
              palette,colors,breaksString,colorRamp,mask) => {
              val extent = Extent.fromString(bbox)

              val re = RasterExtent(extent, cols, rows)

              val layers = layersString.split(",")
              val weights = weightsString.split(",").map(_.toInt)

              val model = Model.weightedOverlay(layers,weights,re)

              val overlay =
                if(mask == "") {
                  model
                } else {

                  val transformed: jts.Geometry =
                    GeoJson.parse[Polygon](mask) match {
                      case Polygon(poly: jts.Polygon) =>
                        Transformer.transform(poly, Projections.LatLong, Projections.WebMercator)
                      case _ =>
                        throw new Exception(s"Invalid GeoJSON: $mask")
                    }
                  model.mask(Geometry.fromJts[Geometry](transformed))
                }
              
              val breaks =
                breaksString.split(",").map(_.toInt)
              
              val ramp = {
                val cr = ColorRampMap.getOrElse(colorRamp,ColorRamps.BlueToRed)
                if(cr.toArray.length < breaks.length) { cr.interpolate(breaks.length) }
                else { cr }
              }

              val png:ValueSource[Png] = overlay.renderPng(ramp, breaks)

              png.run match {
                case Complete(img,h) =>
                  respondWithMediaType(MediaTypes.`image/png`) {
                    complete(img.bytes)
                  }
                case Error(message,trace) =>
                  println(message)
                  println(trace)
                  println(re)

                  failWith(new RuntimeException(message))
              }
            }
          }
        } ~
        path("sum") {
          parameters('polygon,
            'layers,
            'weights) {
            (polygonJson,layersString,weightsString) => {
              val start = System.currentTimeMillis()

              val transformed =
                GeoJson.parse[Polygon](polygonJson) match {
                  case Polygon(poly: jts.Polygon) =>
                    Transformer.transform(poly, Projections.LatLong, Projections.ChattaAlbers)
                      .asInstanceOf[jts.Polygon]
                  case _ =>
                    throw new Exception(s"Invalid GeoJSON: $polygonJson")
                }
              val layers = layersString.split(",")
              val weights = weightsString.split(",").map(_.toInt)

              val summary = Model.summary(layers,weights,transformed)

              summary.run match {
                case Complete(result,h) =>
                  val elapsedTotal = System.currentTimeMillis - start

                  val layerSummaries =
                    "[" + result.layerSummaries.map {
                      ls =>
                      val v = "%.2f".format(ls.score * 100)
                      s"""{ "layer": "${ls.name}", "total": "${v}" }"""
                    }.mkString(",") + "]"

                  val totalVal = "%.2f".format(result.score * 100)
                  val data = 
                    s"""{
                          "layerSummaries": $layerSummaries,
                          "total": "${totalVal}", 
                          "elapsed": "$elapsedTotal"
                        }"""
                  complete(data)
                case Error(message,trace) =>
                  failWith(new RuntimeException(message))
              }
            }
          }
        }
      } ~
      pathPrefix("") {
//        getFromDirectory(directoryName)
        getFromDirectory(staticPath)
      }
    }
  }
}
