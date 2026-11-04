// 国際貨物輸送管理システム（ADR-001：Unit を最上位に、contract で連携する）
// 依存の向き：domain ← usecase ← commandadapter、contract は shared-kernel だけに依存する葉、
// queryadapter は自 Unit の domain・contract に依存してよい（他 Unit の domain は不可）。
// 別の Unit を追加するときは、ModuleBoundariesSpec の units に足す（検査 A1）。

ThisBuild / scalaVersion := "3.9.0"
ThisBuild / organization := "cargotracker"
ThisBuild / version := "0.1.0-SNAPSHOT"

val pekkoVersion = "1.5.0"

lazy val commonSettings = Seq(
  scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked", "-Werror"),
  libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.20" % Test
)

def unitModule(path: String) =
  Project(path.replace('/', '-'), file(s"modules/$path")).settings(commonSettings)

lazy val sharedKernel = unitModule("shared-kernel")

// U-02 貨物予約
lazy val bookingDomain = unitModule("booking/domain").dependsOn(sharedKernel)
lazy val bookingUseCase = unitModule("booking/use-case").dependsOn(bookingDomain, sharedKernel)
lazy val bookingContract = unitModule("booking/contract").dependsOn(sharedKernel)
lazy val bookingCommandAdapter = unitModule("booking/command-adapter")
  .dependsOn(bookingUseCase % "compile->compile;test->test", dbTestSupport % Test)
  .settings(
    // イベントの Protobuf（ScalaPB。ADR-007）。生成コードの警告は -Werror の対象から外す。
    Compile / PB.targets := Seq(scalapb.gen() -> (Compile / sourceManaged).value / "scalapb"),
    scalacOptions += "-Wconf:src=.*scalapb.*:silent",
    // DB を使うテストを、同じ JVM で並行して走らせない（Testcontainers の起動を 1 回にし、テスト間の干渉を避ける）
    Test / fork := true,
    libraryDependencies ++= Seq(
      "com.thesamet.scalapb" %% "scalapb-runtime"                % "0.11.21",
      // 読み書きの DAO（手書きの Slick 定義。ADR-008）
      "com.typesafe.slick"   %% "slick"                          % "3.6.1",
      "org.postgresql"       %  "postgresql"                     % "42.7.14",
      // GraphQL の Mutation（Sangria + circe。ADR-006）
      "org.sangria-graphql"  %% "sangria"                        % "4.2.20",
      "org.sangria-graphql"  %% "sangria-circe"                  % "1.3.2",
      "io.circe"             %% "circe-core"                     % "0.14.17",
      "io.circe"             %% "circe-parser"                   % "0.14.17",
      // 集約アクター（Pekko 標準の EventSourcedBehavior。個人製の effector は使わない。ADR-002）
      "org.apache.pekko"     %% "pekko-actor-typed"              % pekkoVersion,
      "org.apache.pekko"     %% "pekko-persistence-typed"        % pekkoVersion,
      "org.apache.pekko"     %% "pekko-actor-testkit-typed"      % pekkoVersion % Test,
      "org.apache.pekko"     %% "pekko-persistence-testkit"      % pekkoVersion % Test
    )
  )
lazy val bookingQueryAdapter =
  unitModule("booking/query-adapter")
    .dependsOn(bookingContract, bookingDomain, sharedKernel, dbTestSupport % Test)
    .settings(
      Test / fork := true,
      libraryDependencies ++= Seq(
        "com.typesafe.slick" %% "slick"      % "3.6.1",
        "org.postgresql"      % "postgresql" % "42.7.14",
        // GraphQL（Sangria + circe。ADR-006）
        "org.sangria-graphql" %% "sangria"        % "4.2.20",
        "org.sangria-graphql" %% "sangria-circe"  % "1.3.2",
        "io.circe"            %% "circe-core"     % "0.14.17",
        "io.circe"            %% "circe-parser"   % "0.14.17"
      )
    )

// マイグレーション（Flyway。ADR-008）。Unit ごとにスキーマと履歴を持ち、共有（reference・platform）を先に適用する。
// 場所は db/migration/<name> に分けて、classpath 上で衝突させない。
lazy val platformMigration = unitModule("platform-migration")
lazy val bookingMigration  = unitModule("booking/migration")

// DB を使うテストの共通部品（本番コードは持たない）。PostgreSQL の実物を Testcontainers で 1 回起動し、
// テストごとに空の DB を作って全マイグレーションを適用する。他のモジュールの Test からだけ使う。
lazy val dbTestSupport = unitModule("db-test-support")
  .dependsOn(platformMigration, bookingMigration)
  .settings(
    libraryDependencies ++= Seq(
      "org.flywaydb"   % "flyway-core"                % "13.12.0",
      "org.flywaydb"   % "flyway-database-postgresql" % "13.12.0",
      "org.postgresql" % "postgresql"                 % "42.7.14",
      "org.testcontainers" % "postgresql"             % "1.21.4"
    )
  )

// マイグレーションの検査（I7 ほか）。PostgreSQL の実物（Testcontainers）で行う。メモリ上の別 DB は使わない（ADR-011）。
lazy val migrationTests = unitModule("migration-tests")
  .dependsOn(platformMigration, bookingMigration)
  .settings(
    Test / fork := true,
    libraryDependencies ++= Seq(
      "org.flywaydb"      % "flyway-core"                        % "13.12.0" % Test,
      "org.flywaydb"      % "flyway-database-postgresql"         % "13.12.0" % Test,
      "org.postgresql"    % "postgresql"                         % "42.7.14" % Test,
      "com.dimafeng"     %% "testcontainers-scala-scalatest"     % "0.44.1"  % Test,
      "com.dimafeng"     %% "testcontainers-scala-postgresql"    % "0.44.1"  % Test
    )
  )

// ローカルで動かす実行物（結線の根。Bolt 1 はインメモリ Journal で 1 つの JVM に載せる。永続化は SP-1 の後に決める）。
// ※ pekko-persistence-testkit をアプリの Journal として使うのは、このローカル実行物だけ。
lazy val localServer = Project("local-server", file("apps/local-server"))
  .settings(commonSettings)
  .dependsOn(bookingCommandAdapter, bookingQueryAdapter, dbTestSupport % Test)
  .settings(
    Test / fork := true,
    // スキーマの固定ファイル（apps/web/schema）の場所をテストに渡す
    Test / javaOptions += s"-Dcargotracker.root=${(ThisBuild / baseDirectory).value}",
    libraryDependencies ++= Seq(
      "org.apache.pekko" %% "pekko-http"                % "1.4.2",
      "org.apache.pekko" %% "pekko-stream"              % pekkoVersion,
      "org.apache.pekko" %% "pekko-persistence-query"   % pekkoVersion,
      "org.apache.pekko" %% "pekko-persistence-testkit" % pekkoVersion,
      "org.apache.pekko" %% "pekko-actor-testkit-typed" % pekkoVersion % Test,
      // ログの出力先（SLF4J の実装）。無いとログが捨てられる。slf4j-api（2.0.17）に合わせる。テスト用の起動口（E2eServer）と検査だけで使う
      "org.slf4j" % "slf4j-simple" % "2.0.17" % Test
    )
  )

// 構造の検査（A1・A3 など）。全モジュールをクラスパスに載せて、パッケージの依存を検査する。
lazy val architectureTests = unitModule("architecture-tests")
  .dependsOn(
    sharedKernel,
    bookingDomain,
    bookingUseCase,
    bookingContract,
    bookingCommandAdapter,
    bookingQueryAdapter
  )
  .settings(libraryDependencies += "com.tngtech.archunit" % "archunit" % "1.5.1" % Test)

lazy val root = (project in file("."))
  .aggregate(
    sharedKernel,
    bookingDomain,
    bookingUseCase,
    bookingContract,
    bookingCommandAdapter,
    bookingQueryAdapter,
    platformMigration,
    bookingMigration,
    dbTestSupport,
    migrationTests,
    localServer,
    architectureTests
  )
  .settings(name := "cargo-tracker", publish / skip := true)
