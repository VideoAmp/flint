package flint
package server
package messaging

import scala.reflect.runtime.universe.TypeTag

import play.api.libs.json._

object MessageSchemaGenerator {
  private val addTypeToRequired = (__ \ 'required).json.update(
    __.read[JsArray].map(required => JsString("$type") +: required)
  )
  private val addTypeToProperties = (__ \ 'properties).json.update(
    __.read[JsObject].map(_ + ("$type" -> Json.obj("type" -> "string")))
  )

  def main(args: Array[String]): Unit = {
    // scalastyle:off println

    import Console.err

    def abort(message: String) = {
      err.println(message)
      sys.exit(1)
    }

    var outputFilename: Option[String] = None
    var generateClient                 = false
    var generateServer                 = false

    @annotation.tailrec
    def processArgs(argList: List[String]): Unit =
      argList match {
        case "-f" :: filename :: theRest =>
          if (filename.startsWith("-")) {
            abort(s"Invalid filename $filename")
          }
          outputFilename = Some(filename)
          processArgs(theRest)
        case "-f" :: Nil =>
          abort("-f must be followed by a filename")
        case "-c" :: theRest =>
          generateClient = true
          processArgs(theRest)
        case "-s" :: theRest =>
          generateServer = true
          processArgs(theRest)
        case arg :: _ =>
          abort(s"Unknown flag $arg")
        case Nil =>
      }

    processArgs(args.toList)

    // If neither flag is set, output both kinds of messages
    if (!generateClient && !generateServer) {
      generateClient = true
      generateServer = true
    }

    val clientMessageSchemata = if (generateClient) {
      err.println("Generating client message schema")
      Seq(
        createMessageSchema[AddWorkers],
        createMessageSchema[ChangeDockerImage],
        createMessageSchema[LaunchCluster],
        createMessageSchema[TerminateCluster],
        createMessageSchema[TerminateWorker]
      )
    } else { Seq.empty[JsObject] }

    val serverMessageSchemata = if (generateServer) {
      err.println("Generating server message schema")
      Seq(
        createMessageSchema[ClusterLaunchAttempt],
        createMessageSchema[ClusterTerminationAttempt],
        createMessageSchema[DockerImageChangeAttempt],
        createMessageSchema[InstanceContainerState],
        createMessageSchema[InstanceDockerImage],
        createMessageSchema[InstanceState],
        createMessageSchema[WorkerAdditionAttempt],
        createMessageSchema[WorkerTerminationAttempt]
      )
    } else { Seq.empty[JsObject] }

    val schemata = clientMessageSchemata ++ serverMessageSchemata

    val schemataJsonText =
      schemata
        .map(Json.prettyPrint)
        .map(_.replaceAll("\n", "\n  "))
        .mkString("[\n  ", ",\n  ", "\n]")

    outputFilename match {
      case Some(filename) =>
        import scala.collection.JavaConverters._
        import java.nio.file.{ Files, Paths }

        err.println(s"Writing output to $filename")

        val outputPath = Paths.get(filename)
        Files.write(outputPath, Seq(schemataJsonText).asJava)
      case None =>
        println(schemataJsonText)
    }
  }

  private def createMessageSchema[T: TypeTag]: JsObject =
    AutoMessageSchema
      .createSchema[T]
      .transform(addTypeToRequired)
      .flatMap(_.transform(addTypeToProperties))
      .get
}
