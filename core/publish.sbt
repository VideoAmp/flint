publishTo := {
  val vamp = "https://videoamp.artifactoryonline.com/videoamp/"

  if (isSnapshot.value)
    Some("snapshots" at vamp + "snapshot")
  else
    Some("releases"  at vamp + "release")
}
