---
name: review-design-open-risks
description: docs/design 設計一式（C#/.NET10/Dapper 版）レビューで検出した高リスク項目とドキュメント間不整合。実装着手前に解消すべき。
metadata:
  type: project
---

docs/design/ 設計レビュー（2026-07-04）で検出した、実装に入る前に解消すべき構造的リスク。これらは設計判断の欠落・ドキュメント間矛盾であり、実装後に発見すると手戻りが大きい。

**Why:** Dapper + 手書き SQL + DDD 集約 + SQLite/PostgreSQL 二方言 + post-commit イベントという組み合わせは、フレームワーク（EF Core/JPA）が肩代わりしていた「集約永続化・トランザクション・イベント収集」を全て手動設計する必要があるが、その設計が未記述のまま実装スケルトンだけ先行している。

**How to apply:** これらに触れるストーリー実装やリファクタリング時は、まず該当設計の空白を埋める ADR を起こしてから着手する。次回この話題が出たら現ファイルを再読して解消済みか確認する（memory は 2026-07-04 凍結）。

未解決の高リスク（実装で再確認必須）:
1. 集約の子エンティティ永続化・再構築戦略が未設計（Cargo↔Leg, Voyage↔CarrierMovement, TrackingActivity↔Events, Invoice↔LineItem）。UpdateAsync はルート表のみ更新。
2. ドメインイベントの集約内蓄積 → post-commit ディスパッチの機構が未設計。architecture_backend の code sample は `_publisher.Publish` をインラインで呼び、同ドキュメントの推奨と矛盾。Unit of Work 実体なし（repo は IDbTransaction 引数で受けるだけ）。
3. リポジトリの実行時 SQL に `NOW()` / `RETURNING` 等 PostgreSQL 方言が混入。DbUp のプロバイダ別ディレクトリはスクリプトのみ吸収し、実行時 SQL は SQLite で動かない。
4. ドキュメント間不整合: Money(decimal)@domain-model vs MoneyAmount(integer 最小通貨単位)@data-model / cargo.shipper_id(UUID) FK → shipper.id(BIGINT) 型不一致 / BookingId(string)@domain vs UUID@data-model / EntityConfiguration 命名（EF Core 残存）/ test_strategy は楽観ロック言及も schema に version 列なし。
