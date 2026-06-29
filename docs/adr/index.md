# ADR (Architecture Decision Records)

技術的意思決定を記録した ADR です。

## ADR 一覧

| ADR | 決定内容 | ステータス |
| :--- | :--- | :--- |
| [0001](0001-haskell-servant-stack.md) | Haskell 版バックエンドスタックとして Servant + Warp を採用 | 承認 (2026-06-26) |
| [0002](0002-arch-check-implementation.md) | アーキテクチャ規約検査ツール `arch-check` の自作実装 (HLint + 自作 AST 解析のハイブリッド) | 承認 (2026-06-26) |
| [0004](0004-cross-bc-shipper-ref.md) | Cross-BC 参照に `ShipperRef` VO を導入 (ALLOWLIST 解消 / IT3 U-05 Phase 1 完了) | 採用 (2026-06-29) |
| [0005](0005-bounded-context-error-types.md) | BC 固有エラーを `Cargotracker.<BC>.Domain.Error` に分離 (Phase 1 IT3 起票) | 提案 (2026-06-29) |

ADR の作成には `creating-adr` スキルを使用してください。
