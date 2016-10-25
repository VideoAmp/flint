package flint
package service
package aws

import scala.concurrent.Promise

import com.amazonaws.AmazonWebServiceRequest
import com.amazonaws.handlers.AsyncHandler

private[aws] class AwsRequestHandler[Req <: AmazonWebServiceRequest, Res]
    extends AsyncHandler[Req, Res] {
  private val promise = Promise[Res]

  val future = promise.future

  override def onError(ex: Exception) = promise.failure(ex)

  override def onSuccess(request: Req, result: Res) = promise.success(result)
}
