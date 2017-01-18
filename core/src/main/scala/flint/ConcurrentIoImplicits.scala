package flint

private[flint] object ConcurrentIoImplicits {
  lazy implicit val ioExecutorService = flint.ioExecutorService

  lazy implicit val ioExecutionContext = flint.ioExecutionContext
}
