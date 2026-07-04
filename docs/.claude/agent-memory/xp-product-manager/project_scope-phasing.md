---
name: project-scope-phasing
description: Cargo Tracker C# 版のスコープ段階化（初期フェーズ／IT／Phase2）の判断が複数ドキュメントに散在している状況
metadata:
  type: project
---

Cargo Tracker C# 版（take-1）の設計は、スコープ段階化の判断が単一のロードマップ文書ではなく複数ドキュメントに散在している。

- backend: 「初期フェーズではイベントソーシング不採用」「HATEOAS 不採用」
- data-model: `cargo` の `booking_amount_*`/`transport_status`/`tracking_number`/`consignee_*` は「将来追加予定（IT4+ / 各 Context 実装時）」、IT2 完了済み
- non_functional: 荷主セルフサービスポータル・個人情報 DB 暗号化・GDPR 対応・Billing イベントソーシング移行を Phase2 / 1〜2 年後に配置

**Why:** DDD 参考実装の C# 移植で、25 ユーザーストーリー（US21-23 Billing のみ優先度中、他は高）を反復的に実装している。ドメインは 8 コンテキスト（Booking/Tracking/Routing 中核、Billing/Handling/Estimation/Shipper/Shared）。
**How to apply:** スコープや優先順位の相談時は、これらの散在した段階化情報を突き合わせる。可視な「ストーリー→イテレーション」対応表（release/iteration plan）が docs 配下に未整備な点を優先課題として扱う。
