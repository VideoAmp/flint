package flint
package service

import rx.Rx

private[service] trait MessageReceiver[Recv <: Message] {
  val receivedMessage: Rx[Option[Recv]]
}
