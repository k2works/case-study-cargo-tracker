# イテレーション 11 計画（スケルトン）

> **ステータス**: スケルトン（草案）。IT10 staging 実機完了 + `iteration_report-10.md` + 正式 `developing-review` 完了後に、実績ベロシティ / 最新 review 指摘を反映して正式版へ昇格する。
>
> **想定昇格時期**: IT10 完了直後（staging 環境構築 + 実機検証の所要期間に依存）。具体的な日付は IT10 retrospective 時点で確定する。本スケルトン作成時点（2026-06-09）の staging 着手日は未定。

## 概要

| 項目 | 内容 |
|------|------|
| **イテレーション** | IT11（Release 1.2 着手 / 構造的負債返済 + 業務スコープ拡張） |
| **期間（仮）** | 2 週間（IT10 完了直後の月曜起算、staging 構築完了日に依存。本スケルトン時点では 2026-06-22 〜 2026-07-03 を仮置き） |
| **想定ベロシティ** | 8 SP（IT5-IT10 平均、IT10 staging 完了後に実績ベロシティで再算定） |
| **ゴール（草案）** | IT10 中間レビュー H / M 持ち越し（PreAuthFilter / EnumCheckConstraintVerifier 共通化、SDK contract test、Prometheus alert rule 化）+ US28（Invoice 返金）/ US29（チャージバック申し立て）の本格スコープ確定 |

---

## ゴール（草案）

### イテレーション終了時の達成状態（草案）

1. **B1 共通化リファクタリング（IT10 中間レビュー H1 / M1）**: `BasePreAuthFilter` を `shared` モジュールに抽出（5 ms コピペ解消）+ `EnumCheckConstraintVerifier` を `shared-test` に抽出（3 ms コピペ解消、ADR-0023 Cons 緩和策）
2. **B2 SDK contract test（IT10 中間レビュー M2 + H3）**: Stripe SDK の HMAC + tolerance の境界値挙動を 1 ファイルでカバーする contract test を追加。SDK バージョン更新時に PR で差分検知可能化
3. **B3 Prometheus alert rule YAML 化（IT10 中間レビュー M3）**: `tracking.public_token.refresh.consecutive_failures` Gauge ≥ 3 のアラートを `infra/prometheus/alerts.yml` に明文化し、Grafana / alert manager に投入する PR ベースの運用フローを確立
4. **B4 US28 候補（Invoice 返金処理スコープ確定）**: `charge.refunded` 受信時の Invoice 状態遷移（`REFUNDED` / `PARTIALLY_REFUNDED` 追加 or 別ストーリー分離）の要件定義 + ADR 起票
5. **B5 US29 候補（チャージバック申し立て管理スコープ確定）**: `charge.dispute.created` 受信時の業務 workflow（Invoice 状態 `DISPUTED` 追加 or Dispute Aggregate 新規作成）の要件定義 + ADR 起票

### 成功基準（草案）

- [ ] `shared-security` モジュールに `BasePreAuthFilter` 抽出、5 ms から重複コード削除
- [ ] `shared-test` モジュールに `EnumCheckConstraintVerifier` 抽出、3 ms `*CheckConstraintTest` を thin wrapper 化
- [ ] Stripe SDK contract test が 1 ファイルで HMAC + tolerance + payload parsing をカバー
- [ ] `infra/prometheus/alerts.yml` に `tracking_public_token_refresh_consecutive_failures` アラート定義、Grafana で実機 firing 確認
- [ ] US28 / US29 の受入基準ドラフト + ADR-0024 / ADR-0025 起票

---

## ストーリー候補（草案）

### B1: 共通化リファクタリング（IT10 H1 + M1 / 2 SP）

IT10 で「Rule of Three 到達」と判定したが staging 安定確認後に持ち越した共通化を実施する。

| # | タスク（仮） | 見積もり | 状態 |
|---|--------|---------|------|
| B1.1 | `shared-security` モジュール新設 + `BasePreAuthFilter` 抽出 | 2h | [ ] |
| B1.2 | 5 ms の `PreAuthFilter` を `BasePreAuthFilter` 継承 / 削除に置換 | 3h | [ ] |
| B1.3 | `shared-test` モジュール新設 + `EnumCheckConstraintVerifier` 抽出 | 2h | [ ] |
| B1.4 | 3 ms の `*CheckConstraintTest` を thin wrapper に整理 | 2h | [ ] |
| B1.5 | ADR-0026: `shared-security` モジュール新設の根拠 | 0.5h | [ ] |

### B2: SDK contract test（IT10 H3 + M2 / 1 SP）

| # | タスク（仮） | 見積もり | 状態 |
|---|--------|---------|------|
| B2.1 | Stripe SDK の HMAC + tolerance + payload parsing を 1 ファイルでカバーする contract test 設計 | 1h | [ ] |
| B2.2 | contract test 実装（バージョンアップ時に PR で差分検知可能化） | 2h | [ ] |
| B2.3 | `PaymentGatewayWebhookController` の二段判定（前段自前 + 後段 SDK）を一段化検討（IT10 H3 解消） | 1h | [ ] |

### B3: Prometheus alert rule YAML 化（IT10 M3 / 1 SP）

| # | タスク（仮） | 見積もり | 状態 |
|---|--------|---------|------|
| B3.1 | `infra/prometheus/alerts.yml` 新設 + `tracking_public_token_refresh_consecutive_failures >= 3` アラート定義 | 1h | [ ] |
| B3.2 | Grafana / alert manager 投入 PR フロー（CI で yaml 検証）確立 | 2h | [ ] |
| B3.3 | staging で実機 firing → PagerDuty / Slack 通知到達確認 | 1h | [ ] |

### B4: US28 候補スコープ確定（返金処理 / 2 SP）

| # | タスク（仮） | 見積もり | 状態 |
|---|--------|---------|------|
| B4.1 | US28 受入基準ドラフト（Invoice 状態 / Domain Event / API / S23 UI） | 2h | [ ] |
| B4.2 | ADR-0024: 返金処理の集約境界（Invoice 拡張 vs Refund 新規 aggregate）| 2h | [ ] |
| B4.3 | データモデル設計（`refund` テーブル / `invoice.refunded_amount` カラム）+ migration ドラフト | 2h | [ ] |

### B5: US29 候補スコープ確定（チャージバック申し立て管理 / 2 SP）

| # | タスク（仮） | 見積もり | 状態 |
|---|--------|---------|------|
| B5.1 | US29 受入基準ドラフト（Dispute Lifecycle / Stripe 申し立て連携 / 業務 workflow） | 2h | [ ] |
| B5.2 | ADR-0025: 申し立て管理の集約境界 + Stripe Dashboard 連携範囲 | 2h | [ ] |
| B5.3 | データモデル設計（`dispute` テーブル / `invoice.dispute_status` カラム）+ migration ドラフト | 2h | [ ] |

---

## タスク合計（草案）

| カテゴリ | SP | 理想時間 | 由来 |
|---------|----|----|------|
| B1 共通化リファクタリング | 2 | 9.5h | IT10 中間レビュー H1 / M1 |
| B2 SDK contract test | 1 | 4h | IT10 中間レビュー H3 / M2 |
| B3 Prometheus alert rule YAML 化 | 1 | 4h | IT10 中間レビュー M3 |
| B4 US28 候補スコープ確定 | 2 | 6h | IT10 A3.9a 続編、charge.refunded |
| B5 US29 候補スコープ確定 | 2 | 6h | IT10 A3.9a 続編、charge.dispute.created |
| **合計** | **8** | **29.5h** | IT10 中間レビュー 4 件 + 業務スコープ 2 件 |

---

## 起点となる根拠

| 由来 | 取り込み | 備考 |
|---|---|---|
| [IT10 中間レビュー](../review/IT10_interim_review_20260609.md) H1 | B1 | PreAuthFilter 5 ms コピペ共通化 |
| 同 M1 | B1 | EnumCheckConstraintVerifier 共通化（ADR-0023 Cons 緩和策） |
| 同 H3 | B2 | tolerance 二段判定の一段化検討 |
| 同 M2 | B2 | SDK upgrade 検知 contract test |
| 同 M3 | B3 | Prometheus alert rule YAML 化 |
| US26 受入基準 IT10 A3.9a 続編 | B4 / B5 | charge.refunded / charge.dispute.created の本格業務反映 |
| [IT9 開発成果物レビュー](../review/IT9_review_20260606.md) IT11+ 持ち越し 14 件 | B6（候補） | H1（Invoice ES 決定性）/ H2（PARTIALLY_PAID → PAID 経路テスト）/ M2-M7 / L1-L4 / L7。IT11 着手時にスコープ判断 |
| 正式 `developing-review`（staging 完了後） | TBD | IT11 着手前に追加指摘が出れば B7 以降として統合判断 |

---

## IT9 review IT11+ 持ち越し候補（草案、B6 として B4/B5 完了後に判断）

IT9 マルチパースペクティブレビューで「IT11+ 検討（持ち越し）」と分類された 14 件のうち、IT11 着手時に取り込む候補を以下に明示する。IT10 staging 完了 + 正式 `developing-review` 結果と合わせて、IT11 開始時に最終スコープを決定する。

| ID | 観点 | 内容（要約） | 取り込み判断 |
|---|---|---|---|
| IT9 H1 | programmer | `Invoice.on(PaymentRecordedEvent)` の balance 再構築が ES の決定性を脅かす（event に paidSoFar を含める shared 契約変更検討） | shared 契約変更 = 影響範囲大、IT11 で ADR-0024 起票後に IT12 以降実装 |
| IT9 H2 | programmer | `BillingStatus.canTransitionTo` で `PARTIALLY_PAID → PAID`（手動完全入金）経路のテスト不足 | B6 候補（1 SP 以内、Domain test 追加のみ） |
| IT9 M2 | programmer | `PaymentGatewayWebhookController` の `catch (Exception e)` で TimeoutException 握り潰し | IT10 A3.6 / A3.7 改善で部分緩和、残は IT11 で型別 catch に分離 |
| IT9 M3-M5 | programmer / architect | `WebhookProcessed` POJO → record / SendGrid SDK サブクラス ArchUnit 検知 / PartialPaymentRecorded 境界判定 | B6 / B7 候補、Domain / Test レベル変更 |
| IT9 M6 | tester | `BalanceTracker.withTotalDue` 既入金 > 新 totalDue エッジ | B6 候補（プロパティベース検証） |
| IT9 M7 | tester | JWT フィルタの時刻関連境界値（期限切れ / exp 欠落 / 署名アルゴリズム不一致） | B6 候補（JwtAuthenticationFilterTest 拡張） |
| IT9 L1-L4 / L7 | 各観点 | 低優先度 5 件 | Release 1.2 以降検討維持 |

---

## リスク（草案）

| ID | リスク | 重要度 | 対策 |
|---|---|---|---|
| R1 | `shared-security` モジュール新設で Gradle 設定が複雑化、5 ms × test 全部に影響 | 中 | B1.1 を IT11 Day 1 に着手し、トラブル時のバッファ確保 |
| R2 | US28 / US29 のドメイン設計が IT11 期間内に確定できない（業務要件確認に時間が必要） | 高 | B4 / B5 はスコープ確定（ADR + ドラフト）までを成果物とし、実装は IT12 以降に分離 |
| R3 | staging 完了後に正式 `developing-review` で新規 H 優先度指摘が出て見積もりオーバー | 中 | IT11 着手前に正式 review を実施し、本計画を update してから着手 |

---

## ジャーナル（草案）

| 日付 | 内容 | 担当 |
|------|------|------|
| 2026-06-09 | iteration_plan-11 スケルトン作成（IT10 staging 完了前 / IT クロージング作業の延長として）。IT10 中間レビュー H / M 持ち越し + US28 / US29 候補で 8 SP / 29.5h を仮構成。staging 完了 + iteration_report-10 + 正式 developing-review 後に正式版へ昇格 | k2works |

---

## 関連

- [iteration_plan-10.md](iteration_plan-10.md) — IT10 計画 / タスク状態
- [journal-it10.md](journal-it10.md) — IT10 中間サマリ
- [IT10 中間レビュー](../review/IT10_interim_review_20260609.md) — B1-B3 の由来
- [ADR-0023](../adr/0023-flyway-enum-sync-verification.md) — B1.3-B1.4 の Cons 緩和策
- [release_plan.md](release_plan.md) — リリース計画
