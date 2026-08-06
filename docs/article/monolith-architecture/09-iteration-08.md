# 第 9 章：IT8 引取記録・追跡照会・状態手動更新

## このイテレーションのゴール

> 引取作業記録・追跡情報照会・貨物状態手動更新を完成させ、Phase 2 追跡機能を完結させる

3 つのストーリーを 1 イテレーションで消化します。追跡機能の完成イテレーションです。

| 項目 | 内容 |
| :--- | :--- |
| 目標 SP | 10 |
| 実績 SP | 10 |
| Java テスト | 301 件（+29） |
| Playwright E2E | 87 件（+9） |
| カバレッジ | 80% 以上を維持 |

## 扱うユーザーストーリー

| ID | ストーリー | SP |
| :--- | :--- | :--- |
| US16 | 引取作業を記録する | 3 |
| US18 | 追跡情報を照会する | 3 |
| US17 | 貨物状態を手動更新する | 3 |

## Java 実装

### CQRS のクエリ側が本領を発揮する

追跡情報の照会（US18）は、このプロジェクトで最も CQRS が効く箇所です。画面に必要なのは「現在の状態 + 荷役履歴」であり、集約を復元してから詰め替えるのは無駄です。

```java
// tracking/application/internal/queryservices/TrackingDetailDto.java
public record TrackingDetailDto(
        String trackingNumber,
        String bookingId,
        CargoTrackingStatus status,
        List<TrackingEventDto> events
) {
    public record TrackingEventDto(
            String eventType,
            String eventTypeDisplayName,
            String locationUnlocode,
            LocalDateTime completionTime,
            String voyageNumber
    ) {}
}
```

`eventTypeDisplayName`（「受領」「積込」など）が DTO に含まれている点に注目してください。これは表示専用の項目で、ドメインモデルには存在しない情報です。クエリ側が表示都合の形でデータを組み立てられるのが、CQRS を採る主な理由です。

書き込み側の `TrackingRecord` 集約には、この項目はありません。`TrackingEventType` 列挙が `getDisplayName()` を持ち、クエリサービスがそれを呼んで DTO に詰めます。

### 認証なしの公開追跡ページ

追跡照会には 2 つの入口があります。ログイン済みの追跡管理者向けと、荷主が追跡番号だけで見る公開ページです。

```java
// tracking/interfaces/web/PublicTrackingController.java
@Controller
@RequestMapping("/public/tracking")
public class PublicTrackingController {

    @GetMapping("/{trackingNumber}")
    public String showTrackingDetail(@PathVariable String trackingNumber, Model model) {
        var detail = trackingQueryService.findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("tracking", detail);
        return "tracking/detail";
    }
}
```

公開ページは認証なしでアクセスできます。ここには 2 つのセキュリティ上の論点があります。

**1 つめ：追跡番号が推測されないこと。** 第 8 章で見た `SecureRandom` による生成が、この要件を支えています。連番だったら、他人の貨物情報が総当たりで読めます。

**2 つめ：公開ページで見せる情報を絞ること。** このコントローラは認証済みページと **同じ `TrackingDetailDto` と同じテンプレート（`tracking/detail`）** を使っています。つまり、認証済みユーザーに見せる情報がそのまま公開されます。

`TrackingDetailDto` には `bookingId` が含まれています。荷役の場所（UN/LOCODE）と時刻の履歴も全部含まれます。追跡番号を知っている人には全部見せてよい、という判断であれば問題ありませんが、その判断が記録されていません。

TypeScript 実装ではこの点を明示的に扱っており、セキュリティチェックリストに「公開ページの情報露出（公開追跡の最小表示）」を項目として立てています。公開ページ専用に情報を削った DTO を用意する、という対処です。

**同じ DTO を認証あり／なしで共有すると、片方の変更がもう片方の情報露出になる**という危険があります。認証済み画面に項目を 1 つ足したら、公開ページにも出てしまう構造です。

### 手動状態更新（US17）

追跡管理者が状態を手で変える機能です。イベント種別として `MANUAL_UPDATE` が追加されます。

```java
public void addManualUpdateEvent(CargoTrackingStatus newStatus, String locationUnlocode, LocalDateTime dateTime) {
    // ... null / blank チェック
    TrackingActivityEvent event = new TrackingActivityEvent(
            TrackingEventType.MANUAL_UPDATE, locationUnlocode, dateTime, null);
    handlingEvents.add(event);
    this.status = newStatus;
}
```

通常の荷役イベントは種別から状態が導出されますが、手動更新は指定された状態にそのまま遷移します。第 8 章で見た `deriveStatus` でも `MANUAL_UPDATE` だけは現状維持で、状態の決定は呼び出し側に委ねられています。

```java
case MANUAL_UPDATE -> this.status;
```

ここには**遷移の妥当性検査がありません**。`CLAIMED`（引取済）から `AWAITING_RECEIPT`（受領待ち）へ戻すこともできます。手動更新という機能の性質上、ある程度の自由度は必要ですが、無制限でよいかは業務判断です。

監査の観点では、`MANUAL_UPDATE` イベントが履歴に残るため「誰かが手で変えた」ことは追跡できます。ただし **誰が変えたかは記録されていません**。`TrackingActivityEvent` に操作者のフィールドがないためです。

Ruby 実装では、この点が IT9 で問題として扱われています。

> 料金調整の取消・監査証跡（担当者・理由・日時）・例外管理から該当請求書への導線を実装

担当者・理由・日時の 3 点セットを監査証跡として持たせる、という対処です。さらにレビューで「偽陽性テスト（担当者を実氏名で検証）」が検出されています。テストが担当者名をハードコードした固定値で検証しており、実際には記録されていなくても通ってしまう状態でした。

**監査証跡は、記録していることをテストで検証しないと形骸化する**という例です。

## 他言語ではどう書いたか

### 公開ページの情報最小化

追跡照会は「認証なしで見せる」という要件を持つ唯一の機能で、実装の差が出ました。

| 言語 | 公開ページの扱い |
| :--- | :--- |
| Java | 認証済みと同じ DTO・同じテンプレート |
| TypeScript | 公開追跡は最小表示（セキュリティチェックリスト項目として明示） |
| Ruby | 公開追跡ページ + Turbo Frame による 30 秒差分ポーリング |
| Rust | 公開ルートを認証ミドルウェアの適用外に明示的に列挙 |

Ruby 実装は公開ページに自動更新を入れています。荷主が画面を開いたまま状況を見るという使い方を想定した設計です。

Rust の「認証ミドルウェアの適用外を明示的に列挙する」方式は、**デフォルト拒否（fail-closed）** の考え方です。認証を全ルートに適用し、公開したいものだけを例外リストに書く。逆（デフォルト公開で保護したいものにアノテーションを付ける）にすると、アノテーションの付け忘れが情報漏洩になります。

TypeScript 実装のふりかえりにも fail-closed 認証が ADR-011 として記録されており、複数実装が同じ結論に達しています。

### 横断的な防御とヘルスチェック

認証・レート制限などをミドルウェアで横断適用する際、全実装が踏みうる落とし穴があります。Flix 実装で実際に起きました。

> 横断的な防御はヘルスチェックを除外する。一律適用すると過負荷で liveness が 503 を返し再起動ループになる。

同時実行数を制限するセマフォを全リクエストに適用したところ、負荷が高いときにヘルスチェック（liveness probe）もキューに詰まり、503 を返し、コンテナオーケストレータがコンテナを再起動する。再起動すると負荷がさらに集中して、また落ちる。

Flix 実装ではこれが IT3 と IT7 の 2 回、別の防御機構で再現しています。1 回目の教訓が、2 回目の実装（セマフォ）に適用されなかったためです。

**横断的関心事を追加するときは、必ずヘルスチェックの経路を確認する**というのは、モノリスに限らずあらゆるサーバー実装に当てはまる注意点です。

### 状態の手動更新をどこまで許すか

US17 の「手動更新」は、状態機械の設計に例外を持ち込みます。各実装の扱いを比較します。

- **Java** — 遷移検査なし。任意の状態に変更可
- **Scala・Haskell** — `canTransitionTo` を通すため、遷移表にない変更は拒否される
- **Go・Rust** — 集約メソッドで検査

Scala・Haskell 方式では、手動更新であっても遷移表に従います。そのため「引取済から受領待ちに戻す」ような操作は、遷移表に明示的に追加しない限りできません。

どちらが正しいかは業務次第です。ただし Java の「無制限」は、選択した結果ではなく検査を書かなかった結果です。**制約を書かないことは、制約がないという設計判断を暗黙に選んでいる**ことになります。

## このイテレーションの学び

### 未達を未達として記録する

IT8 のふりかえりでは、受入条件の部分充足が明示的に列挙されています。

| 項目 | 状態 |
| :--- | :--- |
| SonarQube Quality Gate | 未確認（IT5 で PASS 後、IT8 で再び未実施） |
| US16 / US17 メール通知 | 未実装（メール送信インフラが未整備） |
| US18 推定到着日の表示 | 部分実装（フィールドが未実装） |
| IT5 レビュー指摘 H-1〜H-9 | 蓄積継続 |

このうち「US18 推定到着日」は IT9 でも対応されず、IT10 まで持ち越されました。

正直に記録すること自体は良い実践です。ただし Ruby 実装のふりかえりが指摘する通り、**DoD の条件を書き写すと正典の変更に追随しません**。受入基準を DoD にコピーするのではなく、ユーザーストーリーへの参照として引用する形にしないと、「未達」の記録そのものが誤りになります。Ruby 実装では、書き写した条件が正典の更新に追随せず、3 イテレーション連続で「未達」を誤記録していたことが判明しています。

### テストファイルの構造を壊す

IT8 のふりかえりに、地味だが実務的な記録があります。

> `tracking.spec.ts` で `setupTrackingIssuedBooking` 関数が `test.describe` ブロック内に誤挿入され、構文エラーが発生した。テストファイルの全体構造を把握してから追記する習慣が必要。

E2E テストファイルが大きくなるほど起きやすい問題です。87 件の E2E テストが数ファイルに分かれており、追記位置を誤ると構造が壊れます。

対処として Page Object パターンが機能しています。IT5 以降、`BookingRoutePage.ts`・`BillingPage.ts`・`TrackingPage.ts`・`ExceptionPage.ts` と、画面ごとに Page Object を切り出しました。テストファイル本体は「Page Object を組み合わせたシナリオ」だけになり、追記の影響範囲が小さくなります。

### H2 インメモリ DB の限界

IT2 から続いていた H2 の問題が、IT8 でも再発しています。

> E2E テスト実行中に `CHK_SHIPPER_TYPE` チェック制約失敗が発生した。H2 2.x の既知バグ（長時間セッション後の DB クローズ）が原因。アプリ再起動で回避できるが、根本対策（PostgreSQL への切り替えまたは H2 ファイルベース DB）が必要。

本番は PostgreSQL、テストは H2 という構成の弊害です。他言語の多くは Testcontainers で本番と同じ PostgreSQL をテストに使っており、この種の問題が起きていません。

| 言語 | テスト DB |
| :--- | :--- |
| Java | H2（PostgreSQL 互換モード） |
| C# / F# / Scala / Rust / Go / TypeScript / Haskell | Testcontainers（実 PostgreSQL） |
| Flix | H2 |
| Ruby | 実 PostgreSQL |

Testcontainers はコンテナ起動のオーバーヘッドがある分テストが遅くなりますが、**本番と違う DB でテストすることの隠れたコスト**を IT2 から IT9 まで払い続けた Java 実装と比べれば、割に合う投資です。

Java 実装でも PostgreSQL への移行は繰り返し検討されましたが、IT10 のリリース準備時まで先送りされ、結局実施されませんでした。第 5 章で述べた「余力次第の項目は消化されない」の別の例です。

---

- 前の章：[第 8 章：IT7 追跡番号発行と荷役作業記録](08-iteration-07.md)
- 次の章：[第 10 章：IT9 遅延・破損・紛失の例外処理](10-iteration-09.md)
