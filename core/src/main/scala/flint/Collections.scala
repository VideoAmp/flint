package flint

trait Collections {
  private[flint] type Seq[+T] = collection.immutable.Seq[T]
  private[flint] val Seq = collection.immutable.Seq

  private[flint] type MMap[K, V] = collection.mutable.Map[K, V]
  private[flint] val MMap = collection.mutable.Map
}
