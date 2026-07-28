# Changelog

本プロジェクト（Cargo Tracker Rails 版 / ruby/take-1）の変更履歴。
[Semantic Versioning](https://semver.org/) と [Conventional Commits](https://www.conventionalcommits.org/) に従う。

## [0.1.0] - 2026-07-28

Phase 1（予約基盤）リリース。認証・荷主登録・貨物予約の基盤を確立。

### Features

- 貨物予約 US04/US05/US06（Booking Context・Shipper への ACL 境界） (5c1f4890)
- 画面遷移図に沿った全ルートのプレースホルダ画面（ウォーキングスケルトン） (53e92bb0)
- シード利用者とログイン画面のデフォルト入力 (44d4b842)
- 荷主登録 US02/US03（ヘキサゴナル PORO） (47e8a696)
- ウォーキングスケルトンと認証 US26/US27（5 ロール RBAC・アカウントロック） (6a31a545)

### Bug Fixes

- IT2 レビュー高優先の対応（アトミック遷移・例外露出・設計同期） (0ff4e816)
- IT1 レビュー高優先の修正（重複競合・割引率必須・監査ログ検証） (748e3157)
- スタイルシートの読み込み修正とアプリ全体の CSS 追加 (179a0934)
- data-model を正典に整合（割引率 30%・5 ロール・住所カラム） (145c2677)

### Refactoring

- DIP 回復・公開 API・Packwerk privacy で BC 境界を確立（T1/T3/T4） (d8edb8cf)

### Chores / CI

- CI 修正（packwerk-extensions）と負債返済（T5/T9） (8c1d9831)
- workflow_dispatch を追加し take ブランチで手動実行可能に (6e9495e1)
- loofah/rails-html-sanitizer を脆弱性修正版へ更新 (7ed666f8)
- Backend CI ワークフローを構築 (a5868519)
- pre-commit フック（Husky + lint-staged）を追加 (6c992834)
- Rails 8 アプリケーション開発環境を構築 (f072764b)

### 品質指標

- RSpec 151 examples, 0 failures / カバレッジ Line 96.07% / Branch 82.99%
- RuboCop・Brakeman・bundler-audit・Packwerk（privacy）すべてクリーン
- Backend CI success

[0.1.0]: https://github.com/k2works/case-study-cargo-tracker/releases/tag/ruby%2Ftake-1%2Fv0.1.0
