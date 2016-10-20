package flint
package server
package messaging

import rx.Rx

private[messaging] trait MessageReceiver[Recv <: Message] {
  val receivedMessage: Rx[Option[Recv]]
}
