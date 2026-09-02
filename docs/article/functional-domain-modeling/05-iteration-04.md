---
type: Article
title: "第 5 章：IT4 経路確定から予約確定まで"
description: "IT4。状態がデータを持つ設計で経路確定から予約確定までを表し、永続化との往復で失われるものを扱う。"
tags: [article, functional-domain-modeling]
status: stable
generated: { by: human:kakimomokuri, at: 2026-08-06T02:33:18Z }
---

# 第 5 章：IT4 経路確定から予約確定まで

## このイテレーションのゴール

> 算出済み経路候補の選択・確定から、予約への紐付け・荷主通知・予約確定まで、経路設計者と営業担当者をまたぐ予約フローを完結させる

本シリーズの中心となる章です。**状態がデータを持つ状態機械**という技法が、ここで完全な形になります。

| 項目 | 内容 |
| :--- | :--- |
| 目標 SP | 12（US09 / US10 / US11 / US12 / US13） |
| 局面 | 中盤／アプローチ: インサイドアウト |
| ユニットテスト | 128 件緑（+34） |
| 統合テスト | 101 件緑（+16） |
| アーキテクチャテスト | 13 件緑（+5） |
| カバレッジ（全体 / ドメイン層） | **94.1% / 90.2%**（全 8 イテレーション中の最高値） |
| ADR | 0007 経路設計中状態は BookingState DU 拡張で表現 / 0010 Routing→Booking 連携は合成層の ACL 変換 |

## 扱うユーザーストーリー

| ID | ストーリー |
| :--- | :--- |
| US09 | 経路を選択・確定する |
| US10 | 経路条件を調整して再算出する |
| US11 | 経路情報を予約に紐付ける |
| US12 | 確定経路を荷主に通知する |
| US13 | 予約を確定する |

## モデリング：状態がデータを持つ

`BookingState` に、旅程を持つ状態が追加されます。

```fsharp
// src/CargoTracker.Booking/Domain.fs
/// 予約状態（IT4 で RouteProposed/Confirmed を追加・ADR-0007 系統）。
/// IT7 で Delivered/Settled を段階追加（配送完了→精算完了・ADR-0013・戦略の Delivered 制限）。
type BookingState =
    | Preliminary
    | RoutingRequested
    | RouteProposed of CargoItinerary
    | Confirmed of CargoItinerary
    | Delivered of CargoItinerary
    | Settled of CargoItinerary
    | Cancelled of reason: string
```

旅程は `Cargo` レコードのフィールドではなく、**状態のペイロード**です。ここが設計の核心です。

- `Preliminary`・`RoutingRequested` — 旅程を持たない。持ちようがない
- `RouteProposed` 以降 — 必ず旅程を持つ。持たずには構築できない
- `Cancelled` — 理由を持つ

「経路提案済みなのに旅程が null」という状態が、**型として存在しません**。

旅程を取り出すコードは、状態の確認を兼ねます。

```fsharp
    /// 状態が保持する旅程（旅程を持つ状態のみ）。永続化で leg テーブルへ書き出す際に使う。
    let itinerary (state: BookingState) : CargoItinerary option =
        match state with
        | RouteProposed i
        | Confirmed i
        | Delivered i
        | Settled i -> Some i
        | Preliminary
        | RoutingRequested
        | Cancelled _ -> None
```

`or パターン`（`| RouteProposed i | Confirmed i | ...`）で、旅程を持つ 4 状態をまとめています。状態を追加すると、この関数がコンパイルエラーになるので、**旅程を持つかどうかの判断が漏れません**。

### 姉妹シリーズとの対比

Java 実装は同じ要件をこう書きました。

```java
// Java: 旅程は独立したフィールド。状態とは無関係に null でありうる
private final CargoItinerary cargoItinerary;
private final BookingStatus status;
```

さらに、状態名の設計にも問題がありました。「経路設計者に引き渡した」（US06）と「経路が紐付いた」（US11）を、どちらも `ROUTE_PROPOSED` で表したのです。結果として `assignItinerary` が `ROUTE_PROPOSED → ROUTE_PROPOSED` の自己遷移になり、状態を見ても経路の有無が分かりません。

F# 実装は `RoutingRequested`（引き渡した）と `RouteProposed of CargoItinerary`（紐付いた）を分けています。しかも後者は旅程を持つので、状態を見れば内容まで分かります。

ADR-0007 として「経路設計中状態は BookingState DU の拡張で表現する」が記録されている通り、これは意識的な設計判断です。

## モデリング：ビジネスルールをドメインで検証する

旅程を紐付けるとき、それがルート仕様（出発地・目的地・期限）を満たすかを確認する必要があります。

判定は旅程側の純粋関数です。

```fsharp
    /// 旅程がルート仕様（出発地・目的地・到着期限）を満たすか（domain-model の RouteSpecification.isSatisfiedBy に対応）。
    let satisfies (spec: RouteSpecification) (itinerary: CargoItinerary) : bool =
        Location.sameAs (firstLoadLocation itinerary) (RouteSpecification.origin spec)
        && Location.sameAs (lastUnloadLocation itinerary) (RouteSpecification.destination spec)
        && System.DateOnly.FromDateTime((expectedArrivalTime itinerary).UtcDateTime)
           <= RouteSpecification.arrivalDeadline spec
```

3 つの条件が `&&` で並んでいるだけです。DB もフレームワークも介在しないので、テストは値を作って呼ぶだけで済みます。

そして状態遷移がこれを使います。

```fsharp
    /// 状態遷移（US06/US11/US13）。網羅的パターンマッチにより許可されない遷移は InvalidStateTransition を返す。
    let execute (cargo: Cargo) (command: BookingCommand) : Result<Cargo * BookingEvent list, DomainError> =
        match cargo.State, command with
        | Preliminary, SubmitForRouting ->
            Ok({ cargo with State = RoutingRequested }, [ RoutingRequestedEvent cargo.BookingId ])

        // US11: 経路提案。旅程がルート仕様（出発地・目的地・期限）を満たすことを検証する。
        | RoutingRequested, ProposeRoute itinerary ->
            if CargoItinerary.satisfies cargo.RouteSpecification itinerary then
                Ok(
                    { cargo with
                        State = RouteProposed itinerary },
                    [ CargoRouted cargo.BookingId ]
                )
            else
                Error(BusinessRuleViolation("RouteSpecification", "旅程がルート仕様（出発地・目的地・到着期限）を満たしていません。"))

        // US13: 予約確定。
        | RouteProposed itinerary, ConfirmBooking ->
            Ok(
                { cargo with
                    State = Confirmed itinerary },
                [ BookingConfirmed cargo.BookingId ]
            )

        // US13 受入条件4: 予約確定から経路設計中へ差し戻す（ルート変更希望）。
        | Confirmed _, RestoreToRouting ->
            Ok({ cargo with State = RoutingRequested }, [ BookingRestoredToRouting cargo.BookingId ])
```

`| RouteProposed itinerary, ConfirmBooking ->` の行に注目してください。**パターンマッチで状態を確認すると同時に旅程を取り出しています**。「確定するには経路提案済みでなければならず、そのとき旅程は必ず存在する」が 1 行で表現されています。

Java 実装ではこれが 2 段階になります。`requireStatus(ROUTE_PROPOSED)` で状態を確認し、別途 `cargoItinerary` を読む。読んだ値が null でないことは型では保証されません。

キャンセルは特別扱いです。

```fsharp
        // Cancelled からの Cancel は不正遷移。それ以外の状態からは Cancel 可能（US13 受入条件5）。
        | Cancelled _, Cancel _ -> Error(InvalidStateTransition(BookingState.toString cargo.State, "Cancel"))

        | _, Cancel reason -> Ok({ cargo with State = Cancelled reason }, [ BookingCancelled(cargo.BookingId, reason) ])

        | state, cmd -> Error(InvalidStateTransition(BookingState.toString state, sprintf "%A" cmd))
```

「キャンセル済みからのキャンセルだけ不正、他はすべて可」を、**より具体的なパターンを先に書く**ことで表しています。F# のパターンマッチは上から順に照合するため、この順序に意味があります。

姉妹シリーズの Java 実装は同じことを `EnumSet` で書きました。

```java
private static final EnumSet<BookingStatus> CANCELLABLE_STATUSES =
        EnumSet.of(BookingStatus.PRELIMINARY, BookingStatus.ROUTE_PROPOSED, BookingStatus.CONFIRMED,
                BookingStatus.TRACKING_ISSUED, BookingStatus.IN_TRANSIT);
```

許可する状態を列挙する形です。状態を追加したとき、この集合への追加を忘れると**エラーもなく静かにキャンセル不可になります**。F# 版は「キャンセル済み以外すべて」と書いているので、状態が増えても意図どおりに動きます。

## 永続化との往復で失われるもの

DU を DB に落とすとき、情報が減ります。

```fsharp
    /// 永続化のための文字列表現（cargo.booking_status）。
    let toString (state: BookingState) : string =
        match state with
        | Preliminary -> "PRELIMINARY"
        | RoutingRequested -> "ROUTING_REQUESTED"
        | RouteProposed _ -> "ROUTE_PROPOSED"
        | Confirmed _ -> "CONFIRMED"
        | Delivered _ -> "DELIVERED"
        | Settled _ -> "SETTLED"
        | Cancelled _ -> "CANCELLED"
```

`RouteProposed _` のワイルドカードが示す通り、**ペイロードは捨てられます**。旅程は `leg` テーブルに別途保存し、キャンセル理由は保存していません。

復元はその逆で、ペイロードを外から与えます。

```fsharp
    /// 永続化された文字列から状態を復元する（cargo.booking_status）。
    /// `ROUTE_PROPOSED`/`CONFIRMED` は旅程（leg テーブル由来）を要するため itinerary を受け取る。
    /// 【往復非対称の注意】`Cancelled reason` の reason は booking_status カラムに保持しないため、
    /// ラウンドトリップで理由は失われ空文字で復元される（cancellation_reason 化は将来）。
    let ofString (itinerary: CargoItinerary option) (value: string) : Result<BookingState, DomainError> =
        let withItinerary ctor =
            match itinerary with
            | Some i -> Ok(ctor i)
            | None -> Error(ValidationError("BookingState", sprintf "%s の復元には旅程が必要です。" value))

        match value with
        | "PRELIMINARY" -> Ok Preliminary
        | "ROUTING_REQUESTED" -> Ok RoutingRequested
        | "ROUTE_PROPOSED" -> withItinerary RouteProposed
        | "CONFIRMED" -> withItinerary Confirmed
        | "DELIVERED" -> withItinerary Delivered
        | "SETTLED" -> withItinerary Settled
        | "CANCELLED" -> Ok(Cancelled "")
        | other -> Error(ValidationError("BookingState", sprintf "未知の予約状態です: %s" other))
```

コメントに **「往復非対称の注意」** として、キャンセル理由が失われることが明記されています。

これは関数型ドメインモデリングを実務に適用したときの典型的な摩擦です。型は「キャンセルには理由がある」と主張していますが、DB スキーマがそれを保持していないため、復元すると空文字になります。**型が保証しているのはメモリ上の一貫性だけ**であり、永続化の往復までは守りません。

対処としては、スキーマに `cancellation_reason` を足すのが正解です。この実装は「将来」として先送りし、代わりに**非対称であることをコメントで明示**しました。次に読む人が「理由が空になるのはバグか？」と悩まずに済みます。

なお `withItinerary` の設計は堅実です。旅程を要する状態を旅程なしで復元しようとすると `Error` になります。DB の状態カラムと `leg` テーブルが食い違っていれば、復元の時点で気づけます。

## ワークフロー：共通形を 1 つ作る

7 種類の状態遷移コマンドがありますが、アプリケーション層の処理はすべて同じ形です。

```fsharp
// src/CargoTracker.Booking/Application.fs
    /// 予約を読み込みコマンドを適用して永続化し、コミット成功後にイベントを発火する共通ワークフロー。
    /// `repo.Update` が Ok を返した時点で永続化はコミット済みのため、ここでの dispatch は post-commit（ADR-0002）。
    /// 失敗（NotFound・ドメイン検証エラー・永続化失敗）時はイベントを発火しない。
    let private applyCommand
        (repo: CargoRepository)
        (dispatcher: BookingEventDispatcher)
        (bookingId: BookingId)
        (command: BookingCommand)
        : Async<Result<Cargo * BookingEvent list, DomainError>> =
        asyncResult {
            let! found = repo.FindById bookingId

            let! cargo =
                match found with
                | Some c -> Ok c
                | None -> Error(NotFound("Cargo", BookingId.value bookingId))

            let! updated, events = Cargo.execute cargo command
            do! repo.Update updated
            // 永続化コミット後にのみイベントを順次発火する（ロールバック時は未発火）。
            for e in events do
                // ... dispatch（例外は握り潰してログに残す）
            return updated, events
        }
```

各ユースケースは、この共通形にコマンドを渡すだけになります。

```fsharp
    /// 確定経路を予約に紐付ける（US09/US11・RoutingRequested → RouteProposed）。
    /// 旅程がルート仕様を満たすかはドメイン（Cargo.execute）が検証する。
    let proposeRoute (repo: CargoRepository) (dispatcher: BookingEventDispatcher)
                     (bookingId: BookingId) (itinerary: CargoItinerary) =
        applyCommand repo dispatcher bookingId (ProposeRoute itinerary)

    /// 予約を確定する（US13・RouteProposed → Confirmed）。
    let confirmBooking (repo: CargoRepository) (dispatcher: BookingEventDispatcher) (bookingId: BookingId) =
        applyCommand repo dispatcher bookingId ConfirmBooking

    /// 経路設計中へ差し戻す（US13 受入条件4・Confirmed → RoutingRequested）。
    let restoreToRouting (repo: CargoRepository) (dispatcher: BookingEventDispatcher) (bookingId: BookingId) =
        applyCommand repo dispatcher bookingId RestoreToRouting
```

**遷移を DU のコマンドとして表したことの配当**です。ユースケースごとに「読み込み → 検証 → 更新 → イベント発火」を書き写す必要がなく、1 行で済みます。

姉妹シリーズの Java 実装では、`confirmBooking`・`cancelBooking`・`assignItinerary`・`settleBooking`・`assignToRouting` がそれぞれ 6〜10 行の同じ形を繰り返しており、そのうち 1 つでイベント発行が漏れました。共通形に括り出せていれば、漏れようがありません。

### post-commit ディスパッチの実務的な妥協

イベント発火の部分には、率直な判断が書かれています。

> post-commit のため永続化は既に確定済み。発火はベストエフォートとし、失敗しても確定済みの結果を巻き戻さない（実消費への差し替え時はディスパッチャ側でリトライ/DLQ を担う）。dispatch 例外は握り潰さずログに残す（可観測性・レビュー中#1）。

コミット済みの結果は巻き戻せないので、通知の失敗は握り潰す。ただし**ログには必ず残す**。「レビュー中#1」という記載から、この可観測性の担保はレビュー指摘で追加されたものと分かります。

型で守れる範囲の外側です。分散した副作用の信頼性は、リトライや DLQ という運用の仕組みで担保するしかありません。

## このイテレーションの学び

### カバレッジの最高値が出た理由

このイテレーションのカバレッジは全体 94.1%、ドメイン層 90.2% で、8 イテレーション中の最高値です。

理由は明確で、**このイテレーションの成果物のほとんどが純粋関数**だからです。状態遷移・ルート仕様の充足判定・旅程の連結検査。いずれも DB もフレームワークも要らず、値を作って呼ぶだけでテストできます。

姉妹シリーズで見た通り、Java 実装は新しいコンテキストを追加するたびにカバレッジが下がりました（IT4 で 91%、IT5 で 88%、IT6 で 81%）。F# 実装が 89〜94% の帯を維持できたのは、**テストしにくいコードの割合が構造的に低い**ためです。

### 状態機械を DU で表す効用

このイテレーションで得られたものを整理します。

| 得たもの | 仕組み |
| :--- | :--- |
| 「経路提案済みなのに旅程なし」が作れない | 状態がペイロードを持つ |
| 旅程の取り出しに null チェックが不要 | パターンマッチが状態確認を兼ねる |
| 状態追加時に修正箇所が漏れない | 網羅性検査 |
| ユースケースが 1 行で書ける | 遷移をコマンド DU にした |
| キャンセル可能状態の列挙が不要 | catch-all パターンで「それ以外」を表せる |

### 型が守らない領域

同時に、限界も見えています。

- **永続化の往復** — キャンセル理由は DB に保持されず失われる。型は主張するが DB は保証しない
- **イベント発火の到達性** — post-commit のベストエフォートであり、失敗しうる
- **状態の意味** — `Delivered` と `Settled` の業務的な違いは、型からは読み取れない

3 つ目は特に注意が要ります。DU のケース名は開発者が付けた名前であり、それが業務の言葉と一致しているかはコンパイラの関知するところではありません。**ユビキタス言語の一致は、型ではなくレビューで守るもの**です。

---

- 前の章：[第 4 章：IT3 航海スケジュールと経路候補算出](04-iteration-03.md)
- 次の章：[第 6 章：IT5 追跡と荷役](06-iteration-05.md)
