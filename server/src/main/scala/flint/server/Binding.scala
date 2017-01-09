package flint
package server

import java.net.URI

import scala.concurrent.Future

private[flint] trait Binding {
  val messagingUrl: URI

  val serviceUrl: URI

  def unbind(): Future[Unit]
}
