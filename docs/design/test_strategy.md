---
title: テスト戦略 - 国際貨物輸送管理システム（Flix 版）
description: DDD・ヘキサゴナル・CQRS を Flix で実装するためのテスト戦略。効果ハンドラによるテストダブル、カバレッジ計測不能への代替統制、テストレベルの定義を記録する。
published: true
date: 2026-07-31T00:00:00.000Z
tags: design, test-strategy, tdd, flix, effects, hexagonal, cqrs
---

# テスト戦略 - 国際貨物輸送管理システム（Flix 版）

## 1. 概要

### 1.1 目的

本ドキュメントは、国際貨物輸送管理システム（Flix 実装）のテスト戦略を定義する。以下の問いに常に回答できる状態を維持することを目的とする。

- 「この機能はどのテストレベルで保証されているか」
- 「何をどこまでテストすべきか」
- 「テストが失敗したとき、どこを修正すべきか」

### 1.2 基本方針

- **TDD を全開発プロセスで適用する**: レッド → グリーン → リファクタリングのサイクルを厳守する
- **テストをアーキテクチャに対応させる**: ヘキサゴナルの境界（= 効果宣言）を活かし、テスト可能性を設計段階で確保する
- **テストダブルは言語機能で作る**: モックライブラリを使わず、出力ポート（効果）に**インメモリ実装のハンドラ**を適用する
- **テストの重複を排除する**: 各テストレベルの責務を分離し、同一ロジックを複数レベルで重複検証しない
- **テストを実行可能なドキュメントとして扱う**: テストコードがシステムの振る舞いを説明する

### 1.3 Flix 固有の前提と、それがもたらす戦略上の差分

| Flix の事情 | 戦略への影響 |
| :--- | :--- |
| モックライブラリが存在しない | 効果ハンドラでテストダブルを作る。**むしろ Java 版より単純**になる |
| カバレッジ計測ツールが存在しない | 行カバレッジを品質ゲートに使えない。**ビジネスルール ⇄ テストのトレーサビリティ表**で網羅を担保する（6 章） |
| SonarQube に Flix アナライザがない | Flix コードの静的解析は `arch-lint`（自作）+ コンパイラ警告 + レビューで代替する |
| ArchUnit が使えない | `use` / `import` 宣言を走査する `arch-lint` を自作し、CI で強制する（3.3 節） |
| Testcontainers の相互運用コストが高い | 既定は H2（PostgreSQL 互換モード）。CI の日次ジョブのみ実 PostgreSQL に対して同じテストを流す |
| WireMock が使えない | JDK `HttpServer` によるスタブサーバで外部 API 契約テストを行う（4 章） |
| テスト関数は `Unit \ Assert` を返す | `@Test def name(): Unit \ Assert = Assert.assertEq(expected = ..., actual)` の形。真偽値を返す形式ではない（Flix 0.75.1 で確認） |
| パターンマッチの網羅性検査がある | 状態 `enum` にケースを追加すると**コンパイラが考慮漏れを検出する**。状態遷移テストの一部をコンパイル時に前倒しできる |
| 効果がシグネチャに現れる | 「このユースケースが触る副作用」がテスト前に型で分かる。テストで用意すべきハンドラの漏れが起きない |

### 1.4 アーキテクチャとテストレベルの対応

```plantuml
@startuml
!theme plain

package "ユニットテスト対象（効果なし）" {
  package "Domain Layer" {
    [Cargo 集約]
    [Voyage 集約]
    [HandlingActivity 集約]
    [Invoice 集約]
    [TrackingActivity 集約]
    [Estimate 集約]
    [値オブジェクト（Location 等）]
    [Html 画面関数]
  }
}

package "ユニットテスト対象（インメモリハンドラ）" {
  package "Application Layer" {
    [BookCargo / RouteCargo]
    [RegisterHandling]
    [TrackingQuery]
    [SettleInvoice]
  }
}

package "統合テスト対象" {
  package "Primary Adapters" {
    [Router / 認可ミドルウェア]
    [Web / REST ハンドラ]
  }
  package "Secondary Adapters（効果ハンドラ実装）" {
    [JdbcCargoRepo]
    [HttpExternalRouting]
  }
}

package "E2E テスト対象" {
  [Playwright シナリオ]
}

package "静的検査" {
  [arch-lint]
}

[Cargo 集約] --> [BookCargo / RouteCargo]
[BookCargo / RouteCargo] --> [Web / REST ハンドラ]
[Web / REST ハンドラ] --> [Playwright シナリオ]

note right of [値オブジェクト（Location 等）]
  効果を要求しない純粋関数は
  すべてユニットテストで保証する
end note

@enduml
```

| アーキテクチャ層 | テストレベル | 手段 |
| :--- | :--- | :--- |
| ドメイン層（集約・値オブジェクト・ドメインサービス） | ユニット | `flix test`。依存なし |
| 画面関数（`Html` を返す純粋関数） | ユニット | `Html.render` の結果を検証 |
| アプリケーション層（ユースケース） | ユニット | 出力ポートにインメモリ効果ハンドラを適用 |
| ルーティング・認可ミドルウェア | 統合 | JDK `HttpClient` で実リクエスト |
| 出力アダプタ（JDBC ハンドラ・SQL） | 統合 | H2 + Flyway（日次は実 PostgreSQL） |
| 外部 ACL ポート（5 件） | 契約 | JDK `HttpServer` スタブ |
| アーキテクチャ規約 | 静的検査 | `arch-lint` |
| ユーザーシナリオ全体 | E2E | Playwright |

---

## 2. テスト形状の選択

### 2.1 採用形状: ピラミッド型

```plantuml
@startditaa
        /------------------\
       /   E2E テスト (5%)  \
      /      Playwright      \
     /------------------------\
    /     統合テスト (25%)      \
   /  H2 + HttpClient + HttpServer\
  /       スタブ + arch-lint        \
 /----------------------------------\
/         ユニットテスト (70%)         \
/  flix test + インメモリ効果ハンドラ    \
/--------------------------------------\
@endditaa
```

**採用理由**

- **ドメイン層が厚い**: DDD により、`BookingStatus` の状態遷移、荷役妥当性検証（MISROUTED 判定）、割引・税計算など、外部依存なしでテスト可能なロジックが集中する
- **効果によりテストダブルのコストがほぼゼロ**: ポートのインメモリ実装を 1 度書けば全ユースケースで再利用でき、ユニットテストを厚くする障壁が低い
- **純粋関数が多い**: 画面生成すら純粋関数であり、ブラウザを起動せずに検証できる領域が Java 版より広い
- **コスト効率**: ユニットテストは高速（目標 30 秒以内）。E2E はフレイキーになりやすく最小限に留める

### 2.1.1 統合テストの比率を保つ方針（IT7 で決定）

IT6 のレビューで**統合テストの比率が 35%**（推奨 25%）に達していることが指摘された（M11）。
`VoyageHttpTest` だけでサーバを 21 回起動しており、Datalog による経路探索（US08）が
入るとさらに増える見込みだった。IT7 の着手前に方針を決めた。

| 方針 | 内容 |
| :--- | :--- |
| **組み合わせはドメイン単体で尽くす** | 経路の到達可能性・接続の時間整合・期限の境界・推奨順は `RouteFinderTest`（効果なし・サーバ起動なし）で網羅する |
| **統合テストは経路ごとに 1 本** | 「直行が出る」「積み替えが出る」「0 件の 2 種」「対象外」「認可」「導線」——**HTTP を通らないと確かめられないものだけ**を置く |
| **同じことを 2 度確かめない** | 積み替えの組み合わせを HTTP で網羅すると、ドメイン単体と同じ判定をサーバ起動のコストで繰り返すことになる |

IT7 では US08 に対しドメイン単体 22 件・統合 10 件とした。
**判断の基準は「サーバを起動しないと分からないか」**である。分かるならドメインへ置く。

> **比率そのものを目標にしない**。「統合テストを減らす」ために HTTP でしか
> 確かめられない検証を削ると、**数字は良くなるが安全性は下がる**。
> 減らすのは重複であって、カバレッジではない。

### 2.2 採用しない形状と理由

| 形状 | 採用しない理由 |
| :--- | :--- |
| **ダイヤモンド型**（統合テスト重視） | 単一プロセスのモノリスであり、サービス間契約の検証ニーズがない。統合テストを主軸にすると TDD サイクルが遅くなる |
| **逆ピラミッド型**（E2E 重視） | Playwright はヘッドレスブラウザを起動するためフレイキーになりやすく、htmx の 30 秒ポーリングを含む動的 UI は安定性確保が困難。フィードバックループが 15 分以上になる |

---

## 3. テストレベルの定義

### 3.1 ユニットテスト

#### 3.1.1 ドメイン層（効果なし）

集約・値オブジェクトは効果を一切要求しない。したがって準備が不要で、入力と出力だけで検証できる。

```flix
/// test/booking/CargoTest.flix
/// テスト関数は Unit \ Assert を返す（Bool ではない）
@Test
def testConfirmRequiresItinerary(): Unit \ Assert =
    let cargo = Fixtures.preliminaryCargo();
    Assert.assertEq(expected = Err(ItineraryNotAssigned), Cargo.confirm(cargo, Fixtures.timestamp()))

@Test
def testAssignItineraryRejectsLateArrival(): Unit \ Assert =
    let cargo = Fixtures.preliminaryCargo();          // 期限 2026-06-30
    let bad   = Fixtures.itineraryArrivingAt("2026-07-05");
    Assert.assertTrue(Result.isErr(Cargo.assignItinerary(cargo, bad, Fixtures.timestamp())))
```

| 観点 | 方針 |
| :--- | :--- |
| 状態遷移 | 許可される遷移・拒否される遷移の**両方**を書く。終端状態からの遷移拒否を必ず含める |
| 値オブジェクト | スマートコンストラクタの境界値（空文字・桁数・範囲外）を検証する |
| 網羅性 | `enum` にケースを追加した際、パターンマッチの網羅性検査がコンパイル時に漏れを検出する。テストはそれを補完する位置づけとする |
| 日付・時刻 | ドメイン層は `Timestamp` を引数で受け取り、現在時刻を取得しない。テストで時刻を完全に固定できる |

> **境界の落とし穴**: 期限が日付（DATE）、到着が時刻付き（TIMESTAMP）の場合、素朴な大小比較では
> 「期限当日の到着」を期限超過と誤判定する。日付単位で比較し、テストに**期限当日の時刻付き到着**を必ず含めること。

#### 3.1.2 アプリケーション層（インメモリ効果ハンドラ）

出力ポートが効果であるため、テストではハンドラを差し替えるだけでよい。モックライブラリは不要である。

```flix
/// test/support/InMemoryHandlers.flix
pub def withInMemoryCargoRepo(initial: List[Cargo], f: Unit -> a \ ef + CargoRepo): a \ ef - CargoRepo =
    let store = Ref.fresh(initial);
    run f() with handler CargoRepo {
        def findByTrackingId(id, k) =
            k(List.find(c -> Cargo.trackingId(c) == id, Ref.get(store)))
        def store(cargo, k) =
            Ref.put(upsert(cargo, Ref.get(store)), store); k()
        def listAll(k) = k(Ref.get(store))
    }

/// 発行イベントを記録するスパイ
pub def withRecordingEventBus(f: Unit -> a \ ef + EventBus): (a, List[DomainEvent]) \ ef

/// test/booking/RouteCargoTest.flix
@Test
def testRouteCargoPublishesCargoRouted(): Unit \ Assert =
    let published = withInMemoryCargoRepo(Fixtures.preliminaryCargo() :: Nil, () ->
        withFixedClock(Fixtures.timestamp(), () -> {
            let (result, events) = withRecordingEventBus(() ->
                BookCargoService.routeCargo(Fixtures.routeCommand())
            );
            Result.isOk(result) and List.exists(isCargoRouted, events)
        })
    );
    Assert.assertTrue(published)
```

| 観点 | 方針 |
| :--- | :--- |
| 検証対象 | ユースケースのオーケストレーション（取得 → ドメイン呼び出し → 保存 → イベント発行）と、失敗時に保存もイベント発行も起きないこと |
| 検証しない対象 | ドメインのビジネスルールそのもの（3.1.1 の責務） |
| スパイ | `RecordingEventBus` で「何が発行されたか」、`RecordingCargoRepo` で「何回 store されたか」を検証する |
| 時刻 | `Clock` 効果を固定ハンドラで差し替え、時刻依存ロジックを決定的にする |

#### 3.1.3 画面関数

```flix
@Test
def testShowRendersTrackingIdAndBadge(): Unit \ Assert =
    let html = Booking.Pages.show(Fixtures.bookingDetailView()) |> Html.render;
    Assert.assertTrue(String.contains("CARGO-001", html) and String.contains("badge bg-primary", html))

@Test
def testShowEscapesScriptTag(): Unit \ Assert =
    let view = Fixtures.bookingDetailViewWithName("<script>alert(1)</script>");
    let html = Booking.Pages.show(view) |> Html.render;
    Assert.assertTrue(not String.contains("<script>alert(1)</script>", html))
```

### 3.2 統合テスト

#### 3.2.1 永続化（JDBC ハンドラ + SQL）

```plantuml
@startuml
!theme plain
start
:H2 をインメモリ起動\n(MODE=PostgreSQL);
:Flyway でマイグレーション適用;
:JDBC 効果ハンドラを適用;
:テスト対象のユースケースを実行;
:SQL で結果を検証;
:トランザクションをロールバックして次テストへ;
stop
@enduml
```

| 観点 | 方針 |
| :--- | :--- |
| 対象 | `ResultSet` ⇄ ADT のマッピング、SQL の正確性、CQRS 読み取りクエリの JOIN、一意制約・楽観ロック |
| DB | 既定は H2（PostgreSQL 互換モード）。**CI の日次ジョブで実 PostgreSQL コンテナに対して同一テストを実行**し、方言差を検出する |
| 分離 | テストごとにトランザクションをロールバックする。並列実行時はスキーマを分ける |
| マイグレーション | テストも本番と同じ Flyway スクリプトを適用する。テスト専用 DDL を作らない |

> **H2 と PostgreSQL の差分リスク**: 型変換・日付関数・`ON CONFLICT` などで挙動が異なりうる。
> 「H2 で緑・本番で赤」を防ぐため、日次の実 PostgreSQL 実行を品質ゲートの一部とする。

#### 3.2.2 HTTP 経路（ルーティング・認可・PRG）

```flix
@Test
def testHandlerRoleCannotCreateBooking(): Unit \ Assert + IO =
    TestServer.withApp(app -> {
        let res = TestClient.post(app, "/api/v1/bookings", Fixtures.bookingJson(),
                                  TestClient.asRole(Handler));
        Assert.assertEq(expected = 403, Response.status(res))
    })
```

| 観点 | 方針 |
| :--- | :--- |
| 認可 | **全ルート × 全ロール**の可否をテーブル駆動で網羅する。ルーティング表からテストケースを生成し、ルート追加時にテスト漏れが起きない構造にする |
| 未認証 | 保護ルートへの未認証アクセスが `302 /login` になること、公開ルートは `200` になること |
| CSRF | トークンなしの `POST` が `403` になること |
| PRG | フォーム送信成功時の `302` とリダイレクト先 |
| バリデーション | 不正入力時に `400` とフィールドエラーが返ること |

### 3.3 アーキテクチャテスト（`arch-lint`）

`ops/scripts/arch-lint`（Node.js）で Flix ソースの `use` / `import` / 特定構文を走査し、以下の規約を検査する。違反は CI で失敗させる。

`arch-lint` 自体にも回帰テストを用意する。`ops/scripts/arch-lint/fixtures/` に規約ごとの
**違反例（負例）と適合例（正例）**を配置し、CI で「負例をすべて検出し、正例を 1 件も誤検出しない」ことを検証する。
検査器のバグによるサイレントな規約違反を防ぐためであり、これを欠くと `arch-lint` は保証にならない。

> **規約の正典**: 検出方法・既知の例外・メタテストの仕様は
> [arch-lint 規約仕様](arch_lint_rules.md) を正典とする。IT1 のレビューで規約 5 の定義に
> 矛盾が見つかったため、実装前に確定させた（IT2 タスク 0.2）。本表は概要である。

| # | 規約 | 検出方法 |
| :--- | :--- | :--- |
| 1 | `domain/**` が `infrastructure/**`・`interfaces/**` を参照しない | `use` 宣言の走査 |
| 2 | `domain/**` が `java.**` を参照しない | `import java.` の走査 |
| 3 | `application/**` が `infrastructure/**` を参照しない | `use` 宣言の走査 |
| 4 | 異なる Bounded Context 間の直接参照がない（`Shared`・ACL・イベント経由のみ） | モジュールパスの照合 |
| 5 | 効果ハンドラの**合成**が `infrastructure/runtime/**` とテスト以外に出現しない | 構文パターンの走査 |
| 6 | `domain/**`・`application/**`・`interfaces/**` に `run ... with handler` が出現しない | 構文パターンの走査 |
| 7 | `Html.RawUnsafe` の使用箇所が許可リストに含まれる | 呼び出し箇所の列挙と突合 |
| 8 | `<form>` を `Element("form", ...)` で直接構築していない（`Components.form` を使う） | 構文パターンの走査 |
| 9 | SQL 文字列に変数を補間していない（`"SELECT ... ${x}"`） | 文字列補間パターンの走査 |
| 10 | `shared` が Bounded Context を参照していない（合成ルートを除く） | モジュールパスの照合 |

> **規約 5・6 の分離**: IT1 のレビューで、旧規約 5（`run ... with` の出現箇所）が
> アダプタ側のハンドラ定義（`withJdbcReadDb`）まで違反にしてしまうことが判明した。
> 「ハンドラの**定義**」と「複数ハンドラの**合成**」を分け、規約 5（合成の位置）と
> 規約 6（レイヤ違反としてのハンドラ適用）に再定義している。これに伴い旧規約 6（SQL）は 9 へ移した。

> 新しい Bounded Context を配線した際は、`arch-lint` の許可リスト（合成ルートからの依存）を同じコミットで更新する。
> 更新漏れは CI 失敗として即座に検出される。

### 3.4 E2E テスト（Playwright）

| 項目 | 内容 |
| :--- | :--- |
| 実行対象 | `flix build-jar` で生成した JAR を Docker Compose（アプリ + PostgreSQL）で起動 |
| シナリオ数 | 5 本以内に抑える |
| 対象シナリオ | ①見積作成 → 予約登録、②経路設計者による経路割り当て → 予約確定、③荷役登録 → 追跡ステータス反映（htmx ポーリング含む）、④公開追跡照会（未認証）、⑤配送完了 → 精算書発行 → 支払記録 |
| 安定化 | 固定シードデータを投入する。時刻依存は環境変数でアプリの `Clock` ハンドラを固定時刻モードに切り替える |
| 失敗時 | スクリーンショット・トレースを Artifact として保存する |

---

## 4. 外部 API 契約テスト（JDK `HttpServer` スタブ）

WireMock の代替として、JDK 標準の `HttpServer` でスタブを立て、ACL ポートの HTTP 契約を固定する。
外部依存ライブラリを追加しないため、脆弱性・保守リスクを持ち込まない。

### 4.1 シナリオ一覧

| ポート（効果） | 正常シナリオ | 異常シナリオ |
| :--- | :--- | :--- |
| `ExternalRouting` | ルート検索 → 3 候補返却 | 接続タイムアウト → 過去実績データにフォールバック |
| `CustomsClearance` | 通関申請 → `CLEARED` | `HELD` ステータス → 例外イベント発行 |
| `PaymentGateway` | 支払処理 → `CONFIRMED` | 決済失敗 → `OVERDUE` 状態遷移 |
| `PortManagement` | 港湾入港通知 → 受理 | 港湾満杯 → 代替港提案 |
| `Notification` | 通知送信 → `202 Accepted` | 通知失敗 → ログ記録のみ（非クリティカル） |

### 4.2 スタブサーバの実装方針

```flix
/// test/support/StubServer.flix
/// ルート → 応答（ステータス・ボディ・遅延）の対応表を受け取り、一時ポートで起動する
pub def withStub(stubs: List[(String, StubResponse)], f: Int32 -> a \ ef + IO): a \ ef + IO

@Test
def ルート検索で3候補が返却される(): Bool \ IO =
    withStub(("/api/routes/search", StubResponse.json(200, Fixtures.threeRoutesJson())) :: Nil, port -> {
        let routes = HttpExternalRouting.searchRoutes(baseUrl(port), Fixtures.routeSearchRequest());
        List.length(routes) == 3
    })

@Test
def 接続タイムアウト時に過去実績データへフォールバックする(): Bool \ IO =
    withStub(("/api/routes/search", StubResponse.delayed(6000)) :: Nil, port -> {
        // タイムアウト閾値 5 秒を超過させる
        let routes = HttpExternalRouting.searchRoutes(baseUrl(port), Fixtures.routeSearchRequest());
        not List.isEmpty(routes) and List.forAll(RouteCandidate.isFallback, routes)
    })
```

| 観点 | 方針 |
| :--- | :--- |
| 検証内容 | 送信リクエスト（パス・メソッド・ボディの必須項目）と、応答からドメイン型へのデコード結果の両方 |
| 異常系 | タイムアウト・4xx・5xx・不正 JSON の 4 種を各ポートで最低 1 本ずつ用意する |
| ACL の責務 | 外部システムのモデルがドメインに漏れないこと（`RouteCandidate` 等の自ドメイン型に変換されていること）を検証する |
| 適用先 | ACL アダプタ（効果ハンドラ実装）に対して行う。アプリケーション層のテストではスタブハンドラを使い、HTTP を経由しない |

---

## 5. ユーザーストーリーとテストのトレーサビリティ

`docs/requirements/user_story.md` の US01-US27 を正典とする。**ストーリーの追加・番号変更があった場合、本表を同一の変更で更新する**。

| US | タイトル | 優先度 | ユニットテスト | 統合 / 契約テスト | E2E |
| :--- | :--- | :--: | :--- | :--- | :--- |
| US01 | 輸送見積を作成する | 高 | `Estimate` 集約・`RouteCandidate`・見積有効期限 | `JdbcEstimateRepo`・見積作成 API | **シナリオ①** |
| - | （見積承認 → 予約の自動引き継ぎ） | - | 将来イテレーション。現時点では対応ストーリーなし | - | - |
| US02 | 荷主を登録する | 高 | `Shipper` 集約・値オブジェクトの境界値・重複判定の順序・画面の描画 | `JdbcShipperRepo`（往復・一意制約）・荷主登録 API（PRG・CSRF・重複導線） | シナリオ①（**IT5 予定**） |
| US03 | 法人荷主を登録する | 高 | 法人区分の必須項目・割引率の値域（0 / 30% / 範囲外） | 荷主登録 API（法人項目の検証）・htmx 断片の切替 | - |
| US04 | 貨物予約を登録する | 高 | `Cargo` 集約・`BookingStatus` 初期状態・港と日付の妥当性・ACL 経由の荷主解決の順序 | `JdbcCargoRepo`・予約登録 API（マスタ外の港・実在しない日付・未知の種別） | シナリオ①（**IT5 予定**） |
| US05 | 危険物・冷凍貨物の予約を登録する | 高 | `CargoType` 値オブジェクト・特殊要件の必須検証 | 予約登録 API（必須項目のバリデーション） | - |
| US06 | 予約情報を経路設計者に引き渡す | 高 | `Cargo` の引き渡し遷移（`ROUTE_PROPOSED`） | 引き渡し API・認可（`Sales` のみ） | - |
| US07 | 航海スケジュールを検索する | 高 | `Voyage` 集約・`Schedule` 値オブジェクト | `JdbcVoyageRepo`・航路検索クエリ | - |
| US08 | 経路候補を算出する | 高 | 到達可能性判定（Datalog）・積み替え整合性検証 | `ExternalRouting` スタブ（正常・タイムアウト） | **シナリオ②** |
| US09 | 経路を選択・確定する | 高 | `Cargo.assignItinerary`・端点/期限の検証 | 経路割当 API・認可（`Router` のみ） | **シナリオ②** |
| US10 | 経路条件を調整して再算出する | 高 | 条件変更後の候補再生成 | 再算出 API・候補 0 件時の応答 | - |
| US11 | 経路情報を予約に紐付ける | 高 | `CargoItinerary` の整合（Leg の連続性） | `JdbcCargoRepo`（旅程の永続化） | - |
| US12 | 確定経路を荷主に通知する | 高 | `CargoRouted` イベント生成 | `Notification` スタブ | - |
| US13 | 予約を確定する | 高 | `Cargo.confirm`・`CONFIRMED` 遷移・経路未割当時の拒否 | 確定 API・`CargoBooked` イベント配信 | **シナリオ②** |
| US14 | 追跡番号を発行する | 高 | `TrackingId` 生成規則・一意性 | 追跡番号の永続化・一意制約 | - |
| US15 | 荷役作業を記録する | 高 | `HandlingActivity` 集約・MISROUTED 判定 | `JdbcHandlingRepo`・荷役登録 API | **シナリオ③** |
| US16 | 引取作業を記録する | 高 | `HandlingType.Claim`・**通関未完了時の引取拒否** | 引取 API・`CustomsClearance` スタブ | - |
| US17 | 貨物状態を手動更新する | 高 | `TransportStatus` 遷移（9 値）の可否 | 手動更新 API・認可（`Tracker` のみ） | - |
| US18 | 追跡情報を照会する | 高 | `TrackingPublicPagesTest`（21 件）・`TrackingQueryTest`（3 件）・`ComponentsTest`（15 件）・`LayoutTest`（10 件） | `JdbcReadDbTest`（3 件）・`PublicTrackingHttpTest`（9 件） | シナリオ④（**IT3 予定・未実装**） |
| US19 | 遅延例外を処理する | 高 | エスカレーション判定（48 時間境界） | 例外処理 API・`Notification` スタブ | - |
| US20 | 破損・紛失例外を処理する | 高 | `ExceptionType`・LOST の即時エスカレーション | 例外処理 API・`Notification` スタブ | - |
| US21 | 輸送料金を算出する | 中 | `Invoice` 集約・`Money`・消費税計算・端数処理 | `JdbcInvoiceRepo`・料金算出 API | - |
| US22 | 法人割引を適用する | 中 | `DiscountPolicy`・割引率計算・有効期限判定 | 割引適用 API | - |
| US23 | 精算を処理する | 中 | `Invoice.settle`・`PaymentStatus` 遷移・`OVERDUE` 判定 | `PaymentGateway` スタブ（正常・失敗） | **シナリオ⑤** |
| US24 | 航海スケジュールを新規登録する | 高 | `Voyage` 集約・`CarrierMovement` の時系列整合 | 航路登録 API・認可（`Router` のみ） | - |
| US25 | 既存航海スケジュールを更新する | 高 | スケジュール変更時の影響判定（紐付く旅程の再検証） | 航路更新 API・楽観ロック | - |
| US26 | システムにログインする | 高 | パスワード検証・ロール解決 | ログイン API・**セッション ID 再生成**・アカウントロック | **シナリオ①〜⑤の前提** |
| US27 | システムからログアウトする | 中 | - | ログアウト API・セッション無効化 | - |

> **列の意味**: 「ユニットテスト」「統合 / 契約テスト」に書くのは**テスト観点**であり、
> テスト関数名や件数ではない。件数はテストを足すたびに陳腐化する。
> 実装済みテストとの対応付けは [ビジネスルール ⇄ テスト トレーサビリティ](business_rule_traceability.md) が担う。

**運用規約**:

- 本表はカバレッジ計測の代替統制として機能する。**ユーザーストーリー完了の定義（DoD）に「本表の該当行を埋めること」を含める**
- CI で `docs/requirements/user_story.md` の US 番号一覧と本表の US 番号一覧を突合し、**欠番があれば失敗させる**（`ops/scripts/trace-lint`）。表の更新忘れを人手のレビューに頼らない
- 「-」は当該テストレベルでの検証が不要であることを意味する。未着手は空欄とし、両者を区別する

---

## 6. 品質メトリクスと代替統制

### 6.1 カバレッジ計測不能への対処

Flix にはカバレッジツールが存在しないため、行カバレッジ率を品質ゲートに用いない。代わりに以下 3 点で網羅性を担保する。

| 統制 | 内容 | 判定 |
| :--- | :--- | :--- |
| **ビジネスルールの網羅** | `docs/design/business_rule_traceability.md` に「コンテキスト / ルール / テスト関数名 / 状態」の表を維持する。[ドメインモデル設計](domain-model.md) の「ビジネスルール」節が入力元 | 未対応ルールがゼロであること。イテレーションクローズ時にレビューする |
| **ストーリートレーサビリティ** | 5 章の表を各イテレーションで更新する | 完了ストーリーに空欄がないこと |
| **状態遷移の網羅** | 状態 `enum` の各ケースについて、遷移可否テストが存在する | パターンマッチ網羅性検査（コンパイラ）+ 遷移表テスト |

### 6.2 品質ゲート条件

| 条件 | 基準 | 適用対象 | 判定手段 |
| :--- | :--- | :--- | :--- |
| 全テスト成功 | 100% | プロジェクト全体 | `flix test` |
| アーキテクチャ規約違反 | 0 件 | Flix ソース全体 | `arch-lint` |
| コンパイラ警告 | 0 件 | Flix ソース全体 | `flix build`（警告をエラー扱い） |
| ビジネスルール未テスト | 0 件 | ドメイン層 | 6.1 の一覧レビュー |
| 依存脆弱性（High 以上） | 0 件 | `flix.toml` の Maven 依存・Docker イメージ | Trivy |
| JS の静的解析 | 違反 0 件 | `ops/scripts/**`・`gulpfile.js`（`arch-lint`・`trace-lint` を含む） | **ESLint**（`npm run script:lint`。**Flix コードは解析できない**。下記参照） |
| E2E シナリオ | 全 5 本成功 | main ブランチ | Playwright |

品質ゲートが失敗した場合、PR のマージをブロックする。

### 6.3 ドキュメント整合の機械検査（`trace-lint`）

トレーサビリティ表が網羅性の唯一の物差しである以上、**表が実態とずれることは
カバレッジ計測が壊れているのと同義**である。IT2 のレビューで実際に 3 件のずれが
見つかったため、人手の維持をやめて CI で検査する（`npm run trace:lint`）。

| # | 検査内容 |
| :--: | :--- |
| 1 | すべての US がストーリートレーサビリティ表（5 章）に載っている |
| 2 | 表に載っている US が [ユーザーストーリー](../requirements/user_story.md) に実在する（削除・番号変更の検出） |
| 3 | ビジネスルール対応表が**「済」として挙げる**テスト関数が実在する |
| 4 | **ソースのコメントが引用するテスト関数**（`Xxx.testYyy` 形式）が実在する（`src` / `test` の両方） |

検査 4 は IT4 のふりかえり P3（存在しないテストを根拠にコメントを書いた）への対策である。
コメントが「検証済み」と読ませると、**実際には誰も守っていない前提が守られているように見える**。
人の規律（引用時に `grep` で確認する）は忘れるため機械に任せる。
モジュール名付きの形に限るのは、`testYyy` 単独が散文と区別できず誤検出で検査自体が
信用されなくなるためである。

検査 3 が「済」の行に限るのは、未着手・実装中の行に書かれたテスト名は
「これから書く予定」であり、実在しないのが正常だからである。
検査したいのは**「済と書いてあるのにテストがない」という偽の緑**である。

### 6.4 運用スクリプトの静的解析（SonarQube から ESLint へ）

**SonarQube は Flix を解析できない**（サポート言語に含まれない）。したがって
アプリケーション本体の品質ゲートとしては機能せず、対象は **Node.js の運用スクリプト**に限られていた。

> **IT5 での決着（ふりかえり Try T9）**: SonarQube を**品質ゲートから外し、ESLint に置き換えた**。
>
> 理由は検査内容ではなく**実行されるかどうか**である。SonarQube はローカルサーバの起動と
> トークン（`.env` の `SONAR_TOKEN`）を要するため、**IT3・IT4 と 2 イテレーション連続で
> 実行されなかった**。通していない条件を「品質ゲート」と呼び続けると、定義が形骸化する。
> IT5 で 3 回目の未実施にする前に決める、というのが Try T9 だった。
>
> ESLint は同じ対象を、サーバもトークンも要らず **CI で必ず走る**形で検査する
> （`npm run script:lint`）。検査範囲を狭めたのではなく、**実際に通す**ようにした。
> 導入時に 5 件の指摘（`consistent-return` 3 件・未使用変数 2 件）が実際に見つかっている。
>
> `sonar-project.properties` と `gulp sonar-local:*` は**残す**。手元で深い分析を
> したいときに使える手段を消す理由はない。ただし**品質ゲートの条件ではない**。

`arch-lint` を対象に含めるのは、検査器のバグが「検査をパスしているのに違反している」状態を
生むためである（IT2 で実際に発生した）。**検査器のコードこそ静的解析にかける価値がある。**

Flix 本体の品質は次の 3 つで担保する。SonarQube の代替ではなく、**これが本体の統制である**。

| 統制 | 対象 |
| :--- | :--- |
| `arch-lint`（規約 10 件 + メタテスト 25 件） | アーキテクチャ規約の違反 |
| セキュリティ回帰テスト（8.4） | 自作の認証・セッション・CSRF |
| ビジネスルール ⇄ テスト トレーサビリティ（6.1） | ドメインルールの網羅 |

> **IT2 クローズ時の「SonarQube 未実施＝未達」について**: 評価としては正しいが、
> 未達だったのは JS 側の静的解析であり、Flix 本体の品質判断が欠けていたわけではない。
> IT3 で設定を用意したが実行されず、IT5 で ESLint へ置き換えた（上記）。

---

## 7. CI/CD とのテスト連携

### 7.1 ステージ別テスト戦略

| ステージ | テスト種別 | 目標時間 | 失敗時の扱い |
| :--- | :--- | :--- | :--- |
| コミット（ローカル） | ユニットテスト + `arch-lint`（`npm run dev:verify`） | **< 60 秒** | コミット前に修正 |
| PR | ユニット + 統合（H2）+ 契約 + `arch-lint` + Trivy | **< 8 分** | PR マージ不可 |
| main マージ後 | E2E（Playwright） | **< 15 分** | 通知（ホットフィックス優先） |
| 日次 | 全テスト（統合を**実 PostgreSQL** で再実行） | **< 20 分** | 通知。方言差の検出 |
| リリース | 全テスト（統合を**実 PostgreSQL** で実行）+ 性能テスト | **< 30 分** | リリース停止 |

> Flix のコンパイルは Java より時間がかかる場合がある。CI ではビルドキャッシュ（`~/.flix`・`lib/`）を必ず有効化する。

### 7.2 パイプライン図

```plantuml
@startuml
!theme plain

|ローカル|
start
:git commit;
:flix test（ユニット）\n< 30 秒;
:arch-lint\n< 10 秒;
if (成功?) then (yes)
  :コミット完了;
else (no)
  :修正してリトライ;
  stop
endif

|PR|
:git push / PR 作成;
fork
  :ユニットテスト\n< 3 分;
fork again
  :統合テスト（H2 + Flyway）\n+ HTTP 経路テスト\n< 4 分;
fork again
  :契約テスト（HttpServer スタブ）\n< 2 分;
fork again
  :arch-lint + Trivy\n< 1 分;
end fork
:SonarQube 解析\n（SQL / Dockerfile / JS）;
if (全チェック成功?) then (yes)
  :PR マージ許可;
else (no)
  :PR マージ不可;
  stop
endif

|main ブランチ|
:main マージ;
:E2E テスト（Playwright）\n< 15 分;
if (E2E 成功?) then (yes)
  :デプロイ続行;
else (no)
  :通知・ホットフィックス対応;
  stop
endif

|日次|
:統合テストを実 PostgreSQL で再実行;
if (成功?) then (yes)
  :方言差なし;
else (no)
  :Issue 起票（H2 との差分）;
  stop
endif

@enduml
```

---

## 8. TDD 開発ワークフロー

### 8.1 インサイドアウト TDD（バックエンド）

ドメイン層から外側へ向かって開発する。効果宣言を先に置くことで、実装なしにアプリケーション層のテストが書ける。

```plantuml
@startuml
!theme plain

start
:ユーザーストーリーと受入基準を確認する;

group Step 1: ドメインモデルのユニットテスト
  :【RED】集約・値オブジェクトのテストを書く\n（Given-When-Then）;
  :【GREEN】ADT とスマートコンストラクタ、\n状態遷移関数を最小実装する;
  :【REFACTOR】重複除去・命名改善;
end group

group Step 2: ポート（効果）の宣言
  :必要な出力ポートを eff として宣言する;
  :テスト用のインメモリハンドラを用意する;
end group

group Step 3: アプリケーション層のユニットテスト
  :【RED】ユースケースのテストを書く\n（インメモリハンドラを適用）;
  :【GREEN】ユースケース関数を実装する\n（効果を要求するだけ。実装は知らない）;
  :【REFACTOR】オーケストレーションを整理;
end group

group Step 4: アダプタの統合・契約テスト
  :【RED】JDBC ハンドラ / HTTP ハンドラ /\n ルーティングのテストを書く;
  :【GREEN】H2・HttpClient・HttpServer スタブで実装する;
  :【REFACTOR】SQL 最適化・エラーハンドリング整理;
end group

:arch-lint で規約検証;
:合成ルートに配線し、全テスト GREEN を確認してコミット;
stop

@enduml
```

> **アウトサイドイン TDD（画面主導）** を使う局面では、画面関数（純粋）→ ルーティング → ユースケース → ドメインの順に降りる。
> どちらを採るかは局面ごとに開発戦略で定める。

### 8.2 必ず TDD を適用するビジネスルール

#### `Cargo` の `BookingStatus` 状態遷移

```
PRELIMINARY → ROUTE_PROPOSED → CONFIRMED → TRACKING_ISSUED
    → IN_TRANSIT → DELIVERED → SETTLED
    ↘ MISROUTED（異常系）
    ↘ CANCELLED（キャンセル）
```

テスト観点：許可される遷移／拒否される遷移の両方、終端状態（`SETTLED`・`CANCELLED`）からの遷移拒否。
遷移表を `List[(BookingStatus, BookingEvent, Result[...])]` として定義し、テーブル駆動で網羅する。

#### `HandlingActivity` の MISROUTED 判定

```flix
@Test
def testHandlingAtUnplannedPortMarksMisRouted(): Unit \ Assert =
    let cargo    = Fixtures.cargoRoutedTokyoToHamburg();
    let activity = Fixtures.handlingAt("SGSIN", Load);   // 旅程に含まれない港
    Assert.assertEq(
        expected = Ok(MisRouted),
        Result.map(Cargo.status, Cargo.applyHandling(cargo, activity))
    )
```

#### `Invoice` の料金計算（法人割引・消費税）

```flix
@Test
def testCorporateDiscountAndTax(): Unit \ Assert =
    let base     = Money.jpy(100_000);
    let discount = DiscountPolicy.corporate(Percentage.of(10));
    match Invoice.calculate(base, discount, TaxRate.standard()) {
        case Err(e)  => Assert.fail("計算に失敗した: ${e}")
        case Ok(inv) =>
            Assert.assertEq(expected = Money.jpy(90_000), Invoice.netAmount(inv));
            Assert.assertEq(expected = Money.jpy(9_000),  Invoice.taxAmount(inv));
            Assert.assertEq(expected = Money.jpy(99_000), Invoice.totalAmount(inv))
    }
```

端数処理（切り捨て・四捨五入）と通貨混在の拒否を必ずテストに含める。金額は整数最小単位で保持する。

#### `TrackingExceptionEvent` のエスカレーション判定

遅延 48 時間を閾値とし、閾値ちょうど・閾値未満・閾値超過の 3 ケースを必ず書く。

### 8.3 Bounded Context 別の TDD 優先順位

| Bounded Context | 最初にテストするルール | 理由 |
| :--- | :--- | :--- |
| Booking | `BookingStatus` 遷移 | 最も複雑な状態機械。影響範囲が大きい |
| Estimation | 見積の作成とルート候補生成・有効期限判定 | 業務フローの起点。見積から予約への引き継ぎは将来イテレーション |
| Routing | 到達可能性判定（Datalog）と `ExternalRouting` のフォールバック | 外部依存は本番障害の主要因。Datalog は本実装固有で検証が必要 |
| Tracking | CQRS 読み取りクエリの結果整合と性能 | 30 秒ポーリングの負荷を事前に確認する |
| Handling | MISROUTED 判定 | 荷役記録ミスは重大な運用インシデントになる |
| Billing | 割引・消費税計算をテーブル駆動で網羅 | 金額計算のバグは法的リスクを伴う |
| Shared | `Location`（UN/LOCODE）のバリデーション | 全コンテキストが共有し、影響範囲が広い |

### 8.4 セキュリティ回帰テスト（必須）

認証・セッション・CSRF を自作するため、以下を回帰テストとして固定する（[技術スタック選定](tech_stack.md) のリスク補償）。

| 項目 | 検証内容 |
| :--- | :--- |
| パスワード | 平文がログ・レスポンス・DB に現れないこと、BCrypt 検証が誤ったパスワードを拒否すること |
| セッション | ログイン成功時にセッション ID が再生成されること（セッション固定攻撃対策）、ログアウトで無効化されること、タイムアウトが効くこと |
| Cookie | `HttpOnly` / `Secure` / `SameSite` 属性が付与されていること |
| CSRF | トークンなし・他セッションのトークンでの `POST` が拒否されること |
| 認可 | 全ルート × 全ロールの可否表（3.2.2）に一致すること |
| エスケープ | 主要画面でスクリプト混入入力がエスケープされること |
| SQL | 入力値に `'` や `;` を含めても構文エラー・情報漏洩が起きないこと |
