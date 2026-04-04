---
title: テスト戦略 - 国際貨物輸送管理システム
description: DDD・ヘキサゴナル・CQRS アーキテクチャに基づくテスト戦略。ピラミッド型テスト形状とテストレベルの定義、カバレッジ目標、ツール選定を記録する。
published: true
date: 2026-03-31T00:00:00.000Z
tags: design, test-strategy, tdd, hexagonal, cqrs
---

# テスト戦略 - 国際貨物輸送管理システム

## 1. 概要

### 1.1 目的

本ドキュメントは、国際貨物輸送管理システムにおけるテスト戦略を定義する。テスト戦略を事前に策定し、以下の問いに常に回答できる状態を維持することを目的とする。

- 「この機能はどのテストレベルで保証されているか」
- 「何をどこまでテストすべきか」
- 「テストが失敗したとき、どこを修正すべきか」

### 1.2 基本方針

- **TDD（テスト駆動開発）を全開発プロセスで適用する**: レッド → グリーン → リファクタリングのサイクルを厳守する
- **テストをアーキテクチャに対応させる**: ヘキサゴナルアーキテクチャの境界（ポート）を活かし、テスト可能性を設計段階で確保する
- **テストの重複を排除する**: 各テストレベルの責務を明確に分離し、同一ロジックを複数レベルで重複検証しない
- **テストを実行可能なドキュメントとして扱う**: テストコードがシステムの振る舞いを説明する

### 1.3 アーキテクチャとテスト戦略の対応関係

```plantuml
@startuml
!theme plain

package "ユニットテスト対象" {
  package "Domain Layer" {
    [Cargo 集約]
    [Voyage 集約]
    [HandlingActivity 集約]
    [Invoice 集約]
    [TrackingActivity 集約]
    [値オブジェクト（Location 等）]
  }
  package "Application Layer" {
    [BookingService]
    [RoutingService]
    [TrackingQueryService]
    [BillingService]
  }
}

package "統合テスト対象" {
  package "Infrastructure Layer（入力側アダプター）" {
    [BookingController]
    [TrackingController]
  }
  package "Infrastructure Layer（出力側アダプター）" {
    [CargoRepositoryImpl]
    [ExternalRoutingAdapter]
  }
}

package "E2E テスト対象" {
  [Playwright シナリオ]
}

[Cargo 集約] --> [BookingService]
[BookingService] --> [BookingController]
[BookingController] --> [Playwright シナリオ]

note right of [値オブジェクト（Location 等）]
  ヘキサゴナルアーキテクチャ境界ごとに
  テストレベルを分離する
end note

@enduml
```

ヘキサゴナルアーキテクチャの各層は以下のテストレベルに対応する。

| アーキテクチャ層 | テストレベル | 理由 |
|---|---|---|
| ドメイン層（集約・値オブジェクト・ドメインサービス） | ユニットテスト | 外部依存ゼロ。純粋なビジネスロジック |
| アプリケーション層（ユースケースサービス） | ユニットテスト（ポートをモック） | ポートへの委譲とオーケストレーションを検証 |
| 入力側アダプター（Controller） | 統合テスト（MockMvc） | HTTP マッピングとバリデーションを検証 |
| 出力側アダプター（Repository） | 統合テスト（Testcontainers） | SQL クエリの正確性を実 DB で検証 |
| 外部 ACL ポート（5 件） | 統合テスト（WireMock） | 外部システムとの契約を検証 |
| ユーザーシナリオ全体 | E2E テスト（Playwright） | クリティカルパスの品質保証 |

---

## 2. テスト形状の選択

### 2.1 採用形状: ピラミッド型

```plantuml
@startditaa
        /------------------\
       /   E2E テスト (5%)  \
      /   Playwright 1.44+  \
     /----------------------\
    /    統合テスト (25%)     \
   /  Testcontainers + MockMvc \
  /  + WireMock + ArchUnit     \
 /----------------------------\
/       ユニットテスト (70%)    \
/   JUnit 5 + Mockito + AssertJ \
/--------------------------------\
@endditaa
```

**採用理由**:

- **ドメイン層が厚い**: DDD を採用しており、Cargo・Voyage・HandlingActivity・Invoice の各集約にビジネスロジックが集中する。BookingStatus の 8 値遷移、荷役妥当性検証（MISROUTED 判定）、法人割引計算など、外部依存なしでテスト可能なロジックが多い
- **ヘキサゴナルアーキテクチャによる高いテスト可能性**: ドメイン層とインフラ層の境界がポートで分離されており、モックの差し替えが容易。ユニットテストが書きやすい設計になっている
- **CQRS による読み取りモデルの分離**: TrackingContext の読み取りクエリはドメインロジックを持たず、統合テストで Repository を直接検証するだけで十分
- **コスト効率**: ユニットテストは実行が高速（< 30 秒）でメンテナンスコストが低い。E2E テストはフレイキーになりやすく、最小限にとどめることで CI の安定性を維持する

### 2.2 採用しない形状と理由

| 形状 | 採用しない理由 |
|---|---|
| **ダイヤモンド型**（統合テスト重視） | 本システムは単一モノリス（ヘキサゴナル）で構成されており、マイクロサービス間の契約検証ニーズがない。統合テストを主軸にするとテスト実行時間が増大し、TDD サイクルが遅くなる |
| **逆ピラミッド型**（E2E 重視） | Playwright テストはヘッドレスブラウザを起動するためフレイキーになりやすく、htmx の 30 秒ポーリングを含む動的 UI はテストの安定性確保が困難。E2E を主軸にするとフィードバックループが 15 分以上になる |

---

## 3. テストレベルの定義

### 3.1 ユニットテスト（Unit Test）

#### 責務・検証対象

- **ドメイン層**: 集約の状態遷移・不変条件・ビジネスルール、値オブジェクトの等価性・バリデーション、ドメインサービスのロジック
- **アプリケーション層**: ユースケースサービスのオーケストレーション（ポートはモック）

#### カバレッジ目標

| 対象 | 行カバレッジ | 分岐カバレッジ |
|---|---|---|
| ドメイン層 | **85% 以上** | **80% 以上** |
| アプリケーション層 | **80% 以上** | **75% 以上** |

#### 使用ツール

- **JUnit 5**: テストフレームワーク（`@Test`, `@ParameterizedTest`）
- **Mockito 5**: ポートインターフェースのモック（`@Mock`, `@InjectMocks`）
- **AssertJ 3**: 流暢なアサーション（`assertThat(...).isEqualTo(...)`）

#### 実行タイミング

- **ローカル**: すべてのコミット時（目標 **30 秒以内**）
- **PR**: 自動実行（コミットプッシュ時）
- **CI**: GitHub Actions の `unit-test` ジョブ

#### 除外対象

- インフラ層（MyBatis マッパー、HTTP クライアント）— 統合テストで担保する
- DTO / レコードクラス — データ保持のみでロジックがない
- Spring Boot アプリケーションコンテキスト — `@SpringBootTest` はユニットテストに**使用しない**

#### 実装例: Cargo 集約の BookingStatus 遷移テスト

```java
class CargoBookingStatusTest {

    @Test
    void 予約が確定できる() {
        // Given: ルートが割り当て済みの貨物
        var cargo = CargoFixture.withRouteAssigned();

        // When: 予約を確定する
        cargo.confirmBooking();

        // Then: ステータスが CONFIRMED に遷移する
        assertThat(cargo.getBookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void ルート未割り当て状態で予約確定しようとすると例外が発生する() {
        // Given: ルートが未割り当ての貨物
        var cargo = CargoFixture.preliminary();

        // When & Then: 不変条件違反で例外が発生する
        assertThatThrownBy(cargo::confirmBooking)
                .isInstanceOf(BookingDomainException.class)
                .hasMessageContaining("ルートが割り当てられていません");
    }

    @Test
    void 危険物の取扱不可港にルートを割り当てると例外が発生する() {
        // Given: 危険物フラグが立った貨物と危険物取扱不可の港を経由するルート
        var cargo = CargoFixture.hazardous();
        var prohibitedRoute = RouteFixture.viaHazardousProhibitedPort();

        // When & Then: ドメインルール違反で例外が発生する
        assertThatThrownBy(() -> cargo.assignRoute(prohibitedRoute))
                .isInstanceOf(HazardousCargoRoutingException.class);
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class,
                names = {"CONFIRMED", "COMPLETED", "CANCELLED"},
                mode = EnumSource.Mode.INCLUDE)
    void 終端状態からの遷移は許可されない(BookingStatus terminalStatus) {
        // Given: 終端ステータスの貨物
        var cargo = CargoFixture.withStatus(terminalStatus);

        // When & Then: ステータス遷移が拒否される
        assertThatThrownBy(cargo::confirmBooking)
                .isInstanceOf(InvalidBookingStatusTransitionException.class);
    }
}
```

#### 実装例: H2 PostgreSQL 互換モード設定

統合テストではなく、ドメイン層に依存した軽量テストで H2 を使用する場合の設定:

```yaml
# src/test/resources/application-test.yml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH
    driver-class-name: org.h2.Driver
  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
```

---

### 3.2 統合テスト（Integration Test）

#### 責務・検証対象

- **Repository（MyBatis マッパー）**: SQL クエリの正確性、トランザクション、楽観的ロック
- **Controller（MockMvc）**: HTTP リクエスト/レスポンスのマッピング、バリデーション、エラーハンドリング
- **外部 ACL ポート（WireMock）**: 外部システムとの契約遵守、タイムアウト・フォールバック

#### カバレッジ目標

| 対象 | 行カバレッジ |
|---|---|
| Repository（インフラ層） | **75% 以上** |
| Controller 層 | **70% 以上** |

#### 使用ツール

- **JUnit 5**: テストフレームワーク
- **Testcontainers 2 + `@ServiceConnection`**: 実 PostgreSQL 16 コンテナを自動起動
- **Spring MockMvc**: HTTP 層の結合テスト（サーブレットコンテキストは起動）
- **WireMock 3**: 外部 ACL ポートのスタブ（5 件すべてを対象）

#### 実行タイミング

- **PR 時**: GitHub Actions の `integration-test` ジョブ（目標 **5 分以内**）
- **ローカル**: Docker が起動している環境で任意実行

#### 実装例: CargoRepository の保存・検索テスト（Testcontainers）

```java
@SpringBootTest
@Testcontainers
class CargoRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private CargoRepository cargoRepository;

    @Test
    @Transactional
    void 貨物を保存して追跡番号で検索できる() {
        // Given: 新規貨物エンティティ
        var cargo = CargoFixture.newBooking(
                TrackingId.of("CARGO-001"),
                UnLocode.of("JPTYO"),
                UnLocode.of("DEHAM")
        );

        // When: 保存して検索する
        cargoRepository.save(cargo);
        var found = cargoRepository.findByTrackingId(TrackingId.of("CARGO-001"));

        // Then: 保存したエンティティと一致する
        assertThat(found).isPresent();
        assertThat(found.get().getOrigin()).isEqualTo(UnLocode.of("JPTYO"));
        assertThat(found.get().getDestination()).isEqualTo(UnLocode.of("DEHAM"));
    }

    @Test
    void 存在しない追跡番号で検索するとOptionalEmptyを返す() {
        // Given & When
        var result = cargoRepository.findByTrackingId(TrackingId.of("NONEXISTENT"));

        // Then
        assertThat(result).isEmpty();
    }
}
```

#### 実装例: BookingController の MockMvc テスト

```java
@WebMvcTest(BookingController.class)
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BookingApplicationService bookingApplicationService;

    @Test
    void 貨物予約登録APIが201を返す() throws Exception {
        // Given: 予約登録リクエスト
        var request = """
                {
                  "originUnLocode": "JPTYO",
                  "destinationUnLocode": "DEHAM",
                  "arrivalDeadline": "2026-06-30"
                }
                """;
        var expectedTrackingId = TrackingId.of("CARGO-001");
        given(bookingApplicationService.bookNewCargo(any()))
                .willReturn(expectedTrackingId);

        // When & Then
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trackingId").value("CARGO-001"));
    }

    @Test
    void 出発地コードが不正な場合は400を返す() throws Exception {
        // Given: 不正な UN/LOCODE を含むリクエスト
        var invalidRequest = """
                {
                  "originUnLocode": "INVALID",
                  "destinationUnLocode": "DEHAM",
                  "arrivalDeadline": "2026-06-30"
                }
                """;

        // When & Then
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("originUnLocode"));
    }
}
```

#### WireMock 契約テストの概要

各 ACL ポートに対して WireMock スタブを定義する。詳細は [セクション 4](#4-wiremock-契約テストシナリオacl-ポート別) を参照。

---

### 3.3 アーキテクチャテスト（Architecture Test）

#### 責務・検証対象

ヘキサゴナルアーキテクチャの依存関係ルールをコードレベルで自動検証する。アーキテクチャの腐敗（依存関係の逆転・Bounded Context 間の直接参照）を CI で検出する。

#### 使用ツール

- **ArchUnit 1.x**: Java パッケージの依存関係を宣言的に検証

#### 実行タイミング

- **PR 時**: GitHub Actions の `unit-test` ジョブに統合（ユニットテストと同時実行）
- **ローカル**: `./gradlew test` で自動実行

#### 検証ルール 4 件

```java
@AnalyzeClasses(packages = "com.example.cargotracker")
class HexagonalArchitectureTest {

    // ルール 1: domain パッケージが infrastructure パッケージを import しない
    @ArchTest
    static final ArchRule domainDoesNotDependOnInfrastructure =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..infrastructure..")
                    .because("ドメイン層はインフラ層を直接参照してはならない。" +
                             "依存方向は infrastructure → domain でなければならない");

    // ルール 2: domain パッケージで Spring アノテーションを使用しない
    @ArchTest
    static final ArchRule domainDoesNotUseSpringAnnotations =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().beAnnotatedWith(Component.class)
                    .orShould().beAnnotatedWith(Service.class)
                    .orShould().beAnnotatedWith(Repository.class)
                    .orShould().beAnnotatedWith(Autowired.class)
                    .because("ドメイン層は Spring フレームワークに依存してはならない。" +
                             "ドメインオブジェクトは POJO でなければならない");

    // ルール 3: アプリケーション層がインフラ層を直接参照しない（Port 経由のみ許可）
    @ArchTest
    static final ArchRule applicationDoesNotDependOnInfrastructureDirectly =
            noClasses()
                    .that().resideInAPackage("..application..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..infrastructure..")
                    .because("アプリケーション層はポートインターフェース経由でのみ" +
                             "インフラ層と通信しなければならない");

    // ルール 4: 異なる Bounded Context 間でクラスを直接参照しない
    @ArchTest
    static final ArchRule boundedContextsDoNotDirectlyReference =
            SlicesRuleDefinition.slices()
                    .matching("com.example.cargotracker.(*)..")
                    .should().notDependOnEachOther()
                    .ignoreDependency(
                            resideInAPackage("..shared.."),
                            alwaysTrue()
                    )
                    .because("Bounded Context 間の通信はドメインイベントまたは" +
                             "ACL（Anti-Corruption Layer）経由でなければならない。" +
                             "shared パッケージ（共有カーネル）への参照は許可する");
}
```

---

### 3.4 E2E テスト（End-to-End Test）

#### 責務・検証対象

クリティカルなユーザーシナリオをブラウザレベルで検証する。ドメインロジックの再検証は行わず、ユーザー体験の観点からシステム全体が協調動作することを確認する。

**優先シナリオ（US08・US10・US13）**:

| シナリオ | 理由 |
|---|---|
| US08: 予約を確定する | 予約フローの最終ステップ。複数コンテキストが連携する |
| US10: 荷役作業を記録する | 最も頻繁に実行される運用操作 |
| US13: 追跡情報を照会する | 顧客向け重要機能。htmx ポーリングを含む |

#### カバレッジ目標

- 優先度「高」のユーザーシナリオ（US01〜US15）の **80% カバー**

#### 使用ツール

- **Playwright 1.44+**: ブラウザ自動化（TypeScript）
- **htmx 対応**: `waitForSelector` によるポーリング更新の待機

#### 実行タイミング

- **main ブランチマージ後**: GitHub Actions の `e2e-test` ジョブ（目標 **15 分以内**）
- **リリース前**: 全 E2E シナリオを実行

#### htmx 30 秒ポーリングへの対応

htmx の `hx-trigger="every 30s"` による自動更新を Playwright でテストするには、`waitForSelector` でポーリング後の DOM 更新を待機する。

```typescript
// htmx ポーリング完了を待機するユーティリティ
async function waitForHtmxUpdate(page: Page, selector: string, timeout = 35000) {
  // htmx が更新した要素に hx-request 属性が付与されるため、
  // その変化を監視してポーリング完了を検出する
  await page.waitForFunction(
    (sel) => {
      const el = document.querySelector(sel);
      return el && !el.hasAttribute('hx-request');
    },
    selector,
    { timeout }
  );
}
```

#### 実装例: US13 追跡情報照会の Playwright テスト（TypeScript）

```typescript
import { test, expect, Page } from '@playwright/test';

test.describe('US13: 追跡情報を照会する', () => {
  let page: Page;

  test.beforeEach(async ({ browser }) => {
    page = await browser.newPage();
  });

  test('追跡番号で貨物の現在状態を照会できる', async () => {
    // Given: 荷役作業が記録済みの貨物が存在する
    await page.goto('/tracking');

    // When: 追跡番号を入力して検索する
    await page.fill('[data-testid="tracking-id-input"]', 'CARGO-001');
    await page.click('[data-testid="search-button"]');

    // Then: 追跡情報が表示される
    await expect(page.locator('[data-testid="transport-status"]'))
      .toHaveText('IN_PORT', { timeout: 10000 });
    await expect(page.locator('[data-testid="current-location"]'))
      .toContainText('東京港');
  });

  test('htmx ポーリングで追跡情報が自動更新される', async () => {
    // Given: 追跡ページを表示している
    await page.goto('/tracking/CARGO-001');
    const initialStatus = await page
      .locator('[data-testid="transport-status"]')
      .textContent();

    // When: バックエンドで荷役イベントが発生し、30 秒後にポーリングが更新される
    // （テスト環境ではポーリング間隔を 5 秒に短縮）
    await waitForHtmxUpdate(page, '[data-testid="tracking-panel"]', 10000);

    // Then: ページを再読み込みせずに最新状態が反映される
    const updatedStatus = await page
      .locator('[data-testid="transport-status"]')
      .textContent();
    expect(updatedStatus).not.toBe(initialStatus);
  });

  test('存在しない追跡番号を入力するとエラーメッセージが表示される', async () => {
    // Given
    await page.goto('/tracking');

    // When
    await page.fill('[data-testid="tracking-id-input"]', 'NONEXISTENT-999');
    await page.click('[data-testid="search-button"]');

    // Then
    await expect(page.locator('[data-testid="error-message"]'))
      .toContainText('追跡番号が見つかりません');
  });
});

async function waitForHtmxUpdate(page: Page, selector: string, timeout = 35000) {
  await page.waitForFunction(
    (sel) => {
      const el = document.querySelector(sel);
      return el && !el.hasAttribute('hx-request');
    },
    selector,
    { timeout }
  );
}
```

---

## 4. WireMock 契約テストシナリオ（ACL ポート別）

各外部 ACL ポートに対して正常・異常シナリオを定義し、WireMock でスタブ化する。

### 4.1 シナリオ一覧

| ポート | 正常シナリオ | 異常シナリオ |
|---|---|---|
| ExternalRoutingServicePort | ルート検索 → 3 候補返却 | 接続タイムアウト → 過去実績データにフォールバック |
| CustomsClearancePort | 通関申請 → CLEARED | HELD ステータス → 例外イベント発行 |
| PaymentGatewayPort | 支払い処理 → CONFIRMED | 決済失敗 → OVERDUE 状態遷移 |
| PortManagementPort | 港湾入港通知 → 受理 | 港湾満杯 → 代替港提案 |
| NotificationPort | メール通知送信 → 202 Accepted | 通知失敗 → ログ記録（非クリティカル） |

### 4.2 WireMock 実装例

#### ExternalRoutingServicePort: ルート検索（正常・タイムアウト）

```java
@SpringBootTest
@AutoConfigureWireMock(port = 0)
class ExternalRoutingServiceAdapterTest {

    @Autowired
    private ExternalRoutingServicePort routingServicePort;

    @Test
    void ルート検索で3候補が返却される() {
        // Given: WireMock スタブ定義（3 候補を返す）
        stubFor(post(urlEqualTo("/api/routes/search"))
                .withRequestBody(matchingJsonPath("$.origin", equalTo("JPTYO")))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "routes": [
                                    {"id": "R001", "legs": [{"voyageNumber": "V001"}], "transitTime": 14},
                                    {"id": "R002", "legs": [{"voyageNumber": "V002"}], "transitTime": 18},
                                    {"id": "R003", "legs": [{"voyageNumber": "V003"}], "transitTime": 21}
                                  ]
                                }
                                """)));

        // When: ルート検索を実行する
        var request = RouteSearchRequest.of(
                UnLocode.of("JPTYO"),
                UnLocode.of("DEHAM"),
                LocalDate.of(2026, 6, 30)
        );
        var routes = routingServicePort.searchRoutes(request);

        // Then: 3 候補が返却される
        assertThat(routes).hasSize(3);
        assertThat(routes.get(0).getTransitDays()).isEqualTo(14);
    }

    @Test
    void 接続タイムアウト時に過去実績データにフォールバックする() {
        // Given: タイムアウトを発生させるスタブ
        stubFor(post(urlEqualTo("/api/routes/search"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(6000))); // 6 秒遅延（タイムアウト閾値 5 秒を超過）

        // When: ルート検索を実行する
        var request = RouteSearchRequest.of(
                UnLocode.of("JPTYO"),
                UnLocode.of("DEHAM"),
                LocalDate.of(2026, 6, 30)
        );
        var routes = routingServicePort.searchRoutes(request);

        // Then: 過去実績データからフォールバック候補が返却される
        assertThat(routes).isNotEmpty();
        assertThat(routes).allSatisfy(route ->
                assertThat(route.isFallback()).isTrue()
        );
    }
}
```

#### CustomsClearancePort: 通関申請（CLEARED・HELD）

```java
@SpringBootTest
@AutoConfigureWireMock(port = 0)
class CustomsClearanceAdapterTest {

    @Autowired
    private CustomsClearancePort customsClearancePort;

    @Test
    void 通関申請が承認されてCLEAREDステータスを返す() {
        // Given
        stubFor(post(urlEqualTo("/api/customs/clearance"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("""
                                {"status": "CLEARED", "clearanceId": "CUS-001"}
                                """)));

        // When
        var result = customsClearancePort.submitClearance(
                ClearanceRequest.of(TrackingId.of("CARGO-001"))
        );

        // Then
        assertThat(result.getStatus()).isEqualTo(ClearanceStatus.CLEARED);
    }

    @Test
    void 通関保留HELDステータス受信時に例外イベントが発行される() {
        // Given
        stubFor(post(urlEqualTo("/api/customs/clearance"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("""
                                {"status": "HELD", "reason": "書類不備", "holdId": "HOLD-001"}
                                """)));

        // When
        var result = customsClearancePort.submitClearance(
                ClearanceRequest.of(TrackingId.of("CARGO-002"))
        );

        // Then: HELD ステータスが返却され、例外イベントが発行可能な状態になる
        assertThat(result.getStatus()).isEqualTo(ClearanceStatus.HELD);
        assertThat(result.getHoldReason()).isEqualTo("書類不備");
    }
}
```

#### PaymentGatewayPort: 支払い処理（CONFIRMED・失敗）

```java
@SpringBootTest
@AutoConfigureWireMock(port = 0)
class PaymentGatewayAdapterTest {

    @Autowired
    private PaymentGatewayPort paymentGatewayPort;

    @Test
    void 支払い処理が成功してCONFIRMEDを返す() {
        // Given
        stubFor(post(urlEqualTo("/api/payments"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("""
                                {"status": "CONFIRMED", "transactionId": "TXN-001"}
                                """)));

        // When
        var result = paymentGatewayPort.processPayment(
                PaymentRequest.of(InvoiceId.of("INV-001"), Money.of(150000, "JPY"))
        );

        // Then
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.CONFIRMED);
    }

    @Test
    void 決済失敗時にOVERDUE状態への遷移情報が返却される() {
        // Given: 決済失敗レスポンス
        stubFor(post(urlEqualTo("/api/payments"))
                .willReturn(aResponse()
                        .withStatus(402)
                        .withBody("""
                                {"status": "FAILED", "errorCode": "INSUFFICIENT_FUNDS"}
                                """)));

        // When
        var result = paymentGatewayPort.processPayment(
                PaymentRequest.of(InvoiceId.of("INV-002"), Money.of(500000, "JPY"))
        );

        // Then: 失敗情報が返却される（OVERDUE 遷移はドメイン層が担当）
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.getErrorCode()).isEqualTo("INSUFFICIENT_FUNDS");
    }
}
```

#### PortManagementPort: 港湾入港通知（受理・代替港提案）

```java
@SpringBootTest
@AutoConfigureWireMock(port = 0)
class PortManagementAdapterTest {

    @Autowired
    private PortManagementPort portManagementPort;

    @Test
    void 港湾入港通知が受理される() {
        // Given
        stubFor(post(urlEqualTo("/api/ports/arrival"))
                .willReturn(aResponse()
                        .withStatus(202)
                        .withBody("""
                                {"accepted": true, "berthId": "BERTH-A1"}
                                """)));

        // When
        var result = portManagementPort.notifyArrival(
                ArrivalNotification.of(UnLocode.of("JPTYO"), VoyageNumber.of("V001"))
        );

        // Then
        assertThat(result.isAccepted()).isTrue();
        assertThat(result.getBerthId()).isEqualTo("BERTH-A1");
    }

    @Test
    void 港湾満杯時に代替港が提案される() {
        // Given
        stubFor(post(urlEqualTo("/api/ports/arrival"))
                .willReturn(aResponse()
                        .withStatus(409)
                        .withBody("""
                                {
                                  "accepted": false,
                                  "reason": "PORT_FULL",
                                  "alternativePorts": ["JPYOK", "JPKOB"]
                                }
                                """)));

        // When
        var result = portManagementPort.notifyArrival(
                ArrivalNotification.of(UnLocode.of("JPTYO"), VoyageNumber.of("V002"))
        );

        // Then: 代替港リストが返却される
        assertThat(result.isAccepted()).isFalse();
        assertThat(result.getAlternativePorts())
                .containsExactly(UnLocode.of("JPYOK"), UnLocode.of("JPKOB"));
    }
}
```

#### NotificationPort: メール通知（202 Accepted・失敗ログ）

```java
@SpringBootTest
@AutoConfigureWireMock(port = 0)
class NotificationAdapterTest {

    @Autowired
    private NotificationPort notificationPort;

    @Test
    void メール通知送信が202Acceptedを返す() {
        // Given
        stubFor(post(urlEqualTo("/api/notifications/email"))
                .willReturn(aResponse()
                        .withStatus(202)));

        // When: 通知送信を実行する
        assertThatNoException().isThrownBy(() ->
                notificationPort.sendEmail(
                        EmailNotification.of("customer@example.com", "貨物が到着しました", "...")
                )
        );

        // Then: スタブが呼び出されたことを確認する
        verify(1, postRequestedFor(urlEqualTo("/api/notifications/email")));
    }

    @Test
    void 通知失敗時にログを記録して処理を継続する() {
        // Given: 通知サービスがエラーを返す（非クリティカルなので例外を飲み込む）
        stubFor(post(urlEqualTo("/api/notifications/email"))
                .willReturn(aResponse()
                        .withStatus(503)));

        // When & Then: 例外が外部に伝播しない（ログのみ記録）
        assertThatNoException().isThrownBy(() ->
                notificationPort.sendEmail(
                        EmailNotification.of("customer@example.com", "通知テスト", "...")
                )
        );
    }
}
```

---

## 5. ユーザーストーリーとテストのトレーサビリティ

| US | タイトル | ユニットテスト | 統合テスト | E2E テスト | 優先度 |
|---|---|---|---|---|---|
| US01 | 輸送見積を作成する | `QuotationService`、`Quotation` 値オブジェクト | `ExternalRoutingServicePort` WireMock | - | 高 |
| US02 | 荷主を登録する | `Shipper` 集約、`ShipperRegistrationService` | `ShipperRepository`、`ShipperController` | - | 高 |
| US03 | 法人荷主を登録する | `CorporateShipper` 集約、法人割引率計算 | `CorporateShipperRepository`、`ShipperController` | - | 高 |
| US04 | 貨物予約を登録する | `Cargo` 集約、`BookingStatus` 初期遷移 | `CargoRepository`、`BookingController` | - | 高 |
| US05 | 危険物・冷凍貨物の予約を登録する | `Cargo` 集約（危険物フラグ）、`CargoCategory` 値オブジェクト | `CargoRepository`、`BookingController` | - | 高 |
| US06 | 最適ルートを検索する | `RoutingService`、`Itinerary` 値オブジェクト | `ExternalRoutingServicePort` WireMock（正常・タイムアウト） | - | 高 |
| US07 | ルートを選択して予約に紐付ける | `Cargo#assignRoute()`、`BookingStatus.ROUTE_PROPOSED` 遷移 | `CargoRepository`（ルート保存）、`RoutingController` | - | 高 |
| US08 | 予約を確定する | `Cargo#confirmBooking()`、`BookingStatus.CONFIRMED` 遷移 | `BookingController`（確定 API）、`CargoRepository` | **US08 シナリオ** | 高 |
| US09 | 追跡番号を発行する | `TrackingId` 値オブジェクト（一意性）、`TrackingIdGenerator` | `CargoRepository`（追跡番号保存） | - | 高 |
| US10 | 荷役作業を記録する | `HandlingActivity` 集約、MISROUTED 判定ロジック | `HandlingActivityRepository`、`HandlingController` | **US10 シナリオ** | 高 |
| US11 | 引取作業を記録する | `HandlingActivity`（RECEIVED イベント） | `HandlingController`（引取 API） | - | 高 |
| US12 | 貨物状態を手動更新する | `TrackingActivity`、`TransportStatus` 遷移（9 値） | `TrackingController`（手動更新 API） | - | 高 |
| US13 | 追跡情報を照会する | - | `TrackingQueryService`（CQRS 読み取り）、`TrackingController` | **US13 シナリオ** | 高 |
| US14 | 遅延例外を処理する | `TrackingExceptionEvent` エスカレーション判定 | `TrackingController`（例外処理 API）、`NotificationPort` WireMock | - | 高 |
| US15 | 破損・紛失例外を処理する | `HandlingException` 集約、`ExceptionType` 値オブジェクト | `HandlingController`（例外記録 API）、`CustomsClearancePort` WireMock | - | 高 |
| US16 | 輸送料金を算出する | `Invoice` 集約、`FreightCalculationService`、消費税計算 | `InvoiceRepository`、`BillingController` | - | 中 |
| US17 | 法人割引を適用する | `DiscountPolicy` 値オブジェクト、法人割引率計算ロジック | `BillingController`（割引適用 API）、`PaymentGatewayPort` WireMock | - | 中 |
| US18 | 精算を処理する | `Invoice#settle()`、`InvoiceStatus` 遷移 | `BillingController`（精算 API）、`PaymentGatewayPort` WireMock（正常・失敗） | - | 中 |

---

## 6. カバレッジ目標とメトリクス

### 6.1 レイヤー別カバレッジ目標

| レイヤー | 行カバレッジ目標 | 分岐カバレッジ目標 | 計測ツール |
|---|---|---|---|
| ドメイン層（`domain` パッケージ） | **85% 以上** | **80% 以上** | JaCoCo / SonarQube |
| アプリケーション層（`application` パッケージ） | **80% 以上** | **75% 以上** | JaCoCo / SonarQube |
| インフラ層 - Repository（`infrastructure.persistence` パッケージ） | **75% 以上** | — | JaCoCo / SonarQube |
| インフラ層 - Controller（`infrastructure.web` パッケージ） | **70% 以上** | — | JaCoCo / SonarQube |

### 6.2 SonarQube Quality Gate 条件

| 条件 | 基準値 | 適用対象 |
|---|---|---|
| 行カバレッジ（新規コード） | **80% 以上** | 新規追加コード |
| 重複コード率 | **3% 以下** | プロジェクト全体 |
| Reliability Rating | **A**（バグゼロ） | プロジェクト全体 |
| Security Rating | **A**（脆弱性ゼロ） | プロジェクト全体 |
| Maintainability Rating | **A** | 新規コード |
| Security Hotspot Review | **100%** | 新規コード |

Quality Gate が失敗した場合、PR のマージをブロックする。

---

## 7. CI/CD とのテスト連携

### 7.1 ステージ別テスト戦略

| ステージ | テスト種別 | 目標時間 | 失敗時の扱い |
|---|---|---|---|
| コミット（ローカル） | ユニットテスト + アーキテクチャテスト | **< 60 秒** | コミット前に修正 |
| PR | ユニット + 統合 + ArchUnit + SonarQube | **< 5 分** | PR マージ不可 |
| main ブランチマージ後 | E2E テスト | **< 15 分** | Slack 通知（ホットフィックス優先） |
| リリース | 全テスト + パフォーマンステスト | **< 30 分** | リリース停止 |

### 7.2 GitHub Actions パイプライン図

```plantuml
@startuml
!theme plain

|ローカル|
start
:git commit;
:ユニットテスト\n(JUnit 5 + Mockito)\n< 30 秒;
:アーキテクチャテスト\n(ArchUnit)\n< 10 秒;
if (テスト成功?) then (yes)
  :コミット完了;
else (no)
  :修正してリトライ;
  stop
endif

|PR|
:git push / PR 作成;
fork
  :ユニットテスト\n< 2 分;
fork again
  :統合テスト\n(Testcontainers + MockMvc\n + WireMock)\n< 3 分;
end fork
:SonarQube 解析\nQuality Gate チェック;
if (全テスト + Quality Gate 成功?) then (yes)
  :PR マージ許可;
else (no)
  :PR マージ不可\n（コメントで詳細通知）;
  stop
endif

|main ブランチ|
:main マージ;
:E2E テスト\n(Playwright)\n< 15 分;
if (E2E 成功?) then (yes)
  :デプロイ続行;
else (no)
  :Slack 通知\nホットフィックス対応;
  stop
endif

|リリース|
:リリースタグ作成;
:全テストスイート実行;
:パフォーマンステスト\n< 10 分;
if (全テスト成功?) then (yes)
  :リリース承認;
  stop
else (no)
  :リリース停止\nインシデント対応;
  stop
endif

@enduml
```

---

## 8. TDD 開発ワークフロー

### 8.1 インサイドアウト TDD（バックエンド）

ドメイン層から外側に向かって開発する。外部依存を後回しにすることで、ビジネスロジックに集中できる。

```plantuml
@startuml
!theme plain

start
:ユーザーストーリーを確認する\n（受入条件を把握する）;

group Step 1: ドメインモデルのユニットテスト
  :【RED】集約・値オブジェクトの\nテストを書く\n（Given-When-Then 形式）;
  :【GREEN】最小限の実装でテストを通す;
  :【REFACTOR】重複除去・命名改善;
end group

group Step 2: アプリケーション層のユニットテスト
  :【RED】ユースケースサービスの\nテストを書く\n（ポートはモックする）;
  :【GREEN】ポートインターフェースを\n定義してサービスを実装する;
  :【REFACTOR】サービスのオーケストレーションを整理;
end group

group Step 3: アダプターの統合テスト
  :【RED】Repository / Controller /\n外部 ACL アダプターの\n統合テストを書く;
  :【GREEN】Testcontainers / MockMvc /\nWireMock で実装する;
  :【REFACTOR】クエリ最適化・エラーハンドリング整理;
end group

:ArchUnit でアーキテクチャルール検証;
:全テストが GREEN であることを確認してコミット;
stop

@enduml
```

### 8.2 重要なビジネスルール（必ず TDD 適用）

以下のビジネスルールは複雑度が高く、テストファーストで実装しなければならない。

#### Cargo の BookingStatus 状態遷移（8 値）

```
PRELIMINARY → ROUTE_PROPOSED → CONFIRMED → CUSTOMS_PENDING
    → IN_TRANSIT → IN_PORT → COMPLETED
    ↘ MISROUTED（異常系）
    ↘ CANCELLED（キャンセル）
```

テスト観点:

- 各遷移の正常系（許可されている遷移）
- 各遷移の異常系（許可されていない遷移 → `InvalidBookingStatusTransitionException`）
- 終端状態（COMPLETED・CANCELLED）からの遷移拒否

#### HandlingActivity の荷役妥当性検証（MISROUTED 判定）

```java
@Test
void 指定ルート外の港で荷役を実行するとMISROUTED判定になる() {
    // Given: 東京→ハンブルク のルートを持つ貨物
    var cargo = CargoFixture.withRoute(
            RouteFixture.tokyoToHamburg()
    );

    // When: ルートに含まれないシンガポールで荷役を記録する
    var activity = HandlingActivity.of(
            cargo.getTrackingId(),
            UnLocode.of("SGSIN"), // ルート外の港
            HandlingType.LOAD,
            LocalDateTime.now()
    );

    // Then: 貨物が MISROUTED 状態に遷移する
    cargo.applyHandlingActivity(activity);
    assertThat(cargo.getBookingStatus()).isEqualTo(BookingStatus.MISROUTED);
}
```

#### Invoice の料金計算（法人割引・消費税計算）

```java
@Test
void 法人割引10%と消費税10%が正しく計算される() {
    // Given: 基本料金 100,000 円、法人割引率 10% の Invoice
    var baseAmount = Money.of(100_000, "JPY");
    var corporateDiscount = DiscountPolicy.corporate(Percentage.of(10));

    // When: 料金を確定する
    var invoice = Invoice.calculate(baseAmount, corporateDiscount, TaxRate.STANDARD);

    // Then: 割引後 90,000 円 × 消費税 10% = 99,000 円
    assertThat(invoice.getNetAmount()).isEqualTo(Money.of(90_000, "JPY"));
    assertThat(invoice.getTaxAmount()).isEqualTo(Money.of(9_000, "JPY"));
    assertThat(invoice.getTotalAmount()).isEqualTo(Money.of(99_000, "JPY"));
}
```

#### TrackingExceptionEvent のエスカレーション判定

```java
@Test
void 遅延が48時間を超える場合にエスカレーションフラグが立つ() {
    // Given: 遅延 72 時間の例外イベント
    var event = TrackingExceptionEvent.delay(
            TrackingId.of("CARGO-001"),
            Duration.ofHours(72)
    );

    // When: エスカレーション判定を実行する
    var result = escalationPolicy.evaluate(event);

    // Then: エスカレーション対象と判定される
    assertThat(result.requiresEscalation()).isTrue();
    assertThat(result.getEscalationLevel()).isEqualTo(EscalationLevel.CRITICAL);
}

@Test
void 遅延が48時間以内の場合はエスカレーション不要と判定される() {
    // Given: 遅延 24 時間の例外イベント
    var event = TrackingExceptionEvent.delay(
            TrackingId.of("CARGO-002"),
            Duration.ofHours(24)
    );

    // When
    var result = escalationPolicy.evaluate(event);

    // Then
    assertThat(result.requiresEscalation()).isFalse();
}
```

### 8.3 Bounded Context 別 TDD 優先順位

| Bounded Context | TDD 優先ルール | 理由 |
|---|---|---|
| Booking Context | BookingStatus 遷移（8 値）を最初にテストする | 最も複雑な状態機械。バグの影響範囲が大きい |
| Routing Context | ExternalRoutingServicePort のフォールバックをテストする | 外部依存が本番障害の主要因になりやすい |
| Tracking Context | CQRS 読み取りクエリのパフォーマンスを統合テストで検証する | 30 秒ポーリングの負荷を事前に確認する |
| Handling Context | MISROUTED 判定ロジックを先にテストする | 荷役記録ミスは運用上重大なインシデントになる |
| Billing Context | 割引・消費税計算を `@ParameterizedTest` で網羅する | 金額計算のバグは法的リスクを伴う |
| Shared Domain | Location（UN/LOCODE）のバリデーションを値オブジェクトレベルで担保する | 全コンテキストが共有するため、バグの影響範囲が広い |
