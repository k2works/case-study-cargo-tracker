# ADR 0002: 境界付けられたコンテキストの構成と正典の定義

BC の構成を「7 コンテキスト + Shared Domain」とし、正典をドメインモデル設計に定める。

日付: 2026-07-11

## ステータス

2026-07-11 承認されました

## コンテキスト

Go 版設計ドキュメントの並列作成により、境界付けられたコンテキストの数がドキュメント間で 5 / 6 / 7 / 8 と食い違う不整合が発生した（設計レビュー 2026-07-11 の重要度「高」指摘 #1）。原因は (1) Shipper・Estimation Context の反映漏れ、(2) Shared Domain を BC として数えるか否かの基準の曖昧さ、の 2 点である。BC 一覧が食い違うと `internal/<context>/` のパッケージ構成、go-arch-lint のルール、DI の組み立てがすべてぶれる。

## 決定

1. システムは **7 つの境界付けられたコンテキスト**（Booking / Shipper / Routing / Tracking / Handling / Billing / Estimation）と **共有ドメイン（Shared Domain）** で構成する。
2. Shared Domain は境界付けられたコンテキストではなく、共有カーネル（Location・ShipperId・TransportStatus・RoutingStatus）の置き場と位置づける。数え方は常に「7 BC + Shared Domain」と表記する。
3. BC の定義・一覧の**正典（Single Source of Truth）は docs/design/domain-model.md** とし、他の設計ドキュメントはこれに従う。
4. Estimation Context は独立コンテキストであり、見積→予約の引き継ぎは将来対応とする（現時点では他コンテキストとイベント連携しない）。
5. Booking → Shipper の参照は `ShipperExistenceChecker` ACL ポート経由に限定する。

## 影響

- パッケージ構成は `internal/{booking,shipper,routing,tracking,handling,billing,estimation,shared}` の 8 ディレクトリとなる。
- go-arch-lint のコンポーネント定義は 7 BC + shared の単位で行い、BC 間の直接 import 禁止を CI で検証する。
- BC の追加・分割・統合を行う場合は、まず domain-model.md を改訂し、その後に他ドキュメントへ波及させる手順とする。

## コンプライアンス

- go-arch-lint のルールで BC 間直接参照禁止を機械的に検証する。
- 設計レビュー時に「BC の数・名称が domain-model.md と一致しているか」をチェック項目とする。

## 備考

著者: 開発チーム（Claude Code 支援）。背景は docs/review/design_go_review_20260711.md 改善提案 #1 を参照。
