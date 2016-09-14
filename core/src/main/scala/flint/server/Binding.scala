package flint
package server

import scala.concurrent.Future

private[flint] trait Binding {
  def unbind(): Future[Unit]
}
