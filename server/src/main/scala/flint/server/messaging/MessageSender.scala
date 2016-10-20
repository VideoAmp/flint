package flint
package server
package messaging

import scala.concurrent.Future

private[messaging] trait MessageSender[Send <: Message] {
  def sendMessage(message: Send): Future[Send]
}
