---
type: Article
title: "第 8 章：IT7 追跡番号発行と荷役作業記録"
description: "IT7。追跡番号発行と荷役作業記録を Java と他 9 言語で比較する。"
tags: [article, monolith-architecture]
status: stable
generated: { by: human:kakimomokuri, at: 2026-08-06T01:40:03Z }
---

# 第 8 章：IT7 追跡番号発行と荷役作業記録

## このイテレーションのゴール

> IT6 申し送り事項を解消し、追跡番号発行と荷役作業記録の基盤を構築する

Release 1.0 を出した後、後回しにしていた Phase 2 後半（追跡）に戻ります。Tracking Context が新設され、コンテキストは 6 つになります。

| 項目 | 内容 |
| :--- | :--- |
| 目標 SP | 10 |
| 実績 SP | 10 |
| Java テスト | 272 件以上 |
| Playwright E2E | 78 件（+11） |
| カバレッジ | 81.7%（**新規コード基準**） |

カバレッジの計測基準が「全体」から「新規コード」に変わっています。第 5 章で述べた通り、コンテキスト追加のたびに全体平均が下がるため、イテレーション単位の品質はこちらで見るほうが妥当です。

## 扱うユーザーストーリー

| ID | ストーリー | SP |
| :--- | :--- | :--- |
| US14 | 追跡番号を発行する | 3 |
| US15 | 荷役作業を記録する | 5 |

## Java 実装

### 追跡番号という識別子

追跡番号は、荷主が問い合わせに使う対外的な識別子です。内部 ID（UUID）とは別に生成します。

```java
// tracking/domain/model/valueobjects/TrackingNumber.java
public record TrackingNumber(String value) {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final SecureRandom RANDOM = new SecureRandom();

    public static TrackingNumber generate() {
        String datePart = LocalDate.now().format(DATE_FORMATTER);
        byte[] randomBytes = new byte[4];
        RANDOM.nextBytes(randomBytes);
        String randomPart = HexFormat.of().withUpperCase().formatHex(randomBytes);
        return new TrackingNumber("TRK-" + datePart + "-" + randomPart);
    }
}
```

`SecureRandom` を使っている点が重要です。追跡番号は**認証なしで照会できる**公開 URL のキーになるため（US18 の公開追跡ページ）、連番や推測可能な値だと他人の貨物情報が漏れます。4 バイト（32 ビット）の乱数部は、同日内の総当たりに対して十分とは言い切れませんが、少なくとも設計意図としては正しい方向です。

`LocalDate.now()` を静的メソッド内で直接呼んでいる点は、テスタビリティ上の弱点です。生成日をテストで固定できません。F# 実装のように、時刻取得をポート（`IdGenerator` / `Clock`）として注入する設計であれば、この問題は起きません。

### コンテキストをまたぐ番号発行

追跡番号の発行は Tracking Context の責務ですが、起点は Booking Context（「予約確定した貨物に番号を出す」）です。

```java
// booking/application/internal/outboundservices/acl/TrackingPort.java
public interface TrackingPort {
    String issueTrackingNumber(String bookingId);
}
```

```java
// booking/infrastructure/services/TrackingAdapter.java
@Component
public class TrackingAdapter implements TrackingPort {

    private final TrackingCommandService trackingCommandService;

    @Override
    public String issueTrackingNumber(String bookingId) {
        var command = new IssueTrackingNumberCommand(bookingId);
        return trackingCommandService.issueTrackingNumber(command).value();
    }
}
```

ポートは `String` を返します。`TrackingNumber` 型（Tracking Context の値オブジェクト）を返すと、Booking Context がその型を知る必要が生じるためです。アダプターが `.value()` で剥がしています。

型安全性を捨てて境界の薄さを取った形です。Booking Context 側では `Cargo.trackingNumber` が `String` になっており、値オブジェクトの利点が失われます。代案は Booking Context 側にも `BookingTrackingNumber` のような自前の型を用意することで、Scala 実装はこの方式を採っています（`BookingTrackingNumber`）。

### 荷役イベントから状態を導出する

`TrackingRecord` 集約は、荷役イベントの追記によって状態が変わります。

```java
// tracking/domain/model/aggregates/TrackingRecord.java
public void addHandlingEvent(TrackingActivityEvent event) {
    if (event == null) throw new IllegalArgumentException("event must not be null");
    handlingEvents.add(event);
    this.status = deriveStatus(event.getEventType());
}

private CargoTrackingStatus deriveStatus(TrackingEventType eventType) {
    return switch (eventType) {
        case RECEIVE -> CargoTrackingStatus.RECEIVED;
        case LOAD -> CargoTrackingStatus.LOADED;
        case UNLOAD -> CargoTrackingStatus.UNLOADED;
        case CLAIM -> CargoTrackingStatus.CLAIMED;
        case MANUAL_UPDATE -> this.status;
        case EXCEPTION -> CargoTrackingStatus.EXCEPTION;
    };
}
```

イベント種別から状態への写像を `switch` 式で書いています。Java の `switch` 式は enum に対して網羅性を検査するため、`TrackingEventType` に値を追加すればコンパイルエラーになります。**列挙の追加漏れをコンパイラが検出する**構造で、これは Haskell や Rust のパターンマッチと同等の安全性です。

ここで重要なのは、`status` を**フィールドとして保持している**ことです。イベント列から毎回導出するのではなく、最新の状態を永続化します。

Ruby 実装のふりかえりに、この点の教訓が記録されています。

> 発生前状態を永続化せず履歴から再導出すると、ユニット緑でもクロスリクエストで誤復帰する偽の安全網。必ずカラム永続化。

イベント履歴から状態を毎回計算し直す設計にすると、リクエストをまたいだときに状態が意図せず巻き戻ることがあります。イベントソーシングを採用していないのに、状態だけをイベントから導出するのは中途半端であり、**状態は状態として永続化する**のが正解です。

### 集約の可変性、再び

`TrackingRecord` は可変です。

```java
private CargoTrackingStatus status;
private final List<TrackingActivityEvent> handlingEvents = new ArrayList<>();
```

`Cargo` は不変、`Invoice` は可変、`TrackingRecord` も可変。プロジェクト内で 3 つの集約が 2 つの方式に分かれています。

`TrackingRecord` の場合、イベントを追記していく性質上、可変のほうが自然ではあります。不変にすると、イベント追加のたびにリスト全体をコピーした新インスタンスを作ることになります。ただし、それが意図的な判断だった記録はありません。

なお、コレクションの公開は防御されています。

```java
public List<TrackingActivityEvent> getHandlingEvents() {
    return Collections.unmodifiableList(handlingEvents);
}
```

集約の内部コレクションをそのまま返すと、外部から `add` されて不変条件が壊れます。この防御は正しく効いています。

## 他言語ではどう書いたか

### 荷役記録と追跡記録の分離

Java は Tracking Context 1 つで追跡番号と荷役履歴の両方を扱います。他言語では分割したものがあります。

| 言語 | コンテキスト構成 |
| :--- | :--- |
| Java | Tracking Context（追跡番号 + 荷役履歴） |
| Ruby | Tracking Context（`TrackingActivity` 集約）+ Handling Context（`HandlingActivity`） |
| F# | Handling Context を独立プロジェクト（`CargoTracker.Handling`）として分離 |
| TypeScript | Handling Context を独立（`contexts/handling/`） |
| Rust | Tracking / Handling を別クレート |

分離した実装では、「荷役作業員が作業を記録する」（Handling）と「追跡管理者が状況を照会する」（Tracking）を別の業務として扱っています。役割（ロール）が違い、画面も違い、更新頻度も違うためです。

分離すると、当然コンテキスト間連携が必要になります。TypeScript 実装では Handling Context が Booking の情報を必要とするため、ACL を通した貨物スナップショットを持ちます。

```
contexts/handling/domain/model/cargo-snapshot.ts
contexts/handling/application/outboundservices/acl/cargo-snapshot-acl.ts
```

「スナップショット」という名前が示す通り、Booking Context の `Cargo` そのものではなく、Handling が必要とする項目だけを写した読み取り専用の型です。ACL の翻訳が明示的に型として現れています。

**分割の是非は、役割と更新頻度が本当に違うかで決まります**。Java 実装のように 1 つにまとめても、荷役記録と追跡照会が同じ集約を触る限り破綻はしません。ただし、荷役作業員向けの画面と追跡管理者向けの画面が同じコンテキストのコントローラに同居することになり、認可の設計が複雑になります。

### 追跡番号の生成方式

| 言語 | 生成方式 |
| :--- | :--- |
| Java | `TRK-{yyyyMMdd}-{8 桁 HEX}`（`SecureRandom`） |
| F# | `IdGenerator` ポート（`unit -> Guid`）から決定的に生成 |
| Rust | UUID ベース |
| Scala | 業務コード形式 |

F# の方式は、時刻・乱数といった副作用を**ポートとして注入**します。

```fsharp
/// IdGenerator ポート（unit -> Guid）から決定的にコードを生成する（ADR-0006）。
let generate (newId: IdGenerator) : BookingId =
    let head = (newId ()).ToString("N").Substring(0, 8).ToUpperInvariant()
    BookingId(sprintf "BKG-%s" head)
```

テストでは固定 Guid を返す関数を渡せるため、生成された ID をアサートできます。Java 実装のように `UUID.randomUUID()` や `LocalDate.now()` を直接呼ぶと、この検証ができません。

これは F# の言語機能というより設計の選択です。Java でも `IdGenerator` インターフェースを切れば同じことができます。ただし ADR に記録して全体で徹底するという運用が伴わないと、`randomUUID()` の直接呼び出しが混ざります。F# 実装は ADR-0006 として明文化しました。

### ロールの追加と画面の到達性

追跡機能の追加は、新しいロール（荷役作業員・追跡管理者）を導入します。ここで全実装が共通して踏む落とし穴があります。

画面を実装し、受入基準も満たし、テストも緑。しかし **そのロールでログインしたユーザーが、ダッシュボードやナビゲーションからその画面に辿り着けない**。

Flix 実装のふりかえりに、この問題が最も明確に記録されています。

> 差し戻しとキャンセルに画面からの入口が無く、テストが緑のまま US13 の受入基準 4・5 が実質未達だった（受入テストが URL を直接 POST していたため見逃していました）。

受入テストが URL を直接叩いていたため、ボタンが存在しないことに気づけなかった、という構図です。

同種の教訓は複数の実装で繰り返し出ています。

- **ロール軸の到達性** — そのロールのダッシュボード／navbar からリンクがあるか
- **状態軸の到達性** — その状態のレコードの詳細画面から、次の操作を起動できるか

対処として記録されているのは、画面実装の DoD に「個別画面 + ナビゲーション（navbar / dashboard / 検証テスト）の両方を確認する」を入れることです。UI 設計書・navbar・dashboard・検証テストの 4 点が一致していることを確かめる、という具体的な手順まで落とされています。

Java 実装でも E2E テストに `NavbarPage.ts` という Page Object があり、ナビゲーション順序の検証テストが IT5 以降追加されています。

## このイテレーションの学び

### 「実装した」と「使える」の距離

このイテレーションの学びは、前章の期限超過チェックと同じ構造です。

| 段階 | 検出する仕組み |
| :--- | :--- |
| ドメインロジックが正しい | 単体テスト |
| ユースケースが動く | アプリケーション層のテスト |
| HTTP で叩ける | 統合テスト |
| **画面から起動できる** | E2E テスト（**ただし UI を経由する場合のみ**） |
| **そのロールが辿り着ける** | ナビゲーション検証テスト |

下の 2 段は、意識して作らないと検証されません。E2E テストであっても、URL を直接叩く実装になっていれば導線の欠落を見逃します。

Flix 実装の記録が示す通り、**受入テストが「ユーザーがやること」をなぞっていない限り、受入基準を検証していることにはなりません**。

### コメントは仕様として扱う

Flix 実装の IT10 ふりかえりに、興味深い指摘があります。

> コメントは仕様として読み実装と突き合わせる。「〜しない」「〜まで確かめる」は宣言しただけで守った気になる。IT10 で 4 件。

コード中のコメントに「この関数は X をしない」「Y まで検証する」と書いてあるのに、実装がそうなっていないケースが 4 件見つかった、という記録です。

本記事で引用してきたコード内コメントの多くは、設計意図を正確に伝える良質なものです。しかし同時に、**コメントは検証されない**という性質を持ちます。Java 実装の `RouteCandidateProvider` が `cargoType` を引数に取りながら使っていない件（第 5 章）も、同じ性質の問題です。

対処は、コメントに書いた制約をテストに落とすことです。「危険物には対応可能な航海のみ」と書くなら、それを検証するテストを書く。書けないなら、コメントも書かない。

---

- 前の章：[第 7 章：IT6 法人割引と精算処理](07-iteration-06.md)
- 次の章：[第 9 章：IT8 引取記録・追跡照会・状態手動更新](09-iteration-08.md)
