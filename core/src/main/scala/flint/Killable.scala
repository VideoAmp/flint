package flint

import scala.concurrent.Future

trait Killable {
  def terminate(): Future[Unit]
}
