# Changelog

本プロジェクト（Cargo Tracker Rails 版 / ruby/take-1）の変更履歴。
[Semantic Versioning](https://semver.org/) と [Conventional Commits](https://www.conventionalcommits.org/) に従う。

## [0.2.0] - 2026-07-28

Phase 2（航路・経路・確定・通知）リリース。航海スケジュールから経路候補提示・経路確定・予約確定・通知基盤までを確立。

### Features

- 経路（旅程）を予約に紐付ける US09/US11（CargoItinerary/Leg・ROUTE_PROPOSED） (3cdbf77e)
- 予約確定・差戻し・キャンセルと通知駆動 US12/US13（ADR-0002 ドメインイベント） (96e936b8)
- 経路条件調整の再算出 US10・cargos 拡張（consignee/routing_status） (ad9f2a86)
- 通知基盤（DomainEvents・NotificationRecorder・notifications） (6eed077e)
- 経路候補算出 US08（外部 ACL・フォールバック・ADR-0004） (8ef5ffe6)
- 航海検索 US07 と航路 UI（一覧/登録/詳細/更新） (c19987c7)
- 航海スケジュール登録・更新 US24/US25（Routing Context） (380c5c9b)
- Location 共有カーネル（packs/shared）を導入 (13b8c1e9)
- 荷主 ID 入力を荷主名で選択可能に T14 (b2f38660)

### Bug Fixes

- 経路割り当て導線の維持とドメインイベント購読のテスト分離 (1dd05b07)
- IT3 レビュー高優先の対応（時系列検証・型正規化・ACL 堅牢化・導線） (48303c55)
- 業務サンプルシードを development 限定にし CI のテスト重複を解消 (3fe8cb85)
- RuboCop カスタム cop を Rails autoload 対象から除外 (75683687)
- CSS を全ビュー対応のデザインシステムに刷新 (145eea4c)

### Refactoring

- IT3 負債返済 T17（Location 配線）/T18（射影）/T19（経路候補 UX） (72dd318e)
- 予約未検出メッセージを定数化（SonarQube 指摘対応） (7fde5622)

### Documentation

- IT4 実装を設計ドキュメントに反映（7 点） (dc71eb69)
- ADR-0004 US08 経路候補の BC 帰属 (4402e817)

### Chores

- SonarQube 品質ゲートを導入（T8）・Bug 0 に是正 (60f27a29)
- 負債返済 T11（JS ドライバ）・T2（ドメイン AR 禁止 cop） (92c080fb)

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
