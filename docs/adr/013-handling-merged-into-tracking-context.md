# ADR-013: 荷役（Handling）機能を Tracking Context に統合する

設計ドキュメントが独立 BC として定義していた Handling を、実装では Tracking Context 内に統合する。独立 BC への分割は将来のスケール要件が顕在化してから行う。

日付: 2026-07-11

## ステータス

承認済み

## コンテキスト

設計ドキュメントと実装の間に、荷役（Handling）機能の配置に関する乖離がある（`docs/review/design_docs_review_20260711.md` 高優先度 #4）。

- 設計ドキュメント 3 件（`docs/design/domain-model.md`・`docs/design/data-model.md`・`docs/design/architecture_backend.md`）は Handling を独立した境界付けられたコンテキストとして定義している（`HandlingActivity` 集約、`handling_activity` テーブル等）。
- しかし実装には `handling` パッケージが存在せず、荷役機能は Tracking Context 内に統合されている。
  - コマンド: `tracking/application/internal/commandservices/RecordHandlingEventCommand`
  - リポジトリ Record: `tracking/infrastructure/repositories/HandlingEventRecord`
  - テーブル: `tracking_handling_event`（`V14__create_tracking_handling_event.sql`）
- 荷役イベントは貨物の追跡状態を更新する起点であり、Tracking 集約（`TrackingRecord`）と強く結合している。現状のドメインでは、荷役の記録と追跡状態の更新は同一トランザクション・同一ユースケース内で完結している。

独立 BC として分割するには、独立デプロイ・独立開発・ドメイン的に意味のある単位という要件を満たす必要がある。現時点では荷役と追跡は不可分であり、分割は結合度を上げるだけで実利がない。

## 決定

**荷役（Handling）機能は Tracking Context に統合された機能として扱う。独立 BC への分割は行わない。**

統治分割（Governed Splitting）の原則に従い、まず小さく作り、自然な切れ目（変更頻度の差、チーム境界、独立デプロイ要件）が観察できるようになってから分割を判断する。現時点では以下の理由で統合を維持する。

- 荷役イベントの記録と追跡状態の更新が同一ユースケース内で完結しており、切り離すと分散トランザクションや結果整合性の複雑さを招く。
- 独立デプロイ・独立開発の要件が顕在化していない。
- ドメイン的にも「荷役 → 追跡状態の変化」が一連の流れとして表現されている。

### 分割を再検討するトリガー

以下のいずれかが顕在化した時点で、独立 BC への分割を再評価する。

- 荷役処理と追跡処理で変更頻度・リリースサイクルが乖離する。
- 荷役処理に独立したスケール要件（高スループット、専用チーム）が生じる。
- 荷役ドメインのモデルが Tracking から独立した振る舞い・ライフサイクルを持つように成長する。

### 設計ドキュメント側の修正方針

本 ADR は決定の記録に留め、設計ドキュメント本体の修正は別途行う。修正時は以下の方針に従う。

- `docs/design/domain-model.md`: `HandlingActivity` 集約を独立 BC として記述している箇所を、Tracking Context 内の機能（荷役イベント記録が Tracking 集約を更新する）として整理する。
- `docs/design/data-model.md`: `handling_activity` テーブルの記述を実装の `tracking_handling_event` に整合させる。
- `docs/design/architecture_backend.md`: Handling を独立 BC とするコンテキスト構成図・パッケージ構成を Tracking 統合の実態に合わせる。
- `docs/design/test_strategy.md`: Handling に関するテスト記述を Tracking Context のテストとして位置づける。

### 代替案

| 代替案 | 却下理由 |
|--------|---------|
| 設計ドキュメント通り Handling を独立 BC として実装する | 荷役と追跡が不可分な現状では分散トランザクション・結果整合性の複雑さを招くだけで実利がない。無理な分割は結合度を上げる |
| 現状を放置し設計と実装の乖離を残す | 「なぜ handling パッケージがないのか」の議論が繰り返され、ドキュメントの信頼性が損なわれる |

## 影響

### ポジティブ

- 設計と実装の乖離が意思決定として明文化され、同じ議論の繰り返しを防げる。
- 荷役と追跡が同一コンテキストにあることで、荷役イベント記録と追跡状態更新をトランザクション整合性のまま単純に実装できる。
- 統治分割の判断基準（分割トリガー）が明示され、将来の分割判断が容易になる。

### ネガティブ

- Tracking Context の責務がやや広くなる。荷役ドメインが成長した際に分割コストが発生する。
- 設計ドキュメント本体の修正が別タスクとして残る（本 ADR では未実施）。

## コンプライアンス

- `handling` パッケージを新設しないこと。荷役機能は `tracking` パッケージ配下に配置する。
- 分割トリガーのいずれかが顕在化した場合は、本 ADR を Superseded とし、後続 ADR で分割の決定を記録すること。

## 備考

- 関連レビュー: `docs/review/design_docs_review_20260711.md` 高優先度 #4
- 関連 ADR: [ADR-010](010-practical-ddd-package-structure.md)（パッケージ構成の標準）
- 参考: 統治分割（Governed Splitting） — 先に小さく作り、自然な切れ目が見えてから割る
