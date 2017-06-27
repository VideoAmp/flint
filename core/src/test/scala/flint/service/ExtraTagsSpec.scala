package flint.service

import org.scalatest.FlatSpec

class ExtraTagsSpec extends FlatSpec {
  private[this] val baseExtraTags = ExtraTags(
    Map(
      "foo"       -> "bar",
      "biz"       -> "baz",
      "something" -> "important"
    ))

  private[this] val additionalExtraTags = ExtraTags(
    Map(
      "something" -> "unimportant",
      "answer"    -> "42"
    ))

  behavior.of("extend")

  it should "properly overwrite existing tag values when extending" in {
    val mergedExtraTags = baseExtraTags.extend(additionalExtraTags)
    assert(
      mergedExtraTags == ExtraTags(
        Map(
          "foo"       -> "bar",
          "biz"       -> "baz",
          "something" -> "unimportant",
          "answer"    -> "42"
        )
      )
    )
  }
}
