package flint
package service

sealed trait ClusterTerminationReason {
  val name = toString
}

object ClusterTerminationReason {
  private[flint] def apply(name: String): ClusterTerminationReason = name match {
    case ClientRequested.name => ClientRequested
    case IdleTimeout.name     => IdleTimeout
    case TTLExpired.name      => TTLExpired
  }

  case object ClientRequested extends ClusterTerminationReason

  case object IdleTimeout extends ClusterTerminationReason

  case object TTLExpired extends ClusterTerminationReason
}
