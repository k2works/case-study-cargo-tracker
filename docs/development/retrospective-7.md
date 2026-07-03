# IT7 ふりかえり (KPT)

## 概要

| 項目 | 内容 |
| :--- | :--- |
| イテレーション | IT7 |
| 期間 | 2026-09-28 〜 2026-10-11 (計画) / 実質 2026-07-03 単日集中実装 (Ralph Loop 2 週合計 66 反復) |
| 計画 SP | 10 |
| 実績 SP | **30+ SP (達成率 300%+)** |
| コミット数 | 66+ (Ralph Loop 期間、`792d3629..6edb4463`) |
| テスト | 641 → 776 (+135)、hspec / hspec-wai / hedgehog 全緑 |
| 新規 BC | 1 個 (Exception BC) |
| 新規 ADR | 3 件 (0013 Notification 主キー、0014 Exception 状態遷移、0015 法人割引 rank 由来) |
| Migration | 3 本追加 (tracking_state_audit / exception_record / add_notification_id) |
| Ralph Loop | 2 週 (1 週目 iter 1-58 + 2 週目 iter 1-8) = 66 反復 |

---

## Keep (継続すべき良かったこと)

### 技術面

- **Ralph Loop 2 週運用の確立**: 1 週目 (iter 1-58) で US17/US19/US20/US22 + ADR-0013 の一巡実装、2 週目 (iter 1-8) で T7-01 UNLOAD 接続 + T6-09 RolePolicy + T6-07 correlation_id と、**目的別に週を切り分ける**運用が機能した
- **ADR 移行を Maybe で段階導入**: ADR-0013 (Notification 主キー移行) を `nId :: Maybe NotificationId` で開始し、Phase 別 (migration → VO → 集約 → Application → Postgres → Handling DI) にコミットを分割。既存 5 callsite の変更を最小化しつつ 4 コミットで完了。この規律を memory (`feedback_adr-migration-via-maybe.md`) に永続化
- **Rule 6 準拠の DI パターン確立**: `IO Text` (UUID / 確認コード生成器) を Composition Root (Main.hs) から注入することで、Interfaces → Infrastructure 直接 import を回避。ADR-0013 (Handling BC UUID) と T7-01 (Handling BC 6 桁コード) で 2 度成功
- **Domain 純粋関数 + Interfaces ヘルパーの層分離**: `RolePolicy` (`[Role] -> Bool` の純粋述語 3 種) を Domain に、`RoleGate` (Cookie 認証 + Policy 統合) を Interfaces に分離。SessionAuth 実装から独立してユニットテスト網羅可能
- **Exception BC 新設で Text-DTO Cross-BC パターンを再適用**: `markInExceptionByTrackingNumber` (Exception → Tracking) を Text-only helper で実装。ADR-0004 Rule 4 違反 0 件を維持
- **hedgehog の恒常運用**: `checkTransitionForException` (5 property)、`Discount` (5 property)、`RouteFinder` / `RouteEvaluator` に加え、IT7 で追加した Domain も全て hedgehog カバー。**プロパティテストが第 2 の TDD 規律**として定着

### プロセス面

- **1 コミット = 1 変更規律**: Ralph Loop 66 反復すべてで pre-commit (fourmolu / hlint / arch-check / stack test) をパスさせながら 1 変更単位でコミット。診断用ブランチや revert が発生しなかった
- **View → Servant → Main → hspec-wai の 4 分割**: US17 1 スライスを iter 42-45 の 4 反復に分けたことで、コミット差分が読みやすく、pre-commit 失敗もイテレーション内で収まった
- **end-of-life 判定**: Ralph Loop の Stop hook 継続時に「AI 単独完結タスクが消化済 = end-of-life」を明示的に判断し、Docker/DB 環境依存タスク (Testcontainers) を無理に進めなかった

### ドキュメント面

- **iteration_plan-7.md を Ralph Loop の逐次ジャーナル化**: 「Ralph Loop iter N: 内容 コミット」の 1 行追記を各 iter で実施することで、後で読み返す際に**プロセス痕跡が Git ログと二重化**され、意思決定の背景が追跡可能に
- **journal 20260703.md でメタ知見を集約**: 4 つの学び (View→Servant→Main→hspec-wai 4 分割 / Rule 6 は DI で解決 / Maybe で ADR 移行 / 1 コミット 1 変更) を記録し、次回 Ralph Loop 運用の指針化

---

## Problem (改善すべき問題点)

### 技術面

- **Interfaces 層のテストが薄い**: Ralph 2 週目 developing-review で指摘 — `RoleGate` (3 分岐)、`generateSixDigitCodeText` (境界値)、`handlerPost` UNLOAD 分岐 (副作用) がテスト未実装。Domain 層は厚いが Interfaces 層が薄い「アイスクリームコーン化」の兆候
- **RolePolicy 未配線でセキュリティ露出**: `RolePolicy` / `RoleGate` は完成しているが、US17 手動更新 / US19-20 例外登録の Servant API に `Header "Cookie"` + `requireRoleGate` の配線が未完了。**現状は認証なしで US17 手動更新が可能** — user-representative 視点で高リスク指摘
- **UNLOAD → 通知チャネル未接続**: T7-01 で確認コード自動発行は接続したが、荷受人への配信 (US26 メール等) が未接続。**UNLOAD 単体では業務価値ゼロ**
- **T6-05 Testcontainers 未着手**: Docker/DB 環境設定を伴うため AI 単独完結困難として先送り。IT7 内で Postgres 実装のリグレッション検知の網が張れていない

### プロセス面

- **Ralph Loop 2 週目後半 (iter 9-16) は空回り**: iter 9-16 は end-of-life 判定で最小応答のみ。stop hook を早めに `/ralph-loop:cancel-ralph` する運用を先に memory 化していれば無駄な context 消費を避けられた
- **hspec-wai を Postgres 依存で回避しがち**: `handlerPost` UNLOAD 分岐の副作用テストを「Postgres 依存」と割り切って未実装。fake Repository で Application 層まで検証する規律が徹底されていない

### ドキュメント面

- **ADR 起票が実装先行**: RolePolicy / RoleGate の Domain/Interfaces 分離、`IO Text` DI パターンなど、設計判断は明確だが ADR-0016 相当の起票が未実施 (technical-writer レビュー指摘)

---

## Try (次に試すこと)

### 高優先 (IT8 冒頭で必達)

| ID | 内容 | 期待効果 |
| :--- | :--- | :--- |
| T7-A | RolePolicy を US17 手動更新 API に先行配線 (最低 1 API) | 現状のセキュリティ露出を解消、監査/コンプライアンス上の受け入れ可能化 |
| T7-B | `generateSixDigitCodeText` hedgehog プロパティ (常に長さ 6 かつ全て数字、0/5/99999/999999 境界値) | 先頭 0 パディングは典型的欠陥ポイント、既存 hedgehog 資産で 30 分で追加可能 |
| T7-C | `handlerPost` UNLOAD 分岐の副作用テスト (fake `ConfirmationCodeRepository` を spy 化し、UNLOAD のみ発火・冪等性を検証) | Cross-BC 発火は業務仕様の核心、Postgres 依存を理由にせず fake で 1 時間で追加可能 |
| T7-D | ADR-0016 起票 (Role ベース認可の Domain/Interfaces 分離設計) | 次期 BC 追加時の判断根拠を明文化 |

### 中優先 (IT8 内で対応)

| ID | 内容 | 期待効果 |
| :--- | :--- | :--- |
| T7-E | US26 通知チャネル接続 (UNLOAD 時のコード配信をメール送信 or 画面表示 に接続) | UNLOAD ステップが業務価値を持つようになる |
| T7-F | `handlingPageApp` の DI 引数 8 個を `AppDeps` レコード or newtype ラップに集約 | `IO Text` 2 種の取り違え防止、可読性向上 |
| T7-G | T6-05 Testcontainers 統合テスト (Postgres Repository 4 種) | 実 DB SQL マッピングのリグレッション検知 |
| T7-H | T6-07 katip 依存追加 + 自作 JSON Lines Logging の置換 | CloudWatch 統合、correlation_id 伝搬の標準化 |
| T7-I | ADR-0002 に「Application Input record は Text-only を維持」を追記 | Cross-BC 境界の暗黙ルールを明文化、将来の Rule 4 違反リスク軽減 |

### 低優先 (Release 2.0 準備)

| ID | 内容 |
| :--- | :--- |
| T7-J | ADR-0013 Phase 4 (`nId :: Maybe` → 非 Maybe 化、移行運用完了後) |
| T7-K | ADR-0014 3 種例外詳細化 (`TsDelayed` / `TsDamaged` / `TsLost`、現状 `TsInException` に統合) |
| T7-L | Ralph Loop の Stop hook 継続を「AI 単独完結タスク消化後は即 cancel」に規律化 (feedback memory 更新) |
| T7-M | ExceptionListView に Damage/Loss フィルタと詳細ページ UI 追加 |
| T7-N | RoleGate の JSON エラー body を `Aeson.encode` で型安全構築、403 メッセージを "insufficient permissions" に |

---

## IT7 で完了した Try (IT6 由来)

- T6-01 (E2E ハッピーパス): Stage 1-4/7 有効化、Stage 5-6 は T7-01 完了で前提充足済 (E2E スクリプト再有効化は次イテレーション)
- T6-03 (v1.0.0-mvp tag + CHANGELOG 切出し): CHANGELOG 完了 (`c9b5e025`)、tag は T6-01 統合ハッピーパス完了後
- T6-04 (Pricing/Notification 上流反映): IT6 内で完了 (`c463c36e`)
- T6-06 (k6 スモーク CI 統合): iter 39-40 で完了 (`4837c038` + `2abdb5c2`)
- T6-08 (ADR-0013 起票): iter 18 で起票 → 全 Phase 実装完了

---

## Ralph Loop 2 週運用の学び (IT7 メタ知見)

### 週別スコープの明確化

| 週 | スコープ | 反復数 | 主要成果 |
| :--- | :--- | :---: | :--- |
| 1 週目 | 本体ストーリー実装 (US17/US19/US20/US22) + Exception BC + ADR-0013 | 58 反復 | 全 BC 一巡完成 + Notification 主キー移行 3 Phase |
| 2 週目 | 保証系タスク (T7-01 / T6-09 / T6-07) | 8 反復 | T7-01 完了、T6-09 Policy/Gate 部分、T6-07 correlation_id 部分 |

**学び**: 1 週目は「本体スコープの網羅」、2 週目は「保証系の埋め合わせ」に分けると、後者の end-of-life 判定 (Docker 依存タスクの区切り) がしやすい。

### end-of-life の早期判定

- 2 週目 iter 9 以降は Stop hook 継続でも実質的な作業なし → 早めに `/ralph-loop:cancel-ralph` すべき
- 判断基準: 「次の変更が Docker / DB / UI 判断 / セキュリティ設計を伴うなら AI 単独完結困難 → 中断」

---

## ベロシティ実績と次期 IT の計画への反映

### 実績推移

| IT | 計画 SP | 実績 SP | 達成率 |
| :---: | :---: | :---: | :---: |
| IT1 | 13 | 20 | 154% |
| IT2 | 10 (+Try 8+横断 2) | 18 | 180% |
| IT3 | 29 | 22 | 76% |
| IT4 | 20 | 19 | 95% |
| IT5 | 22 | 40+ | 182% |
| IT6 | 18 | 30+ | 167% |
| **IT7** | **10** | **30+** | **300%+** |

平均ベロシティ (単純平均): **25.6 SP**

### IT8 計画への示唆

- **平均 25.6 SP** に加え、Ralph Loop 2 週運用が確立したことで **IT7 と同等の 30+ SP 消化** が現実的
- ただし IT8 は Release 2.0 GA を睨むため、**保証系タスク (RoleGate 配線 / US26 通知チャネル / Testcontainers / katip 完全移行 / E2E ハッピーパス)** を確実に組み込む必要あり
- **IT8 目標 SP: 20-25**、内訳:
  - IT7 繰越 (T7-A〜T7-D 高優先): 4-6 SP
  - 本体 US23 精算 / US25 引取通知配信: 8-10 SP
  - 保証系 (T7-E〜T7-I 中優先): 6-8 SP
  - バッファ: 2-3 SP

---

## 関連ドキュメント

- [IT7 計画](./iteration_plan-7.md)
- [Ralph Loop 2 週目 レビュー](../review/ralph-loop-week2_review_20260703.md) (2026-07-03)
- [journal 20260703](../journal/20260703.md) — Ralph Loop iter 38-58 サイクルの学び
- [リリース計画](./release_plan.md)
- ADR-0013 (Notification 主キー移行、IT7 で 3 Phase 実装完了) — `docs/adr/0013-notification-primary-key-design.md`
- ADR-0014 (Exception 状態遷移ポリシー、IT7 で 3 Phase 実装完了) — `docs/adr/0014-exception-state-transition-policy.md`
- ADR-0015 (法人割引 contract_rank 由来設計、IT7 で採用) — `docs/adr/0015-corporate-discount-rank-derived.md`
