# ADR 0010: 割引ポリシー管理コンテキスト（Discount Policy Context）の新設

管理者が割引方針を登録・編集する管理機能（US-ADM-01）を独立した境界付けられたコンテキストとして新設し、BC 正典を「8 コンテキスト + Shared Domain」に更新する。

日付: 2026-07-27

## ステータス

2026-07-27 承認されました（ADR-0002 を改訂）

## コンテキスト

IT8 の後続対応として、ウォーキングスケルトンでプレースホルダ表示だった 2 画面（ダッシュボード・割引ポリシー管理 `/admin/discount-policies`）を実装した。このうち割引ポリシー管理（US-ADM-01）は当初 IT8 スコープ外（iteration_plan-8 注5・data-model.md で「`discount_policy` 物理テーブルは作成しない」と明記）だったが、実装対象に含めた。

実装にあたり、次の設計判断が必要になった。

1. 割引ポリシーの管理機能を **Billing Context の既存 `DiscountPolicy`（請求時の割引率算出を担うステートレス値オブジェクト）に統合するか、独立 BC にするか**。
2. 独立 BC にする場合、ADR-0002 が「7 BC + Shared Domain」を正典として固定し、その動機が「BC 数のドリフト（5/6/7/8 の食い違い）を止めること」であるため、**無記録に 8 個目の BC を追加すると ADR-0002 の趣旨に反する**。

## 決定

1. 割引ポリシー管理を **独立した境界付けられたコンテキスト「Discount Policy Context」（`internal/discountpolicy`）** として新設する。理由:
   - 管理者が有効期間付きで割引方針を CRUD 管理するライフサイクルは、請求 1 件の整合性を守る Billing の集約とは責務・可変性の軸が異なる。
   - Billing の `DiscountPolicy`（VO・請求時の割引率算出）を可変な永続集約に変えると、テスト済みの請求フロー（ADR-0004 の 30% 上限・法人割引）に影響が及ぶ。棲み分けにより既存契約を保つ。
2. これに伴い **BC 正典を「8 コンテキスト + Shared Domain」に更新**する（Booking / Shipper / Routing / Tracking / Handling / Billing / Estimation / **Discount Policy**）。ADR-0002 決定 1 を本 ADR で改訂する。
3. 同名概念 `DiscountPolicy` が 2 つ並存することを許容し、区別を明文化する:
   - **Billing の `DiscountPolicy`**（`billing/domain`・値オブジェクト）: 請求時に荷主種別・金額から割引率を算出する。
   - **Discount Policy Context の `DiscountPolicy`**（`discountpolicy/domain`・集約ルート）: 管理者が登録・編集する割引方針（種別・割引率・有効期間）。
4. 永続化は専用テーブル `discount_policy`（migration 000018）を追加し、割引率は data-model.md と一貫して `NUMERIC(5,4)`・0.0000〜0.3000（ADR-0004 の 30% 上限を DB CHECK でも担保）とする。
5. 永続化アクセスは、当 BC のみ **sqlc 生成コードを用いず直接 pgx** で実装する（集約が単純な CRUD で sqlc の利点が薄く、スキーマも独立のため）。他 BC の sqlc 方針（ADR-0005/0006）とは意図的に異なる点を本 ADR で記録する。

## 影響

- パッケージ構成に `internal/discountpolicy/{domain,application,infrastructure,interfaces}` が加わり、`internal/` は 9 ディレクトリ（8 BC + shared）となる。
- go-arch-lint に `discountpolicy-*` コンポーネントを追加し、他 BC への直接依存禁止を CI で検証する（`make arch` green）。
- 正典 domain-model.md の BC 表・コンテキストマップ・ユビキタス言語に Discount Policy Context を追記する（本 ADR と同時反映）。
- iteration_plan-8 注5 と data-model.md の「スコープ外」記述を「IT8 後続対応でスコープ内化」に更新する。
- 当 BC の割引ポリシーを Billing の請求フローへ適用する連携は現時点では行わない（将来対応。請求時割引率は引き続き Shipper への ACL で取得する）。

## コンプライアンス

- go-arch-lint のルールで Discount Policy Context の BC 独立性（他 BC 直接参照禁止）を機械的に検証する。
- 設計レビュー時に「BC の数・名称が domain-model.md と一致しているか（8 BC + Shared）」をチェックする。

## 備考

著者: 開発チーム（Claude Code 支援）。背景は IT8 クローズのマルチパースペクティブレビュー（docs/review/it8_go_review_20260727.md、architect / technical-writer 指摘）を参照。関連: [[ADR-0002]]（改訂元）・[[ADR-0004]]（割引率上限）・[[ADR-0005]]（sqlc 方針）。
