package flint
package server
package messaging

import org.scalatest.FlatSpec

class MessageCodecSpec extends FlatSpec {
  behavior of "MessageCodec"

  TestMessages.testMessages.foreach { message =>
    it should s"roundtrip ${message.getClass.getSimpleName}" in {
      testRoundtrip(message)
    }
  }

  private def testRoundtrip(message: Message): Unit = {
    val serializedMessage = MessageCodec.encode(message)
    MessageCodec
      .decode(serializedMessage)
      .fold(
        errs => fail(buildDecodingErrorMessage(serializedMessage, errs)),
        (deserialized: Message) => assert(message == deserialized)
      )
  }
}
