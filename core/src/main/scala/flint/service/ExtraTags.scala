package flint
package service

case class ExtraTags(tags: Map[String, String] = Map.empty) {
  def extend(extraTags: ExtraTags): ExtraTags = ExtraTags(
    tags.foldLeft(extraTags.tags) { (acc, pair) =>
      if (!acc.contains(pair._1)) {
        acc + pair
      } else {
        acc
      }
    }
  )
}
