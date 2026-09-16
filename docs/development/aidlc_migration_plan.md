---
type: Plan
title: "AI-DLC 移行計画 - 国際貨物輸送管理システム"
description: "XP 版の成果物を AI-DLC の契約（リスク台帳・用語集・受入条件・Unit・リリース計画・ガードレール）に揃える移行計画。2026-09-16 承認済み。"
tags: [development, ai-dlc, migration]
status: stable
generated: { by: claude-code/claude-opus-5, at: 2026-09-16T10:46:23Z }
sources:
  - { resource: /reference/AI-DLC導入ガイド.md, title: AI-DLC 導入ガイド }
  - { resource: /reference/リリース・イテレーション計画ガイド_AI-DLC版.md, title: リリース・イテレーション計画ガイド（AI-DLC 版） }
  - { resource: /reference/ユースケース作成ガイド_AI-DLC版.md, title: ユースケース作成ガイド（AI-DLC 版） }
  - { resource: /requirements/user_story.md, title: ユーザーストーリー }
  - { resource: /strategy/inception-deck.md, title: インセプションデッキ }
verified:
  - { by: human:kakimomokuri, at: 2026-09-16T10:20:57Z }
---

# AI-DLC 移行計画：国際貨物輸送管理システム

## 現状

- 構成: 単一プロジェクト（`docs/<category>/` 直下）。検査は `--story-id 'US\d+'` を付けて実行する
- 検査結果（移行前）: **ERROR 2 / WARN 7**
  - ERROR: `risk_register.md` 無し、`units.md` 無し
  - WARN: 移行計画無し、`glossary.md` 無し、静的・動的モデル無し（※誤検知、後述）、受入条件 Given/When/Then 0 本（※判定語の不一致、後述）、`release_plan.md` 無し、`bolt_plan-N.md` 無し、`developing-backend/PROJECT.md` 無し
- CLAUDE.md: AI-DLC 採用節あり、ペルソナは開発ガイド（AI-DLC 版）を参照済み
- docs/reference: AI-DLC 導入ガイド・AI-DLC 版ガイド 4 本・用語集が揃い、verified 済み
- 戦略: `inception-deck.md`（フェーズ 1 MVP〜フェーズ 3 のスコープ）、`business_architecture.md`
- 要件: 要件定義書、BUC 24 件、UC 22 件、ストーリー 31 本（US01〜US31）
  - 31 本すべてに「受け入れ基準」があるがチェックリスト形式で、Given/When/Then は 0 本
  - 検査スクリプトは「受入条件」「受け入れ条件」の語で判定するため、現状は「受入条件あり 0 本」と出る
- 設計: `docs/design/` は `index.md` のみ（アーキテクチャ・ドメインモデル・データモデル等すべて未作成）
- 計画: `release_plan.md`・`iteration_plan-N.md` ともに無し。進行中のイテレーションは無い
- コード: `apps/` は `.gitkeep` のみ（グリーンフィールド）。検査スクリプトは `.gitkeep` をファイルとして数え「ブラウンフィールド」と誤判定している
- ガードレール: `.claude/skills/*/PROJECT.md` は `migrating-okf` のみ。人が AI を修正した記録として `docs/review/ドメインモデル分析_review_20260331.md` がある（`docs/journal/` は空）

## 移行ステップ

- [x] 1. CLAUDE.md に AI-DLC 採用を明記する ※対応済みのため作業なし
- [x] 2. AI に渡せる範囲（In/Out）とリスク台帳を `docs/requirements/risk_register.md` に作る ※前提は決定事項 1
- [x] 3. ドメイン用語集を `docs/design/glossary.md` に作る（要件定義書の情報モデル・状態モデル、ストーリーの語から集め、語のゆれを統一する） ※統一する語の選択は要承認
- [x] 4. 既存コードの静的・動的モデルへの昇格 ※グリーンフィールドのため対象外（`apps/` は `.gitkeep` のみ）
- [x] 5. `user_story.md` の受入条件を Given/When/Then に揃え、「参照する成果物」「確認ポイント」「AI の仮定」欄を加える。見出し語は「受入条件」に統一する ※31 本を UC の塊ごとに分けて承認を受ける。書き直せない曖昧な基準は要確認として戻す
- [x] 6. ストーリーを Unit にまとめ、依存 DAG とストーリーマップを `docs/requirements/units.md` に書き、各ストーリーに Unit 欄を加える ※Unit の境界は要承認
- [x] 7. `docs/development/release_plan.md` を作り、Unit ごとのエントロピー評価とスコープ・深さ・テスト戦略、Bolt の並びを書く ※既存が無いため新規作成。フェーズ分けはインセプションデッキに合わせる
- [x] 8. 進行中のイテレーション計画の Bolt 計画への読み替え ※進行中のイテレーションが無いため対象外。最初の Bolt（ウォーキングスケルトン）の計画は移行後に `opening-iteration` で作る
- [x] 9. ガードレールを `developing-backend`・`developing-frontend`・`planning-releases`・`analyzing-usecases`・`developing-review`・`ai-agent-guidelines` の `PROJECT.md` に書く ※技術スタック未選定のため、実装パス・既存実装の参照先は「未定」とし、TDD の三原則・計画の置き場所・確認必須の操作・レビュー指摘から写す規律を中心にする
- [x] 10. 作成・変更した文書に `apply-okf` を適用し、`index.md`・`log.md` を更新して `--check` で検証する（目標: ERROR 0）

## 結果（2026-09-16）

- 検査結果: 移行前 **ERROR 2 / WARN 7** → 移行後 **ERROR 0 / WARN 2**（`--story-id 'US\d+'`）
- 残った WARN: 静的・動的モデル（`apps/.gitkeep` による誤検知）、`bolt_plan-N.md` 無し（進行中のイテレーションが無いため対象外）
- 成果物: `risk_register.md`・`glossary.md`・`units.md`・`release_plan.md` を新規作成し、`user_story.md` の受入条件 31 本を Given/When/Then 化。ガードレールとして 6 スキルの `PROJECT.md` を `.claude/skills/` と `.agents/skills/` に追加
- OKF: 作成・変更した文書にフロントマターと `verified` を付与し、各 `index.md` と `log.md` を更新。`okf_apply.py apply --changed` は移行前からの未コミットの変更（`docs/reference/` の AI-DLC 版ガイドなど）も対象にするため使わず、文書ごとに適用した

## 検査との対応

| 検査項目（移行前） | 埋めるステップ |
| :--- | :--- |
| ERROR `risk_register.md` 無し | 2 |
| ERROR `units.md` 無し | 6 |
| WARN `glossary.md` 無し | 3 |
| WARN 受入条件 Given/When/Then 0 本 | 5 |
| WARN `release_plan.md` 無し | 7 |
| WARN `bolt_plan-N.md` 無し | 8（対象外として WARN を残す） |
| WARN `developing-backend/PROJECT.md` 無し | 9 |
| WARN 移行計画無し | 本計画 |
| WARN 静的・動的モデル無し | 4（誤検知として WARN を残す） |

## AI の仮定

- 本リポジトリはケーススタディ（架空の国際貨物輸送会社）であり、本番データ・本番の鍵・実在する決済情報は存在しない（決定事項 1 で確認済み）
- `docs/article/source/` の多言語実装は記事の参照元であり、本プロジェクトの `apps/` ではないため移行対象に含めない
- 検査スクリプトの誤判定（`.gitkeep` によるブラウンフィールド判定、「受け入れ基準」の語を数えない）は booster 管理のスクリプト側の課題として扱い、本移行では修正しない。ステップ 5 で見出し語を「受入条件」に揃えることで後者は解消する
- Unit 候補はインセプションデッキのスコープ（予約・経路設計・追跡・荷役・通知・精算）と既存レビューの 5 コンテキスト（Booking / Routing / Tracking / Handling / Shared）が起点になる。境界はステップ 6 のゲートで判断する

## 決定事項（2026-09-16 承認時）

1. **AI に渡せる範囲**: ケーススタディのため制約なし。本番データ・鍵は存在せず、`docs/article/source/` の OSS 参照実装も AI のコンテキストに使ってよい
2. **フロントエンド**: 実装する予定あり。ステップ 9 に `developing-frontend/PROJECT.md` を含める
3. **文書間の不整合**: 移行中に直す。件数・description は該当ステップで修正し、US29（通関申告）とスコープ外「税関申告システム」の関係はステップ 5 の要確認として戻す

## 要確認

- 既存レビュー（ドメインモデル分析）の「高」優先度の指摘（`TransportStatus` 5 値と要件定義の 9 状態の乖離など）をガードレールとして写すか、設計ステップの入力に留めるか（ステップ 9 で判断）
