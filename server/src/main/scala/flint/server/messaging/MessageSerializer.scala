package flint
package server
package messaging

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper

private[server] object MessageSerializer {
  private val mapper = new ObjectMapper with ScalaObjectMapper
  mapper.registerModule(DefaultScalaModule)

  def serializeMessage[M <: Message](message: M): String = mapper.writeValueAsString(message)

  def deserializeMessage[M <: Message](messageText: String): M = mapper.readValue(messageText)
}
