# ADR (Architecture Decision Records)

技術的意思決定を記録した ADR です。

## ADR 一覧

| ADR | 決定内容 | ステータス |
| :--- | :--- | :--- |
| [ADR-0001](ADR-0001-flix-adoption.md) | Flix の採用とバージョン固定方針 | 承認済み（2026-08-03） |
| [ADR-0002](ADR-0002-self-built-web-and-security.md) | Web・セキュリティ基盤の自作とその補償策 | 承認済み（2026-08-03） |
| [ADR-0003](ADR-0003-session-concurrency.md) | 同一利用者の同時セッション数を 1 とする | 承認済み（2026-07-31） |
| [ADR-0004](ADR-0004-public-tracking-url-scheme.md) | 公開貨物追跡の URL 設計（共有用パスとフォーム送信のクエリ） | 承認済み（2026-07-31） |
| [ADR-0005](ADR-0005-single-routing-table.md) | ルーティング表の単一化と効果の持ち上げ（`lift`・`TxMode`） | 承認済み（2026-08-03） |
| [ADR-0006](ADR-0006-fixed-point-quantities.md) | 小数を伴う量は固定小数点整数で保持する | 承認済み（2026-08-03） |
| [ADR-0007](ADR-0007-defer-external-acl-and-scope-v1.md) | 外部連携 ACL 基盤を延期し、v1.0.0 のスコープから精算を外す | 承認済み（2026-08-03） |
| [ADR-0008](ADR-0008-optimistic-locking-by-content-version.md) | 更新の衝突は内容から導いた版の照合で検出する | 承認済み（2026-08-03） |

> **承認日は、その ADR を承認した実際の日付を書く**（IT6 で方針を確定）。
> IT2-IT4 の ADR には作成日より後の日付が入っており（2026-08-28・08-31・09-16）、
> 並びが決定の順序と食い違っていた。**日付は決定の前後関係を追う唯一の手掛かり**
> であり、実物と違う値が入っていると後から経緯を辿れない。
> 作成コミットの日付に合わせて訂正した。

ADR の作成には `creating-adr` スキルを使用してください。
