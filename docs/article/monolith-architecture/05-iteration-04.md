---
type: Article
title: "第 5 章：IT4 航海スケジュール検索と経路候補算出"
description: "IT4。航海スケジュール検索と経路候補算出を Java と他 9 言語で比較する。"
tags: [article, monolith-architecture]
status: stable
generated: { by: human:kakimomokuri, at: 2026-08-06T01:40:03Z }
---

# 第 5 章：IT4 航海スケジュール検索と経路候補算出

## このイテレーションのゴール

> 航海スケジュール検索と経路候補算出の基盤を構築し、Estimation コンテキストのスタブを実データ連携に移行する

IT3 で置いたスタブを実装に差し替えるイテレーションです。同時に Routing Context が新設され、コンテキストが 4 つになります。

| 項目 | 内容 |
| :--- | :--- |
| 目標 SP | 10 |
| 実績 SP | **8**（SonarQube スキャン 2 SP を保留） |
| Java テスト | 190 件（実行 217 件） |
| Playwright E2E | 40 件 |
| 命令カバレッジ | 91%（IT2 の 93% から -2%） |
| ブランチカバレッジ | 75%（IT2 の 81% から -6%） |

実績が目標を下回った最初のイテレーションです。カバレッジも下がっています。理由は後述します。

## 扱うユーザーストーリー

| ID | ストーリー | SP |
| :--- | :--- | :--- |
| US07 | 航海スケジュールを検索する | 5 |
| US08 | 経路候補を算出する | 8（IT4 は基本実装まで） |

## Java 実装

### Routing Context の追加

航海（Voyage）は独立した集約です。

```java
// routing/domain/model/Voyage.java
public class Voyage {

    private final Long id;
    private final VoyageNumber voyageNumber;
    private final Schedule schedule;

    public Location getDepartureLocation() {
        if (schedule.isEmpty()) return null;
        return schedule.carrierMovements().get(0).getDepartureLocation();
    }

    public Location getArrivalLocation() {
        if (schedule.isEmpty()) return null;
        var movements = schedule.carrierMovements();
        return movements.get(movements.size() - 1).getArrivalLocation();
    }
}
```

`Schedule` は `CarrierMovement`（区間輸送）のリストを持つ値オブジェクトです。航海は複数の寄港地を経由しうるため、出発地と到着地は先頭・末尾の区間から導出します。

`getDepartureLocation()` が `null` を返しうる点は設計上の弱点です。空の `Schedule` を持つ `Voyage` を、コンストラクタが拒否していません。値オブジェクトの不変条件（区間が 1 つ以上ある）を `Schedule` 側で守っていれば、この null チェックは不要になります。

### スタブから実データへ

IT3 のスタブを実装に差し替えます。

```java
// estimation/infrastructure/providers/VoyageRouteCandidateProvider.java
@Component
@Primary
public class VoyageRouteCandidateProvider implements RouteCandidateProvider {

    private static final BigDecimal BASE_COST_PER_DAY = new BigDecimal("20000");

    private final VoyageRepository voyageRepository;

    @Override
    public List<RouteCandidate> findCandidates(
            Location origin, Location destination,
            LocalDate arrivalDeadline, CargoType cargoType) {

        List<Voyage> voyages = voyageRepository.findByRoute(
                origin, destination, LocalDate.now(), arrivalDeadline);

        return voyages.stream()
                .map(this::toRouteCandidate)
                .sorted(Comparator.comparingInt(RouteCandidate::transitDays))
                .toList();
    }
}
```

`@Primary` で既存のスタブより優先されるようにしています。スタブ実装は削除せずに残っており、テストで使う余地を残す構成です。

差し替えにあたって **アプリケーション層とドメイン層のコードは変更していません**。IT3-改善でポートを抽出しておいた効果が、この 1 イテレーションで回収されました。

ただし、この実装には受入基準の未達が残っています。

> US07 受入条件 6「危険物・冷凍貨物の場合、対応可能な航海のみに絞り込む」が実装されていない。実データではすべての航海が貨物種別を区別しない。

`findCandidates` は `cargoType` を引数に取っていますが、**使っていません**。ポートのシグネチャには存在するのに実装が無視している状態で、これはコンパイラも lint も検出しません。IT4 のふりかえりで正直に記録されていますが、IT10 まで未対応のまま残りました。

### プレゼンテーション都合のメソッドがドメインに漏れる

IT4 のふりかえりに、こう記録されています。

> **`Voyage.getDurationDays()` をドメインモデルに追加**: テンプレート側の計算を簡略化するために `getDurationDays()` メソッドをドメインオブジェクトに追加した。純粋なドメインモデルへのプレゼンテーション関連メソッドの混入が蓄積すると設計が汚染される可能性がある。

所要日数は航海の業務的な属性でもあるため、この 1 件だけを見れば問題とは言い切れません。問題は判断の基準です。「テンプレートが呼びやすいから」を理由にドメインメソッドを足し始めると、ドメインモデルは徐々にビューモデルに変質します。

このケースは境界線上ですが、**なぜ足したかを記録に残した**ことに価値があります。次に同種の追加をするとき、「前回はビュー都合で足した」という履歴が判断材料になります。

### アーキテクチャテストの拡充

IT4 では ArchUnit ルールに Estimation Context 分が追加されました。既存のルールは次の形です。

```java
// architecture/HexagonalArchitectureTest.java
// ルール 1: ドメイン層はインフラ層に依存しない
@ArchTest
static final ArchRule domain_should_not_depend_on_infrastructure =
        noClasses().that().resideInAPackage("..domain.model..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .allowEmptyShould(true);

// ルール 3: ドメイン層に Spring アノテーションを使用しない
@ArchTest
static final ArchRule domain_should_not_use_spring_component =
        noClasses().that().resideInAPackage("..domain.model..")
                .should().beAnnotatedWith("org.springframework.stereotype.Component")
                .allowEmptyShould(true);

// ルール 5: Booking のドメイン層は Shipper コンテキストのドメイン層に直接依存しない
@ArchTest
static final ArchRule booking_domain_should_not_depend_on_shipper_domain =
        noClasses().that().resideInAPackage("..booking.domain..")
                .should().dependOnClassesThat().resideInAPackage("..shipper.domain..")
                .as("Booking ドメイン層は Shipper コンテキストのドメイン層に直接依存してはならない（shared 経由のみ許可）");
```

ルール 3（ドメイン層に Spring アノテーションを使わない）は、モノリスで最も効くルールです。DI コンテナのアノテーションがドメインに付き始めると、ドメインモデルの単体テストがコンテナ起動を要求するようになり、テストが一気に遅くなります。

## 他言語ではどう書いたか

### 経路候補算出のアルゴリズム

US08 は本来グラフ探索の問題です（積み替えを含む経路を出発地から目的地まで探索する）。ただし全言語とも、最初のイテレーションでは **直行便の検索**に留めています。

Java 実装の候補算出は、航海を所要日数昇順に並べるだけです。

```java
return voyages.stream()
        .map(this::toRouteCandidate)
        .sorted(Comparator.comparingInt(RouteCandidate::transitDays))
        .toList();
```

料金は「基本単価 × 所要日数」という単純な計算で、`BASE_COST_PER_DAY = 20000` がハードコードされています。

このハードコードは全言語に共通します。料金体系は IT10（US21 輸送料金算出）で正式に扱われるため、それまでの暫定値です。**暫定値であることが分かるように定数として名前を付けておく**のは、最低限の作法として全言語が守っています。

### アーキテクチャ検証の実装差

IT4 でアーキテクチャルールを拡充したのは Java だけではありません。各言語の検証手段を比較すると、性質の違いが見えます。

| 言語 | 手段 | 検証タイミング | 特徴 |
| :--- | :--- | :--- | :--- |
| Java | ArchUnit | テスト実行時 | パッケージ名のパターンでルールを書く |
| C# / F# | ArchUnitNET | テスト実行時 | 同上（.NET 版） |
| Scala | ArchUnit | テスト実行時 | JVM 共通 |
| Go | go-arch-lint | `make arch` | YAML でコンポーネントと許可依存を宣言 |
| Ruby | Packwerk | `packwerk check` | pack ごとに公開 API を宣言 |
| TypeScript | dependency-cruiser | lint 実行時 | 依存グラフのルールを JSON で記述 |
| Rust | Cargo の依存グラフ | **コンパイル時** | クレート境界がそのまま強制される |
| Flix | **自作 arch-lint** | テスト実行時 | プロジェクト固有ルールを自前実装 |
| Haskell | モジュール公開リスト | コンパイル時（部分的） | export リストで内部型を隠す |

Rust だけがコンパイル時に落ちます。`domain-booking` クレートの `Cargo.toml` に `domain-shipper` が書かれていなければ、参照するコードは書けません。IT8 のレビューで「BC 独立は全 IT 中もっとも厳格」と評価されているのは、開発者の規律ではなくビルドシステムの帰結です。

Go は `go-arch-lint` の YAML に許可依存を宣言します。裏返すと、**新しいコンテキストを追加したときに YAML の更新を忘れると `make arch` が落ちます**。Go 実装では実際にこれが起きており、合成ルート（`main.go`）に新 BC を配線したのに allowlist を更新しておらず、アーキテクチャチェックが赤になっています。ルールが厳しいほど、ルール自体のメンテナンスが必要になるという当然の帰結です。

Flix は既製のアーキテクチャテストライブラリがないため、自前で `arch-lint` を実装しました。興味深いのは、**その lint 自体にメタテストを付けている**ことです。IT2 の時点ではメタテストが緑でも実コードの違反を 0 件しか検出できておらず、原因はフィクスチャが「最小の違反例」だけで構成されていたことでした。実コードの形に近いフィクスチャに直して、初めて検出が機能しました。

**検証ツールを自作するなら、そのツールが本当に検出できるかを実コードの形で確かめる必要がある**というのは、Flix 固有ではなく一般に成り立つ教訓です。

## このイテレーションの学び

### 実績が目標を下回った

IT4 の実績は 8 SP（目標 10 SP）。差の 2 SP は SonarQube スキャンです。

| ストーリー | 予定 | 加算 |
| :--- | :--- | :--- |
| IT3-改善（`RouteCandidateProvider` 抽出・ArchUnit 追加・属性保全テスト） | 2 | **0** |
| US07 | 5 | 5 |
| US08 基本実装 | 3 | 3 |
| 合計 | 10 | **8** |

IT3-改善の 3 項目のうち 2 項目は完了しているのに、加算を 0 にしています。「部分完了は 0 点」という扱いです。ベロシティを実態より高く見せない、という判断で、計画の精度を保つには正しい処理です。

### カバレッジが下がる

命令カバレッジが 93% → 91%、ブランチが 81% → 75% に低下しました。ふりかえりの分析は「Routing コンテキスト追加による分母増加」「動的クエリ分岐の増加」です。

これは以降のイテレーションでも繰り返し起きます。

| イテレーション | 命令カバレッジ | 主因 |
| :--- | :--- | :--- |
| IT2 | 93% | — |
| IT4 | 91% | Routing Context 追加 |
| IT5 | 88% | `CargoItinerary` / `Leg` 追加 |
| IT6 | 81% | Billing Context 追加 |
| IT9 | 80% | — |
| IT10 | 80.9% | — |

**新しいコンテキストを追加した直後は必ず下がる**という規則性があります。新規コードのテストが既存コードと同水準の密度で書かれていても、既存の高カバレッジ領域に薄いコードが加わればならされるためです。

この事実は、カバレッジ目標を「全体 80% 以上」という形だけで管理することの限界を示します。IT7 以降のレポートで「新規コード基準 81.7%」という表現に切り替わっているのは、SonarQube の New Code 概念を使い始めたためです。**全体の平均ではなく、今回書いたコードの被覆率を見る**ほうが、イテレーション単位の品質管理には適しています。

TypeScript 実装のレポートも同じ形になっており、「全体 94.05%・新規 92.1%」と両方を併記しています。

---

- 前の章：[第 4 章：IT3 輸送見積と経路設計への引き渡し](04-iteration-03.md)
- 次の章：[第 6 章：IT5 経路の選択・確定・紐付け](06-iteration-05.md)
