package flint
package server
package messaging

import rx.Rx

private[server] trait MessageReceiver[Recv <: Message] {
  val receivedMessage: Rx[Option[Recv]]
}
