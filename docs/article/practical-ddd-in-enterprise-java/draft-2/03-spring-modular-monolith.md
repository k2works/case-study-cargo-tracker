---
type: Article
title: "第 3 章：Spring Platform 上のモジュラーモノリス"
description: "Spring 上のモジュラーモノリスとして、パッケージ構成の正典と実際・ドメインモデル・アプリケーションサービス・読み取り側を示す。"
tags: [article, practical-ddd-in-enterprise-java]
status: stable
generated: { by: human:kakimomokuri, at: 2026-08-27T09:30:34Z }
---

# 第 3 章：Spring Platform 上のモジュラーモノリス

前章までで、Cargo Tracker の業務をどう分割し、どの型に何を置いたかを見ました。この章はそれを **Spring Boot のコードとして配置する**話です。

主眼は「Spring の使い方」ではありません。**ドメイン境界を壊さずに Spring を外側へ置く配置**と、**その配置が崩れていないことを機械で確かめる手段**の 2 つです。後者が無いと、前者は最初のイテレーションでしか成立しません。

この章の記述は `docs/article/source/java-2/` の実装に基づきます。設計ドキュメントからの引用と実コードからの引用は区別して示します。**両者が食い違っている箇所は、食い違ったまま示します。**

## この章のゴール

1. BC ごとの 4 層構成を、どのパッケージに何を置くかまで説明できること
2. BC を越える経路が ACL ポートとドメインイベントの 2 つに限られている理由と、それを強制している検査を説明できること
3. **設計ドキュメントに書かれた構成と、実装が持つ構成の差**を自分で確かめられること

## Spring プラットフォーム

### 起動クラス

起動は標準的な `@SpringBootApplication` です。

```java
package com.example.cargotracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 国際貨物輸送管理システムの起動クラス。
 *
 * <p>本システムはモジュラーモノリスであり、6 つの境界付けられたコンテキスト
 * （booking / shipper / routing / tracking / billing / estimation）と共有カーネルで構成される。
 * 各 BC はトップレベルパッケージに 1 対 1 で対応する。
 *
 * <p>外部システムとの HTTP 連携は行わない（ADR-006）。経路算出・通関・決済・港湾・通知は
 * いずれも内部シミュレーションとして実装する。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CargoTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CargoTrackerApplication.class, args);
    }
}
```

転記元: [`CargoTrackerApplication.java`](../../source/java-2/apps/cargo-tracker/src/main/java/com/example/cargotracker/CargoTrackerApplication.java)

**この Javadoc は既に実態から遅れています。** 「6 つの境界付けられたコンテキスト」と列挙していますが、トップレベルの業務パッケージは `booking` / `shipper` / `routing` / `tracking` / `billing` / `estimation` に加えて `handling`（ADR-010 で独立した BC に昇格）と `security`（ADR-007 の支援サブドメイン）があり、**8 つ**です。

本章の後半で見るとおり、この実装は境界の規則を機械で検査しています。しかし**「Javadoc の説明文が実態と合っているか」は検査できません。** 検査できるものとできないものの線引きは、この章を通じて繰り返し出てきます。

### 使っている機能

依存は `build.gradle` に用途ごとのコメントつきで並んでいます。

```groovy
dependencies {
    // --- Web / テンプレート ---
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'
    implementation 'org.springframework.boot:spring-boot-starter-validation'

    // --- セキュリティ（RBAC 8 ロール。non_functional.md §4.1 が正典）---
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.thymeleaf.extras:thymeleaf-extras-springsecurity6'

    // --- 永続化（ADR-004: MyBatis を採用し JPA は採用しない）---
    implementation "org.mybatis.spring.boot:mybatis-spring-boot-starter:${mybatisStarterVersion}"
```

転記元: [`build.gradle`](../../source/java-2/apps/cargo-tracker/build.gradle)

Spring が担うのは次の 4 つに限られています。

| 役割 | 手段 |
| :--- | :--- |
| ユースケース・リポジトリ・アダプタの結線 | `@Service` / `@Component` / `@Mapper`（コンストラクタインジェクション） |
| トランザクション境界の制御 | `@Transactional` |
| Web 入出力 | `@Controller`（Thymeleaf） |
| ドメインイベントの発行と購読 | `ApplicationEventPublisher` / `@TransactionalEventListener` |

**ドメインオブジェクトは Spring のアノテーションを 1 つも持ちません。** これは規約ではなく検査で守られています（後述）。

### モジュラーモノリスの「モジュール」は Gradle のモジュールではない

ビルド構成を見ると、モジュールは 1 つしかありません。

```groovy
rootProject.name = 'cargo-tracker'
```

転記元: [`settings.gradle`](../../source/java-2/apps/cargo-tracker/settings.gradle)

`include` が 1 行もありません。**BC ごとに Gradle のサブプロジェクトを切ってはいない**ということです。

境界を Gradle のモジュールに落とせば、コンパイラが越境を止めてくれます。それをせずに単一モジュールのままにした以上、**境界は別の手段で守る必要があります**。この章の後半（「境界を検査で固定する」）は、その手段の話です。

### 採用しなかったものと、その理由

DDD の実装記事は「何を使ったか」を書きますが、この実装で目を引くのは**何を使わないと決めたかが実行可能になっている**点です。

```groovy
    // ADR で明示的に排除した依存。キーは依存の座標の一部、値は根拠。
    def forbidden = [
            'com.h2database'            : 'ADR-003: H2 はローカル起動のみ。本番の成果物に含めない',
            'wiremock'                  : 'ADR-006: 外部連携が無いため WireMock は採用しない',
            'spring-cloud-contract'     : 'ADR-006: 契約テストの対象が存在しない',
            // ORM のみを対象とする。hibernate-validator は Bean Validation の実装であり
            // ADR-004 の対象外である（org.hibernate で一括除外すると誤検知する）。
            'org.hibernate.orm'         : 'ADR-004: 永続化は MyBatis。JPA / Hibernate は採用しない',
            'jakarta.persistence'       : 'ADR-004: JPA の API を本番に持ち込まない',
    ]
```

転記元: [`build.gradle`](../../source/java-2/apps/cargo-tracker/build.gradle)（`verifyProductionDependencies` タスク）

このタスクは `check` に繋がっており、違反すればビルドが落ちます。

```groovy
tasks.named('check') { dependsOn tasks.named('verifyProductionDependencies') }
```

転記元: [`build.gradle`](../../source/java-2/apps/cargo-tracker/build.gradle)

**注目すべきは検査対象の選び方です。**

```groovy
// 検査対象は runtimeClasspath ではなく productionRuntimeClasspath である。
// developmentOnly は runtimeClasspath には載る（ローカル起動のため）が、
// bootJar には載らない。**runtimeClasspath を見ると常に赤になり、意味を成さない。**
```

さらに、ADR が明示的に認めた例外を検査自身が知っています。

```groovy
    // ADR-003 は開発環境向けに `-PincludeH2=true` での opt-in を認めている。
    // **認めた抜け道を検証が知らないと、正当な手順がビルドを落とす。**
    // 逆に、opt-in していないのに H2 が入っていれば落ちる。
    if (project.hasProperty('includeH2')) {
        forbidden.remove('com.h2database')
    }
```

規則を検査に落とすとき、**規則が認めている例外まで落とさないと、正しい手順を踏んだ人がビルドを壊されます。** そうなった検査は、じきに無効化されます。

なお `spring-cloud-contract` がこの一覧にあるとおり、**この実装は Spring Cloud を使っていません**。外部システムとの HTTP 連携そのものが無い（ADR-006）ため、契約テストの相手が存在しないからです。

## パッケージ構成の正典と、実際

### 設計ドキュメントが定める構成

`architecture_backend.md` は全 BC 共通のパッケージ構成を「正典」として定めています。その冒頭には、本シリーズの出発点になる 1 行があります。

> Practical DDD in Enterprise Java (Chapter 3) のパッケージ構造に準拠する。

引用元: [`docs/design/architecture_backend.md`](../../source/java-2/docs/design/architecture_backend.md)

同じ文書が定める構成は次のとおりです。

```text
com.example.cargotracker.<bounded-context>/
├── domain/
│   ├── model/                   ドメインサービス・ドメイン例外
│   │   ├── aggregates/          集約ルート
│   │   ├── entities/            集約の内側で同一性を持つもの
│   │   ├── valueobjects/        値オブジェクト・列挙・識別子
│   │   └── commands/            業務の要求をまとめた型（該当が無ければ作らない）
│   ├── event/                   ドメインイベント
│   └── repository/              リポジトリ interface（出力ポート。実装はここに置かない）
├── application/
│   └── internal/
│       ├── commandservices/     コマンドサービス（ユースケース実行）
│       ├── queryservices/       クエリサービス（CQRS 読み取り側）
│       └── outboundservices/
│           └── acl/             BC 間 ACL の出力ポート interface
├── infrastructure/
│   ├── repositories/            リポジトリ実装・MyBatis Mapper・Record
│   ├── acl/                     BC 間 ACL アダプタ実装
│   ├── brokers/                 ドメインイベントハンドラ
│   └── config/                  BC 固有の Spring 構成・シードデータ
└── interfaces/
    ├── rest/                    REST Controller
    │   ├── dto/                 リクエスト / レスポンス DTO
    │   └── transform/           DTO ⇔ コマンド変換（Assembler）
    ├── web/                     画面 Controller（Thymeleaf）
    └── events/                  外部起点のイベントハンドラ
```

引用元: [`docs/design/architecture_backend.md`](../../source/java-2/docs/design/architecture_backend.md)「パッケージ構成（全 BC 共通の正典）」

### 実装が持つ構成

**この正典と実装は、3 か所で食い違います。**

| # | 正典の記述 | 実装 |
| :--- | :--- | :--- |
| 1 | `interfaces/rest/`（＋ `dto/`・`transform/`） | **全 BC で存在しない。** `@RestController` も `@ResponseBody` も 0 件 |
| 2 | BC ごとの `domain/event/` | **無い。** ドメインイベントは `shared/domain/event` にまとめられている |
| 3 | `infrastructure/brokers/`（ドメインイベントハンドラ） | **無い。** ハンドラは `interfaces/events/` に置かれている |

いずれも「設計が間違っていた」という話ではありません。**正典は実装より先に書かれ、実装が進むあいだに動いた**というだけです。

正典自身がその経緯を記録しています。

> **注**: 旧版は本節と「パッケージ構造」節に**互換性のない 2 つの構成**を併記していた（`domain/model/aggregates|valueobjects` 系と `domain/model|event|repository` 系）。実装者がどちらを見るかで構造が分岐するため、いったん後者に一本化した。
>
> **IT19 でこの一本化を取り消し、両者を組み合わせた**（ADR-024）。`domain/model` の**内側**を構成要素で分ける形であり、`model` / `event` / `repository` の 3 分割はそのまま残る。**併記に戻したのではなく、階層を 1 段深くした**。

引用元: [`docs/design/architecture_backend.md`](../../source/java-2/docs/design/architecture_backend.md)

**読者への含意は単純です。** この構成を自分のプロジェクトへ持ち込むときは、正典の図をそのまま写すのではなく、**実装側を見て確かめてください。** 本章のこれ以降は実装側を基準に説明します。

### BC のルートに何が書いてあるか

各 BC のトップレベルパッケージには `package-info.java` が置かれ、その BC が何であるかを宣言しています。

```java
/**
 * 予約コンテキスト。貨物予約の受付・旅程管理・BookingStatus の状態遷移を責務とする。
 *
 * <p>本パッケージは境界付けられたコンテキストのルートである。トップレベルパッケージと
 * BC は 1 対 1 に対応し、ArchUnit の slices ルールがこの前提を検証する
 * （docs/design/test_strategy.md §3.3 ルール 4・5）。
 *
 * <p>他の BC のクラスを直接参照してはならない。連携は ACL ポートまたは
 * ドメインイベントを経由する（docs/design/domain-model.md「BC 間 ACL ポート一覧」）。
 *
 * <p>内部構成は docs/design/architecture_backend.md「パッケージ構成（全 BC 共通の正典）」に従う。
 */
package com.example.cargotracker.booking;
```

転記元: [`booking/package-info.java`](../../source/java-2/apps/cargo-tracker/src/main/java/com/example/cargotracker/booking/package-info.java)

**宣言だけなら、守られなくても誰も気づきません。** この Javadoc が挙げている「ArchUnit の slices ルール」が、宣言を実際に強制している相方です。

## ドメインモデルの実装

集約は POJO です。フレームワークのアノテーションも、永続化の型も持ちません。

```java
/**
 * 貨物。Booking Context の集約ルート。
 *
 * <p>状態遷移の規則は {@link BookingStatus} が持ち、本クラスは
 * 「どのコマンドをいつ実行してよいか」を集約の文脈で判断する。
 *
 * <p><strong>Setter を持たない。</strong> 状態を変える手段は業務のことばで名づけた
 * 振る舞い（{@link #cancel()} 等）に限る。Setter を生やすと、不変条件を通らずに
 * 状態を書き換える経路ができる。
 */
public class Cargo {

    private final BookingId bookingId;
    private final ShipperId shipperId;
    private final CargoSpecification cargoSpecification;
    private final RouteSpecification routeSpecification;
    private final long version;

    /**
     * 予約がどこまで進んだか（状態・経路・追跡番号）。
     *
     * <p><strong>経路は予約状態とは別に動く。</strong> 経路を確定しても
     * {@code BookingStatus} は変わらない（遷移表 3）。
     */
    private CargoProgress progress;
```

転記元: [`booking/domain/model/aggregates/Cargo.java`](../../source/java-2/apps/cargo-tracker/src/main/java/com/example/cargotracker/booking/domain/model/aggregates/Cargo.java)

このクラスの import 文は、自 BC の値オブジェクトと共有カーネルの `ShipperId` だけです。`org.springframework` も `org.apache.ibatis` も現れません。

### 「依存しない」を検査に落とす

この性質は、レビューではなく ArchUnit が守っています。

```java
    @ArchTest
    static final ArchRule ドメイン層はSpringに依存しない =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..")
                    .because("ドメイン層は Spring フレームワークに依存してはならない");
```

転記元: [`PackageStructureTest.java`](../../source/java-2/apps/cargo-tracker/src/test/java/com/example/cargotracker/PackageStructureTest.java)

**永続化については、これだけでは足りませんでした。** その理由が同じファイルに記録されています。

```java
    /**
     * ADR-004: ドメイン層が MyBatis の型に依存しない。
     *
     * <p><strong>「ドメイン層はインフラ層に依存しない」だけでは足りない。</strong>
     * {@code org.apache.ibatis} は {@code ..infrastructure..} に含まれないため、
     * ドメインの集約に {@code @Results} や {@code @Param} を直接付けても、
     * 依存方向のルールは緑のまま通る。ADR-004 は「ドメインモデルの
     * {@code @Entity} は不要になる」ことを利点として挙げているが、
     * それを強制する仕組みが無かった（IT2 タスク 0-1 の棚卸しで発覚）。
     */
    @ArchTest
    static final ArchRule ドメイン層はMyBatisに依存しない =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("org.apache.ibatis..")
                    .because("永続化技術はドメインモデルに現れてはならない（ADR-004）");
```

転記元: [`PackageStructureTest.java`](../../source/java-2/apps/cargo-tracker/src/test/java/com/example/cargotracker/PackageStructureTest.java)

**ADR が挙げた「利点」は、それを強制する検査が無ければただの期待です。** レイヤーの依存方向という一般的な規則は、永続化ライブラリの座標がレイヤーのパッケージ名と一致しないために素通りしました。規則を検査へ落とすときは、**その規則が実際に何を捕まえるか**を確かめる必要があります。

## アプリケーションサービス

ユースケースは `application/internal/commandservices/` に置かれ、トランザクション境界と業務の順序制御を担います。

```java
    /**
     * 予約を登録する。
     *
     * <p>荷主の存在確認は ACL ポート経由で行う（ビジネスルール 9）。
     * **ドメインモデルの中で確認しようとすると BC 間の直接参照になる**ため、
     * 集約の外側であるここが確認の場所になる。
     */
    @Transactional
    public Result book(BookCargoCommand command, String actor) {
        if (!shipperExistenceChecker.exists(command.shipperId())) {
            return Result.shipperNotFound();
        }

        // 港マスタに無い港は、外部キー違反（500）ではなく業務のエラーとして返す。
        // **どの港が悪いのかを示さないと、利用者は直せない**
        List<Location> unknownPorts = knownPorts.findUnknown(List.of(
                command.routeSpecification().origin(),
                command.routeSpecification().destination()));
        if (!unknownPorts.isEmpty()) {
            return Result.unknownPorts(unknownPorts);
        }

        Cargo cargo = Cargo.book(command);
        cargoRepository.save(cargo);

        if (AUDIT.isInfoEnabled()) {
            AUDIT.info("貨物予約登録 bookingId={} shipperId={} origin={} destination={} actor={}",
                    cargo.bookingId().value(), cargo.shipperId().value(),
                    cargo.routeSpecification().origin().unlocode(),
                    cargo.routeSpecification().destination().unlocode(),
                    AuditValue.sanitize(actor));
        }

        return Result.booked(cargo);
    }
```

転記元: [`BookCargoCommandService.java`](../../source/java-2/apps/cargo-tracker/src/main/java/com/example/cargotracker/booking/application/internal/commandservices/BookCargoCommandService.java)

3 つの判断が読み取れます。

**1. 集約に入らない確認は、集約の外に置く。** 荷主が存在するかは Booking のデータだけでは決まりません。ここで確認しなければ、`Cargo` が Shipper を知ることになります。

**2. 業務のエラーは業務の言葉で返す。** 港マスタに無い港をそのまま INSERT すれば外部キー違反になり、利用者には 500 が返ります。どの港が悪いのかを返さなければ、利用者は直せません。

**3. 集約そのものを返さない。**

```java
    /**
     * 登録の結果。
     *
     * <p><strong>集約そのものを返さない。</strong> 呼び出し側（画面）が要るのは
     * 「どの予約ができたか」だけである。可変の集約を渡すと、画面から状態を
     * 動かせてしまい、<strong>不変条件を通らない経路ができる</strong>。
```

転記元: [`BookCargoCommandService.java`](../../source/java-2/apps/cargo-tracker/src/main/java/com/example/cargotracker/booking/application/internal/commandservices/BookCargoCommandService.java)

集約から setter を排しても、集約を画面へ渡してしまえば振る舞いメソッドを画面から呼べます。**境界は「型に何を生やさないか」だけでなく「型をどこまで運ぶか」でも決まります。**

## 受信側 — REST は無い

正典は `interfaces/rest/` を規定していますが、**実装に REST API はありません**。受信側は `interfaces/web/` の `@Controller` だけです。

```java
@Controller
@RequestMapping("/bookings")
public class BookingController {
```

転記元: [`BookingController.java`](../../source/java-2/apps/cargo-tracker/src/main/java/com/example/cargotracker/booking/interfaces/web/BookingController.java)

画面は Thymeleaf で、部分更新は htmx が担います。**HTML のフラグメントを返すエンドポイントが、実質的な API の役割を果たします。**

```java
    @GetMapping("/new/specification")
    public String specificationFields(
            @ModelAttribute("form") BookingForm form,
            @RequestParam(name = ATTR_CARGO_TYPE, defaultValue = "GENERAL") String cargoType,
            Model model) {
        CargoType type;
        try {
            type = CargoType.valueOf(cargoType);
        } catch (IllegalArgumentException e) {
            type = CargoType.GENERAL;
        }
        model.addAttribute(ATTR_CARGO_TYPE, type);
        // **入力済みの値を持ち帰る。** 種別を選び直しただけで申告が消えると、
        // UN 番号のような書類から転記する値を二度入力することになる
        // （フォーム全体を hx-include で送っているのは、そのためである）
        return "booking/_specification :: fields";
    }
```

転記元: [`BookingController.java`](../../source/java-2/apps/cargo-tracker/src/main/java/com/example/cargotracker/booking/interfaces/web/BookingController.java)

返しているのは JSON ではなくテンプレートのフラグメント名です。**JSON を返す API を 1 本も持たないまま、部分更新の要求を満たしています。**

Controller の分割も業務で切られています。

```java
 * <p><strong>状態を進める操作は {@link BookingProgressController} に置く。</strong>
 * 遷移は実行するロールが操作ごとに異なり（営業担当者・追跡管理者）、
 * 認可の規則もそこに集まる。一覧・登録・詳細と混ぜると、どの操作が誰のものか
 * 読み取れなくなる。
```

転記元: [`BookingController.java`](../../source/java-2/apps/cargo-tracker/src/main/java/com/example/cargotracker/booking/interfaces/web/BookingController.java)

## 読み取り側

### コマンドとクエリは分かれている

`application/internal/` は `commandservices` と `queryservices` に分かれ、この分離は業務パッケージすべてで徹底されています。

```java
/**
 * 貨物予約の読み取り（CQRS のクエリ側）。
 *
 * <p>実装はインフラ層に置く（ArchUnit ルール 3）。
 */
public interface BookingQueryService {
```

転記元: [`BookingQueryService.java`](../../source/java-2/apps/cargo-tracker/src/main/java/com/example/cargotracker/booking/application/internal/queryservices/BookingQueryService.java)

クエリ側が返すのは集約ではなく画面向けの View 型です。**その View に何を含めるかは業務の判断です。**

```java
/**
 * 予約コンテキストの読み取り（CQRS のクエリ側）。
 *
 * <p>画面が判断を持たないよう、<strong>表示名・バッジ・操作の可否まで決めて渡す</strong>。
 * 実装はインフラ層に置く（ArchUnit ルール 3）。
 */
package com.example.cargotracker.booking.application.internal.queryservices;
```

転記元: [`queryservices/package-info.java`](../../source/java-2/apps/cargo-tracker/src/main/java/com/example/cargotracker/booking/application/internal/queryservices/package-info.java)

一覧の既定の並び順まで、クエリサービスの契約として書かれています。

```java
    /**
     * 経路割り当て待ちの予約（US06 / US08。経路設計者の作業入口）。
     *
     * <p>対象は引き渡し済み（{@code ROUTE_PROPOSED}）で経路が未割り当てのもの。
     * <strong>既定の並び順は希望期限の昇順</strong>である（`ui_design.md`）。
     * 経路設計者が朝に見るのは「どれが一番切羽詰まっているか」であり、
     * **予約 ID 順では役に立たない**。
     */
    Page<BookingView> findAwaitingRouting(PageRequest page);
```

転記元: [`BookingQueryService.java`](../../source/java-2/apps/cargo-tracker/src/main/java/com/example/cargotracker/booking/application/internal/queryservices/BookingQueryService.java)

### ただし CQRS はここまでである

**読み取り専用のテーブルもビューも存在しません。** クエリ側は書き込み側と同じテーブルを SELECT します。マイグレーションに `CREATE VIEW` は 1 件もありません。

**Mapper の XML もありません。** 設計ドキュメントの CQRS 適用方針は「MyBatis の XML マッパーで JOIN クエリを直接記述し」と書いていますが、実装に `.xml` は 1 件も無く、SQL はすべてアノテーションで書かれています。これも正典と実装の食い違いの 1 つです。

```java
@Mapper
public interface BookingQueryMapper {

    String SELECT_ROW = """
            SELECT CAST(c.booking_id AS VARCHAR) AS bookingId,
```

転記元: [`BookingQueryMapper.java`](../../source/java-2/apps/cargo-tracker/src/main/java/com/example/cargotracker/booking/infrastructure/repositories/BookingQueryMapper.java)

### SQL に業務規則を書かない

同じ Mapper の Javadoc に、クエリ側の設計判断が書かれています。

```java
/**
 * 貨物予約の読み取り専用マッパー（CQRS のクエリ側）。
 *
 * <p>荷主名は JOIN で 1 回の SQL に含める。予約 1 件ごとに荷主を引き直すと
 * 一覧で N+1 になる。
 *
 * <p><strong>状態の表示名とキャンセル可否は SQL で組み立てない。</strong>
 * 遷移表の規則を SQL にも書くと、規則が 2 か所に散って必ず片方だけが更新される。
 * ここでは列挙子名までを返し、表示名と可否は {@code BookingStatus} から導く。
 */
```

転記元: [`BookingQueryMapper.java`](../../source/java-2/apps/cargo-tracker/src/main/java/com/example/cargotracker/booking/infrastructure/repositories/BookingQueryMapper.java)

**CQRS のクエリ側はドメインモデルを迂回します。** その迂回路に業務規則を書くと、規則がモデルと SQL の 2 か所に増えます。前章で見た「状態遷移表を `BookingStatus` に閉じ込める」という判断は、クエリ側でも守られて初めて意味を持ちます。

なお、読み取り側でも**業務の判断は application 層に置く**という規則が別に定められています（ADR-022）。「どれを出すか・どうまとめるか」が業務の言葉で説明でき、かつ確かめるのに DB の起動が要るときだけ、判断を純粋な関数として application 層へ出します。**JOIN した結果を詰め替えるだけの一覧は分けません。** そこに判断は無く、分けると往復が増えるだけだからです。

## 送信側 — BC を越える 2 つの経路

BC を越える経路は 2 つに限られています。**同期の ACL ポート**と**ドメインイベント**です。

### ACL ポートは利用側が定義し、提供側が実装する

```java
/**
 * 予約コンテキストが他の BC・マスタへ問い合わせる出力ポート（ACL）。
 *
 * <p><strong>ポートを定義するのは利用する側である。</strong> 実装は提供側の BC に置く。
 * ここは BC 間の<strong>唯一の許可された越境点</strong>であり、
 * ArchUnit ルール 4 はこのパッケージだけを除外する。
 */
package com.example.cargotracker.booking.application.internal.outboundservices.acl;
```

転記元: [`outboundservices/acl/package-info.java`](../../source/java-2/apps/cargo-tracker/src/main/java/com/example/cargotracker/booking/application/internal/outboundservices/acl/package-info.java)

ポートは Booking 側にあります。

```java
/**
 * 荷主の存在確認（Booking → Shipper の ACL ポート）。
 *
 * <p>ビジネスルール 9（{@code domain-model.md}）: Booking Context は Shipper Context に
 * 直接依存せず、本ポートを通じて荷主の存在を確認する。ArchUnit ルール 4 が
 * この境界を固定しており、{@code booking} から {@code shipper} のクラスを直接参照すると
 * ビルドが落ちる。
 *
 * <p><strong>本ポートが返すのは「存在するか」だけである。</strong> 荷主の名称や
 * 契約割引率を返し始めると、Booking が Shipper のモデルを知ることになり、
 * ACL を挟んだ意味が失われる。表示用の荷主名は Booking のクエリ側が
 * 読み取り専用の SQL で取得する（CQRS のクエリ側）。
 */
public interface ShipperExistenceChecker {
```

転記元: [`ShipperExistenceChecker.java`](../../source/java-2/apps/cargo-tracker/src/main/java/com/example/cargotracker/booking/application/internal/outboundservices/acl/ShipperExistenceChecker.java)

実装は Shipper 側にあります。

```java
/**
 * {@link ShipperExistenceChecker} の実装（Shipper 側のアダプタ）。
 *
 * <p><strong>実装を Shipper 側に置くのは、依存の向きを一方向に保つためである。</strong>
 * Booking 側に置くと、Booking のインフラ層が Shipper のリポジトリを知ることになり、
 * ACL を挟んでも Booking → Shipper の実体依存が残る。ポートの定義は利用側（Booking）、
 * 実装は提供側（Shipper）に置くことで、越境は「Booking が定義した契約を Shipper が満たす」
 * という 1 方向だけになる。
 *
 * <p>返すのは存在の有無のみで、荷主のドメインオブジェクトは境界の外に出さない。
 */
@Component
public class ShipperExistenceCheckerAdapter implements ShipperExistenceChecker {

    private final ShipperRepository shipperRepository;

    public ShipperExistenceCheckerAdapter(ShipperRepository shipperRepository) {
        this.shipperRepository = shipperRepository;
    }

    @Override
    public boolean exists(ShipperId shipperId) {
        return shipperId != null && shipperRepository.findById(shipperId).isPresent();
    }

    @Override
    public Optional<ShipperId> findIdByShipperCode(String shipperCode) {
        if (shipperCode == null || shipperCode.isBlank()) {
            return Optional.empty();
        }
        return shipperRepository.findByShipperCode(shipperCode.strip())
                .map(Shipper::id);
    }
}
```

転記元: [`ShipperExistenceCheckerAdapter.java`](../../source/java-2/apps/cargo-tracker/src/main/java/com/example/cargotracker/shipper/infrastructure/acl/ShipperExistenceCheckerAdapter.java)

**ポートの Javadoc が「返すのは存在するかだけ」と釘を刺しているのが要点です。** 荷主名も返してくれれば画面が楽になります。しかし返し始めた瞬間、Booking は Shipper のモデルを知ることになります。表示用の荷主名は、クエリ側が SQL で JOIN して取っています（前節の `SELECT_ROW`）。

**同じ「荷主名を得る」でも、経路によって許されるものが違います。** これが ACL を挟む意味です。

### 同期で状態を変えてよいのは 4 つだけ

BC を越えて**状態を変える**同期ポートは、名簿で固定されています。

```java
    private static final Map<String, String> SYNCHRONOUS_COMMANDS = new LinkedHashMap<>();

    static {
        SYNCHRONOUS_COMMANDS.put("BookingSettlementPort.settle",
                "US23 の受入基準そのもの（入金確認後に予約状態も精算済になる）。"
                        + "経理担当者がその場で気づいて手を打てる。"
                        + "失敗は請求書詳細の警告と監査ログに出る");
        SYNCHRONOUS_COMMANDS.put("InvoiceNotificationPort.notifyIssued",
                "「請求書が届いていない」と言われたときに答えるための記録。"
                        + "宛先が無いことは経理担当者が直せる。"
                        + "失敗は請求書詳細の警告と監査ログに出る");
        SYNCHRONOUS_COMMANDS.put("TrackingPort.issue",
                "追跡番号を発行できたかは営業担当者の操作の結果そのものである。"
                        + "失敗は画面のメッセージに出る");
        SYNCHRONOUS_COMMANDS.put("CargoRouteAssignments.assign",
                "拒否の理由（端点不一致・状態不正）を経路設計者にその場で返す必要がある。"
                        + "失敗は画面のメッセージに出る");
    }
```

転記元: [`CrossContextPortPolicyTest.java`](../../source/java-2/apps/cargo-tracker/src/test/java/com/example/cargotracker/CrossContextPortPolicyTest.java)

**登録されている理由がすべて「誰が失敗を知り、その人は動けるか」で書かれています。** これは ADR-021 の判断基準そのものです。

この ADR は、IT14 で起きた実際の欠陥から生まれました。入金確認の後に予約を `SETTLED` にする `BookingSettlementPort.settle` は `boolean` を返す契約なのに、呼び出し側が戻り値を捨てていました。結果として「入金確認済みだが予約が精算済みでない」請求書がログにも画面にも残らず、その予約は精算後も引取記録を訂正できてしまいました。**テストは全緑、静的解析も PASS のままです。**

ADR-021 が出した結論は、戻り値の使い方を規則にすることではありませんでした。

> **問うべきは戻り値の使われ方ではなく、失敗が人に届くかである。**

引用元: [`docs/adr/021-cross-context-state-change-must-name-where-failure-surfaces.md`](../../source/java-2/docs/adr/021-cross-context-state-change-must-name-where-failure-surfaces.md)

名簿に無い状態変更ポートを追加すると、このテストが赤になります。**「なぜ同期なのか」「失敗はどこに出るのか」を書くまで通りません。**

### 状態の伝播はドメインイベント

問い合わせでもコマンドでもない越境 —— つまり**起きた事実を他の BC が反映する**経路は、ドメインイベントです。

```java
/**
 * BC をまたいで伝播するドメインイベント。
 *
 * <p><strong>共有カーネル（{@code shared.domain.model}）ではない。</strong> ここに置くのは
 * 「起きた事実」だけであり、業務の判断は含まない。ADR-005 が 2 要素に限っているのは
 * 共有カーネルの話であり、イベントはその制限の対象ではない。
 *
 * <p><strong>運ぶのは素の値だけである。</strong> 発行側の型を載せると、購読する BC が
 * 発行側のドメインを参照することになる。イベントは ACL ポートと同じく
 * <strong>境界を越える数少ない通り道</strong>であり、同じ規律が要る。
 *
 * <p>購読は {@code @TransactionalEventListener(AFTER_COMMIT)} で行う（ADR-009）。
 * <strong>発行側のトランザクションがコミットしてから購読側が動く。</strong>
 * コミット前に動くと、発行側が巻き戻ったときに購読側だけが残る。
 */
package com.example.cargotracker.shared.domain.event;
```

転記元: [`shared/domain/event/package-info.java`](../../source/java-2/apps/cargo-tracker/src/main/java/com/example/cargotracker/shared/domain/event/package-info.java)

イベントはすべて `record` で、`shared/domain/event` にまとめられています。**BC ごとの `domain/event` は作られていません**（正典との食い違いの 2 番目）。

イベントの配送は Spring の `ApplicationEventPublisher` です。**同一 JVM 内の同期呼び出しであり、メッセージブローカーは介在しません。** Kafka も RabbitMQ も JMS も、この実装には存在しません。

## 取りこぼしをどう扱うか

結果整合を選ぶと、**購読側の失敗を利用者の画面に返せなくなります**。この実装はその代償を、隠さずに数えるという形で引き受けています。

```java
/**
 * 結果整合の取りこぼしを記録する（ADR-009 の代償への手当て）。
 *
 * <p>BC 間の状態伝播をドメインイベントに変えた結果、<strong>購読側の失敗を
 * 利用者の画面に返せなくなった</strong>。同期で呼んでいたときは「他の操作が先に
 * 行われました」と出せていたものが、いまは購読側の中で終わる。
 *
 * <p><strong>これは結果整合を選んだ以上避けられない代償である。</strong>
 * 問題は代償そのものではなく、<strong>気づく手段を用意しないこと</strong>である。
 * ログに出すだけでは「誰も見ない場所に置いた」のと同じであり、
 * 件数として数えられて初めて閾値を決めて気づける。
 *
 * <p>件数は気づくため、ログは直すためにある。両方を 1 か所で出す。
 */
@Component
public class EventualConsistencySkips {
```

転記元: [`EventualConsistencySkips.java`](../../source/java-2/apps/cargo-tracker/src/main/java/com/example/cargotracker/shared/infrastructure/observability/EventualConsistencySkips.java)

記録するメソッドには、メトリクス設計の判断まで書かれています。

```java
    /**
     * 反映できなかったことを記録する。
     *
     * <p><strong>対象の識別子をタグにしない。</strong> 予約 ID や追跡番号は値の種類が
     * 際限なく増え、時系列データベースを膨張させる。<strong>数えるのは購読者と理由まで</strong>
     * とし、どのレコードだったかはログで追う。
     *
     * @param subscriber 購読側の BC 名（{@code booking} / {@code tracking}）
     * @param reason     反映できなかった理由（{@code NOT_FOUND} / {@code CONFLICTED}）
     * @param key        対象の識別子（予約 ID・追跡番号）。ログにのみ出す
     */
    public void recordSkip(String subscriber, String reason, String key) {
        registry.counter(METRIC_NAME, "subscriber", subscriber, "reason", reason)
                .increment();
        LOG.warn("結果整合の反映を取りこぼした subscriber={} reason={} key={}",
                subscriber, reason, key);
    }
```

転記元: [`EventualConsistencySkips.java`](../../source/java-2/apps/cargo-tracker/src/main/java/com/example/cargotracker/shared/infrastructure/observability/EventualConsistencySkips.java)

購読側はこれを呼びます。

```java
    /**
     * 誤配と輸送開始を反映する。
     *
     * <p><strong>失敗は数えられる場所に出す。</strong> 結果整合では利用者の画面に
     * 返せないため、ここが唯一「反映されなかった」ことを知る手段になる。
     * ログだけでは誰も見ないため、件数として残す（ADR-009 / IT6 追補 A1）。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(HandlingActivityRegisteredEvent event) {
        // **どの種別が何を意味するかは予約が決める**（ADR-009）。
        // ここでするのは、起きた事実をそのまま渡すことだけである
        var result = applyService.apply(event.bookingId(), event.misrouted(), event.handlingType(),
                event.locationUnlocode(), event.completionTime());

        switch (result) {
            case NOT_FOUND, CONFLICTED -> skips.recordSkip(
                    SUBSCRIBER, result.name(), String.valueOf(event.bookingId()));
            default -> { /* 反映できた */ }
        }
    }
```

転記元: [`BookingHandlingEventHandler.java`](../../source/java-2/apps/cargo-tracker/src/main/java/com/example/cargotracker/booking/interfaces/events/BookingHandlingEventHandler.java)

**ここで確認しておくべきは、無いものです。**

| 機構 | この実装での状態 |
| :--- | :--- |
| リトライ（`@Retryable` など） | **無い** |
| 非同期実行（`@Async`） | **無い** |
| Transactional Outbox | **無い** |
| デッドレターキュー | **無い**（そもそもブローカーが無い） |

**取りこぼしは再送されません。数えられて、ログに残るだけです。** 単一 JVM・単一デプロイという前提のもとで、「起きたことに気づける」ところまでを引き受け、それ以上は作っていません。

イベントハンドラの規律そのものも検査されています。`AFTER_COMMIT` 以外の購読や、素の `@EventListener` の使用、取りこぼし記録の漏れは、ソースを走査するテストが検出します。

## イベントは読み取りモデルも作る

Event Sourcing が無くても、イベントは読み取り側に効きます。予約一覧の SQL に、その痕跡が残っています。

```java
                   -- 誤配を検知した荷役の写し（US28）。
                   -- **Handling のテーブルを読みに行かない**（C28）。荷役の登録が
                   -- 運んできた事実を、Booking が自分の列に写している（ADR-009）。
                   -- IT11 は handling_activity を JOIN しており、
                   -- **BC をまたぐ SQL はどの検査にも映らなかった**
                   c.misrouted_location_unlocode AS misroutedFrom,
                   c.misrouted_at                AS misroutedAt,
```

転記元: [`BookingQueryMapper.java`](../../source/java-2/apps/cargo-tracker/src/main/java/com/example/cargotracker/booking/infrastructure/repositories/BookingQueryMapper.java)

もともとは予約一覧が `handling_activity` を JOIN していました。Java の依存グラフ上は何も越境していないため、**ArchUnit は緑のまま**です。SQL の中で境界を越えていたわけです。

これを、荷役のイベントを購読して Booking 自身の列へ書き写す形に変えました。**イベントが運んだ事実を自分のテーブルに保持する**——投影（projection）と呼ぶには素朴ですが、やっていることは同じです。

**Event Sourcing でなくても、イベント駆動は読み取りモデルを作ります。** イベントストアも投影の再構築手順も無いまま、この形だけを採ることはできます。

## 境界を検査で固定する

ここまで見てきた規則は、いずれも文書に書いてあるだけでは守られませんでした。この実装が持つ検査を整理します。

### ArchUnit — Java の依存グラフを見る

`PackageStructureTest` が守っているものは次のとおりです（**件数もコードも写しません**。正典はテストクラス自身です）。

| ルール名 | 守るもの |
| :--- | :--- |
| `すべてのクラスはBC集合のいずれかに属する` | トップレベルパッケージ = BC という前提 |
| `ドメイン層はインフラ層に依存しない` | 依存方向（DIP） |
| `ドメイン層はSpringに依存しない` | ドメインは POJO |
| `ドメイン層はMyBatisに依存しない` | 永続化技術がドメインに現れない |
| `アプリケーション層はインフラ層に依存しない` | ポート経由でのみ通信する |
| `共有カーネルはLocationとShipperIdのみ` | 共有カーネルの肥大化を止める |
| `共有アプリケーション層はBC横断の約束のみ` | `shared.application` の肥大化を止める |
| `共有イベントは事実を運ぶレコードのみ` | イベントに命令や業務ロジックを置かない |
| `共有イベントのネストした型もレコード` | 事実の一部が可変だと購読側が書き換えられる |
| `画面層はリポジトリを直接参照しない` | 読み取りはクエリサービス経由（CQRS） |
| `コンテキスト間でクラスを直接参照しない` | BC 間は ACL ポートかイベント |
| `ドメイン層とアプリケーション層はBCをまたがない` | BC 間の参照を `infrastructure/acl` に閉じる |

出典: [`PackageStructureTest.java`](../../source/java-2/apps/cargo-tracker/src/test/java/com/example/cargotracker/PackageStructureTest.java)

**BC 分離のルールは、除外の範囲が要点です。**

```java
                    // ACL ポートは BC 間の**唯一の許可された越境点**である（ADR-005 / ADR-007）。
                    // ポートを定義するのは利用側の BC、実装するのは提供側の BC であり、
                    // 実装クラスからポートへの参照は必然的に BC をまたぐ。
                    //
                    // **除外するのはポートのパッケージだけである。** 集約や値オブジェクトへの
                    // 直接参照（booking → shipper.domain.model.Shipper 等）は引き続き落ちる。
                    // ここを "..shipper.." のように BC 単位で緩めると、ACL を通す動機が消える。
                    .ignoreDependency(alwaysTrue(), resideInAPackage("..outboundservices.acl.."))
```

転記元: [`PackageStructureTest.java`](../../source/java-2/apps/cargo-tracker/src/test/java/com/example/cargotracker/PackageStructureTest.java)

除外を BC 単位で書いてしまえば、ACL を通す理由が消えます。**検査の緩め方が、設計そのものを決めます。**

同じ箇所には、逆向きに書いた場合の失敗も記録されています。

```java
                    // ignoreDependency の引数は (依存元, 依存先)。**向きを逆にすると
                    // 「shared から他 BC への依存」を無視することになり、狙いと反対に働く**
```

### 依存グラフに現れないものを見る

ArchUnit が見るのは Java の依存グラフだけです。**マッパーの SQL が他 BC のテーブルを JOIN しても、購読が `AFTER_COMMIT` を宣言していなくても、依存グラフには何も現れません。**

そのため、`.java` を読んで検査するテスト群が別にあります。

| 検査 | 見るもの |
| :--- | :--- |
| `MapperTableOwnershipTest` | Mapper の SQL が触るテーブルが、その BC の所有かどうか（ADR-015） |
| `CrossContextPortPolicyTest` | BC 越しに状態を変える同期ポートが名簿にあるか（ADR-021） |
| `EventualConsistencyListenerPhaseTest` | 購読が `AFTER_COMMIT` を宣言しているか |
| `EventualConsistencyPropagationTest` | 取りこぼしの記録に漏れが無いか |
| `EntityEncapsulationTest` | エンティティの生成・変更を、契約した相手だけが呼んでいるか（ADR-024） |

出典: [`docs/design/test_strategy.md`](../../source/java-2/docs/design/test_strategy.md) §3.3

`MapperTableOwnershipTest` は、テーブルの所有 BC を定めた設計ドキュメントの表**そのものを読んで**突き合わせます。**片方だけ直すと赤くなります。**

### カバレッジもレイヤーで分ける

カバレッジ閾値は全体で 1 つではなく、レイヤーごとに設定されています。

```groovy
def coverageLayers = [
        domain        : [pattern: '**/domain/**', line: 0.85, branch: 0.70],
        application   : [pattern: '**/application/**', line: 0.85, branch: 0.60],
        interfaces    : [pattern: '**/interfaces/**', line: 0.85, branch: 0.60],
        infrastructure: [pattern: '**/infrastructure/**', line: 0.90, branch: 0.75],
]
```

転記元: [`build.gradle`](../../source/java-2/apps/cargo-tracker/build.gradle)

このすぐ上に、閾値の決め方が書かれています。

```groovy
// 閾値は実測に追随させる（§6.3 の引き上げ手順）。**目標値をそのまま閾値にしない。**
// 届いていない目標を閾値にすると、閾値を満たすためのテストを書くことになる。
```

**目標と閾値を分けているのが要点です。** 届いていない目標を閾値にすると、そこを埋めるためだけのテストが書かれます。

## トレードオフ — JPA を捨てて何を払ったか

書籍の Chapter 3 は JPA / EclipseLink を使います。この実装は同じパッケージ構造を採りながら、**永続化の選択だけを反転させました**（ADR-004）。

得たものは明確です。ドメインモデルから `@Entity` が消え、SQL が読める形で手元にあり、N+1 は Mapper を見れば分かります。ArchUnit の `ドメイン層はMyBatisに依存しない` が、その状態を維持します。

**払った代金も同じくらい明確です。**

| 失ったもの | それを埋めるために増えたもの |
| :--- | :--- |
| ORM が保証していた「エンティティとテーブルの対応」 | `DataModelDocumentSchemaTest`（設計ドキュメントと実スキーマの一致検査） |
| ORM が生成する SQL の境界（どのテーブルを触るかが型から自明） | `MapperTableOwnershipTest`（SQL が触るテーブルの所有 BC を検査） |
| 方言差の吸収 | `H2DialectSmokeTest`（H2 と PostgreSQL の方言差の検出） |
| クエリの N+1 に対する ORM 側の警告 | `ListQueryEfficiencyTest` などのクエリ回数検査 |

**手で SQL を持つと、ORM が黙って守っていたものを自分で検査しなければなりません。** ADR-004 の判断が正しかったかは、この検査群を維持できるかにかかっています。維持できなくなった時点で、判断は間違いに変わります。

## 次に何が要るか

この実装は、単一 JVM・単一デプロイという前提の上に立っています。その前提が崩れる条件を挙げておきます。

**イベントの配送が JVM を越える必要が出たとき。** `ApplicationEventPublisher` はプロセスの外へ出られません。BC を別プロセスに分けるなら、配送手段の入れ替えが最初の作業になります。

**取りこぼしを数えるだけでは済まなくなったとき。** いまは `NOT_FOUND` / `CONFLICTED` をカウンタに記録して終わりです。件数が業務上無視できなくなれば、再送が要ります。再送を入れるなら、発行の確実性を担保する Transactional Outbox が先に必要です。設計ドキュメントもその順序を注記しています。

> 高可用性が必要なシステムへ移行する際は Transactional Outbox パターンへの移行を検討すること。

引用元: [`docs/design/architecture_backend.md`](../../source/java-2/docs/design/architecture_backend.md)

**イベントの履歴そのものが業務要件になったとき。** 監査や再計算のために「いつ何が起きたか」の列が正典として要るなら、そこで初めて Event Sourcing の検討が始まります。**この実装にイベントストアはありません。** 状態は MyBatis が現在値を直接 UPDATE しています。

いずれも、まだこの実装には**ありません**。本シリーズでこれらを扱うのは、対応する実装を参照元に収録できてからです。**動いていないコードについて設計を語ることはしません。**

## まとめ

この章では、Cargo Tracker の DDD モデルを Spring Boot 上へ配置する方法を見ました。

### 配置について

1. BC = トップレベルパッケージ。Gradle のモジュールは 1 つだけであり、境界はビルドツールが守っていない
2. Spring は DI・トランザクション・Web・イベントの 4 つに限って外側に置く。ドメインは POJO のまま
3. 越境は ACL ポート（問い合わせと、名簿に登録された 4 つのコマンド）とドメインイベント（状態の伝播）の 2 経路のみ
4. REST API は無い。受信側は Thymeleaf + htmx の画面 Controller だけで、HTML フラグメントが API の役割を果たす
5. CQRS はコマンド／クエリのサービス分離まで。読み取り専用テーブルも投影も無い

### 守り方について

1. 依存方向・BC 分離・共有カーネルの範囲は ArchUnit が守る
2. **依存グラフに現れないもの**（SQL の越境・購読フェーズ・同期コマンドの根拠）はソースを走査する検査が守る
3. ADR が「採用しない」と決めた依存は、本番クラスパスに対する検査が守る
4. **検査は、規則が認めた例外まで知っていなければならない**（`-PincludeH2=true`）

### そして、守れないものについて

設計ドキュメントの正典は `interfaces/rest/`・BC ごとの `domain/event/`・`infrastructure/brokers/` を規定していますが、いずれも実装にありません。起動クラスの Javadoc は BC を「6 つ」と書いたままです。**構造は検査できても、構造の説明文は検査できません。**

この章で扱ったのは 1 つの実装アプローチだけです。**モジュラーモノリスと EDA・CQRS/ES を比較するには、比較対象の実装が要ります。** 現時点で参照元にあるのはここまでであり、続きは対応するソースを収録してから扱います。

[次章](04-spring-eda.md)では、この 3 つの条件のうち 1 つめ——**イベントの配送が JVM を越える必要が出たとき**——を扱います。同じ業務を BC ごとのプロセスに分けた別の実装を参照元にして、プロセス境界がイベント駆動に何を新しく要求するかを見ます。
