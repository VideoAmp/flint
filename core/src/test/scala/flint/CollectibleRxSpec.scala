package flint

import java.util.concurrent.TimeoutException

import scala.concurrent.Await
import scala.concurrent.duration._

import org.scalatest.{ FunSuite, Matchers }

import rx._

class CollectibleRxSpec extends FunSuite with Matchers {
  import FlintCtx.owner

  private val resultTimeout = 100 milliseconds

  test("test 1") {
    val a = Var(0)
    val future = a.collectFirst {
      case 0 => true
    }
    Await.result(future, resultTimeout) should be(true)
  }

  test("test 2") {
    val a = Var(0)
    val future = a.collectFirst {
      case 1 => true
    }
    a() = 1
    Await.result(future, resultTimeout) should be(true)
  }

  test("test 3") {
    val a = Var(0)
    val future = a.collectFirst {
      case 1 => true
    }
    assertThrows[TimeoutException] {
      Await.result(future, resultTimeout)
    }
  }
}
