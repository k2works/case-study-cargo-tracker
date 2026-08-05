---
title: IT10 イテレーション計画
description: 追跡番号の発行（US14）と荷役作業の記録（US15）、および IT9 レビューの返済（TS17）を扱う。
---

# IT10 イテレーション計画

## 概要

| 項目 | 内容 |
| :--- | :--- |
| 期間 | 2026-08-05 〜（2 週間相当） |
| 局面 | **終盤**（IT8-IT12）。アプローチは**アウトサイドイン** |
| 対象ストーリー | US14（2 SP）・US15（8 SP）・TS17（2 SP） |
| 計画 SP | **12** |
| 新規 Bounded Context | **Handling Context**（本 IT で立ち上げる） |

> **リリース計画では IT10 を 10 SP（US14・US15）としていました**。
> IT9 のレビューが**中 9 件・低 12 件**を IT10 へ送ったため（中は総数 14 件のうち 5 件を IT9 で返済済み）、**返済を独立ストーリー TS17（2 SP）として
> 計上します**（利用者判断・2026-08-05）。「余力があれば」と書かないのは、
> ADR-0008 が 3 イテレーション繰り越した前例があるためです。

---

## ゴール

### イテレーション終了時の達成状態

**確定した予約に追跡番号が発行され、荷役作業員が現場の作業を記録すると、
その結果が荷主の見る追跡画面に反映される**——ここまでが画面から通ります。

これは本プロジェクトで初めて「**コンテキストをまたいで状態が伝播する**」
イテレーションです（Handling → Tracking → 公開追跡、および Handling → Booking の MISROUTED）。

### 成功基準

| # | 基準 | 検証 |
| :--: | :--- | :--- |
| 1 | 確定済み（CONFIRMED）の予約に追跡番号を発行でき、**BookingStatus が TRACKING_ISSUED**・貨物状態が「受領待ち」になる | `TrackingNumberHttpTest` |
| 2 | 追跡番号が一意に採番される（同時発行でも重複しない） | 同上（同時発行テストを含む） |
| 3 | 荷役作業員が追跡番号で貨物を特定し、受領・積込・荷降しを記録できる | `HandlingHttpTest` |
| 4 | 記録後、貨物の輸送状態が対応する状態に**永続化されて**更新される | 同上 + `HandlingActivityTest` |
| 5 | **予定ルートと異なる場所の LOAD / UNLOAD で MISROUTED が確定する** | `HandlingActivityTest`（デシジョンテーブルを表駆動で網羅） |
| 6 | 存在しない追跡番号は「次に何をすればよいか」を言うエラーになる | `HandlingHttpTest` |
| 7 | 荷役作業員が navbar / ダッシュボードから作業入口へ到達できる | `NavigationReachabilityTest` |
| 8 | 全テスト緑・`arch-lint` 0 件・`trace-lint` 0 件・`test:dbnames` 0 件・CI 緑 | クローズ手順 |
| 9 | **SonarQube Quality Gate が PASS** | クローズ時に利用者が実行（下記「品質ゲートの実行手順」） |

---

## IT9 ふりかえりの反映

IT9 の Try 7 件を、本計画のどこで扱うかを明示します。

| Try | 本計画での扱い |
| :--- | :--- |
| **T1** 状態ごとの到達性を DoD に入れる。受入テストは URL 直叩きをやめる | **タスク 2.4・3.5 の DoD**。荷役の記録画面は追跡番号の状態（未発行・発行済・輸送中・引取済）ごとに到達性を確かめる。受入テストは**画面の HTML からリンクを辿る** |
| **T2** 見送りの判断には、その根拠を壊しうる同 IT のタスク名を併記する | **全タスクの DoD**。「いまは要らない」と書くコメントには、同 IT の他タスク名を必ず添える |
| **T3** 同時実行の安全装置は、外したテストが赤くなることを実測してから入れる | **タスク 1.2 の DoD**（追跡番号の一意採番）。赤くできないなら「検証できていない」とコメントに書く |
| **T4** SonarQube の実行方法を着手前に決める | **決定済み**（利用者判断・2026-08-05）。下記「品質ゲートの実行手順」。3 回目の繰り越しにしない |
| **T5** 自作 lint の例外は許可リストで書く。フィクスチャは差分 1 行の対にする | **タスク 4.2 の DoD**（規約 12 を起こす場合） |
| **T6** 設計同期は実装コミットに同梱し、上位の図も開く | **全実装タスクの DoD**。Handling BC を足すので `architecture_backend.md` の**コンテキストマップと規約一覧を必ず開く** |
| **T7** 検査器を作るタスクの DoD に「CI のどのステップから呼ばれるか」を書く | **タスク 4.2 の DoD** |

### 品質ゲートの実行手順（T4 の決定）

SonarQube は `.env.vault` の復号に対話的なパスワード入力が要るため、
**AI エージェント単独では実行できません**。クローズ時に利用者が次を実行し、
結果を AI が受け取って判定します。

```
! npx gulp vault:decrypt      # .env.vault のパスワードを入力する
! npx gulp sonar-local:check  # scan → gate
```

`!` プレフィックスはセッション内でコマンドを実行し、出力を会話へ流し込みます。
**この手順を `docs/operation/` にも書きます**（タスク 4.1）。手順が計画にしか無いと、
次のイテレーションでまた探すことになります。

---

## ユーザーストーリー

### 対象ストーリー

| ID | 内容 | アクター | SP |
| :--- | :--- | :--- | :--: |
| US14 | 追跡番号を発行する | 経路設計者 | 2 |
| US15 | 荷役作業を記録する | 荷役作業員 | 8 |
| TS17 | IT9 レビューの返済 | — | 2 |

### US14: 追跡番号を発行する

| # | 受入基準 | 本 IT での扱い |
| :--: | :--- | :--- |
| 1 | 「予約確定」状態の予約に対して追跡番号を発行できる | **実装** |
| 2 | 追跡番号は一意に採番される | **実装** |
| 3 | 発行後、貨物状態が「受領待ち」に設定される | **実装**（`TransportStatus.NOT_RECEIVED`） |
| 4 | 荷主に追跡番号と追跡方法をメール通知する | **将来リリース**（US12 と同じ通知基盤。画面と計画の両方に明記する） |

### US15: 荷役作業を記録する

| # | 受入基準 | 本 IT での扱い |
| :--: | :--- | :--- |
| 1 | 追跡番号の入力で貨物を特定できる | **実装**（スキャンは対象外。入力欄のみ） |
| 2 | 作業種別（受領・積込・荷降し）を選択できる | **実装**。正典の `HandlingType` は 5 値（RECEIVE / LOAD / UNLOAD / CUSTOMS / CLAIM）だが、本 IT は **3 値**に絞る。CLAIM は US16・IT11、CUSTOMS は `customs_declaration` ごと US16 まで作らない。**無言で絞らず設計へ反映する** |
| 3 | 作業日時と作業場所（UN/LOCODE）を入力できる | **実装**。**LOAD / UNLOAD は航海番号も必須**（`HandlingType.requiresVoyageNumber()`）——`LegSnapshot.voyageNumber` と突き合わせないと MISROUTED を判定できない。`ui_design.md` の登録ワイヤーに航海番号欄が無いため設計へ追記する |
| 4 | 記録後、貨物状態が対応する状態に自動更新される | **実装**（**カラムに永続化する**。履歴からの再導出を禁じる） |
| 5 | 記録後、荷主に状態変更通知が送信される | **将来リリース**（US12 と同じ通知基盤） |
| 6 | 追跡番号が存在しない場合、エラーメッセージが表示される | **実装** |
| 7 | 作業場所が予定ルートと異なる場合、警告が表示される | **実装**（LOAD/UNLOAD は MISROUTED 確定、RECEIVE は警告） |

### TS17: IT9 レビューの返済（2 SP）

**Day 2 に独立したコミットで行います**。実装タスクと混ぜません。

| # | 返済対象 | 出所 |
| :--: | :--- | :--- |
| 1 | 経路割り当て画面に「現在割り当てられている経路」を表示し、候補一覧の該当行に印を付ける | M10 |
| 2 | 候補の並べ替え（所要日数・概算費用・積替有無）と表示件数の上限 | M11 |
| 3 | 候補未選択で送信したときのメッセージを分ける（＋ラジオに `required`） | M9 |
| 4 | `requiresCsrf` を意味に合う名前へ（「検証が要るか」→「どちらのトークンと照合するか」）。`isValidCsrf` の古い doc も直す | M6 |
| 5 | `findVisibleWithItinerary` の可視範囲判定の複製を畳む | M7 |
| 6 | `statusLabel` の doc コメントが `displayStatusOf` に付け替わっているのを戻す | L1 |
| 7 | 表示ラベル「確認済」を **「予約確定」** へ（US13 の受入基準と現場の呼称に合わせる） | L10・利用者代表 |

**送らないもの**は理由を付けて残します。

| 指摘 | 扱い |
| :--- | :--- |
| M12 確定画面に概算費用が無い | US12（通知）と同じ情報。**US12 と揃えて実装**（将来リリース） |
| M13 差し戻しの記録（日時・理由・外した経路） | **スキーマ変更を伴うため独立ストーリーとして起票**。IT11 の候補 |
| M14 二重送信 Cookie の `__Host-` 未採用 | **ADR で受容範囲を宣言する**（タスク 4.3）。`SameSite=Strict` が主防御 |
| M8 確定・キャンセルで `leg` を全削除・再挿入 | **本 IT で直す**。`leg` を参照する側（荷役実績）が本 IT で現れるため、先送りの前提が崩れる（Try T2 の適用例）→ **タスク 3.3 に含める** |

---

## 壊れ方の観点（6 軸）

着手前に「この機能はどう壊れるか」を書き出します。受入基準は正常系しか書きません。

| 軸 | 問い | 本 IT で該当するもの |
| :--: | :--- | :--- |
| 1 | **状態が永続化されず履歴から再導出されていないか** | 荷役記録後の輸送状態。**カラムに書く**。履歴から導出すると、ユニット緑でもクロスリクエストで誤復帰する（記録済みの教訓） |
| 2 | **同時に来たらどうなるか** | 追跡番号の採番（重複）。同じ貨物への荷役の同時登録 |
| 3 | **その状態のレコードから画面を開けるか** | 追跡番号未発行の予約・引取済の貨物。IT9 H1 の再発防止（Try T1） |
| 4 | **拒まれた理由が画面の表示と食い違わないか** | MISROUTED の警告。存在しない追跡番号 |
| 5 | **横断的な防御が正常な経路を巻き込まないか** | 荷役登録はヘルスチェックを巻き込まない（IT7 の再発防止） |
| 6 | **BC をまたぐ書き込みが集約を通っているか** | Handling → Booking（MISROUTED）・Tracking → Booking（追跡番号）。ADR-0011 の形を踏襲する |

---

## タスク

### 0. 着手前の準備（0 SP）

| # | タスク | 内容 |
| :--: | :--- | :--- |
| 0.1 | 設計ドキュメントの読み合わせ | `domain-model.md` の Tracking / Handling 節、`data-model.md` の該当テーブル、`ui_design.md` の荷役画面 |
| 0.2 | **ADR-0012 の起票**（BC をまたぐ書き込みの一般形。追跡番号の発行と荷役の反映に適用） | 下記「ADR」節。**正典（`domain-model.md`）はイベント方式で描かれている**ため、方式を変える判断を残す |
| 0.3 | **US14 の画面が `ui_design.md` の画面一覧に無い**ことの是正 | 設計への反映が必要（下記「設計ドキュメントへの反映」） |

### 1. US14: 追跡番号の発行（2 SP・Day 1）

| # | タスク | 理想時間 |
| :--: | :--- | :--: |
| 1.1 | 受入テスト（HTTP）を先に書く——確定済みの予約から発行 → 貨物状態が「受領待ち」 | 4 |
| 1.2 | 採番と一意性。形式は `ui_design.md` の **`TRK-YYYYMMDD-NNNN`**——**日付を含むため同一日内の連番が競合点**になる。一意制約を `tracking_activity.tracking_number`（V1 で UNIQUE 既存）に加えて `cargo.tracking_number` にも張るかを決める。**同時発行のテストを書き、一意制約を外すと赤くなることを実測する**（Try T3） | 4 |
| 1.3 | Booking への記録（ACL ポート経由・ADR-0012） | 4 |
| 1.4 | 画面（予約詳細からの発行導線）とナビゲーション整合 | 4 |

### 2. TS17: IT9 レビューの返済（2 SP・**Day 2 に独立したコミット**）

| # | タスク | 理想時間 |
| :--: | :--- | :--: |
| 2.1 | 経路割り当て画面: 現在の経路の表示・並べ替え・件数上限（M10・M11） | 8 |
| 2.2 | 未選択メッセージ・`requiresCsrf` のリネーム・可視範囲判定の重複・doc 位置（M9・M6・M7・L1） | 4 |
| 2.3 | 表示ラベル「確認済」→「予約確定」（L10） | 2 |
| 2.4 | **状態ごとの到達性の確認**（Try T1）。IT9 で作った画面を状態の数だけ開く | 2 |

### 3. US15: 荷役作業の記録（8 SP・Day 3-8）

> **ACL の実現手段を着手前に確定させます**。本リポジトリには 2 系統あります——
> (a) 自 BC の infrastructure に JDBC ACL を置く（ADR-0009。相手のテーブルを SQL で直引きするため
> **規約 4 の「既知の穴 1」に落ちて検出されない**）、(b) 合成ルートの `src/composition/acl/` に
> 翻訳を置く（ADR-0011・規約 11。ファイル数として数えられる）。
>
> **本 IT は全 3 ポートで (b) を採ります**。`src/composition/acl/` は現在 2 本
> （`BookingItineraryAssignmentAdapter`・`EstimationRouteSearchAdapter`）で、
> **本 IT 終了時に 5 本**になります。ADR-0011 の再検討条件は「5 本を超えたら」なので、
> **次に翻訳を足す IT（IT11）で BC 間連携の設計を見直します**——これを IT11 の
> 着手前タスクとして先に書いておきます（「余力次第」にしないため）。
>
> 手段を決めないまま実装すると、数える対象から漏れた経路が生まれます（IT9 P5 の再発形）。


| # | タスク | 理想時間 |
| :--: | :--- | :--: |
| 3.1 | 受入テスト（HTTP）を先に書く——追跡番号で特定 → 受領を記録 → 公開追跡に反映 | 8 |
| 3.2 | **Handling Context の立ち上げ**（新 BC）。`HandlingActivity` 集約・`HandlingType`・`isValidFor` のデシジョンテーブル。**効果を要求しない純粋なドメイン**（終盤でも新 BC のドメインは中盤の規律） | 12 |
| 3.3 | `CargoSnapshotSource` ACL ポート（Handling → Booking の**読み**）。**アダプタは `src/composition/acl/` に置く**（規約 11）。**M8 の返済を含む**（`leg` の全削除・再挿入をやめる） | 8 |
| 3.4 | `TrackingTransportStatusUpdate` ACL ポート（Handling → Tracking の**書き**）。Tracking の集約を通す。**カラムに永続化する**（観点 1）。**アダプタは `src/composition/acl/`** | 8 |
| 3.5 | `BookingMisroutingReport` ACL ポート（Handling → Booking の**書き**）。`Cargo.markMisrouted` を通す（ADR-0011 の形）。**アダプタは `src/composition/acl/`** | 8 |
| 3.6 | 画面（`/handling` 一覧・`/handling/new` 登録）とナビゲーション整合。**navbar の `荷役管理` を `Planned` → `Implemented` へ** | 8 |

### 4. 観測・規律・ドキュメント（0 SP）

| # | タスク | 内容 |
| :--: | :--- | :--- |
| 4.1 | **SonarQube の実行手順を運用ドキュメントへ**（Try T4） | `docs/operation/` に手順を書く |
| 4.2 | **規約 12 の起票判断**（BC の SQL に現れるテーブルは自 BC 所有か ACL 配下）。IT4 M1 由来で 5 イテレーション未着手。**Handling が SQL で他 BC のテーブルを引く誘惑が本 IT で最大**になる | 実装するなら Try T5・T7 の DoD を適用 |
| 4.3 | **ADR: ログイン CSRF の受容範囲**（M14） | `SameSite=Strict` を主防御とする判断を残す |
| 4.4 | 設計ドキュメントの同期（**実装コミットに同梱**・Try T6） | 下記「設計ドキュメントへの反映」 |

### タスク合計

| 区分 | SP | 理想時間 |
| :--- | :--: | :--: |
| US14 | 2 | 16 |
| TS17 | 2 | 16 |
| US15 | 8 | 52 |
| 観測・規律 | 0 | — |
| **合計** | **12** | **84** |

---

## 縮退順序（着手前に確定させる）

判定日に間に合わない見込みなら、**上から順に**落とします。

| 順 | 落とすもの | 理由 |
| :--: | :--- | :--- |
| 1 | US15 受入基準 7 のうち **RECEIVE / CLAIM の警告**（LOAD/UNLOAD の MISROUTED は残す） | 警告は業務を止めない。MISROUTED は貨物が違う船に乗る話であり落とせない |
| 2 | 荷役作業一覧（`/handling`）の検索・絞り込み | 登録できることが先。一覧は全件表示で始める |
| 3 | TS17 のうち M10・M11（経路割り当て画面の改善） | 返済であり、業務は現状でも回る。**ただし落としたら IT11 で必ず返す**（繰り越しの固定化を防ぐため、落とす場合は IT11 計画に先に書く） |

**US14 と US15 の受入基準 1-6 は落としません**。落とすと業務フローが繋がらず、
終盤の目的（業務として成立させる）を満たしません。

### 縮退の判定日

**Day 6 終了時**。US15 のタスク 3.1-3.4 が終わっていなければ、上の順で落とします。

---

## 設計

### ドメインモデル（本 IT のスコープ）

```plantuml
@startuml
title IT10 スコープ - Handling / Tracking / Booking

package "Handling Context（本 IT で立ち上げ）" {
  class HandlingActivity <<aggregate root>> {
    -activityId: String
    -trackingNumber: HandlingTrackingNumber
    -type: HandlingType
    -location: String
    -eventCompletionTime: String
    -voyageNumber: Option[HandlingVoyageNumber]
    +isValidFor(snapshot: CargoSnapshot): HandlingValidity
  }
  class HandlingType <<value object>> {
    -type: String
    +requiresVoyageNumber(): Bool
    +isLoadType(): Bool
    +isClaimType(): Bool
  }
  enum HandlingValidity {
    VALID
    WARNED
    MISROUTED
  }
  class HandlingVoyageNumber <<value object>>
  class HandlingTrackingNumber <<value object>>
}

package "Tracking Context（既存）" {
  class TrackingActivity <<aggregate root>> {
    -trackingNumber: String
    -bookingId: String
    -transportStatus: TransportStatus
    +issue(bookingId): TrackingActivity
    +applyHandling(type): TrackingActivity
  }
  note bottom of TrackingActivity
    **正典（domain-model.md:670）は `currentStatus()` で
    イベント履歴から導出する形**だが、本 IT は
    `transportStatus` を保持する形へ是正する（反映表 #7）。
    導出型は「壊れ方の観点 1」が禁じている形であり、
    DB（V1__init.sql:21）も既に `transport_status` を持つ。
  end note
}

package "Booking Context（既存）" {
  class Cargo <<aggregate root>> {
    +issueTrackingNumber(number): Result
    +markMisrouted(): Result
  }
}

package "Shared Domain" {
  enum TransportStatus
  enum RoutingStatus
}

package "Value Objects" {
  class CargoSnapshot <<value object>> {
    -bookingId: String
    -origin: String
    -destination: String
    -itineraryLegs: List[LegSnapshot]
    -routingStatus: String
  }
  class LegSnapshot <<value object>>
}

interface CargoSnapshotSource <<ACL Port>> {
  +findByTrackingNumber(number): Option[CargoSnapshot]
}

interface BookingTrackingNumberAssignment <<ACL Port>> {
  +assign(bookingId, trackingNumber): AssignmentResult
}

interface BookingMisroutingReport <<ACL Port>> {
  +report(bookingId): ReportResult
}

interface TrackingTransportStatusUpdate <<ACL Port>> {
  +apply(trackingNumber, handlingType): UpdateResult
}

HandlingActivity *-- HandlingType
HandlingActivity *-- HandlingVoyageNumber
HandlingActivity *-- HandlingTrackingNumber
HandlingActivity ..> CargoSnapshot : validates against
HandlingActivity ..> CargoSnapshotSource
HandlingActivity ..> BookingMisroutingReport
HandlingActivity ..> TrackingTransportStatusUpdate
TrackingActivity ..> BookingTrackingNumberAssignment
CargoSnapshot *-- LegSnapshot
TrackingActivity *-- TransportStatus
Cargo *-- RoutingStatus

note bottom of HandlingVoyageNumber
  **コンテキスト固有の型**（IT6-IT9 の踏襲）。
  Routing の VoyageNumber とは別の型にする。
  共有すると航海の妥当性規則が荷役へ漏れる。
end note

note bottom of CargoSnapshotSource
  **`CargoSnapshot` は正典（domain-model.md）では値オブジェクト**であり、
  `isValidFor` に渡される検証材料である。ポートに同じ名前を付けると
  同名別概念になるため、取得口は `CargoSnapshotSource` と名付ける。

  Handling は Booking の Cargo を**読むだけ**。MISROUTED の書き込みは
  別ポートで、Booking の集約（markMisrouted）を必ず通る（ADR-0011）。
end note

note bottom of TrackingTransportStatusUpdate
  **Handling → Tracking も BC 越えの書き込みである**。
  ポートを定義しないと `handling/` から `tracking/` を直接 use するか
  （規約 4 違反）、SQL で `tracking_activity` を直接更新するか
  （規約 4 の既知の穴 1・規約 12 の対象）に倒れる。
end note
@enduml
```

### 状態遷移（本 IT のスコープ）

**2 つの軸が別々に動きます**（IT9 で確立した形）。

```plantuml
@startuml
title TransportStatus（本 IT のスコープ）

[*] --> NOT_RECEIVED : 追跡番号の発行（US14）
NOT_RECEIVED --> RECEIVED : RECEIVE（US15）
RECEIVED --> LOADED : LOAD（US15）
LOADED --> ONBOARD_CARRIER : 出港
ONBOARD_CARRIER --> UNLOADED : UNLOAD（US15）
UNLOADED --> LOADED : 積替の LOAD（US15）
UNLOADED --> AWAITING_CLAIM : 最終港での UNLOAD
AWAITING_CLAIM --> CLAIMED : CLAIM（**US16・IT11**）

note right of ONBOARD_CARRIER
  出港の記録は本 IT の対象外。
  LOAD の直後に ONBOARD_CARRIER へ倒すか、
  LOADED のまま置くかを 3.4 で決める。
  **決めたら ADR かコメントに理由を残す**
end note
@enduml
```

```plantuml
@startuml
title BookingStatus × RoutingStatus（US14 で BookingStatus・US15 で RoutingStatus が動く）

state "RoutingStatus" as RS {
  [*] --> NOT_ROUTED
  NOT_ROUTED --> ROUTED : 経路の割り当て（US09・IT9）
  ROUTED --> NOT_ROUTED : 差し戻し（US13・IT9）
  ROUTED --> MISROUTED : **予定外の港で LOAD / UNLOAD（US15・本 IT）**
}

note bottom
  **BookingStatus は US14 で 1 段進む**（CONFIRMED → TRACKING_ISSUED）。
  正典（domain-model.md 379・408 行）が正規の遷移として定めており、
  AssignTrackingNumberCommand が担う。

  US15（荷役）で動くのは RoutingStatus だけである。
end note
@enduml
```

### データモデル（本 IT のスコープ）

`tracking_activity` / `tracking_handling_event` は **IT1 で作成済み**です（読み取りのみ使用）。
本 IT で追加するのは次の 2 つです。

```plantuml
@startuml
title IT10 で追加・変更するテーブル

entity "cargo（変更）" as cargo {
  * booking_id : VARCHAR(20) <<UK>>
  --
  * booking_status : VARCHAR(30)
  * routing_status : VARCHAR(30)
  **tracking_number : VARCHAR(20)**  <<新規・NULL 許容>>
}

entity "handling_activity（新規）" as handling {
  * id : BIGINT <<PK, BIGSERIAL>>
  --
  * **tracking_number** : VARCHAR(20) <<NOT NULL>>
  * event_type : VARCHAR(30) <<NOT NULL>>
  * event_completion_time : TIMESTAMP <<NOT NULL>>
  * location_unlocode : VARCHAR(5) <<FK → location, NOT NULL>>
  voyage_number : VARCHAR(20)
  operator_name : VARCHAR(200)
  * **validity** : VARCHAR(20) <<NOT NULL>>
  * created_at : TIMESTAMP <<NOT NULL, DEFAULT NOW()>>
  * updated_at : TIMESTAMP <<NOT NULL, DEFAULT NOW()>>
}

note bottom of handling
  **設計（data-model.md:483）からの変更は 2 列だけ**にする。
  `booking_id` → `tracking_number`（US15 受入基準 1）と
  `validity` の新設（MISROUTED の経緯を残す）。
  `event_type` / `event_completion_time` / `operator_name` は
  **改名しない**——既存の `tracking_handling_event` と揃った命名であり、
  揃っていない方向へ変える理由がない。
end note

entity "tracking_activity（既存・IT1）" as tracking {
  * tracking_number : VARCHAR(20) <<UK>>
  * booking_id : VARCHAR(20)
  * transport_status : VARCHAR(30)
}

handling }o..|| tracking : "追跡番号で参照（**FK なし**）"
tracking }o..|| cargo : "予約番号で参照（**FK なし**）"
@enduml
```

**FK を張らない箇所**（コンテキストを越える参照）:

| 参照 | 理由 |
| :--- | :--- |
| `handling_activity.tracking_number` → `tracking_activity` | Handling → Tracking。BC を越える（`leg.voyage_number` と同じ判断） |
| `handling_activity.voyage_number` → `voyage` | Handling → Routing。同上 |
| `tracking_activity.booking_id` → `cargo` | 既存（IT1 から FK なし） |

**マイグレーション**: `V11__add_tracking_number_and_handling.sql`

> **注（設計への反映が必要）**: `data-model.md` の `handling_activity` は
> `booking_id` を持つ設計になっています。本 IT では**追跡番号で引く**
> （US15 受入基準 1 が「追跡番号の入力で貨物を特定できる」と書いている）ため、
> **設計側を追跡番号に合わせて是正します**（タスク 4.4）。
> `customs_declaration` は US16（IT11）まで作りません。

### ユーザーインターフェース（本 IT のスコープ）

```plantuml
@startuml
title IT10 で追加する画面遷移

state ダッシュボード
state 予約詳細 {
  予約詳細 : /bookings/{bookingId}
}
state 荷役作業一覧 {
  荷役作業一覧 : /handling
}
state 荷役作業登録 {
  荷役作業登録 : /handling/new
}
state 公開追跡 {
  公開追跡 : /public/tracking
}

予約詳細 --> 予約詳細 : [追跡番号を発行]（PRG・US14）
ダッシュボード --> 荷役作業一覧 : [荷役管理]（navbar・US15）
荷役作業一覧 --> 荷役作業登録 : [新規登録]
荷役作業登録 --> 荷役作業一覧 : 登録成功（PRG）
荷役作業登録 --> 荷役作業登録 : 追跡番号が無い／場所が予定外（再描画）
荷役作業一覧 --> 公開追跡 : 記録が反映されることを確かめる
@enduml
```

| 画面 | URL | ロール | 状態 |
| :--- | :--- | :--- | :--- |
| 追跡番号の発行 | `/bookings/{bookingId}` 内の操作 | 経路設計者 | **新規**（専用画面は作らない） |
| 荷役作業一覧 | `/handling` | 荷役作業員・追跡管理者 | **新規** |
| 荷役作業登録 | `/handling/new` | 荷役作業員 | **新規** |

> **注（設計への反映が必要）**: `ui_design.md` には発行済みを前提とした記述
> （`:783` の [追跡を表示]・`:1626` の `TRACKING_ISSUED` バッジ）はありますが、
> **発行する操作の導線がありません**。US14 は専用画面を作らず
> 予約詳細の操作にするため、予約詳細の仕様へ追記します（タスク 4.4）。
> また `ui_design.md:125` の「荷役管理 … 準備中（US15・**IT9**）」は
> IT10 の誤りです（同時に是正）。

### ナビゲーション整合性

**荷役作業員（ROLE_HANDLER）は現在、押せる入口を 1 つも持っていません**
（`ui_design.md:135`。IT7 から続く状態）。本 IT で解消します。

| 確認箇所 | 内容 |
| :--- | :--- |
| `ui_design.md` の画面一覧・ナビゲーション表 | 荷役 2 画面を「実装済み」へ |
| `Layout.navItems` | 「荷役管理」を `NavStatus.Planned` → `Implemented` へ |
| ダッシュボード | 荷役作業員向けの作業入口（[荷役を登録する]） |
| `NavigationReachabilityTest` | 荷役作業員が 2 クリック以内で登録画面へ到達できること |

**状態ごとの到達性**（Try T1）も併せて確かめます。

| 貨物の状態 | 荷役を登録できるか | 画面の見え方 |
| :--- | :---: | :--- |
| 追跡番号 未発行 | ✕ | 追跡番号で引けない → 「その追跡番号は登録されていません」 |
| NOT_RECEIVED | ✅ RECEIVE | |
| RECEIVED / UNLOADED | ✅ LOAD | |
| LOADED / ONBOARD_CARRIER | ✅ UNLOAD | |
| CLAIMED | ✕ | US16（IT11）まで到達しない |

### ADR

| # | 主題 | 判断が要る理由 |
| :--- | :--- | :--- |
| **ADR-0012** | **BC をまたぐ書き込みは相手の集約を同期に通す（一般形）**。追跡番号の発行と荷役の反映に適用する | `domain-model.md` のドメインイベント表（1214 行）とコンテキストマップ（155-156 行）は、Handling → Tracking / Booking を **`HandlingActivityRegisteredEvent`** で描いている。本 IT は ADR-0011 の形（同期の ACL ポート + 相手の集約）を採るため、**正典と方式が逆になる**。ADR-0011 が ADR-0009 の該当節を supersede したのと同じ形で、`HandlingActivityRegisteredEvent` を「未採用」にする判断を残す。<br>あわせて **`HandlingActivity` を追跡番号で引く**（正典は `CargoBookingId`）判断も含める——US15 受入基準 1 が「追跡番号の入力で貨物を特定できる」と定めているため。<br>**明示しないと IT11（US16 CLAIM）で同じ議論を繰り返す**（記録済みの教訓「設計図の向きを変えたら ADR」） |
| （検討） | ログイン CSRF の受容範囲（M14） | `SameSite=Strict` を主防御とし `__Host-` を採らない判断（タスク 4.3） |
| （検討） | 規約 12（BC の SQL に現れるテーブル） | 起票するかを 4.2 で判断する |

---

## スケジュール

| Day | 内容 |
| :--- | :--- |
| 1 | タスク 0（ADR-0012・設計の読み合わせ）+ US14 全体 |
| 2 | **TS17（返済枠・独立したコミット）** |
| 3-4 | US15 受入テスト（3.1）+ Handling ドメイン（3.2） |
| 5-6 | ACL ポート（3.3）+ 輸送状態の更新（3.4）。**Day 6 終了時が縮退の判定日** |
| 7-8 | MISROUTED（3.5）+ 画面とナビゲーション（3.6） |
| 9 | 観測・規律・ドキュメント（タスク 4）。**取りこぼしの確認だけにする** |

---

## リスクと対策

| リスク | 影響 | 対策 |
| :--- | :--- | :--- |
| **Handling が新規 BC で 8 SP** | 見積もりを超える | 終盤でも**新 BC のドメインは中盤の規律**で組む（効果を要求しない・現在時刻は引数）。受入テストだけ先に書く |
| **BC をまたぐ状態伝播が初めて** | 見えないバグ | **状態はカラムに永続化する**（履歴からの再導出を禁じる）。記録済みの教訓が直接あたる |
| MISROUTED のデシジョンテーブルが 4 種 × 場所判定 | 実装漏れ | **表駆動テスト**で網羅する（US16 の CLAIM 行も表には書き、本 IT では未実装と明示） |
| `leg` の全削除・再挿入（M8）が荷役の参照を壊す | 参照の破損 | **タスク 3.3 で先に返す**（Try T2 の適用例。前提が崩れたので見送りを撤回する） |
| 追跡番号の採番が同時発行で重複 | 業務停止 | DB の UNIQUE 制約 + 同時発行テスト。**外して赤くなることを実測する**（Try T3） |

---

## 完了条件

### Definition of Done

- [ ] 全受入基準を満たす（将来リリースへ送るものは**画面と計画の両方に明記**）
- [ ] 全テスト緑・`arch-lint` 違反 0 件・`trace-lint` 違反 0 件・`test:dbnames` 重複 0 件
- [ ] CI 緑
- [ ] **SonarQube Quality Gate が PASS**（利用者が実行。手順は上記）
- [ ] **状態ごとの到達性を確認**（Try T1）。受入テストは URL 直叩きでなく画面のリンクを辿る
- [ ] 見送りの判断に**その根拠を壊しうる同 IT のタスク名を併記**（Try T2）
- [ ] 同時実行の安全装置は**外して赤くなることを実測**（Try T3。できないならその旨を書く）
- [ ] 各実装コミットのメッセージに**壊れ方の観点の該当行を引用**
- [ ] `docs/design/` への反映を**実装と同じコミット**で行い、`architecture_backend.md` の
      コンテキストマップと規約一覧も開く（Try T6）
- [ ] ADR-0012 を起票

### デモ項目

| # | デモ項目 | 対応するテスト |
| :--: | :--- | :--- |
| 1 | 経路設計者が確定済みの予約に追跡番号を発行する（貨物状態が「受領待ち」になる） | `TrackingNumberHttpTest.testRouterIssuesTrackingNumber` |
| 2 | 荷役作業員が追跡番号で貨物を特定し、受領を記録する | `HandlingHttpTest.testHandlerRegistersReceive` |
| 3 | 記録が公開追跡画面に反映される | `HandlingHttpTest.testHandlingIsReflectedInPublicTracking` |
| 4 | 予定外の港で積込を記録すると MISROUTED になる | `HandlingActivityTest.testLoadAtUnplannedPortIsMisrouted` |
| 5 | 荷役作業員が navbar から作業入口へ到達できる | `NavigationReachabilityTest.testHandlerReachesHandlingRegistration` |

> **E2E（Playwright）は IT11 以降**とします。IT9 計画で「業務フローが US14 まで通って
> 初めてシナリオ 1 本が端から端まで書ける」と書きましたが、**引取（US16）まで無いと
> 貨物が荷主の手に渡りません**。IT11 で US16 が入った時点でシナリオ 1 本を書きます。

---

## 前イテレーション（IT9）からの引き継ぎ

### 本 IT で扱うもの

| 内容 | 扱い |
| :--- | :--- |
| IT9 レビュー 中 9 件・低 12 件のうち 7 件 | **TS17**（2 SP・Day 2） |
| M8（`leg` の全削除・再挿入） | **タスク 3.3**（前提が崩れたため見送りを撤回） |
| Try T1-T7 | 上記「IT9 ふりかえりの反映」 |
| SonarQube の実行方法 | **決定済み**（利用者がクローズ時に実行） |

### 本 IT で扱わないもの（方針を明記する）

| 内容 | 方針 |
| :--- | :--- |
| **行ロックが働くことの検証**（IT9 P3） | **IT10 でも解けていない**。テスト構成が要求を直列化するため、識別できるテストの書き方を探す。**見つからなければ「検証していない」と書き続ける**——緑にして忘れない |
| 間欠失敗（`Set-Cookie` 欠落・404） | 再現したら診断情報を記録する。IT9 の H5・H6 で診断は強化済み |
| M13（差し戻しの記録） | **独立ストーリーとして起票**。IT11 の候補 |
| M12（確定画面の概算費用） | US12（通知）と揃えて実装（将来リリース） |
| US15 受入基準 4・5 の通知 | 将来リリース（US12 と同じ通知基盤） |

---

## 設計ドキュメントへの反映（タスク 4.4）

**実装コミットに同梱します**（Try T6）。

**設計を変える箇所は「無言で変えない」**。IT10 の計画検証で、
`isValidFor` の戻り値・識別子・型名・カラム名を計画が独自に変えていたのに
反映表に載っていない、という指摘が 6 件出ました。以下に全件を書き出します。

#### `domain-model.md`（Handling / Tracking）

| # | 対象 | 是正内容 |
| :--: | :--- | :--- |
| 1 | `HandlingActivity` の識別子 | `cargoBookingId: CargoBookingId` → **`trackingNumber: HandlingTrackingNumber`**。US15 受入基準 1（追跡番号で特定）が根拠。`HandlingActivityHistory` のクエリキー・`HandlingActivityRegistrationCommand` の入力も同時に変える |
| 2 | `isValidFor` の戻り値 | `boolean` → **`HandlingValidity`（VALID / WARNED / MISROUTED）**。同じデシジョンテーブルが RECEIVE/CLAIM を「警告」、LOAD/UNLOAD を「MISROUTED」と書き分けており、`boolean` では表現できない |
| 3 | `HandlingValidity` | **要素表に追加**（新規 enum） |
| 4 | `HandlingTrackingNumber` | **要素表に追加**（新規・BC 固有の値オブジェクト） |
| 5 | Handling の `VoyageNumber` | → **`HandlingVoyageNumber`**。Tracking は `TrackingVoyageNumber` なのに Handling だけ無接頭で、共有節（1175・1200 行）とも食い違っている（正典内部の不整合） |
| 6 | `HandlingType` | 値オブジェクトのまま**値を 3 に絞る**（RECEIVE/LOAD/UNLOAD）。CUSTOMS・CLAIM は US16・IT11 と明記 |
| 7 | `TrackingActivity` の状態保持 | **`currentStatus()` による導出をやめ、`transportStatus` を保持する形へ**。導出型は本 IT の「壊れ方の観点 1」が禁じている形であり、DB（`V1__init.sql:21`）も既に `transport_status` を持つ。**記録済みの教訓「集約状態の再導出禁止」に正面から反する** |
| 8 | ドメインイベント表（1214 行）・コンテキストマップ（155-156 行） | `HandlingActivityRegisteredEvent` を**未採用**にする（ADR-0012）。同期の ACL ポートを採るため |

#### `data-model.md`

| # | 対象 | 是正内容 |
| :--: | :--- | :--- |
| 9 | `handling_activity` の識別列 | `booking_id` → **`tracking_number`**（#1 と同根拠） |
| 10 | `handling_activity` の新規列 | **`validity`**（VALID/WARNED/MISROUTED を永続化。判定結果を残さないと後から MISROUTED の経緯を追えない） |
| 11 | `handling_activity` の他の列 | **改名しない**。`event_type` / `event_completion_time` / `operator_name` は設計と既存実装（`tracking_handling_event.event_type` / `event_time`）の命名であり、揃っていない方向へ変えない |
| 12 | `cargo.tracking_number` | 「将来追加予定カラム」から実装済みへ。一意制約の有無をタスク 1.2 の決定に合わせて書く |
| 13 | BC 間 FK の例示（1172 行） | 「例: `handling_activity.booking_id` → `cargo.booking_id`」が**存在しないカラムを指す**ようになる。例を差し替える |
| 14 | Flyway マイグレーション一覧 | `V11__add_tracking_number_and_handling.sql` を追加 |

#### `ui_design.md`

| # | 対象 | 是正内容 |
| :--: | :--- | :--- |
| 15 | `:125` | 「荷役管理 … 準備中（US15・**IT9**）」→ IT10・実装済み |
| 16 | 画面一覧・`:135` | 荷役 2 画面を実装済みへ。「押せる入口を 1 つも持たないロール」から荷役作業員を外す |
| 17 | 予約詳細の仕様 | **追跡番号を発行する操作の導線**を追記（発行済みを前提とした記述は `:783`・`:1626` に既存） |
| 18 | 荷役作業登録のワイヤー（`:1027`） | **航海番号の入力欄を追加**（LOAD/UNLOAD で必須）。`[📷 カメラスキャン]` は**将来リリース**と明記（US15 受入基準 1 の「またはスキャン」は本 IT の対象外） |
| 19 | 荷役作業一覧（`:1080`） | 検索条件・一覧列の「貨物 ID（`BK-`）」を**追跡番号**基点へ |
| 20 | `:1021-1022` の 30 秒注記 | 同期反映を採るなら「コミット後にイベント配信 → 最大 30 秒」は誤りになる。**ADR-0012 の決定に合わせる** |
| 21 | `:1060` の `CUSTOMS_CLEARANCE` | `domain-model.md` は `CUSTOMS`。**表記を揃える** |

#### `architecture_backend.md`・その他

| # | 対象 | 是正内容 |
| :--: | :--- | :--- |
| 22 | コンテキストマップ | **Handling の ACL 2 本**（`CargoSnapshotSource` 読み・`BookingMisroutingReport` 書き）＋ **Handling → Tracking 1 本**（`TrackingTransportStatusUpdate`）＋ **Tracking → Booking 1 本**（`BookingTrackingNumberAssignment`）。イベントの矢印は #8 に合わせる |
| 23 | 認可可否表・API 表 | **荷役 3 ルート**（`GET /handling` は Handler・Tracker 可、`GET|POST /handling/new` は Handler のみ）＋ **追跡番号の発行 1 ルート**（Router）。`:941` が「ルートを追加したら本表と認可テストを同一コミットで更新する」と定めている |
| 24 | ロール定義（`:920`） | `Tracker` の責務が「追跡情報管理・例外対応」で**荷役閲覧を含まない**。navbar（`ui_design.md:125`）と食い違っており、`NavigationReachabilityTest` が突合するため落ちる。ロール定義側に荷役閲覧を追記 |
| 25 | `arch_lint_rules.md` | `composition/acl/` のファイル数を 2 → 5 として記録。**再検討条件（5 本超）に達することを明記** |
| 26 | `business_rule_traceability.md` | TR-1（追跡番号）・荷役の該当行を「済」へ |
| 27 | `operation.md` | 荷役作業員の日次手順。**SonarQube の実行手順**（タスク 4.1） |
| 28 | `development_strategy.md` §3 | 「終盤 = E2E シナリオ」の表に「**新 BC を立ち上げる IT は HTTP 受入テストで代替可**（IT8・IT10）」を追記 |

---

## 実績（クローズ時に記入）

| ストーリー | 計画 SP | 実績 SP | 状態 |
|-----------|:--:|:--:|------|
| US14 | 2 | 2 | 完了（受入基準 4 の通知は計画どおり将来リリース） |
| TS17 | 2 | 2 | 完了（7 件返済。M8 は US15 のタスク 3.3 で返した） |
| US15 | 8 | 8 | 完了（受入基準 5 の通知は計画どおり将来リリース） |
| **合計** | **12** | **12** | **縮退 0 件**（IT5-IT10 で 6 イテレーション連続） |

> **IT11 の着手前タスクとして先に書いておく**（計画に無いものは忘れられる）。
>
> 1. **BC 間連携の設計の見直し**——`composition/acl/` が 6 本になり、
>    ADR-0011 の再検討条件（5 本超）に到達した
> 2. **規約 11 のフィクスチャに Handling の 3 本を反映**するか判断する
> 3. HD-4（CLAIM）・HD-6（通関）は US16 で実装する

---

## 更新履歴

| 日付 | 更新内容 |
| :--- | :--- |
| 2026-08-05 | 初版作成。利用者判断 2 件（TS17 を計上し 10 → 12 SP・SonarQube は利用者がクローズ時に実行）を反映 |

---

## 関連ドキュメント

- [リリース計画](release_plan.md)
- [開発戦略](development_strategy.md)
- [IT9 ふりかえり](retrospective-9.md)
- [IT9 実装レビュー](../review/IT9実装_review_20260805.md)
- [ドメインモデル設計](../design/domain-model.md)
- [データモデル設計](../design/data-model.md)
- [UI 設計](../design/ui_design.md)
