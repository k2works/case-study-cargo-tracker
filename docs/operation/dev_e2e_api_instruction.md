# コントローラー E2E テストセットアップ手順書

## 概要

本ドキュメントは、Case Study Cargo Tracker（Scala 版）のコントローラー E2E（End-to-End）テスト環境をセットアップする手順を説明します。

コントローラー E2E テストは ScalaTestPlus-Play の `FakeRequest` / `route` と testcontainers-scala（PostgreSQL）を使用し、画面 Controller・JSON API・認証（`AuthenticatedAction`）・Play Form・CSRF を含むアプリケーション全体を結合した状態でユーザーストーリー単位のフローを検証します。ブラウザ E2E テスト（Playwright）と異なり、JVM 内で完結するため高速に実行できます（Java 版の MockMvc + Testcontainers に相当）。

| 項目 | 内容 |
|------|------|
| テストフレームワーク | ScalaTest + ScalaTestPlus-Play |
| HTTP シミュレーション | `FakeRequest` + `route`（実 HTTP サーバー不要） |
| データベース | PostgreSQL（testcontainers-scala） |
| 認証 | Play Session（`withSession` でログイン状態を構成） |
| CSRF | `CSRFTokenHelper.addCSRFToken` |
| テストディレクトリ | `apps/cargo-tracker/test/cargotracker/e2e/` |

---

## 1. 前提条件

| 前提 | 確認方法 |
|------|---------|
| JDK 21.x / sbt 1.10.x | `java -version` / `sbt --version` |
| Docker Desktop が起動している | `docker info` |
| `sbt compile` が成功する | `cd apps/cargo-tracker && sbt compile` |

> **Note**: testcontainers-scala は Docker を使用して PostgreSQL コンテナを自動起動します。Docker Desktop が起動している必要があります。

---

## 2. ディレクトリ構造

```
apps/cargo-tracker/test/cargotracker/
├── support/
│   └── PostgresContainerSupport.scala   # Testcontainers 共通トレイト
└── e2e/
    ├── US13ConfirmBookingE2ESpec.scala   # US13 予約確定フロー
    ├── US15HandlingEventE2ESpec.scala    # US15 荷役記録フロー
    ├── US18TrackingInfoE2ESpec.scala     # US18 公開追跡ページ
    └── ...                               # ユーザーストーリーごとに追加
```

---

## 3. 基盤トレイトのセットアップ

### 3.1 PostgreSQL Testcontainers 共通トレイト

全てのコントローラー E2E テストがミックスインする共通トレイトを作成します。`TestContainerForAll` により、スイート内で PostgreSQL コンテナを共有し、テスト実行を高速化します。

`test/cargotracker/support/PostgresContainerSupport.scala`:

```scala
package cargotracker.support

import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import org.scalatest.Suite
import org.testcontainers.utility.DockerImageName
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder

trait PostgresContainerSupport extends TestContainerForAll { self: Suite =>

  override val containerDef: PostgreSQLContainer.Def =
    PostgreSQLContainer.Def(
      dockerImageName = DockerImageName.parse("postgres:16-alpine"),
      databaseName = "cargo_tracker_test",
      username = "test",
      password = "test"
    )

  /** コンテナの接続情報で Play アプリケーションを構築する */
  def buildApp(container: PostgreSQLContainer): Application =
    GuiceApplicationBuilder()
      .configure(
        "db.default.url"      -> container.jdbcUrl,
        "db.default.username" -> container.username,
        "db.default.password" -> container.password,
        "play.evolutions.enabled" -> false
        // flyway-play はこの db.default 設定を参照し、起動時にマイグレーションを自動適用する
      )
      .build()
}
```

**ポイント:**

- `TestContainerForAll` はスイート（テストクラス）単位でコンテナを 1 回だけ起動する
- `GuiceApplicationBuilder.configure` で DataSource 設定をコンテナに向ける
- flyway-play の接続先もコンテナに向くため、マイグレーション（`conf/db/migration/default/`）が自動実行される

---

## 4. E2E テストの構造

### 4.1 テストクラスの基本構成

```scala
package cargotracker.e2e

import cargotracker.support.PostgresContainerSupport
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.play.guice.GuiceFakeApplicationFactory
import play.api.test.Helpers.*
import play.api.test.{CSRFTokenHelper, FakeRequest}
import scalikejdbc.*

class US15HandlingEventE2ESpec extends AnyFunSuite with Matchers with PostgresContainerSupport {

  test("E01 荷役作業を登録できる") {
    withContainers { container =>
      val app = buildApp(container)
      running(app) {
        // Given: ログイン済みセッション（HANDLER ロール）でフォーム送信
        val request = CSRFTokenHelper.addCSRFToken(
          FakeRequest(POST, "/handling")
            .withSession(authenticatedSession(role = "HANDLER")*)
            .withFormUrlEncodedBody(
              "trackingNumber" -> "TRK-20260701-0001",
              "handlingType"   -> "LOAD",
              "locationCode"   -> "JPOSA",
              "voyageNumber"   -> "V0042",
              "completionTime" -> "2026-07-01T08:30"
            )
        )

        // When
        val result = route(app, request).get

        // Then: PRG リダイレクトとフラッシュメッセージ
        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get should startWith("/handling")
        flash(result).get("successMessage").get should include("登録しました")

        // DB 検証
        DB.readOnly { implicit session =>
          val count = sql"SELECT count(*) FROM handling_activity WHERE voyage_number = 'V0042'"
            .map(_.int(1)).single.apply().get
          count shouldBe 1
        }
      }
    }
  }

  /** 認証済み Session を構成する（lastAccessedAt・session_generation を含む） */
  private def authenticatedSession(role: String): Seq[(String, String)] =
    Seq(
      "userId"            -> "1",
      "username"          -> "tester",
      "roles"             -> role,
      "sessionGeneration" -> "0",
      "lastAccessedAt"    -> java.time.Instant.now().toEpochMilli.toString
    )
}
```

### 4.2 主要な構成要素

| 構成要素 | 説明 |
|---------|------|
| `PostgresContainerSupport` | testcontainers-scala の PostgreSQL を使用 |
| `buildApp(container)` | DB 設定をコンテナに向けた `Application` を構築 |
| `running(app) { ... }` | アプリケーションのライフサイクル管理 |
| `FakeRequest` + `route` | 実 HTTP サーバーなしでルーティング〜Controller を実行 |
| `CSRFTokenHelper.addCSRFToken` | Play CSRF Filter を通過させる |
| `withSession(...)` | `AuthenticatedAction` が検証する Session 値を直接構成 |

---

## 5. テストパターン

### 5.1 認証（ログインフォーム経由）

`AuthenticatedAction` の検証（タイムアウト・世代番号）まで含めて通す場合は、ログインエンドポイントを実際に呼び出して Session を取得します。

```scala
val loginResult = route(app, CSRFTokenHelper.addCSRFToken(
  FakeRequest(POST, "/login")
    .withFormUrlEncodedBody("username" -> "admin", "password" -> "admin")
)).get
status(loginResult) shouldBe SEE_OTHER
val sessionData = session(loginResult).data.toSeq

// 以降のリクエストに付与
FakeRequest(GET, "/bookings").withSession(sessionData*)
```

### 5.2 画面フォーム送信（PRG + Flash の検証）

```scala
val result = route(app, CSRFTokenHelper.addCSRFToken(
  FakeRequest(POST, "/bookings")
    .withSession(sessionData*)
    .withFormUrlEncodedBody(
      "originUnLocode"      -> "JPOSA",
      "destinationUnLocode" -> "USLAX",
      "arrivalDeadline"     -> "2026-07-15",
      "cargoType"           -> "GENERAL",
      "weight"              -> "1200"
    )
)).get

status(result) shouldBe SEE_OTHER
redirectLocation(result).get should fullyMatch regex "/bookings/BK-.*".r
flash(result).get("successMessage").get should include("登録しました")
```

### 5.3 JSON API 呼び出し

```scala
import play.api.libs.json.Json

val result = route(app,
  FakeRequest(GET, "/api/v1/tracking/TRK-20260701-0001")
).get

status(result) shouldBe OK
(contentAsJson(result) \ "transportStatus").as[String] shouldBe "LOADED"
```

### 5.4 DB 検証（ScalikeJDBC）

```scala
DB.readOnly { implicit session =>
  val bookingStatus = sql"SELECT booking_status FROM cargo WHERE booking_id = $bookingId"
    .map(_.string(1)).single.apply()
  bookingStatus shouldBe Some("CONFIRMED")
}
```

### 5.5 未認証アクセスの検証

```scala
test("未認証ユーザーはログインページにリダイレクトされる") {
  withContainers { container =>
    val app = buildApp(container)
    running(app) {
      val result = route(app, FakeRequest(GET, "/handling/new")).get
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get should include("/login")
    }
  }
}
```

### 5.6 ロール不足（403）の検証

```scala
// TRACKER ロールで荷役登録（HANDLER 専用）にアクセス
val result = route(app, FakeRequest(GET, "/handling/new")
  .withSession(authenticatedSession(role = "TRACKER")*)).get
status(result) shouldBe FORBIDDEN
```

### 5.7 バリデーションエラーの検証

```scala
val result = route(app, CSRFTokenHelper.addCSRFToken(
  FakeRequest(POST, "/bookings")
    .withSession(sessionData*)
    .withFormUrlEncodedBody("originUnLocode" -> "")  // 必須項目が空
)).get

status(result) shouldBe BAD_REQUEST
contentAsString(result) should include("is-invalid")  // フィールドエラー表示
```

### 5.8 htmx リクエスト（部分更新）の検証

```scala
val result = route(app, FakeRequest(GET, "/tracking/TRK-20260701-0001/status")
  .withSession(sessionData*)
  .withHeaders("HX-Request" -> "true")
).get

status(result) shouldBe OK
// フラグメント（_statusTimeline）のみが返り、<html> を含まない
contentAsString(result) should not include "<html"
```

---

## 6. テストの実行

```bash
cd apps/cargo-tracker

# コントローラー E2E テストのみ実行（パッケージ指定）
sbt "testOnly cargotracker.e2e.*"

# 特定のテストクラスのみ
sbt "testOnly cargotracker.e2e.US15HandlingEventE2ESpec"

# 全テスト実行
sbt test
```

> **Note**: 初回実行時は PostgreSQL の Docker イメージをダウンロードするため時間がかかります。

---

## 7. テストデータのクリーンアップ

テストごとに外部キー制約の順序に従ってデータを削除します（子テーブルから順に）。

```scala
override def afterEach(): Unit = {
  DB.localTx { implicit session =>
    sql"DELETE FROM tracking_handling_event".update.apply()
    sql"DELETE FROM tracking_exception_event".update.apply()
    sql"DELETE FROM tracking_activity".update.apply()
    sql"DELETE FROM handling_activity".update.apply()
    sql"DELETE FROM leg".update.apply()
    sql"DELETE FROM cargo".update.apply()
    sql"DELETE FROM shipper".update.apply()
  }
  super.afterEach()
}
```

**重要**: テーブル間の外部キー制約（[データモデル設計](../design/data-model.md)）に注意し、子テーブルから順に削除してください。

---

## 8. E2E テスト追加ガイド

新しいユーザーストーリーのコントローラー E2E テストを追加する際の手順:

1. `test/cargotracker/e2e/` に `US{番号}{機能名}E2ESpec.scala` を作成
2. `PostgresContainerSupport` をミックスイン
3. `withContainers` + `buildApp` + `running(app)` でアプリケーションを構築
4. 前提データの作成 → リクエスト実行 → アサーション（レスポンス + DB） の順で実装
5. `afterEach` でテストデータのクリーンアップ

### 命名規則

| 要素 | 規則 | 例 |
|------|------|-----|
| クラス名 | `US{番号}{機能名}E2ESpec` | `US15HandlingEventE2ESpec` |
| テスト名 | `E{番号} {日本語シナリオ}` | `test("E01 荷役作業を登録できる")` |

---

## 9. Playwright E2E テストとの使い分け

| 観点 | コントローラー E2E（ScalaTestPlus-Play） | ブラウザ E2E（Playwright） |
|------|----------------------------------------|---------------------------|
| 実行速度 | 高速（JVM 内完結） | 低速（ブラウザ起動） |
| テスト範囲 | ルーティング + Controller + Form + 認証 + DB | UI + JS + API + DB |
| JavaScript 検証 | 不可 | 可能（htmx の動作） |
| ビジュアル検証 | 不可 | 可能（スクリーンショット） |
| CI 実行 | 容易（Docker のみ） | ブラウザインストールが必要 |
| 推奨用途 | バックエンドロジック・フォームフロー・認可の検証 | ユーザー操作フロー・画面遷移・htmx の検証 |

両方を組み合わせることで、バックエンドの信頼性とフロントエンドの動作保証を効率的にカバーします。テストピラミッド上、本テストは統合テスト層（約 25%）に位置づけられます（[テスト戦略](../design/test_strategy.md)）。

---

## トラブルシューティング

### Docker が起動していない

**問題**: `Could not connect to Docker daemon`

**解決策**: Docker Desktop が起動していることを確認する

```bash
docker info
```

### Testcontainers のポート競合

**問題**: `Bind for 0.0.0.0:5432 failed: port is already allocated`

**解決策**: Testcontainers はランダムポートを使用するため通常は発生しない。`docker-compose.yml` の PostgreSQL が起動中の場合は影響しないが、テスト側が固定ポートを参照していないか確認する。

```bash
docker compose down
```

### CSRF エラー（403）が返る

**問題**: フォーム POST が `403 Forbidden` になる

**解決策**: `CSRFTokenHelper.addCSRFToken(...)` でリクエストをラップしているか確認する。JSON API は CSRF 対象外設定（`play.filters.csrf.header.protectHeaders`）を確認する。

### テストが遅い

**問題**: E2E テストの実行に時間がかかる

**解決策**:

- `TestContainerForAll`（スイート単位のコンテナ共有）を使用しているか確認
- `GuiceApplicationBuilder` の設定をテストクラス間で統一し、アプリケーション構築のオーバーヘッドを抑える
- `sbt` の `Test / fork := true` と並列度設定を確認する

---

## 関連ドキュメント

- [アプリケーション開発環境セットアップ手順書](./dev_app_instruction.md)
- [Playwright E2E テストセットアップ手順書](./dev_e2e_instruction.md)
- [テスト戦略](../design/test_strategy.md)
- [技術スタック選定](../design/tech_stack.md)
