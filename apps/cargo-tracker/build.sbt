name := "cargo-tracker"
organization := "cargotracker"
version := "0.1.0-SNAPSHOT"

scalaVersion := "3.3.6"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

libraryDependencies ++= Seq(
  guice,
  jdbc,
  "org.postgresql" % "postgresql" % "42.7.4",
  "org.scalikejdbc" %% "scalikejdbc" % "4.3.2",
  "org.flywaydb" %% "flyway-play" % "9.1.0",
  "org.mindrot" % "jbcrypt" % "0.4",
  "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.1" % Test,
  "com.dimafeng" %% "testcontainers-scala-scalatest" % "0.43.0" % Test,
  "com.dimafeng" %% "testcontainers-scala-postgresql" % "0.43.0" % Test,
  // ArchUnit: ヘキサゴナルアーキテクチャの境界をテストで強制する
  "com.tngtech.archunit" % "archunit" % "1.3.0" % Test,
  // ScalaCheck: 値オブジェクトの不変条件をプロパティテストで検証（IT3 タスク 0.11）
  "org.scalatestplus" %% "scalacheck-1-18" % "3.2.19.0" % Test
)

// Docker Engine 29 系の API に対応した testcontainers-java を使用する
dependencyOverrides ++= Seq(
  "org.testcontainers" % "testcontainers" % "1.21.3" % Test,
  "org.testcontainers" % "postgresql" % "1.21.3" % Test
)

// docker-java の API バージョンを明示する
//   - ローカル Docker Desktop は 1.54 まで対応
//   - GitHub Actions Ubuntu runner は最大 1.48
// 互換性最大化のため両環境で受理される 1.48 を採用する
Test / fork := true
Test / javaOptions += "-Dapi.version=1.48"

// 警告ゼロを品質ゲートとする（非機能要件定義）
// -deprecation / -unchecked は Play プラグインが設定済みのため指定しない（重複指定は警告になる）
scalacOptions ++= Seq(
  "-feature",
  "-Werror"
)

// scalafix（セマンティックルール用 SemanticDB）
ThisBuild / semanticdbEnabled := true

// scoverage: 全体 80% を下回ったらビルド失敗（テスト戦略・test_strategy.md）
// IT2 タスク 0.9 で IT1 暫定値 75% から復元。実績 82.34%（IT3 着手前）
coverageMinimumStmtTotal := 80
coverageFailOnMinimum := true
// Play が生成するルーター・リバースルート・Twirl テンプレートと、
// DI 初期化コード（Module / ScalikeJdbcInitializer）は計測対象外
coverageExcludedPackages := "<empty>;Reverse.*;router\\..*;views\\.html\\..*"
coverageExcludedFiles := ".*/Module\\.scala;.*ScalikeJdbcInitializer.*"

// SonarQube 連携プロパティ（sbt-sonar）
import sbtsonar.SonarPlugin.autoImport.sonarProperties
sonarProperties := Map(
  "sonar.projectKey"  -> "cargo-tracker-5-backend",
  "sonar.projectName" -> "Cargo Tracker Scala (Backend)",
  "sonar.sources"     -> "app",
  "sonar.tests"       -> "test",
  "sonar.sourceEncoding" -> "UTF-8",
  "sonar.exclusions"  -> "**/target/**,app/views/**",
  "sonar.scala.coverage.reportPaths" -> "target/scala-3.3.6/scoverage-report/scoverage.xml"
)
