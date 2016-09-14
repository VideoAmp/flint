package flint

import rx.Rx

trait Lifecycle extends Killable {
  val lifecycleState: Rx[LifecycleState]
}
