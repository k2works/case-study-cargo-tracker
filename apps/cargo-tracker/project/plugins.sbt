// Play Framework（sbt-native-packager 同梱: sbt stage / dist）
addSbtPlugin("org.playframework" % "sbt-plugin" % "3.0.8")

// コード品質（非機能要件定義のブロッキングゲート）
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.4")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.13.0")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.2.2")
