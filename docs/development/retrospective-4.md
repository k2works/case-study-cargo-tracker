# イテレーション 4 ふりかえり

## 概要

| 項目 | 内容 |
|------|------|
| **イテレーション** | 4 |
| **期間** | 2026-08-25 〜 2026-09-05（計画） |
| **ゴール** | 算出された経路候補を予約に確定・紐付けし、荷主通知と予約確定まで、経路確定〜予約確定の業務フローを一気通貫で実現する（Booking Context の状態機械を経路確定へ拡張する） |
| **局面** | 中盤（インサイドアウト） |
| **計画 SP** | 12（US09/US10/US11/US12/US13） |
| **実績 SP** | 12（US09-13 完了） |
| **達成率** | 100% |

### 成果サマリー

- 実装コミット 10 件（feat 8 / refactor 1 / test 1）＋ docs 多数、全 242 テスト緑（Tests 128 / Integration 101 / Arch 13）・警告 0・Fantomas クリーン
- Booking 状態機械を `RoutingRequested → RouteProposed → Confirmed`（＋差し戻し・キャンセル）へ拡張し、`Leg`/`CargoItinerary` を `create` で連結保証
- 業務フロー「経路候補選択→確定→予約紐付け→荷主通知→予約確定／差し戻し／キャンセル」を横断受け入れテストで縦貫通（US09/US11/US12/US13）
- US10（到着期限の調整・経路再算出）を実装し、条件協議は営業経由という意味論を受入テストで固定
- ADR-0010（Routing→Booking 経路確定連携は合成層の ACL 変換）を起票・参照実装（`RouteAcl`）
- **retro-3 Try#1 を解消**: post-commit イベント dispatch を `RouteAssignment` へ結線（コミット後発火・失敗はベストエフォート）
- IT3 レビュー M1（Result 畳み込みの `traverseResultM` 集約）を消化
- カバレッジ: 全体 94.1% / ドメイン層 90.2%（両閾値クリア）
- セルフレビュー（xp-programmer / xp-tester）を実施し高・中の指摘を反映（検証穴の補強・US10 意味論の固定）

---

## Keep（うまくいったこと）

### 技術的成功

1. **状態機械の型駆動拡張**: `BookingState` に `RouteProposed of CargoItinerary` / `Confirmed of CargoItinerary` を追加し、旅程を持つ状態と持たない状態を型で分離。`Cargo.execute` の全パターンマッチを網羅し、不正遷移（Preliminary→confirm 等）をコンパイル/実行時に排除。ADR-0007 と同系統の DU 拡張規律を踏襲した。
2. **ACL による BC 分離の維持**: Routing 固有 `RouteCandidate` を Booking `CargoItinerary` へ変換する ACL（`RouteAcl`）を Web 合成層に純粋関数として配置（ADR-0010）。両 BC 固有の `VoyageNumber` を値へ落として組み替え、型依存を持ち込まない設計を維持し ArchUnit 緑。
3. **集約ルート経由の旅程永続化**: `leg` を `cargo` 集約の子として全置換（DELETE→INSERT）で同期し、`BookingState.itinerary` を状態→旅程写像の単一の真実として永続化・復元・通知・表示が参照。差し戻し時の leg 削除まで統合テストで実証。
4. **post-commit dispatch の結線（負債解消）**: `RouteAssignment.applyCommand` で永続化コミット後にのみイベントを発火し、失敗経路（NotFound/検証エラー/永続化失敗）では未発火を各層で検証。IT2 H6・IT3 M2・retro-3 Try#1 を解消した。
5. **インサイドアウトの一気通貫**: ドメイン（FsCheck 込み）→アプリ（ワークフロー）→インフラ（leg/通知）→Web（画面・ハンドラ）→受け入れテストの順で US09-13 を縦貫通。

### プロセス的成功

1. **セルフレビューの中間適用**: 実装完了後に xp-programmer/xp-tester の 2 視点を並列でセルフレビューし、post-commit の失敗表現・検証穴・US10 の潜在的矛盾を着手済みコードで検出・是正。正式 developing-review 前に品質を底上げした（[[feedback_review-two-stage]]）。
2. **設計判断の即時ドキュメント化**: US10 の期限意味論と post-commit ベストエフォート方針を計画の「実装で確定した設計判断」に明記し、data-model（0007/0008）・domain-model（RestoreToRouting・CargoItinerary list 表記）へ完了時反映。ドリフトを持ち越さなかった。

---

## Problem（課題）

1. **US10 の期限調整が探索専用にとどまる**: 期限緩和は候補探索の再算出にのみ効き、集約の `RouteSpecification` 期限は変更しない。緩和期限で見えた候補を確定するとドメインが元の期限で棄却する（400）。正式な期限変更コマンドは未実装で、条件協議は営業への口頭導線に依存する。
2. **イベント消費者が依然不在**: post-commit dispatch は結線したが、消費側（Tracking の追跡番号発行等）は IT5+ 未着手のため `StubBookingEventDispatcher` は標準出力ログにとどまる。実消費差し替え時のリトライ/DLQ 方針は未実装。
3. **通知が最小実装**: US12 の荷主通知は `notification_log` への記録のみで、実送信（メール等）と recipient の実アドレス解決（荷主メール参照）は後続 IT 依存。recipient は ShipperId(Guid) を保持するにとどまる。
4. **Web ハンドラの軽微な重複**: `routingPropose` / `bookingNotify` / `bookingStateAction` で接続取得・結果分岐（Ok→PRG / NotFound→404 / _→400）が部分的に重複。「実行→PRG」ヘルパへの集約余地が残る（xp-programmer 中#3）。
5. **候補選択が並び順の決定性に依存**: 表示と確定で候補を二重算出し `candidateIndex` で照合するため、`computeRoutes` の並びが決定的（安定ソート）である前提に依存。将来非決定的になると誤選択の恐れ（xp-programmer 中#4・現状は安定ソートで安全）。

---

## Try（次イテレーションでの改善アクション）

| # | 改善アクション | 責任者 | 期限 | 期待効果 |
|---|--------------|--------|------|---------|
| 1 | Tracking（IT5）着手時に `BookingEventDispatcher` の実消費（`CargoRouted`/`BookingConfirmed` → 追跡番号発行等）を結線し、失敗時のリトライ/DLQ 方針を確立する | 開発担当 | IT5 | ADR-0002 の post-commit を実消費で実証・BC 間イベント駆動の完成 |
| 2 | US10 の正式な期限変更が必要になった時点で、`RouteSpecification` 期限を更新するコマンド（営業起点・荷主協議記録付き）を ADR 込みで導入する | 開発担当 | 必要時 | 期限緩和の探索と実変更の意味論を一致させる |
| 3 | US12 の荷主通知を実送信（メール等）へ拡張し、recipient を荷主メールの実解決に置き換える（Shipper 参照 ACL） | 開発担当 | 通知強化 IT | 通知の実効化・最小実装からの脱却 |
| 4 | Web の「ワークフロー実行→PRG」共通ハンドラへ `routingPropose`/`bookingNotify`/`bookingStateAction` を集約する | 開発担当 | 改善 IT | ハンドラの DRY・可読性向上 |
| 5 | 候補選択を index ではなく候補同一性キー（voyage 番号列）での照合に変更し、並び順非決定性に対する堅牢性を確保する | 開発担当 | 改善 IT | 表示と確定の乖離リスク排除 |

---

## ベロシティ実績と再較正（IT4 終了時）

| イテレーション | 局面 | 計画 SP | 実績 SP | 達成率 |
|---------------|------|---------|---------|--------|
| IT1 | 序盤 | 10 | 10 | 100% |
| IT2 | 序盤 | 10 | 10 | 100% |
| IT3 | 中盤 | 14 | 14 | 100% |
| IT4 | 中盤 | 12 | 12 | 100% |
| **累計** | | **46** | **46** | **100%** |

- 4 イテレーション連続で計画 SP を 100% 達成。中盤（IT3-4）の平均ベロシティは 13 SP/IT で安定。
- ベロシティは安定期に入ったと判断でき、後続イテレーションの見積り基準として 12〜14 SP を採用できる。
