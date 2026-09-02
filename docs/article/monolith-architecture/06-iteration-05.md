---
type: Article
title: "第 6 章：IT5 経路の選択・確定・紐付け"
description: "IT5。経路の選択・確定・予約への紐付けを Java と他 9 言語で比較する。"
tags: [article, monolith-architecture]
status: stable
generated: { by: human:kakimomokuri, at: 2026-08-06T01:40:03Z }
---

# 第 6 章：IT5 経路の選択・確定・紐付け

## このイテレーションのゴール

> 経路選択・確定・条件再算出・予約紐付けを完成させ、Phase 2 の経路設計フローを完結させる。**SonarQube Quality Gate をセッション冒頭で必ず確認する**

ゴール文に運用手順が書き込まれています。4 イテレーション連続で落ちていた品質ゲート確認を、機能ストーリーと同格のゴールに昇格させた形です。

| 項目 | 内容 |
| :--- | :--- |
| 目標 SP | 10 |
| 実績 SP | 10 |
| Java テスト | 250 件（+33） |
| Playwright E2E | 56 件（+16） |
| 命令カバレッジ | 88% |
| ブランチカバレッジ | 75% |
| SonarQube | **PASS**（初回確認） |

## 扱うユーザーストーリー

| ID | ストーリー | SP |
| :--- | :--- | :--- |
| US09 | 経路を選択・確定する | 3 |
| US10 | 経路条件を調整して再算出する | 3 |
| US11 | 経路情報を予約に紐付ける | 2 |

## Java 実装

### 旅程を値オブジェクトで表す

経路が確定すると、予約に旅程（`CargoItinerary`）が紐付きます。旅程は区間（`Leg`）の列です。

```java
// booking/domain/model/valueobjects/CargoItinerary.java
public record CargoItinerary(List<Leg> legs) {

    public CargoItinerary {
        Objects.requireNonNull(legs, "legs must not be null");
        if (legs.isEmpty()) {
            throw new IllegalArgumentException("legs must not be empty");
        }
        legs = Collections.unmodifiableList(legs);
    }

    public LocalDateTime expectedArrivalTime() {
        return legs.get(legs.size() - 1).unloadTime();
    }
}
```

コンパクトコンストラクタで「空の旅程は作れない」を保証し、リストを不変化しています。`record` は防御的コピーを自動ではしないため、この `Collections.unmodifiableList` は必要な処置です（これがないと、渡した側がリストを後から変更できてしまいます）。

区間のほうにも不変条件があります。

```java
// booking/domain/model/valueobjects/Leg.java
public record Leg(
        String voyageNumber,
        Location loadLocation,
        Location unloadLocation,
        LocalDateTime loadTime,
        LocalDateTime unloadTime
) {
    public Leg {
        // ... null チェック
        if (!loadTime.isBefore(unloadTime)) {
            throw new IllegalArgumentException("loadTime must be before unloadTime");
        }
    }
}
```

「積込時刻は荷降し時刻より前」という時間の整合が守られています。

一方、**旅程レベルの時間整合は検査していません**。区間 1 の荷降し時刻より区間 2 の積込時刻が前、という不整合な旅程が作れてしまいます。IT5 のふりかえりでは「`Leg` の時刻整合性バリデーション」が中優先度の申し送りとして記録され、以降のイテレーションで持ち越されました。

**要素の不変条件と、集合の不変条件は別物**です。前者だけ守って後者を忘れるのは、値オブジェクト設計でよくある抜けです。

### 状態遷移ガードの抽出

IT5 の成功基準に、機能とは別の項目が並んでいます。

> `Cargo.requireStatus()` が抽出され、状態遷移ガードが 1 箇所に集約されている

IT2 以降、各遷移メソッドに `if (status != XXX) throw` が散っていました。それを抽出します。

```java
// booking/domain/model/aggregates/Cargo.java
public void requireStatus(BookingStatus expected) {
    if (status != expected) {
        throw new IllegalStateException(
                "現在の状態では操作できません。期待: " + expected.getDisplayName()
                + "、現在: " + status.getDisplayName());
    }
}

public void requireStatus(Set<BookingStatus> expected) {
    if (!expected.contains(status)) {
        throw new IllegalStateException(
                "現在の状態では操作できません。許可された状態: "
                + expected.stream().map(BookingStatus::getDisplayName).toList()
                + "、現在: " + status.getDisplayName());
    }
}
```

許可状態の集合は定数として宣言されます。

```java
private static final EnumSet<BookingStatus> CANCELLABLE_STATUSES =
        EnumSet.of(BookingStatus.PRELIMINARY, BookingStatus.ROUTE_PROPOSED, BookingStatus.CONFIRMED,
                BookingStatus.TRACKING_ISSUED, BookingStatus.IN_TRANSIT);
private static final EnumSet<BookingStatus> ROUTABLE_STATUSES =
        EnumSet.of(BookingStatus.PRELIMINARY, BookingStatus.ROUTE_PROPOSED);
```

これで各遷移メソッドは 1 行のガードで済みます。

```java
public Cargo assignItinerary(CargoItinerary itinerary) {
    Objects.requireNonNull(itinerary, "itinerary must not be null");
    requireStatus(ROUTABLE_STATUSES);
    return new Cargo(bookingId, shipperId, cargoType, weight,
            new State(routeSpecification, BookingStatus.ROUTE_PROPOSED,
                    details(dimensions, quantity, description),
                    handling(hazardousDeclaration, temperatureRequirement),
                    itinerary));
}
```

ただし、この抽出は **完全ではありませんでした**。`assignItinerary` と `cancel` への `EnumSet` パターン適用は IT6 と IT10 でようやく完了しています（IT10 の成功基準に「H-1: `assignItinerary` に `requireStatus(EnumSet.of(...))` パターンが適用されている（`cancel()` も統一）」が残っています）。

リファクタリングを「抽出した」で完了扱いにし、**全呼び出し箇所への適用を確認しなかった**ためです。抽出と適用は別の作業であり、後者を DoD に入れていなければ落ちます。

### IT3 で埋めた状態設計の負債が表面化する

`assignItinerary` の遷移先を見てください。

```java
requireStatus(ROUTABLE_STATUSES);  // PRELIMINARY または ROUTE_PROPOSED
// 遷移先は ROUTE_PROPOSED
```

`ROUTE_PROPOSED` から `ROUTE_PROPOSED` への自己遷移になっています。IT3 で「経路設計者への引き渡し（US06）」と「経路の紐付け（US11）」に同じ状態を割り当てたためです。

結果として、`BookingStatus` を見ても経路が紐付いているか分かりません。判定には `cargoItinerary != null` を見るしかなく、状態機械が業務の実態を表せていない状態です。

Scala・Haskell 実装は `RouteProposed`（提案された）と `RouteAssigned`（紐付いた）を別状態に分けており、この問題が起きていません。

```scala
// Scala: 経路紐付けは RouteProposed → RouteAssigned という明確な遷移
def assignItinerary(itinerary: Itinerary): Either[Cargo.Error, Cargo] =
  if status.canTransitionTo(BookingStatus.RouteAssigned) then
    Right(copy(status = BookingStatus.RouteAssigned, itinerary = Some(itinerary)))
  else Left(Cargo.InvalidStatusTransition(status, BookingStatus.RouteAssigned))
```

Scala 版では、状態から経路の有無が読み取れます。しかも `itinerary: Option[Itinerary]` なので、「`RouteAssigned` なのに旅程が `None`」という不整合は `copy` の呼び出し箇所を見れば防げます。

**2 イテレーション前の状態設計の粗さが、ここでコードの読みにくさとして返ってきた**というのが IT5 の実相です。

### 経路条件の再算出（US10）

US10 は「経路条件を変更して候補を再算出する」というストーリーです。実装は既存の `RouteCandidateProvider` を違う引数で呼び直すだけで、新しいドメイン概念は増えません。

ポートを IT3-改善で抽出しておいた効果が、ここでも効いています。再算出は「同じポートを別の条件で呼ぶ」以上のことをしていません。

## 他言語ではどう書いたか

### 旅程の表現

旅程（区間の列）は全言語が値オブジェクトとして表現しました。差が出るのは「空でない」の保証方法です。

| 言語 | 空リスト排除の方法 |
| :--- | :--- |
| Java | コンパクトコンストラクタで `isEmpty()` を検査 |
| Scala | スマートコンストラクタが `Either` を返す |
| Rust | コンストラクタが `Result` を返す |
| Haskell | スマートコンストラクタが `Either DomainError` を返す（`NonEmpty` 型は未使用） |
| F# | スマートコンストラクタが `Result` を返す |
| Go | コンストラクタが `error` を返す |
| C# / TypeScript / Ruby / Flix | 実行時検査 |

Haskell には `Data.List.NonEmpty` という「空でないリスト」型がありますが、実装では使っていません。使っていれば型レベルで空を排除でき、検査コードが消えます。この選択をしなかったのは、永続化層で `[Leg]` との相互変換が増えるためです。IT2 の `CargoType` と同じ、**型の厳密さと永続化の素直さのトレードオフ**がここでも現れています。

### 経路確定という業務の切り方

US09（経路を選択・確定する）を、どのコンテキストの責務にするかで実装が分かれます。

- **Java・C#・TypeScript** — Booking Context の `Cargo.assignItinerary` が主役。Routing Context は航海の検索だけを担う
- **Rust** — 経路確定を Routing Context に置き、`SelectedRouteSummary` として Booking Context に ACL 経由で渡す
- **Scala** — Booking と Routing の中間に位置づけ、`Itinerary` を Booking Context の値オブジェクトとする

Rust のポート定義を見ると、Booking Context が Routing から受け取るものが最小限に絞られています。

```rust
// crates/domain-booking/src/ports.rs
/// 確定経路の読み取り要約（荷主通知 US12 の通知内容組み立てに使用）。
pub struct SelectedRouteSummary {
    /// 航海番号列（区間順）。
    pub voyage_numbers: Vec<String>,
    /// 経由港の UN/LOCODE 列（出発地→各積替港→目的地）。
    // ...
}
```

`Voyage` 集約そのものではなく、**通知に必要な文字列列だけ**を渡しています。Java 実装では `CargoBookingCommandService` が `VoyageRepository` を直接注入して `Voyage` オブジェクトを扱っており、この点は Rust のほうが境界が厳格です。

Java のふりかえりにも、これは問題として記録されています。

> **Routing コンテキストへの ACL**: `BookingThymeleafController` が `VoyageQueryService`・`Voyage` に直接依存している点は IT5 レビューからの持ち越しで、IT6 でも対応できなかった。

### 状態遷移の実装派閥（再訪）

IT5 で Java が到達した `requireStatus` 方式は、C#・Go・TypeScript・Ruby と同じ「集約メソッド内でガード」の形です。ただし共通ヘルパーへの抽出が済んだ点で、IT2 時点より一段進んでいます。

対して Scala・Haskell・Flix は最初から遷移表（`canTransitionTo`）を持っており、抽出という作業自体が発生していません。「状態遷移のルールは状態型の側にある」という設計を最初に選べば、後で抽出する必要がなくなります。

F# はさらに別の道を取り、状態を判別共用体で表現しています。この方式では、遷移関数の引数型が遷移元状態を要求するため、不正な呼び出しがコンパイルエラーになります。ただし、F# 実装も全遷移をこの方式で書いてはおらず、実際には `Result` を返すスマートコンストラクタとの併用です。**型で全部やろうとすると、状態が増えるたびに型の数が増える**というコストがあるためです。

## このイテレーションの学び

### 品質ゲートは「ゴール」に書くと消化される

4 イテレーション落ち続けた SonarQube スキャンが、IT5 でようやく実行され PASS しました。変わったのは、それをイテレーションのゴール文に書いたことだけです。

> 経路選択・確定・条件再算出・予約紐付けを完成させ、Phase 2 の経路設計フローを完結させる。**SonarQube Quality Gate をセッション冒頭で必ず確認する**

「タスクリストの 1 項目」から「ゴールの一部」に格上げすると消化される、という単純な事実です。タスクリストの下のほうにある項目は、時間が足りなくなったときに真っ先に落ちます。

同種の教訓は他言語の実績にも見られます。Scala 実装は IT9 で pre-commit フックに `sbt test`（フルテスト）を追加しており、「実行を忘れる」余地そのものを消しました。Rust 実装は非機能受け入れとして `cargo audit` / `cargo deny` を DoD に組み込み、緑でないとクローズしない運用にしています。

### 受入基準の未達を記録に残す

IT5 のふりかえりでは、受入条件の未達が 2 件記録されています（US09-AC1 費用情報の表示、US11-AC1 割り当て済み経路情報の表示）。これらは IT6 で対応されました。

ただし同じ IT5 レビューで挙がった技術的負債 H-1〜H-9 のうち、H-8・H-9 は IT8 まで、H-1〜H-3・H-5・H-6 は IT10 まで持ち越されています。**5 イテレーション越しの負債**です。

このパターンは Ruby 実装でも指摘されています。技術的負債の返済枠を「余力次第」で置くと、余力が生まれないため毎イテレーション繰り越されて固定化する。対処は、イテレーション序盤に独立したコミット枠を設けて先着手するか、正直にスコープ外と宣言するかのいずれかです。

IT10 の成功基準に H-1〜H-3・H-5・H-6 が明記されているのは、最終的に前者を選んだ結果です。**リリース直前に負債返済イテレーションを設けざるをえなくなった**、という形での回収でした。

---

- 前の章：[第 5 章：IT4 航海スケジュール検索と経路候補算出](05-iteration-04.md)
- 次の章：[第 7 章：IT6 法人割引と精算処理](07-iteration-06.md)
