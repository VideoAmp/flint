package flint
package server
package messaging

import rx.Rx

private[messaging] trait MessageReceiver {
  val receivedMessage: Rx[Option[Message]]
}
