package flint

import rx.Rx

trait Lifecycle {
  val lifecycleState: Rx[LifecycleState]
}
