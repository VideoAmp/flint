package flint
package server
package messaging

private[server] trait Message

private[server] trait ClientMessage extends Message

private[server] trait ServerMessage extends Message {
  val id: Int
  val error: Option[String]
}
