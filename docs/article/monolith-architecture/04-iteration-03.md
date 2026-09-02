---
type: Article
title: "第 4 章：IT3 輸送見積と経路設計への引き渡し"
description: "IT3。輸送見積と経路設計への引き渡しを Java と他 9 言語で比較する。"
tags: [article, monolith-architecture]
status: stable
generated: { by: human:kakimomokuri, at: 2026-08-06T01:40:03Z }
---

# 第 4 章：IT3 輸送見積と経路設計への引き渡し

## このイテレーションのゴール

> Phase 2 開始 — 輸送見積作成と経路設計者への予約引き渡しを実現し、IT2 申し送り事項を完了する

Phase 1（予約基盤）が終わり、Phase 2（経路設計）に入ります。ここで **新しい Bounded Context（Estimation）が 1 つ増えます**。既存コンテキストへの機能追加ではなく、コンテキストそのものを足す最初のイテレーションです。

| 項目 | 内容 |
| :--- | :--- |
| 目標 SP | 10（IT2 改善: 3 + US01: 5 + US06: 2） |
| 実績 SP | 10 |
| Java テスト | 約 184 件（+18） |
| Playwright E2E | 41 件（+10） |
| カバレッジ | 未計測 |

## 扱うユーザーストーリー

| ID | ストーリー | SP |
| :--- | :--- | :--- |
| US01 | 輸送見積を作成する | 5 |
| US06 | 予約情報を経路設計者に引き渡す | 2 |

US01 の受入基準は「営業担当者が出発地・目的地・希望期限・貨物仕様を入力し、概算ルート候補と料金を含む見積を作成・保存できる」。US06 は「営業担当者が仮受付予約を経路設計者に引き渡すと、予約状態が経路設計中に遷移する」です。

## Java 実装

### スコープを意図的に切る

イテレーション計画に、次の注意書きが明記されています。

> **IT3 スコープ注意**: ルート候補は IT3 ではスタブ実装（静的データ）とし、US07・US08 の実際の航海スケジュール検索・経路算出と連携するのは IT4 で行う

見積機能を完成させるには経路候補が必要ですが、経路候補の算出（US07・US08）は次のイテレーションのストーリーです。ここで「US07 も一緒にやってしまう」誘惑に乗らず、**スタブで縦を通す**判断をしています。

### スタブを差し替え可能にする設計

そのために必要なのが、スタブと実装を同じ型で扱える境界です。

```java
// estimation/domain/model/port/RouteCandidateProvider.java
public interface RouteCandidateProvider {
    List<RouteCandidate> findCandidates(
            Location origin,
            Location destination,
            LocalDate arrivalDeadline,
            CargoType cargoType
    );
}
```

IT3 の実装はスタブです。

```java
// estimation/infrastructure/providers/StubRouteCandidateProvider.java
/**
 * RouteCandidateProvider のスタブ実装。
 * IT4 で VoyageRouteCandidateProvider（実データ連携）に差し替えられるまでの暫定実装。
 */
@Component
public class StubRouteCandidateProvider implements RouteCandidateProvider {

    @Override
    public List<RouteCandidate> findCandidates(
            Location origin, Location destination,
            LocalDate arrivalDeadline, CargoType cargoType
    ) {
        BigDecimal baseCost = new BigDecimal("500000");
        return List.of(
                new RouteCandidate("V001", "SGSIN", 21, baseCost),
                new RouteCandidate("V002", "HKHKG", 28, baseCost.multiply(new BigDecimal("0.96")))
        );
    }
}
```

ポートを **ドメイン層に置き**、実装をインフラ層に置いています。ドメインは「経路候補を得る手段がある」ことだけを知り、それが静的データか DB 検索かを知りません。IT4 でこの実装が `VoyageRouteCandidateProvider` に差し替わりますが、ドメイン層・アプリケーション層のコードは 1 行も変わりませんでした。

ただし正直に書くと、**この `RouteCandidateProvider` は IT3 の時点では存在しませんでした**。IT3 では見積サービスがスタブを直接呼んでおり、ポートの抽出は IT4 のタスク（IT3-改善）として実施されています。差し替えのために結果的にポートが必要になった、という順序です。

**「後で差し替える」と分かっている箇所には最初から境界を引いておく**というのが、この 2 イテレーションの実地の教訓です。

### Estimate 集約

見積は独立した集約です。予約（`Cargo`）とは別のライフサイクルを持ちます。

```java
// estimation/domain/model/Estimate.java
public static Estimate create(
        Location origin,
        Location destination,
        LocalDate arrivalDeadline,
        CargoType cargoType,
        BigDecimal weightKg
) {
    Objects.requireNonNull(origin, "origin must not be null");
    Objects.requireNonNull(destination, "destination must not be null");
    if (Objects.equals(origin, destination)) {
        throw new IllegalArgumentException("origin and destination must be different");
    }
    if (arrivalDeadline == null) {
        throw new IllegalArgumentException("arrivalDeadline must not be null");
    }
    Objects.requireNonNull(cargoType, "cargoType must not be null");
    if (weightKg == null || weightKg.compareTo(BigDecimal.ZERO) <= 0) {
        throw new IllegalArgumentException("weightKg must be positive");
    }
    return new Estimate(EstimateId.generate(), origin, destination, arrivalDeadline, cargoType, weightKg);
}
```

ここで注目したいのは、Estimation Context が **独自の `CargoType` を持っている**ことです。Booking Context にも `CargoType` があり、名前も値も同じですが、別の型です。

```
booking/domain/model/aggregates/CargoType.java
estimation/domain/model/CargoType.java
```

コンテキストごとに同名の概念を別型で持つのは、DDD の観点では正しい選択です。見積時点の貨物種別と予約確定後の貨物種別は、業務上の意味が微妙に違い、将来別々に進化しうるためです。共有カーネルに上げてしまうと、片方の都合でもう片方が変更を強いられます。

一方で `Location` は共有カーネル（`shared.domain.model.Location`）に置かれています。UN/LOCODE は国際規格であり、コンテキストごとに解釈が変わる余地がないためです。

**何を共有し何を分けるかは、「将来別々に変わりうるか」で決める**という基準がここに表れています。

### 予約の引き渡し（US06）

US06 は状態遷移 1 つで表現されます。

```java
public Cargo assignToRouting() {
    requireStatus(BookingStatus.PRELIMINARY);
    return new Cargo(bookingId, shipperId, cargoType, weight,
                     currentStateWith(BookingStatus.ROUTE_PROPOSED));
}
```

`PRELIMINARY`（仮受付）から `ROUTE_PROPOSED` へ。イベントも発行されます。

```java
public void assignToRouting(AssignToRoutingCommand command) {
    // ...
    Cargo routed = cargo.assignToRouting();
    cargoRepository.updateStatus(routed);
    eventPublisher.publishEvent(new BookingAssignedToRoutingEvent(routed.getBookingId()));
}
```

ここで Java 実装は状態名の選択を誤っています。「経路設計者に引き渡した」状態と「経路が提案された」状態を、どちらも `ROUTE_PROPOSED` で表してしまいました。業務上は別の状態です。この曖昧さが IT5 で表面化します。

## 他言語ではどう書いたか

### コンテキストを増やすタイミング

Estimation Context をいつ切り出したかは、言語ごとに大きく違います。

| 言語 | Estimation Context の導入 |
| :--- | :--- |
| Java | IT3（Phase 2 の開始と同時） |
| TypeScript | IT2（Booking と同じイテレーションで縦フローとして） |
| Ruby | IT7（終盤に見積〜精算をまとめて） |
| Rust | 中盤（US01 を単独で扱わず経路設計に統合） |
| Scala | IT3 前後 |

TypeScript 実装のイテレーション計画にはこう書かれています。

> 見積作成 → 貨物予約登録 → 経路設計者への引き渡しまでの **MVP 縦フロー** を完成させる。Estimation Context（見積・ルート候補スタブ）と Booking Context（Cargo 集約・荷受人・危険物/冷凍・状態遷移）を実装し、…

TypeScript は 7 イテレーションと最も粗い刻みで、1 イテレーションに複数コンテキストを載せています。Ruby は逆に、見積を最後のフェーズまで後回しにしました。

**どちらが正しいという話ではありません**。ただし Ruby 実装が見積を IT7 に回せたのは、見積が他機能の前提になっていないからです。US01 は「営業が概算を出す」という独立した業務で、予約登録は見積なしでも成立します。Java が IT3 でこれを実装したのは、Phase 2 の入口として自然な順序だったからで、依存関係の要請ではありません。

### スタブから実装への差し替え

「先にスタブ、後で実装」というパターン自体は全言語が採っていますが、差し替えの安全性に差があります。

Rust では、スタブと実装が同じ trait を実装していない限りコンパイルが通りません。差し替えは `main.rs` の合成ルートで実装型を変えるだけで、型が合わなければビルドが落ちます。

Go は明示的な interface 実装宣言がないため（構造的部分型）、シグネチャが合っていれば通ります。裏を返すと、ポートを変更したときに実装側が追随していないことを検出できるのは、実際に代入する箇所だけです。

Haskell・F# は関数レコード（レコードのフィールドに関数を持つ）でポートを表現する箇所があり、この場合フィールド名の衝突に注意が必要です。F# 実装では、同名フィールドを持つポートレコードを複数定義した際に型推論が最後の定義を選んでしまう問題が起きており、スタブに返り値の型注釈を付けることで回避しています。

### 「同名だが別のコンテキストの型」の扱い

Estimation と Booking がそれぞれ `CargoType` を持つ問題は、名前空間の作法で処理が分かれます。

- **Java・C#・Scala・TypeScript** — パッケージ／名前空間が違えば同名でよい。import で区別
- **Rust** — クレートが違えば同名でよい。`domain_booking::CargoType` と `domain_estimation::CargoType`
- **Go** — パッケージが違えば同名でよい。`booking.CargoType` / `estimation.CargoType`
- **Haskell** — モジュールが違えば同名でよいが、**`import ... (..)` で全部取り込むと衝突する**

Haskell 実装では、この衝突が実際に問題になりました。`Cargo (..)` のようにコンストラクタごと取り込む import は、`origin` / `destination` / `cargoType` といった業務でありふれたフィールド名を裸で持ち込むため、別コンテキストの同名フィールドと衝突します。対処は qualified import の徹底です。

```haskell
import qualified Cargotracker.Booking.Domain.Model.Cargo as Booking
```

同様に F# でも、レコードフィールド名の衝突により型推論が意図しない型を選ぶ問題が起きています。**コンテキストを分けたことによる同名は正しい設計だが、言語の名前解決規則によっては追加の作法を要求される**、というのがこの層の実務的な注意点です。

## このイテレーションの学び

IT3 のふりかえりで最も重いのは、**IT2 からの申し送りが複数持ち越された**ことです。

- `Cargo.requireStatus()` 抽出
- `BookingThymeleafController` の try-catch 共通化
- テスト命名規則の統一
- SonarQube スキャン

これらは IT4 でも消化されず、IT5 まで持ち越されます。IT4 のふりかえりにはこう記録されています。

> **SonarQube Quality Gate が 4 イテレーション連続で未確認**: IT1〜IT4 とスキャンを実行しておらず、静的解析による問題が蓄積している可能性がある。IT4 でも「SonarQube 実行」を Task 1.1 に計上したが、セッション時間の制約で優先度が後回しになった。

計画には毎回入れているのに、毎回落ちています。ここには一般的な構造があります。**「余力があればやる」と位置づけたタスクは、余力が生まれないため永久に繰り越される**。

対処は 2 通りしかありません。イテレーション冒頭の独立したコミット枠で先に片付けるか、正直にスコープ外と宣言して計画から外すかです。IT5 では前者を採り、「SonarQube Quality Gate をセッション冒頭で必ず確認する」をイテレーションのゴール文に書き込むことで、ようやく消化されました。

もう 1 つ、設計上の学びがあります。IT3 のスコープ判断（ルート候補はスタブ）は正しかった一方、**スタブ化する箇所にポートを引き忘れた**ことで IT4 に抽出タスクが生じました。「後で差し替える」と明言している箇所は、その時点で境界を切っておくのが安い。差し替えが決まっている依存は、仮実装ではなく仮**実装**として扱う——つまり、インターフェースは本物にしておくということです。

---

- 前の章：[第 3 章：IT2 特殊貨物と予約確定](03-iteration-02.md)
- 次の章：[第 5 章：IT4 航海スケジュール検索と経路候補算出](05-iteration-04.md)
