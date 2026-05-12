# ADR-0002 データアクセスとして MyBatis を採用する

Read Model（Projection）および Auth DB のデータアクセス技術として、Spring Data JPA / Hibernate ではなく **MyBatis（+ mybatis-spring-boot-starter）** を採用する。Axon Framework の Token / Saga Store には `JdbcTokenStore` / `JdbcSagaStore` を組み合わせる。

日付: 2026-05-12

## ステータス

2026-05-12 承認済み

## コンテキスト

[ADR-0001](0001-axon-framework-adoption.md) で **Axon Framework 5 + Event Sourcing + CQRS** を採用した。集約の永続化は Axon Event Store（Axon Server）で完結する一方、Read Model（Projection）と Auth DB のデータアクセス技術を選定する必要がある。

選定にあたって考慮した制約・要件：

- **複雑な検索クエリ**: 経路候補算出（`OptimalRouteService`）、追跡履歴の時系列照会（UC15）、請求書の集計クエリ（UC17）など、JOIN・GROUP BY・ウィンドウ関数を使う複雑な読み取りが多い
- **CQRS の Query 側の最適化**: ドメインモデルを経由せず、画面表示用 DTO に直接マッピングする必要がある（オブジェクト → リレーション → オブジェクトの往復を避ける）
- **DB スキーマと SQL の見える化**: パフォーマンス問題の原因究明、DBA との協業、SQL ログの分析が容易であること
- **Axon Token / Saga Store との同居**: Projection 更新と Token 更新を同一 JDBC トランザクションで処理し、at-least-once 配信時の冪等性を担保する必要がある
- **チームスキル**: Java 開発者であれば SQL に慣れている。ORM のクエリ DSL（JPQL / Criteria）の学習負荷を避けたい
- **参考プロジェクトとの整合**: 参考実装の 1 つ（`tmp/case-study-cargo-tracker`）は MyBatis を採用しており、運用ノウハウとサンプルコードが利用できる

代表的な選択肢として次の 4 つを評価した。

### 候補 1: Spring Data JPA + Hibernate

- 長所：
  - Spring Boot 標準。エコシステムと書籍・ノウハウが豊富
  - Entity 駆動でアプリ層からのアクセスがシンプル
  - Axon の参考実装（Chapter 6）が JPA ベースで、コード移植が容易
- 短所：
  - 複雑な SQL では JPQL / Criteria の表現力が不足し、結局 `@Query` で生 SQL を書く局面が多い
  - N+1 問題、Lazy Loading の事故が発生しやすい
  - SQL ログから実際のクエリが読みにくい（パラメータ・別名・キャッシュの影響）
  - Read Model 用途には機能過多（Dirty Checking や永続化コンテキスト管理は不要）

### 候補 2: MyBatis + mybatis-spring-boot-starter（採用）

- 長所：
  - **SQL がそのまま見える**。XML / Annotation で SQL を明示し、SQL ログとも 1:1 対応
  - **Projection に最適**: フラットな DTO に画面表示用 SQL の結果を直接マッピング
  - ResultMap で結合・ネスト構造のマッピングも柔軟に表現可能
  - 動的 SQL（`<if>`, `<choose>`, `<foreach>`）が直感的
  - Read Model の更新（INSERT / UPDATE）も明示的で、何が起きているか追跡しやすい
  - 参考プロジェクトと整合（コード移植・運用ノウハウ流用）
- 短所：
  - スキーマ変更時に Mapper XML の同時更新が必要（IDE / プラグインで補助）
  - 開発初期のボイラープレートが JPA より多い
  - Java 開発者間で「SQL 直書きはレガシー」という偏見が一部にあり、教育が必要

### 候補 3: JdbcTemplate / NamedParameterJdbcTemplate

- 長所：Spring Framework 標準・最も軽量
- 短所：
  - SQL を Java 文字列で書くため、複数行 SQL の保守性が低い
  - ResultMap 相当の機能がなく、`RowMapper` を多数書く必要がある
  - MyBatis の動的 SQL や型ハンドラといった機能がない

### 候補 4: jOOQ

- 長所：型安全な SQL DSL、SQL 互換性が高い
- 短所：
  - 商用ライセンスが必要（PostgreSQL でも商用ライセンス対象になる場合あり）
  - 学習コストが高い
  - 国内事例が少なく、運用ノウハウの蓄積が薄い

## 決定

**Read Model / Auth DB のデータアクセス技術として MyBatis を採用する。** Spring Boot 統合は `mybatis-spring-boot-starter` を使用し、Axon Framework の Token / Saga Store は JDBC 実装（`JdbcTokenStore` / `JdbcSagaStore`）を組み合わせる。

### 変更箇所

採用バージョンと適用範囲：

| 項目 | 内容 |
| :--- | :--- |
| MyBatis | 3.5.x |
| Spring 統合 | mybatis-spring-boot-starter 3.0.x |
| Axon Token Store | `org.axonframework.eventhandling.tokenstore.jdbc.JdbcTokenStore` |
| Axon Saga Store | `org.axonframework.modelling.saga.repository.jdbc.JdbcSagaStore` |
| Mapper 配置 | `src/main/resources/mybatis/<Entity>Mapper.xml`（XML 基本、単純 CRUD は Annotation 可） |
| Projection POJO | 純粋な POJO（JPA アノテーション禁止） |
| トランザクション | Spring `@Transactional` + `DataSourceTransactionManager` |
| 同一 DataSource | Read Model と Axon Token / Saga Store を同居（同一 JDBC トランザクション）|
| マイグレーション | Flyway（`V<num>__<desc>.sql`）。Axon の `token_entry` 等もアプリ側で管理 |
| 設定 | `mybatis.configuration.map-underscore-to-camel-case: true`、`mybatis.configuration.cache-enabled: false` |
| 命名規則 | DB カラム snake_case ↔ Java フィールド camelCase（自動変換） |
| SQL バインド | `#{...}`（プリペアド）のみ許可、`${...}`（文字列連結）は原則禁止 |

### 適用範囲

- **対象**: bookingms / routingms / trackingms / handlingms / billingms の Read Model、authms の状態 DB
- **対象外**: Aggregate の永続化（Axon Event Store が担当、フレームワーク非依存）

### 代替案

| 案 | 却下理由 |
| :--- | :--- |
| Spring Data JPA + Hibernate | 複雑 SQL の表現力不足、N+1 リスク、Read Model 用途には機能過多 |
| JdbcTemplate | ResultMap・動的 SQL がなく Mapper 個別実装が増える |
| jOOQ | 商用ライセンス、学習コスト、国内事例の少なさ |

## 影響

### ポジティブ

- **SQL のチューニングが容易**: 実行計画と Mapper の SQL が 1:1 で照合できる
- **複雑な Read Model クエリに柔軟対応**: 経路候補算出・追跡履歴・請求集計のような JOIN/GROUP BY を多用するクエリを自然に書ける
- **Axon との親和性**: `JdbcTokenStore` / `JdbcSagaStore` と同一 DataSource を共有し、`@EventHandler` 内で Projection 更新と Token 更新を同一トランザクションで処理可能（at-least-once の冪等性担保）
- **学習コストの低さ**: SQL を知っていれば理解できる。新規メンバーのオンボーディングが速い
- **参考プロジェクトの活用**: `tmp/case-study-cargo-tracker` のサンプル実装をそのまま参考にできる
- **OWASP A03（インジェクション）対策**: MyBatis の `#{...}` バインド変数を強制することで、ArchUnit 等で自動検証可能

### ネガティブ

- **Mapper XML 保守**: スキーマ変更時に XML の更新が必要。IDE（IntelliJ IDEA / VS Code MyBatisX プラグイン）で補助
- **ボイラープレート**: 単純な CRUD でも Mapper を書く必要があり、JPA に比べて初期コストが大きい
- **Dirty Checking 不在**: エンティティの変更を自動検出せず、明示的に UPDATE を書く必要がある（Read Model 用途では問題にならない、むしろ明示性が利点）
- **Axon 参考実装からの読み替え**: Chapter 6 の JPA ベースサンプルを MyBatis に置換するコストが発生する（ただし `tmp/case-study-cargo-tracker` がこの読み替えのリファレンス）
- **JPA エコシステムの一部が使えない**: Spring Data の `Pageable` / `Specification` 等は直接利用不可（MyBatis-PageHelper 等のプラグインで代替）

## コンプライアンス

次の項目を ArchUnit と CI で自動検証する。

- 各マイクロサービスの `build.gradle` に `mybatis-spring-boot-starter` 依存が含まれていること
- `org.springframework.boot:spring-boot-starter-data-jpa` および `org.hibernate.*` への依存が **存在しないこと**
- `jakarta.persistence.*` への import が **存在しないこと**（ArchUnit ルールで禁止）
- すべての Mapper インターフェースが `@Mapper` アノテーション付与または `@MapperScan` で検出されること
- Mapper XML は `src/main/resources/mybatis/` 配下に配置されること
- Axon の `TokenStore` Bean が `JdbcTokenStore` 実装であること（Application Context 検証）
- Axon の `SagaStore` Bean が `JdbcSagaStore` 実装であること
- Flyway マイグレーションに Axon Token Store / Saga Store のテーブル定義（`token_entry`、`saga_entry`、`association_value_entry`）が含まれていること
- Mapper SQL で `${...}` を使用していないこと（プレースホルダ偽装攻撃の防止、SQL ファイル静的解析）

### ArchUnit ルール例

```java
@ArchTest
static final ArchRule no_jpa_dependency =
    noClasses().should().dependOnClassesThat().resideInAPackage("jakarta.persistence..")
        .as("JPA は採用しない（ADR-0002）。MyBatis Mapper を使用すること");

@ArchTest
static final ArchRule mappers_must_be_annotated =
    classes().that().resideInAPackage("..infrastructure.repositories.mybatis..")
        .and().areInterfaces()
        .should().beAnnotatedWith(Mapper.class);

@ArchTest
static final ArchRule projections_must_be_pojo =
    noClasses().that().resideInAPackage("..domain.projections..")
        .should().beAnnotatedWith("jakarta.persistence.Entity")
        .as("Projection は POJO で実装する（ADR-0002）");
```

## 備考

- 著者: アーキテクト
- 関連 ADR: [ADR-0001 メッセージング基盤として Axon Framework 5 を採用する](0001-axon-framework-adoption.md)
- 参照:
  - [バックエンドアーキテクチャ](../design/architecture_backend.md)
  - [データモデル設計](../design/data-model.md)
  - [技術スタック](../design/tech_stack.md)
  - 参考プロジェクト `tmp/case-study-cargo-tracker/docs/design/architecture_backend.md`（MyBatis 採用の元アイデア）
  - Practical DDD in Enterprise Java（Chapter 6、JPA ベース。MyBatis への読み替えポイントの把握）
- 関連コミット: `989d323 refactor(design): データアクセスを JPA から MyBatis に変更`
