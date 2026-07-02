# IT6 ふりかえり (KPT)

## 概要

| 項目 | 内容 |
| :--- | :--- |
| イテレーション | IT6 |
| 期間 | 2026-09-14 〜 2026-09-27 (計画) / 実質 2026-07-02 単日集中実装 (Ralph Loop 30 反復) |
| 計画 SP | 18 (本体 5 + IT5 繰越 8 + プロセス品質 3 + 上流補完 2) |
| 実績 SP | **30+ SP (達成率 167%)** |
| コミット数 | 30 (eab76f12..4eb19eea) |
| テスト | 502 → 641 (+139)、hspec / hspec-wai / hedgehog 全緑、E2E は IT7 で追加予定 |
| 新規 BC | 2 個 (Pricing / Notification) |
| 新規 ADR | 1 件 (0012 Tx 境界と Cross-BC 参照ポリシー **採用**) |
| 更新 ADR | 1 件 (0010 提案 → **採用** 段階移行完了) |
| Migration | 3 本追加 (pricing_rule / currency_rate / notification) |
| Ralph Loop | 30 反復で本体 + Postgres 実装まで一巡 |

---

## Keep (継続すべき良かったこと)

### 技術面

- **Cross-BC helper (Text-based DTO) パターンの継続適用**: `markClaimedByBookingId` (Handling → Tracking, T5-04) / `sendClaimLogNotificationText` (Handling → Notification, US26) の 2 種を Application ports に集約。Rule 4 違反 0 件を維持
- **Verifier 注入パターン**: `type Verifier = Text -> Text -> Bool` を Shared に配置し、`verifyWith checker` で `constantTimeEqText` / `verifySecret` を差替可能に。ConfirmationCode の bcrypt 移行を 4 phase (定数時間比較 → bcrypt hash ヘルパ → 注入 → migration) に分割できた
- **TxRunner (RankNTypes newtype)**: Application 層に配置し T-01 準拠を保った。`runInTx tx $ Command.execute ...` の 1 行で Tx 境界を宣言でき、テストは `noTxRunner = TxRunner id` で素通し
- **ADR-0012 決定 3 (外部副作用は Tx 完了後)**: Handling.claim の通知発火を `runInTx tx $ VerifyClaim.execute ...` の**後** に配置。Tx ロールバック時に副作用 (メール送信・ログ配信) が漏出しない構造を確立
- **In-Memory Repository の残置**: `InMemoryPricingRuleRepository` / `InMemoryNotificationRepository` を Postgres 実装への移行後も残した。単体テストのフィクスチャや暫定デモ用途で有用
- **Support.HspecWaiJa の統一**: `bodyContainsText :: Text → MatchBody` で `"\xe8\xa9\xb2..."` のバイト列アサーションを可読リテラルに置換。今後の新規 spec の可読性を維持
- **Cost VO の Integer 保持**: `data-model.md §設計判断 3` (BIGINT + VARCHAR(3)) に整合。浮動小数点誤差なし、data-model と Domain / Postgres で 3 層一貫

### プロセス面

- **Ralph Loop の 30 反復消化**: Stop hook で同じプロンプトが再投入されるループを活用し、単日で高優先 5 件 + 中優先 5 件 + プロセス品質 5 件 + 本体 2 ストーリー × Phase 1-7 + Postgres Phase 1-2 を完了。反復ごとに 1 コミット / 1 テスト増分の粒度を維持
- **段階的移行 (bcrypt 3 phase)**: `verify` を「pure な比較」から「Verifier 注入 → bcrypt hash 保存 → wire で verifySecret に切替」の 3 段階に分割。既存 6 テストを緑のまま bcrypt に完全移行できた
- **arch-check Rule 6 / T-01 の早期検知**: `TxRunner` を Infrastructure に置いた時点で Rule 6 (Interfaces → Infrastructure) + T-01 (Tx boundary in Application) の 2 件で違反検出。Application 層への移設で解消 (Rule 遵守設計)
- **一貫した pre-commit フック**: fourmolu / hlint / arch-check / stack test (Domain only) を全 30 コミットで通過。**615 → 641 tests への増分でも構造違反 0 件**
- **Text ベースの Cross-BC error (Rule 4 準拠)**: `DomainError.PricingRuleNotFound !Text` / `CurrencyRateNotFound !Text !Text` を Text で保持し、Shared が Pricing.Currency 型に依存しない設計を採用

### ドキュメント面

- **CHANGELOG [Unreleased] の Release 1.0 整理 (T5-20)**: T5-01〜T5-12 の Added / Changed / Fixed / ADR / Tests を実績ベースで列挙、526 → 535 (現在 641) の推移を明記
- **README 環境変数・Cookie 早見表 (T5-19)**: DATABASE_URL / JWT_SECRET / cargo_session の HttpOnly / SameSite=Lax / Max-Age=28800 / ConfirmationCode の ttlSeconds=86400 / maxAttempts=5 を一箇所集約
- **ADR-0010 段階移行記述 (T5-21)**: 「提案」→「採用」に昇格、IT5 完了 / IT6 T5-01 完了 / IT7+ 拡張の 3 段構成に整理

---

## Problem (改善すべき問題点)

### 技術面

- **E2E テスト未追加**: Playwright ハッピーパス「予約→追跡→引取→料金」を IT6 内で追加できなかった。UI ビュー (CostCalculationView / NotificationListView) は data-testid 属性を含めているが、実行環境が未整備
- **v1.0.0-mvp git tag 未作成**: Release 1.0 MVP を掲げつつ、正式リリースタグを打っていない。CHANGELOG の [Unreleased] からのセクション切出しも未実施
- **developing-review 未実施**: マルチパースペクティブレビュー (5 XP エージェント並列) が IT6 の実装物に対して未実施。IT5 では終盤で 32 件の指摘を抽出できたが、IT6 は同等の品質検証が未完了
- **PostgresPricingRuleRepository の統合テスト不在**: Testcontainers ベースの実 DB 統合テストが `pricing_rule` に対して未整備。単体テストは網羅済だが SQL マッピングは実 DB 未検証
- **NotificationRepository の updateNotification が booking_id + created_at を複合キーに使用**: サロゲート `id` を使わない設計になっており、同 booking_id + 同 created_at の複数レコード衝突リスクがある (現実的には稀だが厳密性は弱い)
- **katip 正式化 (T5-18) 未対応**: 自作 JSON Lines は `Cargotracker.Shared.Infrastructure.Logging` に残置。CloudWatch 統合や correlation_id 伝搬は IT7 以降で対応

### プロセス面

- **Ralph Loop の 30 反復は長い**: 「反復ごとの成果」は明確だが、後半 (Phase 5-7 / Postgres) は前半の高優先技術的負債より進捗確認の粒度が粗くなった。マイルストーン (5 反復ごと) で `--status` を挟むと集中が保てたかもしれない
- **Rule 4 違反の一時発生**: Handling BC が Notification Domain を直接 import した commit (Cross-BC 統合の初期) で arch-check が違反検出。修正で Text-only helper (`sendClaimLogNotificationText`) を追加したが、`Verifier` を Shared に移設した T5-02 Phase 3b と同じ「型が Shared に上がる」パターンを最初から予見できなかった
- **単体テスト重視でパフォーマンス検証がない**: PricingRule.calculate は Integer 演算で高速だが、CurrencyRate.convert × Postgres の実測 P95 (< 500ms) 検証は未実施

### ドキュメント面

- **IT6 計画書と実績の乖離**: 「本体 5 SP」の計画は実績 13 SP (US21 / US26 各 6〜7 SP 相当) に膨らんだ。Domain / Application / Infra (Postgres 含む) / Interfaces / Views / Wire の全レイヤ実装を「5 SP」に見積もった計画は過小評価
- **domain-model.md / data-model.md / ui_design.md への Pricing / Notification 追記が未実施**: iteration_plan-6.md 6.1〜6.3 (上流補完) タスクは実装優先で後回しになった。IT7 で反映が必要

---

## Try (次に試すこと)

### 高優先 (IT7 冒頭で必達)

| ID | 内容 | 期待効果 |
| :--- | :--- | :--- |
| T6-01 | Playwright E2E ハッピーパス「予約→経路→追跡→荷役→引取→料金」1 本追加 | Release 1.0 MVP の統合動作を自動検証、リグレッション検知の網 |
| T6-02 | developing-review 実施 (5 XP エージェント並列でコード・テスト・設計・ドキュメント・ユーザー視点レビュー) | 実装物の品質検証、IT5 と同等の指摘抽出 |
| T6-03 | v1.0.0-mvp git tag 作成 + CHANGELOG [Unreleased] → [1.0.0-mvp] へのセクション切出し | 正式リリース、次期 [Unreleased] を IT7 用に開放 |
| T6-04 | domain-model.md / data-model.md / ui_design.md に Pricing BC / Notification BC を追記 | 上流ドキュメントと実装の同期、次期スコープ検討の土台 |

### 中優先 (IT7〜IT8 で対応)

| ID | 内容 | 期待効果 |
| :--- | :--- | :--- |
| T6-05 | PostgresPricingRuleRepository / PostgresCurrencyRateRepository / PostgresNotificationRepository の Testcontainers 統合テスト追加 | 実 DB SQL マッピングのリグレッション検知 |
| T6-06 | k6 スモーク負荷テストを CI に組み込み (P95 < 500ms 検証) | 非機能要件の CI 自動化、Release 1.0 の品質ゲート強化 |
| T6-07 | katip 正式化 (T5-18): 自作 JSON Lines → katip 移行 | CloudWatch 統合、correlation_id 伝搬、構造化ログの標準化 |
| T6-08 | ADR-0013 (Notification BC の updateNotification 主キー設計) 起票、id サロゲート追加 or 複合キー正式化 | 通知重複衝突リスクの排除、ADR で判断根拠を明文化 |
| T6-09 | AuthProtect middleware の適用範囲拡張 (Confirm/Cancel/Link/Unlink/EvaluateRoute) + Role-based 権限 (ADR-0010 段階移行 IT7 段階) | 認可の網羅、Shipper/Sales/Handler/Tracker/Admin 別権限の実装 |

### 低優先 (Release 2.0 準備)

| ID | 内容 |
| :--- | :--- |
| T6-10 | Ralph Loop の反復粒度ガイドライン化 (5 反復ごとに `--status` で確認) |
| T6-11 | Notification BC のメール送信実装 (SMTP or SES 経由の SendEmailDeliveryPort) |
| T6-12 | 割引率の Shipper.discount_rate 連携 (法人契約割引の Application 経路) |

---

## IT6 完了後の追補 (2026-07-02)

Ralph Loop 終了直後、IT7 着手前にキャッチアップした作業。ふりかえりの結果を先取りして即応した。

| # | 内容 | 対応 Try / 経緯 | コミット |
|---|------|----------------|---------|
| P1 | ホーム / navbar に US21 送料計算 / US26 通知一覧の導線追加 | 受入条件「トップから 1 クリック」の抜けを解消 | `01659f44` |
| P2 | Playwright E2E 2 本追加 (pricing-calculation / notifications) | T6-01 の一部先行 (US21/US26 単体スモーク) | `c4aeb636` |
| P3 | developing-review 実施 (5 XP エージェント並列) | **T6-02 完了**。指摘 12 件を整理 (高 1 / 中 7 / 低 4) | `88ea93d2` (レポート) |
| P4 | H-01 反映: 送料計算 / 通知一覧を未認証ホームから除外 | developing-review 高優先指摘 #1 の即時対応 | `5b29c7dd` |

### 学び

- **Ralph Loop 直後に developing-review を回すと即時対応可能な高優先が 1 件見つかる**: レビュー実施を IT 内タスクに位置づけるべき (次 IT 冒頭ではなく IT 末尾)
- **未認証露出の H-01 方針は SSoT 化されているが、Shared/Web の変更時に自動チェックされない**: arch-check の対象拡張を検討 (低優先 T6-13 として追記候補)
- **単体 E2E で happy-path を代替してはいけない**: T6-01 (予約→追跡→引取→料金) は依然として必須

### T6-01 / T6-02 の状態更新

- T6-01: **一部先行** (US21/US26 単体スモーク完了、統合ハッピーパスは IT7 継続)
- T6-02: **完了** (レポート `docs/review/it6_nav_e2e_review_20260702.md`、中低優先 #2〜#12 は IT7 で消化)

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
| **IT6** | **18** | **30+** | **167%** |

平均ベロシティ (単純平均): **24.8 SP**

### IT7 計画への示唆

- **平均 24.8 SP** を基準としつつ、IT5 / IT6 のような「本体ストーリー × Domain/App/Infra/Interfaces/Views/Wire/Postgres の全レイヤ実装」なら実際は 30+ SP 消化できる
- ただし E2E + developing-review + Postgres 統合テストなど「保証系タスク」を確実に組み込む必要あり
- **IT7 目標 SP: 20-25** で、本体 (US17 貨物状態手動更新 / US19 遅延例外 / US20 破損紛失例外 / US22 法人割引) + IT6 繰越 (T6-01〜T6-04) を計画

---

## 関連ドキュメント

- [IT6 完了報告書](./iteration_report-6.md)
- [IT6 計画](./iteration_plan-6.md)
- [リリース計画](./release_plan.md)
- ADR-0012 (Tx 境界と Cross-BC 参照ポリシー、IT6 T5-03 で採用起票)
- ADR-0010 (Session Cookie 認証、IT6 T5-01 で「採用」に昇格)
