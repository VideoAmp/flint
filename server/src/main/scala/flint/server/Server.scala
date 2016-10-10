package flint
package server

import scala.concurrent.Future

private[flint] trait Server {
  def bindTo(bindInterface: String, bindPort: Int, websocketRoute: String): Future[Binding]
}
