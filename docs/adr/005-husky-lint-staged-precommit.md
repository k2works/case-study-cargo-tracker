# ADR-005: Husky + lint-staged で pre-commit 品質チェックを実施する

コミット前に Gradle check を自動実行し、品質基準を満たさないコードのコミットを防止する。

日付: 2026-04-04

## ステータス

承認済み

## コンテキスト

コード品質を維持するため、コミット前に自動的な品質チェックを実行する仕組みが必要である。

- Java ソースファイルが変更された場合のみ Gradle check を実行したい
- チーム全員に同じチェックを適用したい
- Node.js ベースの Gulp タスクランナーが既に導入されている

## 決定

**Husky + lint-staged を導入し、Java ファイルの変更時に `./gradlew check` を自動実行する。**

### 変更箇所

- `package.json` に `husky`、`lint-staged`、`prepare` スクリプトを追加
- `.husky/pre-commit` で `npx lint-staged` を実行
- `ops/scripts/pre_commit_gradle_check.mjs` で `apps/cargo-tracker` の `./gradlew check` を実行
- `package.json` の `lint-staged` 設定で `apps/cargo-tracker/src/**/*.java` をトリガーに指定

### 代替案

| 代替案 | 却下理由 |
|--------|---------|
| Git hooks を手動設定 | チームメンバー間で設定が異なるリスク |
| CI のみでチェック | プッシュ後に失敗が判明し手戻りが発生する |
| pre-push フック | コミット単位での品質担保ができない |

## 影響

### ポジティブ

- 品質基準を満たさないコードがリポジトリに入ることを防止できる
- `npm install` で自動セットアップされるため、手動設定が不要

### ネガティブ

- Java ファイル変更時のコミットに数十秒かかる（Gradle check の実行時間）
- 緊急時は `--no-verify` でスキップ可能だが非推奨

## コンプライアンス

- Java ファイルを変更してコミットした際に Gradle check が自動実行されること
- check 失敗時にコミットがブロックされること

## 備考

- 関連コミット: `0eec9a8`
