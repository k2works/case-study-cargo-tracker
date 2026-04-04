# API E2E テストセットアップ手順書

## 概要

本ドキュメントは、Case Study Cargo Tracker の API E2E（End-to-End）テスト環境をセットアップする手順を説明します。

API E2E テストは Spring Boot の `MockMvc` と Testcontainers（PostgreSQL）を使用し、REST API・画面 Controller・Spring Security を含むアプリケーション全体を結合した状態でユーザーストーリー単位のフローを検証します。ブラウザ E2E テスト（Playwright）と異なり、JVM 内で完結するため高速に実行できます。

| 項目 | 内容 |
|------|------|
| テストフレームワーク | JUnit 5 + Spring Boot Test |
| HTTP クライアント | MockMvc |
| データベース | PostgreSQL（Testcontainers） |
| 認証 | Spring Security MockMvc（`formLogin`） |
| テストディレクトリ | `apps/cargo-tracker/src/test/java/com/example/cargotracker/e2e/` |

---

## 1. 前提条件

| 前提 | 確認方法 |
|------|---------|
| Java 25.x | `java -version` |
| Docker Desktop が起動している | `docker info` |
| `./gradlew build` が成功する | `cd apps/cargo-tracker && ./gradlew build` |

> **Note**: Testcontainers は Docker を使用して PostgreSQL コンテナを自動起動します。Docker Desktop が起動している必要があります。

---

## 2. ディレクトリ構造

```
apps/cargo-tracker/src/test/java/com/example/cargotracker/
├── support/
│   └── PostgreSQLIntegrationTestBase.java   # Testcontainers 共通基底クラス
└── e2e/
    ├── US11ReceiveHandlingEventE2ETest.java  # US11 引取記録フロー
    ├── US12ManualUpdateE2ETest.java          # US12 手動更新フロー
    ├── US13TrackingInfoE2ETest.java          # US13 公開追跡ページ
    ├── US14US15ExceptionE2ETest.java         # US14/US15 例外フロー
    ├── US16E2ETest.java                      # US16 輸送料金算出
    ├── US17E2ETest.java                      # US17 請求書フロー
    └── US18E2ETest.java                      # US18 割引適用フロー
```

---

## 3. 基盤クラスのセットアップ

### 3.1 PostgreSQL Testcontainers 基底クラス

全ての API E2E テストが継承する共通基底クラスを作成します。シングルトンコンテナパターンにより、複数テストクラス間で PostgreSQL コンテナを共有し、テスト実行を高速化します。

`src/test/java/com/example/cargotracker/support/PostgreSQLIntegrationTestBase.java`:

```java
package com.example.cargotracker.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

public abstract class PostgreSQLIntegrationTestBase {

    static final PostgreSQLContainer<?> postgres;

    static {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("cargo_tracker_test")
                .withUsername("test")
                .withPassword("test");
        postgres.start();
    }

    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name",
                () -> "org.postgresql.Driver");
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }
}
```

**シングルトンコンテナパターンのポイント:**

- `static` ブロックでコンテナを起動するため、JVM プロセス内で 1 回だけ起動される
- `@DynamicPropertySource` で Spring の DataSource 設定をコンテナに向ける
- Flyway の接続先もコンテナに向けることで、マイグレーションが自動実行される

---

## 4. E2E テストの構造

### 4.1 テストクラスの基本構成

```java
@SpringBootTest(properties = {
        "spring.security.user.name=admin",
        "spring.security.user.password=admin",
        "app.seed.enabled=false"
})
@ActiveProfiles("test")
@DisplayName("USxx 〇〇フロー E2E テスト")
class USxxE2ETest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        session = loginAsUser();
    }

    @AfterEach
    void cleanUp() {
        // テストデータのクリーンアップ（外部キー順に DELETE）
        jdbcTemplate.execute("DELETE FROM ...");
    }

    private MockHttpSession loginAsUser() throws Exception {
        return (MockHttpSession) mockMvc
                .perform(formLogin("/login").user("admin").password("admin"))
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getRequest()
                .getSession();
    }

    @Test
    @DisplayName("Exx 〇〇できる")
    void exx_scenario_shouldSucceed() throws Exception {
        // テスト実装
    }
}
```

### 4.2 主要なアノテーションと設定

| アノテーション / 設定 | 説明 |
|---------------------|------|
| `@SpringBootTest` | アプリケーション全体を起動して結合テスト |
| `@ActiveProfiles("test")` | テスト用プロファイルを使用 |
| `properties = {...}` | 認証情報・シード無効化をインラインで指定 |
| `extends PostgreSQLIntegrationTestBase` | Testcontainers の PostgreSQL を使用 |
| `SecurityMockMvcConfigurers.springSecurity()` | MockMvc に Spring Security を統合 |

---

## 5. テストパターン

### 5.1 認証（ログイン・セッション取得）

```java
private MockHttpSession loginAsUser() throws Exception {
    return (MockHttpSession) mockMvc
            .perform(formLogin("/login").user("admin").password("admin"))
            .andExpect(status().is3xxRedirection())
            .andReturn()
            .getRequest()
            .getSession();
}
```

以降のリクエストに `.session(session)` を付与して認証済み状態を維持します。

### 5.2 REST API 呼び出し（POST → Location ヘッダーから ID 取得）

```java
var location = mockMvc.perform(post("/api/shippers")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"テスト荷主\", \"email\": \"test@example.com\", ...}")
                .with(csrf()))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getHeader("Location");

String id = location.substring(location.lastIndexOf('/') + 1);
```

### 5.3 画面フォーム送信

```java
mockMvc.perform(post("/handling/receive")
                .session(session)
                .with(csrf())
                .param("bookingId", bookingId)
                .param("eventType", "RECEIVE")
                .param("locationCode", "JPTYO")
                .param("completionTime", "2026-05-01T09:00"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrlPattern("/handling?bookingId=*"))
        .andExpect(flash().attribute("successMessage", containsString("引取済")));
```

### 5.4 DB 検証

```java
String status = jdbcTemplate.queryForObject(
        "SELECT status FROM freight_charges WHERE id = ?",
        String.class, freightId);
assertThat(status).isEqualTo("DRAFT");
```

### 5.5 未認証アクセスの検証

```java
@Test
@DisplayName("未認証ユーザーはログインページにリダイレクトされる")
void unauthenticated_access_redirectsToLogin() throws Exception {
    mockMvc.perform(get("/handling/receive"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", containsString("/login")));
}
```

### 5.6 バリデーションエラーの検証

```java
mockMvc.perform(post("/handling/receive")
                .session(session)
                .with(csrf())
                .param("bookingId", bookingId)
                .param("receiveConfirmationCode", ""))  // 空でバリデーションエラー
        .andExpect(status().isOk())
        .andExpect(view().name("handling/receive"))
        .andExpect(model().attributeHasFieldErrors("form", "receiveConfirmationCode"));
```

---

## 6. テストの実行

```bash
cd apps/cargo-tracker

# E2E テストのみ実行（パッケージ指定）
./gradlew test --tests "com.example.cargotracker.e2e.*"

# 特定のテストクラスのみ
./gradlew test --tests "com.example.cargotracker.e2e.US11ReceiveHandlingEventE2ETest"

# 全テスト実行
./gradlew test
```

> **Note**: 初回実行時は PostgreSQL の Docker イメージをダウンロードするため時間がかかります。

---

## 7. テストデータのクリーンアップ

`@AfterEach` で外部キー制約の順序に従ってデータを削除します。

```java
@AfterEach
void cleanUp() {
    jdbcTemplate.execute("DELETE FROM handling_events");
    jdbcTemplate.execute("DELETE FROM tracking_numbers");
    jdbcTemplate.execute("DELETE FROM bookings");
    jdbcTemplate.execute("DELETE FROM shippers");
}
```

**重要**: テーブル間の外部キー制約に注意し、子テーブルから順に削除してください。

---

## 8. E2E テスト追加ガイド

新しいユーザーストーリーの API E2E テストを追加する際の手順:

1. `e2e/` パッケージに `USxx<Name>E2ETest.java` を作成
2. `PostgreSQLIntegrationTestBase` を継承
3. `@SpringBootTest` + `@ActiveProfiles("test")` を付与
4. `@BeforeEach` で MockMvc セットアップとログイン
5. `@AfterEach` でテストデータのクリーンアップ
6. テストメソッドに `@DisplayName("Exx 〇〇")` でシナリオを記述
7. 前提データの作成 → API 呼び出し → アサーション の順で実装

### 命名規則

| 要素 | 規則 | 例 |
|------|------|-----|
| クラス名 | `US{番号}{機能名}E2ETest` | `US11ReceiveHandlingEventE2ETest` |
| テストメソッド | `E{番号}_{シナリオ}_{期待結果}` | `E12_recordReceiveHandlingEvent_shouldSucceed` |
| `@DisplayName` | `E{番号} {日本語シナリオ}` | `E12 引取イベントを記録できる` |

---

## 9. Playwright E2E テストとの使い分け

| 観点 | API E2E テスト (MockMvc) | ブラウザ E2E テスト (Playwright) |
|------|-------------------------|-------------------------------|
| 実行速度 | 高速（JVM 内完結） | 低速（ブラウザ起動） |
| テスト範囲 | API + Controller + DB | UI + JS + API + DB |
| JavaScript 検証 | 不可 | 可能（htmx、Alpine.js） |
| ビジュアル検証 | 不可 | 可能（スクリーンショット） |
| CI 実行 | 容易 | ブラウザインストールが必要 |
| 推奨用途 | バックエンドロジック・API フローの検証 | ユーザー操作フロー・画面遷移の検証 |

両方を組み合わせることで、バックエンドの信頼性とフロントエンドの動作保証を効率的にカバーします。

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

**解決策**: Testcontainers はランダムポートを使用するため通常は発生しない。`docker-compose.yml` の PostgreSQL が起動中の場合は停止する。

```bash
docker compose down
```

### テストが遅い

**問題**: E2E テストの実行に時間がかかる

**解決策**:
- シングルトンコンテナパターン（`PostgreSQLIntegrationTestBase`）を使用しているか確認
- `build.gradle` の `maxParallelForks = 1` でテストの並列度を調整

### Spring コンテキストが再起動される

**問題**: テストクラスごとに Spring コンテキストが再作成され、遅い

**解決策**: `@SpringBootTest` の `properties` や `@ActiveProfiles` を全 E2E テストクラスで統一し、Spring のコンテキストキャッシュを活用する。

---

## 関連ドキュメント

- [アプリケーション開発環境セットアップ手順書](./dev_app_instrunction.md)
- [Playwright E2E テストセットアップ手順書](./dev_e2e_instruction.md)
- [テスト戦略](../design/test_strategy.md)
- [技術スタック選定](../design/tech_stack.md)
