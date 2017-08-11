package flint
package server
package messaging

import org.coursera.autoschema.DefaultTypeMappings

import play.api.libs.json.Json

trait MessageTypeMappings extends DefaultTypeMappings {
  override def schemaTypeForScala(typeName: String) =
    schemaTypes.get(typeName).orElse(super.schemaTypeForScala(typeName))

  private val schemaTypes =
    Map(
      "java.time.Duration" ->
        Json.obj("type" -> "string", "description" -> "An ISO-8601 duration"),
      "java.time.ZonedDateTime" -> Json.obj("type" -> "string", "format" -> "date-time"),
      "java.util.UUID" ->
        Json.obj(
          "type"    -> "string",
          "pattern" -> "^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$"),
      "scala.Int"         -> Json.obj("type" -> "integer"),
      "scala.Long"        -> Json.obj("type" -> "integer"),
      "scala.Float"       -> Json.obj("type" -> "number"),
      "scala.Double"      -> Json.obj("type" -> "number"),
      "scala.math.BigInt" -> Json.obj("type" -> "integer"),
      "flint.ContainerState" ->
        Json.obj(
          "type" -> "string",
          "enum" ->
            Json.arr(
              "ContainerPending",
              "ContainerRunning",
              "ContainerStarting",
              "ContainerStopped",
              "ContainerStopping")),
      "flint.InstanceState" ->
        Json.obj(
          "type" -> "string",
          "enum" ->
            Json.arr("Pending", "Running", "Starting", "Terminated", "Terminating")),
      "flint.server.messaging.TerminationReason" ->
        Json.obj(
          "type" -> "string",
          "enum" ->
            Json.arr("ClientRequested", "IdleTimeout", "TTLExpired"))
    )
}
