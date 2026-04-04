# ADR-009: GitHub Actions で Build & Test + E2E の 2 ジョブ CI を構成する

GitHub Actions で Backend ビルド・テストと Playwright E2E テストを 2 ジョブ構成で自動実行する。

日付: 2026-04-04

## ステータス

承認済み

## コンテキスト

コードの変更に対して自動的にビルド・テスト・品質チェックを実行する CI パイプラインが必要である。

- `apps/cargo-tracker/` と `apps/e2e/` の変更を検知して自動実行したい
- E2E テストはバックエンドのビルド・テスト成功後に実行したい
- テスト失敗時にレポートをアーティファクトとして保存したい

## 決定

**GitHub Actions で `build` ジョブと `e2e` ジョブの 2 段構成 CI を構築する。`e2e` は `build` 成功後に実行する。**

### 変更箇所

- `.github/workflows/ci.yml` を新規作成
- `build` ジョブ: JDK 25 + Gradle ビルド・テスト
- `e2e` ジョブ: Spring Boot 起動 → Node.js + Playwright インストール → E2E テスト実行
- トリガー: `java/take-2` ブランチへの push / PR で `apps/cargo-tracker/**`、`apps/e2e/**` の変更時

### 代替案

| 代替案 | 却下理由 |
|--------|---------|
| 単一ジョブで全テスト実行 | E2E テスト失敗時にビルド・テスト結果が分かりにくい |
| E2E テストを別ワークフローに分離 | ワークフロー間の依存管理が複雑 |
| CircleCI / GitLab CI | GitHub リポジトリとの統合の容易さを優先 |

## 影響

### ポジティブ

- ビルド失敗時は E2E をスキップし、フィードバックが高速
- テスト失敗時にレポート（test-reports / playwright-report）がアーティファクトとして 7 日間保存される
- Gradle キャッシュにより 2 回目以降の実行が高速化

### ネガティブ

- E2E ジョブで Spring Boot 起動待ちに最大 2.5 分（30 回 × 5 秒のポーリング）かかる
- Playwright ブラウザのインストールに時間がかかる

## コンプライアンス

- `java/take-2` ブランチへの push で CI が自動実行されること
- build ジョブ・e2e ジョブが共に成功すること

## 備考

- 関連コミット: `4db8fca`
