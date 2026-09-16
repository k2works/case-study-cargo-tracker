---
type: Article
title: "第 4 章：Spring Platform × EDA"
description: "Spring プラットフォーム上でイベント駆動アーキテクチャとして Cargo Tracker を構成する（draft-1）。サービス分割、REST とメッセージング、イベント契約。"
tags: [article, practical-ddd-in-enterprise-java]
status: stable
generated: { by: claude-code/claude-opus-5, at: 2026-09-03T00:00:00Z }
---

# 第 4 章：Spring Platform × EDA

- アプリケーション設計のための様々なDDD成果物をモデルリングプロセスを進めてCargo Trackerアプリケーション設計を詳細化した。
  - 荷物追跡問題領域/コアドメインに特定してCargo Trackerアプリケーションを問題領域のソリューションに割り当てた。
  - Cargo Trackerアプリケーションのサブドメイン/境界づけられたコンテキストを特定した。
  - 集約、エンティティ、値オブジェクトそしてドメインルールを含むドメインモデルを各境界づけられたコンテキスト毎に詳細化した。
  - 境界づけられたコンテキストで要求されるサポートドメインサービスを特定した。
  - 境界づけられたコンテキスト内の様々なオペレーションを特定した。（コマンド、クエリ、イベントそしてサガ）
  - Spring プラットフォームを使ったモノリシック版Cargo Trackerアプリケーションを実装した。
- この章ではCargo Trackerアプリケーションをマイクロサービスアーキテクチャを使って設計しこれまで同様にDDDアーティファクトをSpringプラットフォームで実装します。

## Spring プラットフォーム

- Spring Platform(https://spring.io/)はエンタープライズアプリケーションを構築するJavaの主流フレームワークです。
- 第 3 章で見たプロジェクトポートフォリオは変わりません。変わるのは**どの区画を使うか**です。
  - Core Infrastructure Projects:第 3 章と同じく中核として使います
  - Data Operations and Management Projects:Spring AMQP がここで加わります
  - Cloud Technologies Projects:Spring Cloud Gateway がここで加わります
  - Other Projects:springdoc-openapi による API ドキュメント

```plantuml
@startuml
title Spring Platform projects（本章で使う区画）

package "Core Infrastructure Projects" {
    [Spring Framework] #LightYellow
    [Spring Boot] #LightYellow
    [Spring Security]
    [Spring Integration]
    [Spring Batch]
    [Spring Modulith]
}
package "Data Operations and Management Projects" {
    [Spring Data]
    [Spring AMQP] #LightYellow
    [Spring for Apache Kafka]
    [Spring for Apache Pulsar]
}
package "Cloud Technologies Projects" {
    [Spring Cloud] #LightYellow
    [Spring AI]
    [Spring gRPC]
    [Micrometer]
}
package "Other Projects" {
    [Spring for GraphQL]
    [Spring HATEOAS]
    [Spring REST Docs]
    [Spring Web Services]
}
@enduml
```

> 色を付けたものが本章の実装で実際に使われている区画です。第 3 章では Core Infrastructure Projects だけで足りていました。

### EDA プラットフォームへの要求

- BC をプロセスへ分けると、プラットフォームへの要求はモジュラーモノリスの上位集合になります。
- 分類は第 3 章と同じ 5 つです。**分類そのものは変わらず、各分類の中身が外部化されます。**
  - Business Logic Patterns:サービスの内側の業務ロジック
  - Communication Patterns:サービスをまたぐ通信
  - Distributed Transaction Management Patterns:整合性の担保
  - Deployment Patterns:配置と成果物
  - Readiness Patterns:運用準備（可観測性）

```plantuml
@startuml

title EDA プラットフォーム要求

package "Microservices Platform" {
    package "Business Logic Patterns" {
        [API Development]
        [Domain Model]
        [Data Processing]
        [Data Management]
        [Documentation]
    }
    package "Communication Patterns" {
        [Synchronous Communication]
        [Asynchronous Communication]
        [Domain Events]
        [Anti-Corruption Layer]
        [Messaging Integration]
        [API Gateway]
    }
    package "Distributed Transaction Management Patterns" {
        [Local ACID Transaction]
        [Transaction Propagation]
        [Transactional Event Publication]
        [Dead Letter Handling]
        [Saga]
    }
    package "Deployment Patterns" {
        [Multiple Deployment Units]
        [Executable JAR]
        [Container Image]
        [Externalized Configuration]
        [Database Migration]
        [Database per Service]
    }
    package "Readiness Patterns" {
        [Health Check]
        [Metrics]
        [Info Endpoint]
        [Logging]
        [Distributed Tracing]
    }
}

@enduml
```

- それぞれの要求を、Spring プロジェクトポートフォリオと本章の実装へ対応づけます。
- **第 3 章から変わった行に印を付けます。**

| 分類 | 要求 | 対応する Spring プロジェクト | 本章での実装 | 第 3 章から |
| :--- | :--- | :--- | :--- | :--- |
| Business Logic | API Development | Spring Framework（Spring MVC）／Spring Boot | `interfaces.rest` の `@RestController` | 変更（画面 → API） |
| Business Logic | Domain Model | （フレームワーク非依存のプレーン Java） | `domain.model` の集約・値オブジェクト | 同じ |
| Business Logic | Data Processing | Spring Framework（`@Transactional`） | `application.internal.commandservices` の UseCase | 同じ |
| Business Logic | Data Management | MyBatis Spring Boot Starter | `infrastructure.repositories` の Mapper 実装 | 同じ |
| Business Logic | Documentation | springdoc-openapi | サービスごとの `/swagger-ui` | 同じ |
| Communication | Synchronous Communication | Spring Framework（`RestClient`） | `RestRouteCandidateFinder`（サービス間 HTTP） | **変更**（DI → HTTP） |
| Communication | Asynchronous Communication | Spring AMQP | `RabbitTemplate` / `@RabbitListener` | **変更**（プロセス内 → ブローカー） |
| Communication | Domain Events | Spring AMQP | `TrackingNumberIssued` などの契約イベント | **変更** |
| Communication | Anti-Corruption Layer | Spring Framework（インターフェース + 実装） | `outboundservices.acl` + `infrastructure.acl` | 同じ |
| Communication | Messaging Integration | Spring AMQP（Spring Cloud Stream は不採用） | 交換機とルーティングキーを直接名指しする | **追加** |
| Communication | API Gateway | Spring Cloud Gateway | `gatewayms` | **追加** |
| Distributed Transaction | Local ACID Transaction | Spring Framework（`@Transactional`） | サービス自身の DB に閉じる | 範囲が縮小 |
| Distributed Transaction | Transaction Propagation | Spring Framework | UseCase をトランザクション境界とする | 同じ |
| Distributed Transaction | Transactional Event Publication | Spring Framework（`TransactionSynchronizationManager`） | `RabbitCargoEventNotifier` の `afterCommit` | **変更**（購読側の注釈 → 発行側の自作） |
| Distributed Transaction | Dead Letter Handling | Spring AMQP | デッドレターキューと予備の交換機 | **追加** |
| Distributed Transaction | Saga | Axon Framework など | 本章では未使用（第 5 章で導入） | 同じ |
| Deployment | Multiple Deployment Units | Spring Boot | 8 サービスを個別に起動する | **変更**（単一 → 複数） |
| Deployment | Executable JAR | Spring Boot（`bootJar`） | サービスごとの実行可能 JAR | 単位が増加 |
| Deployment | Container Image | Spring Boot ＋ Dockerfile | サービスごとの `Dockerfile` | 単位が増加 |
| Deployment | Externalized Configuration | Spring Boot（環境変数／Profile） | 既定値つき環境変数と `application-product.yml` | 同じ |
| Deployment | Database Migration | Flyway | サービスごとの `db/migration` | 単位が増加 |
| Deployment | Database per Service | （構成上の判断） | `booking_db` / `routing_db` / `tracking_db` … | **追加** |
| Readiness | Health Check | Spring Boot Actuator | `/actuator/health`（probes 有効） | 単位が増加 |
| Readiness | Metrics | Spring Boot Actuator ／ Micrometer | `/actuator/metrics` | 単位が増加 |
| Readiness | Info Endpoint | Spring Boot Actuator | `/actuator/info` | 単位が増加 |
| Readiness | Logging | Spring Boot（Logback） | 既定構成 | 同じ |
| Readiness | Distributed Tracing | Micrometer Tracing | **未導入** | **必要になったが無い** |

- Business Logic Patterns はほとんど変わりません。**業務ロジックの書き方は配置の仕方に依存しません。**
- Communication Patterns と Distributed Transaction Management Patterns が、プロセスの外へ出ます。
  - 第 3 章では DI・アプリケーションイベント・単一 DB のローカルトランザクションで満たしていました。
  - 本章ではそれぞれ HTTP・メッセージブローカー・サービスごとの DB に置き換わります。
  - **置き換えると要求が 2 つ増えます**（Messaging Integration と Dead Letter Handling）。届かないことが起こりうるからです。
- Deployment Patterns と Readiness Patterns は、要求そのものは変わらず**単位が 8 倍になります**。
  - Spring Boot と Actuator が提供する部分は据え置きです。
  - 増えるのは「同じことを 8 回やる」コストであり、これは自動化で吸収する種類のものです。
- 一方 Distributed Tracing は、**分散したことで初めて必要になり、まだ導入されていません**。
  - 第 3 章では「分散構成となる第 4 章以降の検討事項」と書きました。本章の実装でもまだ検討事項のままです。
  - 1 つの要求を追ってサービスを 3 つ跨いだとき、いまは各サービスのログを時刻で突き合わせるしかありません。

### Spring Boot: 機能

- 前節の要求一覧のうち、Deployment Patterns と Readiness Patterns の大半は Spring Boot が肩代わりします。
- ここは第 3 章と変わりません。**変わるのは、同じ肩代わりをサービスの数だけ用意することです。**

#### 自動構成とスターター

- 各サービスが宣言するスターターは、第 3 章の一覧から画面用のものが抜け、メッセージング用のものが加わった形です。

```gradle
implementation 'org.springframework.boot:spring-boot-starter-web'
implementation 'org.springframework.boot:spring-boot-starter-validation'
implementation 'org.springframework.boot:spring-boot-starter-actuator'
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2'
implementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter:4.0.1'
implementation 'org.springframework.boot:spring-boot-starter-flyway'
implementation 'org.springframework.boot:spring-boot-starter-amqp'
```

転記元: `bookingms/build.gradle`

- 抜けたのは `spring-boot-starter-thymeleaf` と `spring-boot-starter-security` です。
  - 画面は別のフロントエンド（`apps/frontend`）が担います。
  - 認証は Gateway に集約したため、各サービスは Spring Security を持ちません。
- 加わったのは `spring-boot-starter-amqp` です。**プロセスが分かれた瞬間に、メッセージングが必要になります。**
- 共有ライブラリもスターターと同じく依存として宣言します。

```gradle
implementation project(':shared')
```

転記元: `bookingms/build.gradle`

- **1 行の依存が、8 サービスすべてを同時に壊しうる場所です。**共有カーネルを 2 要素に絞る判断（第 3 章）が、ここでは「再デプロイの範囲」という形で効いてきます。

#### 起動クラスとコンポーネントスキャン

- `@SpringBootApplication` を付けたクラスのパッケージが、コンポーネントスキャンの起点になります。

```java
@SpringBootApplication
public class BookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookingApplication.class, args);
    }
}
```

転記元: `bookingms/BookingApplication.java`

- 第 3 章との違いは**スキャンの範囲**です。
  - 第 3 章の起動クラスは全 BC のトップレベルパッケージの親にあり、それがすべての BC を 1 プロセスにまとめる根拠でした。
  - 本章の起動クラスは 1 つの BC の親にしかいません。**他の BC はクラスパスに存在しないため、スキャンしようがありません。**
- 起動クラスに BC の一覧が現れないのもこのためです。サービスの一覧はビルドの構成が持ちます。

```gradle
include 'shared'
include 'gatewayms'
include 'authms'
include 'bookingms'
include 'routingms'
include 'trackingms'
include 'handlingms'
include 'billingms'
include 'simulationms'
```

転記元: `settings.gradle`

#### 外部化された設定

- 設定は**既定値つきの環境変数**で受けます。Profile 別ファイルは本番（Heroku）向けの 1 つだけです。

```yaml
spring:
  application:
    name: bookingms
  datasource:
    url: ${DB_URL:jdbc:h2:mem:booking_db;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH}
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
```

転記元: `bookingms/src/main/resources/application.yml`

- 分散で新しく現れるのが**相手の所在**です。第 3 章にはメソッド呼び出ししかなく、相手の住所という概念がありませんでした。

```yaml
  # **相手の所在は環境ごとに上書きする。**
  #
  # 既定値は**開発機（localhost）を指す**。クラスタの中では自分自身を指すことになり、
  # 入れ忘れても起動は成功して、その相手を使う操作だけが失敗する
  # ——IT5（経路の確定）と IT12（見積の候補）で 2 度踏んだ。
  # **デプロイの手順（k8s マニフェスト・deploy.js）が必ず渡す。**
  routing-service:
    # 経路の確定時に候補の成立を確かめる先（ADR-019 決定 2）
    base-url: ${APP_ROUTING_SERVICE_BASE_URL:http://localhost:8083}
```

転記元: `bookingms/src/main/resources/application.yml`

- **既定値があることが、この設定の危険な点です。**
  - 設定漏れが起動失敗にならず、その相手を使う操作だけが失敗します。
  - 第 3 章の自動構成で見た「起動は成功するが効いていない」と同じ失敗の形が、分散では相手ごとに現れます。
- 設定の受け取り方は 2 通りあり、本章の実装では大半が `@Value` です。

```java
    /** 業務日付は業務タイムゾーンで判断する。UTC で判断すると「当日」の扱いがずれる時間帯ができる。 */
    @Bean
    public Clock clock(@Value("${app.business-time-zone:Asia/Tokyo}") String zoneId) {
        return Clock.system(ZoneId.of(zoneId));
    }
```

転記元: `bookingms/infrastructure/config/BookingConfig.java`

- `@ConfigurationProperties` を使うのは gatewayms と simulationms の 2 つだけです。
  - 第 3 章が `@ConfigurationPropertiesScan` で型として受けていたのに対し、こちらは 1 つずつの値として受けています。
  - **設定項目が少ないうちは差が出ませんが、綴り間違いが起動時に判明しない点は変わりません。**

#### 成果物の切り分け

- 成果物はサービスごとの実行可能 JAR とコンテナイメージです。

```dockerfile
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Heroku Container Runtime は $PORT を注入する。ローカル・kind では既定値を使う。
ENV PORT=8082

RUN addgroup -S spring && adduser -S spring -G spring
COPY build/libs/*.jar /app/app.jar
USER spring
```

転記元: `bookingms/Dockerfile`

- Deployment Patterns の Multiple Deployment Units・Executable JAR・Container Image は、この組み合わせで満たされます。
- **第 3 章と判断が分かれた箇所があります。**H2 の扱いです。

```gradle
runtimeOnly 'org.springframework.boot:spring-boot-h2console'
runtimeOnly 'com.h2database:h2'
runtimeOnly 'org.postgresql:postgresql'
developmentOnly 'org.springframework.boot:spring-boot-devtools'
```

転記元: `bookingms/build.gradle`

- 第 3 章の実装は H2 を `developmentOnly` に置き、本番成果物から締め出したうえで、それをビルドの検証タスクで強制していました。
- 本章の実装では H2 が `runtimeOnly` にあり、**本番の実行クラスパスに入ります**。既定の接続先も H2 のインメモリです。
- **同じ題材でも、実装ごとに判断は分かれます。**どちらが正しいかではなく、
  - 第 3 章は「設定ミスで本番が H2 につながる経路そのものを消す」ことを選び、
  - 本章は「どのサービスも DB 無しで起動できる」ことを選んだ、という違いです。
- 8 つのサービスを開発機で同時に立ち上げる構成では、後者の利便が効きます。**代わりに、締め出しを保証していた検査は失われます。**

#### 運用エンドポイント（Actuator）

- Readiness Patterns は Actuator の公開設定に集約されます。ここも第 3 章と同じ形です。

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      probes:
        enabled: true
      show-details: when-authorized
```

転記元: `bookingms/src/main/resources/application.yml`

- 第 3 章と違うのは `probes: enabled` です。
  - Kubernetes の liveness / readiness プローブ用に `/actuator/health/liveness` と `/actuator/health/readiness` が分かれます。
  - **単一プロセスなら「動いているか」の 1 つで足りますが、8 つのサービスが互いを待つ構成では「起動したか」と「要求を受けられるか」を分ける必要があります。**
- **監視の対象は 8 倍になります。**Readiness Patterns の要求は変わっていませんが、それを満たす作業量は配置の数に比例します。

### Spring Cloud

- 第 3 章では採用していなかった Spring Cloud が、ここで登場します。ただし**使うのは Gateway だけ**です。

```gradle
implementation 'org.springframework.cloud:spring-cloud-starter-gateway-server-webflux:5.0.1'
```

転記元: `gatewayms/build.gradle`

- Gateway は入口を 1 つにまとめ、パスでサービスへ振り分けます。

```yaml
          routes:
            - id: authms
              uri: ${AUTHMS_URI:http://localhost:8081}
            - id: bookingms
              uri: ${BOOKINGMS_URI:http://localhost:8082}
            - id: routingms
              uri: ${ROUTINGMS_URI:http://localhost:8083}
              predicates:
                - Path=/api/v1/voyages/**,/api/v1/routes/**
```

転記元: `gatewayms/src/main/resources/application.yml`

- Gateway が担うのは**認証と JWT の署名検証**です。各サービスは Gateway が付与した検証済みのヘッダを信頼し、**ロールに基づく認可だけ**を行います。

```java
/**
 * 予約コンテキストの REST エンドポイント。
 *
 * <p>HTTP と業務の境界である。<strong>ここで行うのはロールに基づく認可（403）だけ</strong>で、
 * 認証（401）と JWT の署名検証は API Gateway が担う（ADR-004）。Gateway が付与した
 * 検証済みクレーム（{@code X-Authenticated-*}）を信頼する。
 *
 * <p><strong>業務の不変条件をここに書かない。</strong> 入力検証は利用者への案内であり、
 * モデルの正しさはドメイン層が担保する。
 */
package com.example.bookingms.interfaces.rest;
```

転記元: `bookingms/interfaces/rest/package-info.java`

- **Spring Cloud Stream は使っていません。**メッセージングは `spring-boot-starter-amqp`、つまり素の AMQP です。
  - 抽象化のレイヤを 1 枚重ねる代わりに、交換機とルーティングキーを自分で名指しする形を採っています。
  - この選択の代償は、あとの「送信サービス：メッセージブローカー」で見ます。
- Cloud Technologies Projects の他のプロジェクト（サービスディスカバリ・分散設定・サーキットブレーカ）も採用していません。
  - サービスの所在は環境変数で渡し、設定はコンテナの環境に持たせています。
  - **8 サービス程度では、専用の基盤を入れるより設定で足ります。**要るかどうかを決めるのは規模です。

### Spring Framework のまとめ

- Spring Framework の役割は第 3 章と変わりません。変わったのは**イベントの運び方**だけです。

| 機能 | 第 3 章（モジュラーモノリス） | 本章（マイクロサービス） |
| :--- | :--- | :--- |
| DI | 同じ | 同じ |
| 宣言的トランザクション | 同じ（1 DB） | サービスごとの DB に閉じる |
| イベント | `ApplicationEventPublisher` | RabbitMQ（`RabbitTemplate` / `@RabbitListener`） |
| Web | `@Controller`（画面） | `@RestController`（API） |

- ドメイン層がフレームワークを知らない点も変わりません。

```java
/**
 * 業務の言葉と規則を置く層。もっとも内側であり、どの層にも依存しない。
 *
 * <p>構成の詳細は各サブパッケージの説明を参照。依存は常に外から内へ向かう。
 */
package com.example.bookingms.domain;
```

転記元: `bookingms/domain/package-info.java`

- **この一貫性が、実装方式の切り替えを可能にしています。**
  - 第 3 章のドメイン層と本章のドメイン層は、置かれている技術基盤が違っても同じ規律で書かれています。
  - 差し替わったのは外側だけです。

## EDA としての Cargo Tracker

### 境界づけられたコンテキスト

- マイクロサービスアーキテクチャスタイルでは各境界づけられたコンテキスト毎に自己完結した独立したデプロイ単位として他の境界づけられたコンテキストと依存関係をもちません。
- Cargo Trackerアプリケーションを複数のマイクロサービスに分割するパターンは、今まで通りコアドメインをビジネスケイパビリティ/サブドメインそしてそれぞれ分割された境界づけられたコンテキストのソリューションの組み合わせに分割するのと同じです。
- Spring Bootアプリケーションとして生成されるアーティファクトは自己完結ファットJARファイルとして必要な依存関係と設定を含みます。
- ファットJARファイルは組み込みWebコンテキストもランタイムに含みます。
- これにより他の外部アプリケーションサーバーを必要としません。

```plantuml
@startuml

title Anatomy of a Spring Boot application

left to right direction

component "Spring Boot Application" as app
component "Dependencies" as dep
component "Configuration" as conf
component "Runtime" as runtime

app --> dep
app --> conf
app --> runtime

@enduml
```

- マイクロサービスはそれぞれの状態を保存するデータストアを必要とする。
- Database per service patternを採用する。このパターンはマイクロサービス毎に個別のデータストアを持ちます。

```plantuml
@startuml

title Spring Bootベースマイクロサービスのデプロイメントアーキテクチャ


package "Bounded Context" {
   [Booking]
   [Tracking]
   [Routing]
   [Handling]
}

package "Booking Microservice" {
    [bookingms]
    database DB_1 [
       bookingdb
    ]
    [bookingms] --> DB_1
}
package "Tracking Microservice" {
    [trackingms]
    database DB_2 [
       trackingdb
    ]
    [trackingms] --> DB_2
}
package "Routing Microservice" {
    [routingms]
    database DB_3 [
       routingdb
    ]
    [routingms] --> DB_3
}
package "Handling Microservice" {
    [handlingms]
    database DB_4 [
       handlingdb
    ]
    [handlingms] --> DB_4
}

[Booking] --> "Booking Microservice"
[Tracking] --> "Tracking Microservice"
[Handling] --> "Handling Microservice"
[Routing] --> "Routing Microservice"

@enduml
```

#### 境界づけられたコンテキスト：パッケージング

- パッケージングを開始するにあたって最初にやることをは一般的なSpring Bootアプリケーションを作ることです。
- 以下の構成のプロジェクトを作ります。
  - Group - com.example.cargotracker
  - Artifact - bookingms
  - Dependencies - Spring Web Starter, MyBatis Spring Boot Starter, and Spring Cloud Stream
- 構築したプロジェクトはJARファイル(bookingms.jar)にまとめれます。そして `java -jar bookingms.jar` コマンドを使って起動します。

#### 境界づけられたコンテキスト：パッケージ構造

- パッケージングの構造を決定したので次は境界づけられたコンテキストの各パッケージ構造を定義します。
- 境界づけられたコンテキストの上位レベルのパッケージ構造は以下のようになります。

```plantuml
@startuml

title Package structure for the Bounded Contexts

left to right direction

package {
 [interfaces]
}
package {
 [application]
}
package { 
 [domain]
}
package {
 [infrastructure]
}

artifact "Inbound Adapter(s)"
artifact "Application Services"
artifact "Domain Model"
artifact "Outbound Adapter(s)"

interfaces --> "Inbound Adapter(s)"
application --> "Application Services"
domain --> "Domain Model"
infrastructure --> "Outbound Adapter(s)"
@enduml
```

##### インターフェース層（interfaces）

- このパッケージはコミュニケーションプロトコルで分類された境界づけられた全てのコンテキストインバウンドインターフェイスを包含します。
- 具体的には予約の境界づけられたコンテキストはコマンドと呼ばれる状態変更リクエストを送るREST APIsを提供します。同様にクエリと呼ばれる状態取得リクエストを送るREST APIsを提供します。これらのグループは `rest` パッケージに分類されます。
- 他の境界づけられたコンテキストで生成された様々なイベントを購読するイベントハンドラは `eventhandlers` パッケージに分類されます。
- これら2つのパッケージに加えて `transform` パッケージも含みます。これは入ってくるAPIリソース/イベントデータをドメインモデルで要求されるコマンド/クエリモデルに変換するために使われます。

```plantuml
@startuml

title Package structure for interfaces

left to right direction

package {
    [interfaces]
}
package {
    [transform]
}
package {
    [rest]
}
package {
    [eventhandlers]
}

[interfaces] -> [transform]
[interfaces] --> [rest]
[interfaces] --> [eventhandlers]
@enduml
```

##### アプリケーション層（application）

- アプリケーションサービスは境界づけられたコンテキストのドメインモデルのためのファサードの役割を果たします。
- ドメインモデルの根底となる仕事をコマンド/クエリに割り当てるファサードサービスを提供します。
- アプリケーションサービスの役割として
  - コマンドとクエリ割当として参加する
  - コマンド/クエリプロセッシングに必要なインフラコンポーネントを呼び出す
  - ドメインモデルの根底となる中心的関心毎(ロギング、セキュリティ、メトリクス)を提供する
  - 他の境界づけられたコンテキストを呼び出せるようにする

```plantuml
@startuml

title Package structure for Application services

left to right direction

package {
    [application]
}
package {
    [internal]
}
package {
    [commandservices]
}
package {
    [queryservices]
}
package {
    [outboundservices]
}

[application] --> [internal]
[internal] --> [commandservices]
[internal] --> [queryservices]
[internal] --> [outboundservices]

@enduml
```

##### ドメイン層（domain）

- このパッケージは境界づけられたコンテキストのドメインモデルを含みます。
- 以下が境界づけられたコンテキストの中心的なクラスです。
  - Aggregates
  - Entities
  - Value Objects
  - Commands
  - Events

```plantuml
@startuml

title Package structure for our domain model

left to right direction

package {
    [domain]
}
package {
    [model]
}
package {
    [commands]
}
package {
    [aggregates]
}
package {
    [entities]
}
package {
    [valueobjects]
}
package {
    [evnts]
}

[domain] --> [model]
[model] --> [commands]
[model] --> [aggregates]
[model] --> [entities]
[model] --> [valueobjects]
[model] --> [evnts]

@enduml
```

##### インフラストラクチャ層（infrastructure）

- インフラストラクチャパッケージには3つの主要な目的があります。
  - 境界づけられたコンテキストがその状態の操作を受け付けたときに操作を実行する根底となるレポジトリの実装を決める。
  - 境界づけられたコンテキストが状態の変更イベントを扱うときに操作を実行する根底となるイベントブローカーの実装を決める。
  - インフラストラクチャレイヤーのSpring Boot特有の設定を決める。

```plantuml
@startuml
title Package structure for the infrastructure components

left to right direction

package {
    [configuration]
}
package {
    [infrastructure]
}
package {
    [repositories]
}
package {
    [jpa]
}
package {
    [jdbc]
}
package {
    [brokers]
}
package {
    [rabbitmq]
}

[configuration] <- [infrastructure]
[infrastructure] --> [repositories]
[repositories] --> [jpa]
[repositories] --> [jdbc]
[infrastructure] --> [brokers]
[brokers] --> [rabbitmq]

@enduml
```

```plantuml
@startuml

title Package structure for any of our Bounded Context

left to right direction

package {
    [boundedcontext]
}
package {
    [application]
}
package {
    [internal]
}
rectangle {
    [commandservices]
    [queryservices]
    [outboundservices]
}
package {
    [domain]
}
package {
    [model]
}
rectangle {
    [commands]
    [aggregates]
    [entities]
    [valueobjects]
    [domainevents]
}
package {
    [infrastructure]
}
package {
    [brokers]
}
package {
    [repositories]
}
package {
    [services]
}
rectangle {
    [jpa]
    [jdbc]
    [rabbitmq]
    [http]
}
package {
    [interfaces]
}
package {
    [rest]
}
package {
    [events]
}

[boundedcontext] --> [application]
[boundedcontext] --> [domain]
[boundedcontext] --> [infrastructure]
[boundedcontext] --> [interfaces]
[application] --> [internal]
[internal] --> [commandservices]
[internal] --> [queryservices]
[internal] --> [outboundservices]
[domain] --> [model]
[model] --> [commands]
[model] --> [aggregates]
[model] --> [entities]
[model] --> [valueobjects]
[model] --> [domainevents]
[infrastructure] --> [brokers]
[brokers] --> [rabbitmq]
[infrastructure] --> [repositories]
[repositories] --> [jpa]
[repositories] --> [jdbc]
[infrastructure] --> [services]
[services] --> [http]
[interfaces] --> [rest]
[interfaces] --> [events]

@enduml
```

- Cargo Trackerマイクロサービスアプリケーションに置ける境界づけられたコンテキストの完全な実装です。
- 各境界づけられたコンテキストはSpring Bootアプリケーションのfat JARアーティファクトとして実装されます。
- 境界づけられたコンテキストは明確に関心毎が分離されたパッケージ構造のモジュールとして正しく分類されます。

### Cargo Tracker 実装

- 続いてDDDとSpring Boot/Spring Cloudを適用したマイクロサービスアプリケーションの実装に入ります。
- 2つのグループのアーティファクトを実装する必要があります。
  - コアドメイン/ビジネスロジックを含むドメインモデル
  - コアドメインモデルをサポートするサービスとなるドメインモデルサービス

```plantuml
@startuml
title DDDアーティファクトの論理グループ


skinparam component {
    BackgroundColor<<Domain Model>> #D6EAF8
    BackgroundColor<<Domain Model Service>> #D5F5E3
}

package {
[REST] <<Domain Model Service>>
[Event Handler] <<Domain Model Service>>
}

package {
[Command Services] <<Domain Model Service>>
[Outbound Services] <<Domain Model Service>>
[Query Services] <<Domain Model Service>>
[Repository Services] <<Domain Model Service>>
[Event Services] <<Domain Model Service>>
}

package {
[Aggregates] <<Domain Model>>
[Entities] <<Domain Model>>
[Value Objects] <<Domain Model>>
[Commands] <<Domain Model>>
[Queries] <<Domain Model>>
[Events] <<Domain Model>>
}

package {
[MyBatis Repositories] <<Domain Model Service>>
[Message Broker Infrastructure] <<Domain Model Service>>
[Configuration] <<Domain Model Service>>
}

@enduml
```

- Cargo Trackerマイクロサービスアプリケーションでは各境界づけられたコンテキストで実行されるコマンド、各境界づけられたコンテキストで提供されるクエリ、各境界づけられたコンテキストで購読・発行さるイベントを持っている

```plantuml
@startuml
title Cargo Trackerマイクロサービスソリューション

left to right direction

skinparam component {
    BackgroundColor<<Command>> #D6EAF8
    BackgroundColor<<Query>> #D5F5E3
    BackgroundColor<<Event>> #FADBD8
    BorderColor<<Command>> #2E86C1
    BorderColor<<Query>> #28B463
    BorderColor<<Event>> #CB4335
}

package "Cargo Tracker Microservices" {
    package "Booking Microservice" as BookingMS {
        [Assign Route to Cargo] <<Command>>
        [Book Cargo] <<Command>>
        [Cargo Details] <<Query>>
        
        usecase Booking [
          Booking
        ]
    }
    
    package "Tracking Microservice" as TrackingMS {
        [Assign Tracker to Cargo] <<Command>>
        [Track Cargo] <<Query>>
        
        usecase Tracking [
          Tracking
        ]
    }
    
    [Cargo Booked] <<Event>>
    [Cargo Routed] <<Event>>
    [Cargo Handled] <<Event>>
    
    package "Routing Microservice" as RoutingMS {
        usecase Routing [
          Routing
        ]
        
        [Get Itinerary for Route] <<Query>>
        [Maintain Voyages] <<Command>>
    }
    
    package "Handling Microservice" as HandlingMS {
        usecase Handling [
          Handling
        ]
        
        [Register Handling Activity] <<Command>>
        [Handling History Details] <<Query>>
    }
}

BookingMS -[hidden]right-> TrackingMS
BookingMS -[hidden]down-> RoutingMS
RoutingMS -[hidden]right-> HandlingMS

[Assign Route to Cargo] --> Booking
[Book Cargo] --> Booking
[Cargo Details] --> Booking
Booking --> [Get Itinerary for Route] : REST API
Booking --> [Cargo Booked] : publish
Booking --> [Cargo Routed] : publish
Booking --> [Cargo Handled] : subscribe

[Assign Tracker to Cargo] --> Tracking
[Track Cargo] --> Tracking
Tracking --> [Cargo Routed] : subscribe
Tracking --> [Cargo Handled] : subscribe

Routing <-- [Get Itinerary for Route]
Routing <-- [Maintain Voyages]

Handling <-- [Register Handling Activity]
Handling <-- [Handling History Details]
Handling --> [Cargo Handled] : publish
@enduml
```
### ドメインモデル：実装

- ドメインモデルは境界づけられたコンテキストの中心的機能であり関係するアーティファクトを早い段階で明確にします。
- ドメインモデルアーティファクトは以下を必要とします。
  - コアドメインモデル - 集約、エンティティそして値オブジェクト
  - ドメインモデルオペレーション - コマンド、クエリそしてイベント

#### コアドメインモデル：実装

- 境界づけられたコンテキストのコアドメイン実装は境界づけられたコンテキストのビジネス意図を表すアーティファクトをカバーしている。
- ハイレベルの観点では集約、エンティティそして値オブジェクトの特定と実装です。

##### 集約／エンティティ／値オブジェクト

- 集約はドメインモデルの中心です。

```plantuml
@startuml

title 境界づけられたコンテキスト内の集約

left to right direction

package "Booking Microservice" {
[Cargo]
}

package "Handling Microservice" {
[Handling Activity]
}

package "Routing Microservice" {
[Voyage]
}

package "Tracking Microservice" {
[TrackingActivity]
}

@enduml
```

- 集約の実装は以下に従います。
  - 集約クラスの実装
  - 業務属性によるドメインの豊かさ
  - エンティティ/値オブジェクトの実装

###### 集約クラスの実装

###### 業務属性によるドメインの豊かさ

- どの境界づけられたコンテキストの集約でも境界づけられたコンテキストのビジネス言語を明解に表現するべきです。
- getterやsetterしかない貧血集約はビジネス言語が表したいことが複数のレイヤーに漏れ出てしまいDDDの原則に反することになります。
- ドメインリッチな集約をどうやって実装するか？答えは業務属性と業務メソッドにあります。
- 集約の業務属性は集約状態を属性として技術ではなく業務用語を使って表現するべきです。
- 状態をビジネスコンセプトに変換するには貨物集約の以下の属性に従います。
  - Origin Location of the cargo
  - Booking Amount of the cargo
  - Route specification(Origin Location, Destination Location,Destination Arrival Deadline)
  - Itinerary that the cargo is assigned to based on the Route Specification.Legs that the cargo might be routed through to get to the destination
  - Delivery Progress of the cargo aainst its Route Specification and Itinerary assigned to it.The Delivery Progress provides details on the Routing Status,Transport Status,Current Voyage of the cargo,Last Known Location of the cargo, Next Expected Activity, and the Last Activity that occurred on the cargo.

```plantuml
@startuml
title 荷物集約と依存関係

class BookingId<<Entity>> {
    + BookingId: String
}

class Cargo<<Aggregates>> {
    + bookingId: BookingId
    + bookingAmount: BookingAmount
    + origin: Location
    + routeSpecification: RouteSpecification
    + itinerary: Itinerary
    + delivery: Delivery
}

class BookingAmount<<Value Objects>> {
    + bookingAmount: int
}

class Location<<Value Objects>> {
    + unLocCode: String
}

class CargoItinerary<<Value Objects>> {
    + legs: List<Leg>
}

class RouteSpecification<<Value Objects>> {
    + origin: Location
    + destination: Location
    + arrivalDeadline: Date
}

class Delivery<<Value Objects>> {
    + routingStatus: RoutingStatus
    + transportStatus: TransportStatus
    + arrivalDeadline: Date
    + lastKnownLocation: Location
    + currentVoyage: Voyage
    + nextExpectedActivity: CargoHandlingActivity
    + lastHandledEvent: LastCargoHandleEvent
}

class Leg<<Value Objects>> {
    + voyageNumber: VoyageNumber
    + fromUnLocCode: String
    + toUnLocCode: String
    + loadTime: String
    + unloadTime: String
}

class Voyage<<Entity>>

class CargoHandlingActivity<<Entity>>

class LastCargoHandleEvent<<Entity>>

class TransportStatus<<Value Objects>>

class RoutingStatus<<Value Objects>>

BookingId <-- Cargo
Cargo --> BookingAmount
Cargo --> Location
Cargo --> CargoItinerary
Cargo -> RouteSpecification
Cargo --> Delivery
CargoItinerary --> Leg
Delivery --> Voyage
Delivery --> CargoHandlingActivity
Delivery --> LastCargoHandleEvent
Delivery --> RoutingStatus
Delivery --> TransportStatus

@enduml
```

###### エンティティ／値オブジェクトの実装

```plantuml
@startuml
title 荷役クラス図

class HandlingActivity<<Aggregates>>
class Type<<Value Objects>>
class VoyageNumber<<Entities>>
class HandlingLocation<<Entities>>
class CargoDetails<<Entities>>

HandlingActivity -> Type
HandlingActivity --> VoyageNumber
HandlingActivity --> HandlingLocation
HandlingActivity --> CargoDetails

@enduml
```

```plantuml
@startuml
title 公開集約クラス図

class Voyage<<Aggregates>>
class VoyageNumber<<Entities>>
class Schedule<<Value Objects>>
class CarrierMovements<<Value Objects>>

Voyage --> VoyageNumber
Voyage --> Schedule
Schedule --> CarrierMovements
@enduml
```

```plantuml
@startuml
title 追跡クラス図

class TrackingId<<Entities>>
class TrackingActivity<<Aggregates>>
class HandlingEvent<<Entities>>
class Location<<Entities>>
class VoyageNumber<<Entities>>
class EventDetails<<Entities>>

TrackingId <-- TrackingActivity
TrackingActivity -> HandlingEvent
HandlingEvent --> Location
HandlingEvent --> VoyageNumber
HandlingEvent --> EventDetails
@enduml
```

#### ドメインモデルの操作

- 境界づけられたコンテキストのドメインモデルの操作は境界づけられたコンテキストの集約の状態に関連するものです。
- インバウンド操作（コマンド/クエリ）とアウトバウンド操作（イベント）を含みます。

##### コマンド

- 境界づけられたコンテキストのコマンドの実装は以下の手順に従います。
  - コマンドの特定と実装
  - コマンドを実行するコマンドハンドラの特定と実装
- コマンドの特定には集約の状態に影響を及ぼす操作に注目します。
- コマンドを特定したらコマンドのSpring Boot 実装は一般的なPOJOで実装します。
- コマンドハンドラの目的は入力コマンドを実行して集約の状態を決めることです。コマンドハンドラは集約のドメインモデル内で唯一の状態を変更できる場所です。
- Springフレームワークにコマンドハンドラを実装する特別な仕組みはないのでコマンドハンドラの実装は集約内にメソッドを定義します。

```plantuml
@startuml
title コマンドハンドラの実装

class Cargo <<Aggregate>> {
    + Cargo(BookCargoCommand bookCargoCommand) <<CommandHandler>>
    + assignToRoute(RouteCargoCommand routeCargoCommand) <<CommandHandler>>
}

class BookCargoCommand <<Command>>

class RouteCargoCommand <<Command>>

Cargo --> BookCargoCommand 
Cargo --> RouteCargoCommand 

@enduml
```

##### クエリ

- 境界づけられたコンテキスト内のクエリは外部の利用者に向けて境界づけられたコンテキストの状態を提供する責務を持ちます。
- クエリハンドラは境界づけられたコンテキスト内の集約の状態を表す役割を果たします。

##### ドメインイベント

- 境界づけられたコンテキスト内のイベントは境界づけられたコンテキストの集約の状態変更をイベントとして公開するすべての操作です。
- ドメインイベントはマイクロサービスアーキテクチャにおいて中心的な役割を果たします。そして強固なマナーに従ってクリティカルに実装されます。
- 分散マイクロサービスアーキテクチャにおいてイベントはコレオグラフィメカニズムを使って様々なマイクロサービスベースアプリケーションの境界づけられたコンテキスト間の状態とトランザクションの一貫性を維持します。

```plantuml
@startuml
title マイクロサービスアーキテクチャ内のイベントフロー

left to right direction

skinparam component {
    BackgroundColor<<Command>> #D6EAF8
    BackgroundColor<<Query>> #D5F5E3
    BackgroundColor<<Event>> #FADBD8
    BorderColor<<Command>> #2E86C1
    BorderColor<<Query>> #28B463
    BorderColor<<Event>> #CB4335
}

package "Cargo Tracker Microservices" {
    package "Booking Microservice" as BookingMS {
        [Assign Route to Cargo] <<Command>>
        [Book Cargo] <<Command>>
        [Cargo Details] <<Query>>
        
        usecase Booking [
          Booking
        ]
    }
    
    package "Tracking Microservice" as TrackingMS {
        [Assign Tracker to Cargo] <<Command>>
        [Track Cargo] <<Query>>
        
        usecase Tracking [
          Tracking
        ]
    }
    
    [Cargo Booked] <<Event>>
    [Cargo Routed] <<Event>>
    [Cargo Handled] <<Event>>
    
    package "Routing Microservice" as RoutingMS {
        usecase Routing [
          Routing
        ]
        
        [Get Itinerary for Route] <<Query>>
        [Maintain Voyages] <<Command>>
    }
    
    package "Handling Microservice" as HandlingMS {
        usecase Handling [
          Handling
        ]
        
        [Register Handling Activity] <<Command>>
        [Handling History Details] <<Query>>
    }
}

BookingMS -[hidden]right-> TrackingMS
BookingMS -[hidden]down-> RoutingMS
RoutingMS -[hidden]right-> HandlingMS

[Assign Route to Cargo] --> Booking
[Book Cargo] --> Booking
[Cargo Details] --> Booking
Booking --> [Get Itinerary for Route] : REST API
Booking --> [Cargo Booked] : publish
Booking --> [Cargo Routed] : publish
Booking --> [Cargo Handled] : subscribe

[Assign Tracker to Cargo] --> Tracking
[Track Cargo] --> Tracking
Tracking --> [Cargo Routed] : subscribe
Tracking --> [Cargo Handled] : subscribe

Routing <-- [Get Itinerary for Route]
Routing <-- [Maintain Voyages]

Handling <-- [Register Handling Activity]
Handling <-- [Handling History Details]
Handling --> [Cargo Handled] : publish
@enduml
```

- 強固なイベント駆動コレオグラフィアーキテクチャの実装には4つのステージがあります。
  - 境界づけられたコンテキストから発生させる必要のあるドメインイベントを登録する
  - 境界づけられたコンテキストから公開する必要のあるドメインイベントを発生させる
  - 境界づけられたコンテキストから発生したイベントを公開する
  - 他の境界づけられたコンテキストから公開されたイベントを購読する
- アーキテクチャの複雑さを考慮して実装は複数のエリアに分割する
  - ドメインイベントの登録は集約で実装する
  - イベントの発生/公開はアウトバウンドサービスで実装する
  - イベントの購読はインターフェイス/インバウンドサービスでハンドリングする

```plantuml
@startuml
title 集約イベント登録の実装

class AbstractAggregateRoot {
+ registerEvent(Object): void
}

class Cargo {
+ addDomainEvent(Object): void
}

class CargoBookedEvent {
+ cargoBookedEventData: CargoBookedEventData
}

class CargoRoutedEvent {
+ cargoRoutedEventData: CargoRoutedEventData
}

class CargoBookedEventData

class CargoRoutedEventData

AbstractAggregateRoot <|.. Cargo
Cargo --> CargoBookedEvent
Cargo --> CargoRoutedEvent
CargoBookedEvent -> CargoBookedEventData
CargoRoutedEvent -> CargoRoutedEventData

@enduml
```

- コマンドの実行の後に集約がドメインイベントを登録する。イベントの登録は常に集約メソッドのコマンドハンドラとして実装します。

#### ドメインモデルサービス

- ドメインモデルサービスは2つの主要な理由から使われます。
- 良く定義されたインターフェイスを介して境界づけられたコンテキストの状態を外部で使えるようにするため。
- 境界づけられたコンテキストの状態をデータストアに永続化して、境界づけられたコンテキストの状態の変更を外部のメッセージブローカーに公開または他の境界づけられたコンテキストとやり取りするための外部との統合のため。
- ドメインモデルサービスは3種類あります。
  - 受信サービス
  - アプリケーションサービス
  - 送信サービス


```plantuml
@startuml

title ドメインモデルサービス実装サマリ

skinparam component {
    BackgroundColor<<Inbound Services>> #D6EAF8
    BackgroundColor<<Outbound Services>> #D5F5E3
    BackgroundColor<<Application Services>> #FADBD8
}

usecase ExternalConsumers [
  External Consumers 
] 

package "Bounded Context - Domain Model Services" {
  [Inbound Services]
  [Application Services]
  [Domain Model]
  [Outbound Services]
  
  [Inbound Services] --> [Application Services]
  [Application Services] -> [Outbound Services]
  [Application Services] --> [Domain Model]
}

[Other Bounded Contexts]

database DB_1 [
  Database
]
queue BUS_1 [
  Message Broker
]

ExternalConsumers --> [Inbound Services]
[Outbound Services] -> DB_1 : State Storage 
[Outbound Services] --> [Other Bounded Contexts]
[Outbound Services] --> BUS_1 : State Events 

@enduml
```

##### 受信サービス

- 受信サービス（ヘキサゴナルアーキテクチャにおける受信アダプタ）はコアドメインモデルの最も外側のゲートウェイとしての役割を果たします。
- Cargo Trackerアプリケーションでは2つのタイプの受信サービスを提供します。
  - 境界づけられたコンテキストのオペレーションの実行を外部に利用可能にするRESTベースのAPIレイヤー
  - メッセージブローカー経由で実行するイベントのためのSpring Cloud Streamベースのイベントハンドリングレイヤー

###### REST API

- REST APIの責務は外部利用者からの境界づけられたコンテキストに対する振る舞いのHTTPリクエストを受け取ることです。
- リクエストはコマンドまたはクエリに分類できます
- REST APIレイヤーのレスポンスは境界づけられたコンテキストのドメインモデルで認識されるコマンド/クエリモデルでに変換して以降の処理をアプリケーションサービスレイヤーに委譲します。

```plantuml
@startuml

title REST API実装ダイアグラム

class CargoBookingCommandService {
+ bookCargo(BookCargoCommand) : BookingID
}

class CargoBookingController {
+cargoBookingCommandService: cargoBookingCommandService
--
+bookCargo(BookCargoResource): Response
}

class BookCargoCommand

class BookCargoCommandDTOAssembler {
+ toCommandFromDTO(BookCargoResource): BookCargo
}

class BookCargoResource

CargoBookingCommandService <-- CargoBookingController
CargoBookingCommandService -> BookCargoCommand
CargoBookingController -> BookCargoCommandDTOAssembler
CargoBookingController -> BookCargoResource
BookCargoCommand <-- BookCargoCommandDTOAssembler
BookCargoCommandDTOAssembler --> BookCargoResource

@enduml
```

```plantuml
@startuml

title 受信サービス実装プロセスサマリ

Client -> RESTAPI
RESTAPI -> DTOAssemblers
DTOAssemblers -> RESTAPI
RESTAPI -> ApplicationService
ApplicationService -> RESTAPI
Client <- RESTAPI

@enduml
```

1. インバウンドはコマンド/クエリ要求がREST APIから来る。APIクラスはSpring Web MVCプロジェクトを使って実装される。
2. REST APIクラスはリソースデータをドメインモデルで要求されるコマンド/クエリデータフォーマットに変換するアッセンブラコンポーネントを使う。
3. コマンド/クエリデータはアプリケーションサービスで追加処理を行う。

###### イベントハンドラ

- イベントハンドラはインバウンド/インターフェイスレイヤー内に配置され境界づけられたコンテキストを購読するために作られます。
- イベントハンドラは一般的なコマンドオペレーションプロセスとペイロードデータを持ったイベント受け取ります。

```plantuml
@startuml

title イベントハンドラ実装ダイアグラム

class CargoTrackingService {
+ assignTrackingId(TrackingDetailsCommand trackingDetailsCommand): void
}

class CargoRoutedEventHandler {
+ receiveEvent(CargoRoutedEvent, CargoRoutedEventData): void
}

class TrackingDetailsCommand

class TrackCargoCommandDTOAssembler {
+ toCommandFromDTO(CargoRoutedEvent eventData): TrackingDetailsCommand
}

class CargoRoutedEvent

CargoTrackingService <-- CargoRoutedEventHandler
CargoTrackingService -> TrackingDetailsCommand
TrackingDetailsCommand <-- TrackCargoCommandDTOAssembler
CargoRoutedEventHandler -> TrackCargoCommandDTOAssembler
CargoRoutedEventHandler -> CargoRoutedEvent
TrackCargoCommandDTOAssembler --> CargoRoutedEvent

@enduml
```

```plantuml
@startuml

title イベントハンドラの実装プロセスサマリ

MessageBroker -> EventHandler
EventHandler -> DTOAssemblers
DTOAssemblers -> EventHandler
EventHandler -> ApplicationServices
ApplicationServices -> EventHandler

@enduml
```

1. イベントハンドラはメッセージブローカーからインバウンドイベントを受け取る
2. イベントハンドラはアセンブラコンポーネントを使ってリソースデータをドメインモデルで要求されるコマンドデータフォーマットに変換する
3. コマンドデータはアプリケーションサービスに送信して処理を継続する

##### アプリケーションサービス

- アプリケーションサービスは受信/送信サービスと境界づけられたコンテキスト内のコアドメインモデルのファサードまたはポートとして機能します。
- 境界づけられたコンテキスト内では、アプリケーションサービスは受信サービスからのリクエスト受付と対応するサービスへの委譲、すなわちコマンドはコマンドサービスにクエリはクエリサービスに委譲する責任を持ちます。
- コマンド委譲プロセスの一環としてアプリケーションサービスは集約の状態を基底となるデータストアに永続化する責務を持ちます。
- クエリ委譲プロセスの一環としてアプリケーションサービスは基底となるデータストアから集約の状態を取得する責務を持ちます。
- それら責務の一部としてアプリケーションはタスクを完了させるため送信サービスに依存します。
- 送信サービスは物理データストア接続に要求されるコンポーネントに必要なインフラコンポーネントを提供します。


```plantuml
@startuml

title アプリケーションサービスの責務

package "Bounded Context" {
    [Command]
    [Queries]
    [Application Services]
    [Command Services]
    [Queries Services]
    [Outbound Services]
}

[Command] --> [Application Services]
[Queries] --> [Application Services]
[Application Services] --> [Command Services]
[Application Services] --> [Queries Services]
[Application Services] --> [Outbound Services]

@enduml
```


###### アプリケーションサービス：コマンド／クエリの委譲

- レポジトリの一部として、境界づけられたコンテキスト内のアプリケーションサービスはコマンド/クエリリクエストを受け取ります。
- これらのリクエストは主に受信サービス(APIレイヤ)から送信されます。
- 処理の一部として、アプリケーションサービスは最初にドメインモデルのコマンドハンドラ/クエリハンドラを使って状態の設定と問い合わせを行います。
- 最後に送信サービスを使って集約の状態を永続化または問い合わせの実行を行います。

```plantuml
@startuml
title アプリケーションサービスのコマンド/クエリ実装ダイアグラム

class BookCargoCommand <<Command>>
class RouteCargoCommand <<Command>>
class CargoBookingCommandService <<Application Services>> {
    + cargoRepository: CargoRepository
    --
    + bookCargo(BookCargoCommand bookCargoCommand) : void
    + assignRoute(RouteCargoCommand routeCargoCommand) : void
}
class Cargo<<Model>>
class CargoBookingQueryService <<Service>> {
    + cargoRepository: CargoRepository
    --
    + fidAll(): List<Cargo>
    + findAllBookingIds(): List<BookingId>
    + find(String bookingId): Cargo
}
class CargoRepository <<OutboundServices>> {
    + save(Cargo: cargo): void
    + findAll(): List<Cargo>
    + findAllBookingIds(): List<BookingId>
    + find(String bookingId): Cargo
}

BookCargoCommand <-- CargoBookingCommandService
RouteCargoCommand <-- CargoBookingCommandService
CargoBookingCommandService --> Cargo
Cargo <-- CargoBookingQueryService
CargoBookingCommandService -> CargoRepository
CargoBookingQueryService -> CargoRepository
@enduml
```

```plantuml
@startuml

title アプリケーションサービスの実装プロセスサマリ

InboundServices -> ApplicationServices :
ApplicationServices -> CommandQueryHandlers
CommandQueryHandlers -> ApplicationServices
ApplicationServices -> OutboundServices
OutboundServices -> ApplicationServices
ApplicationServices -> InboundServices

@enduml
```
1. 受信サービスレイヤから境界づけられたコンテキスト内のアプリケーションサービスにコマンド/クエリ実行のリクエストが送信されます。
2. アプリケーションサービスはドメインモデル内で定義されたコマンドハンドラ/クエリハンドラに依存して集約の状態を更新・問い合わせします。
3. アプリケーションサービスは送信サービスを使って集約の状態を永続化または問い合わせの実行を行います。

##### 送信サービス

- アプリケーションサービス以下の外部サービスと連携する必要があります。
  - レポジトリ
  - メッセージブローカー
  - 他の境界づけられたコンテキスト
- アプリケーションサービスは送信サービスに連携を依存します。
- 送信サービスは外部サービスとの連携を実現する機能を提供します。


```plantuml
@startuml

title マイクロサービスアーキテクチャ内のドメインモデルサービス

skinparam component {
    BackgroundColor<<Inbound Services>> #D6EAF8
    BackgroundColor<<Outbound Services>> #D5F5E3
    BackgroundColor<<Application Services>> #FADBD8
}

package "Bounded Context" {
    package "Application Service" {
        [Commands]
        [Queries]
    }
    package "Aggregates" {
        [Events]
    }
    
    [Outbound Services]
    
    "Application Service" --> [Outbound Services]
    "Aggregates" --> [Outbound Services]
}

package "External Services" {
  database DB [
    Datastore
  ]
  queue BUS_1 [
    Broker
  ]
  boundary CTX_1 [
    Other Bounded Context
  ]
}

[Outbound Services] --> DB : Persistence API
[Outbound Services] --> BUS_1 : Broker API
[Outbound Services] --> CTX_1 : REST API



@enduml
```

###### 送信サービス：リポジトリクラス

- データベース接続のための送信サービスはレポジトリクラスとして実装されます。
- レポジトリクラスは以下の集約の操作のための機能を提供します。
  - 集約と関連を新規永続化する
  - 集約と関連を更新する
  - 集約と関連を問い合わせする

```plantuml
@startuml
title 送信サービス - レポジトリ実装

interface MyBatisRepository 

class CargoRepository {
    + findByBookingId(String bookingId): Cargo
    + findAllBookingIds(): List<BookingId>
    + findAll(): List<Cargo>
}

MyBatisRepository <|.. CargoRepository

@enduml
```

###### 送信サービス：REST API

- マイクロサービス間の連携にREST APIを使うのは最もよくあるパターンです。

```plantuml
@startuml
title 境界づけられたコンテキスト間のHTTP呼び出し

package "Booking Bounded Context" {
    [Booking Service]
}

package "Routing Bounded Context" {
    [Routing Service]
}

[Booking Service] -> [Routing Service] : HTTP API

@enduml
```

```plantuml
@startuml
title 境界づけられたコンテキスト間の腐敗防止層

package "Booking Bounded Context" {
    [Booking Service]
    [Anti-Corruption Layer]
}

package "Routing Bounded Context" {
    [Routing Service]
}

[Anti-Corruption Layer] -> [Routing Service] : HTTP API

@enduml
```

```plantuml
@startuml
title REST APIクラス図

class CargoRoutingService {
    + findOptimalRoute(originLocation, destinationLocation, arrivalDeadline): TransitPath
}

class CargoRoutingController {
    + cargoRoutingService: CargoRoutingService
    --
    + findOptimalRoute(originLocation, destinationLocation, arrivalDeadline): TransitPath
}

class TransitPath {
    + transitEdges: List<TransitEdge>
}

class TransitEdge {
    + voyageNumber: String
    + fromUnLocCode: String
    + toUnLocCode: String
    + fromDate: Date
    + toDate: Date
}

CargoRoutingService <-- CargoRoutingController
CargoRoutingService -> TransitPath
TransitPath --> TransitEdge

@enduml
```

```plantuml
@startuml
title 送信サービス - REST API実装

class TransitPath

class CargoItinerary

class ExternalCargoRoutingService {
    + fetchRouteForSpecification(RouteSpecification routeSpecification): CargoItinerary
    + toLeg(TrasEdg edge): Leg
}

class RestTemplate

class CargoBookingCommandService {
    + externalCargoRoutingService: ExternalCargoRoutingService
    --
    + assignRouteToCargo(RouteCargoCommand routeCargoCommand): type
}

TransitPath <-- ExternalCargoRoutingService
CargoItinerary <-- ExternalCargoRoutingService
ExternalCargoRoutingService -> RestTemplate
ExternalCargoRoutingService <-- CargoBookingCommandService

@enduml
```

```plantuml
@startuml

title 送信サービス(HTTP)実装プロセス

ApplicationService --> OutboundServices
OutboundServices --> TypeSafeRestClients
OutboundServices --> ACL
ApplicationService <-- OutboundServices

@enduml
```

1. アプリケーションサービスクラスはコマンド/クエリ/イベントを受信する。
2. プロセスの一部として、RESTを使った他の境界づけられたコンテキストとの通信が必要な場合は送信サービスが利用可能です。
3. 送信サービスはRestTemplateクラスを使ってRestクライアントを生成します。

###### 送信サービス：メッセージブローカー

- 送信サービスの最後の責務はコマンド実行中の集約により登録されたドメインイベントを公開することです。

```plantuml
@startuml
title 境界づけられたコンテキスト内のイベントフローメカニズム

ApplicationService --> Aggregates
Aggregates --> Events
OutboundServices --> MessageBroker : Transactional Event Listener
Events <-- OutboundServices
ApplicationService --> OutboundServices : Repositories

@enduml
```

1. アプリケーションサービスは特定のコマンドを受け取ります。
2. アプリケーションサービスは集約コマンドハンドラに処理を委譲します。
3. コマンドハンドラは公開の必要のあるイベントを登録します。
4. アプリケーションサービスは送信サービスのリポジトリを使って集約の状態を永続化します。
5. レポジトリの実行は送信サービス内のイベントリスナでトリガーされます。このイベントリスナは公開する必要のある全てのペンディングドメインイベントを集めます。
6. イベントリスナは同一トランザクションでドメインイベントを外部メッセージブローカーに公開します。

```plantuml
@startuml
title イベントパブリッシャ実装クラス図

interface CargoEventSource

class MessageChannel

class CargoEventPublisherService <<Service>> {
    + cargoEventSource: CargoEventSource
    --
    + handleCargoBookedEvent(CargoBookedEvent cargoBookedEvent): void
    + handleCargoRoutedEvent(CargoRoutedEvent cargoRoutedEvent): void
}

class CargoBookedEvent

class CargoRoutedEvent


CargoEventSource <|-- CargoEventPublisherService
MessageChannel <-- CargoEventPublisherService
CargoEventPublisherService --> CargoBookedEvent
CargoEventPublisherService --> CargoRoutedEvent

@enduml
```

#### 実装のまとめ

- Springプラットフォームを使って複数のDDDアーティファクトと伴にマイクロサービスCargo Trackerアプリケーションの実装を完了しました。

```plantuml
@startuml
title DDD artifact implementation summary using Spring Boot


rectangle {
usecase Aggregates
usecase Entities
usecase ValueObjects
}

rectangle {
usecase Commands
usecase Queries
}

rectangle {
usecase ApplicationServices
usecase InboundServices
usecase OutboundServices
}

rectangle {
usecase MicroserviceMessageChoreography
}

rectangle {
usecase MyBatisSpringStarter
usecase SpringServiceClasses
}

rectangle {
usecase SpringWeb
usecase SpringServiceClasses as cs2
usecase MyBatisSpringStarter as mb2
}

rectangle {
usecase SpringCloudStream
usecase SpringServiceClasses as cs3
usecase MyBatisSpringStarter as mb3
}
@enduml
```

### まとめ

- Springプラットフォームの詳細と提供する機能を確立することから着手した。
- Springプラットフォームが提供するサブプロジェクト(Spring Boot, Spring Web, Spring Cloud Stream, MyBatis Spring Starter)を使ってCargo Trackerマイクロサービスアプリケーションを実装することを決定した。
- さまざまなDDDアーティファクトを開発した。最初に選択した技術を使ってドメインモデル、ドメインモデルサービスを実装。
