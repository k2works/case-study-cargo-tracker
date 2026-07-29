# ADR 0005: 料金調整（減額・補償費用）の符号規約 — 一律減算

US21-6 の料金調整明細（`InvoiceLineItem`）について、種別（REDUCTION 減額 / COMPENSATION 補償費用）によらず金額を減算方向（負値）に正規化し、いずれも請求額を減らすものとして扱う。

日付: 2026-07-29

## ステータス

2026-07-29 承認されました（IT9）

## コンテキスト

- IT8 で US21-6（例外発生時の料金調整）を実装した際、`COMPENSATION`（補償費用）を請求額に**加算**していた。実装当初は「補償費用 = 荷主へ請求する追加費用」という素朴な解釈だった。
- IT8 のマルチパースペクティブレビュー（architect / user-representative）で「補償費用は遅延・破損に対し**当社が荷主へ負担するクレジット**であり、請求額を**減算**すべき。加算は会計上の意味が逆」と高優先度で指摘された。
- 業務判断として IT9 で以下を確定した（iteration_plan-9 の「業務判断」節）:
  - **REDUCTION（減額）**: 当社都合の値引き・goodwill。請求額を減算する。
  - **COMPENSATION（補償費用）**: 遅延・破損に対する当社負担クレジット。請求額を減算する。会計上、減額とは別種別として区別する。
- この変更は「同じ金額入力に対する請求総額の符号が反転する」という**ドメインの意味論的変更**であり、既存の永続データ（seed のみ）・テストの期待値に影響する。構造変更に準じる意思決定として ADR に記録する（IT9 architect レビュー高）。

## 決定

1. **符号規約を `InvoiceLineItem` に閉じる（単一の真実点）**。`normalize_sign` で種別によらず `amount` を負値（`-amount.abs`）に正規化する。アプリ層（`AdjustFreight`）・コントローラ・ビューは符号を意識しない。
2. **種別（`AdjustmentType`）は会計分類のためだけに保持**する。金額への効果（減算）は種別に依存しない。UI では種別選択の近傍に「金額への効果はどちらも減算」と明示する。
3. **`Invoice#add_adjustment` は調整後の請求総額が 0 を下回らないことを集約内で保証**する（過大減額の防止）。`remove_adjustment` は負値明細を `total` から `subtract` して戻す（取消は必ず加算方向）。
4. この不変条件はドメイン層（VO・集約）に閉じ、`docs/reference/コーディングとテストガイド.md` の設計 DoD（T44）に一般原則として明文化する。

## 影響

- `InvoiceLineItem#normalize_sign` は種別引数を取らない（デッドパラメータを除去）。
- 既存テスト（IT8 の料金調整 spec）の期待値を減算方向へ更新済み（`invoice_adjustment_spec` / `billing_invoices_spec`）。
- 永続化済みの請求は seed のみで影響は限定的。
- 将来 `COMPENSATION` を再び加算方向に戻す要件が生じた場合、本 ADR を Superseded とし、`remove_adjustment` の「取消は必ず加算」前提も併せて見直す。
- domain-model.md に `AdjustmentType`（REDUCTION / COMPENSATION・いずれも減算）を反映済み。
