package flint
package service

import scala.concurrent.Future

private[service] trait MessageSender[Send <: Message] {
  def sendMessage(message: Send): Future[Unit]
}
