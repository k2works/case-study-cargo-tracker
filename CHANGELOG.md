# CHANGELOG

すべての重要な変更はこのファイルに記録されます。
フォーマットは [Keep a Changelog](https://keepachangelog.com/ja/1.0.0/) に準拠し、
バージョン管理は [Semantic Versioning](https://semver.org/lang/ja/) に従います。

## [0.1.0] - 2026-03-31 (IT1 完了)

### Added

- **US02**: 荷主登録機能（個人荷主）
  - 荷主名・メールアドレス・電話番号を入力して荷主を登録
  - 登録完了後に荷主 ID（UUID）を発行
- **US03**: 法人荷主登録機能
  - 法人名・契約番号・割引率を追加入力して法人荷主を登録
- **US04**: 貨物予約登録機能
  - 荷主 ID・貨物仕様（種別・重量・寸法・個数・品名）・輸送条件（出発地・目的地・希望日）を入力して予約登録
  - 登録時に仮受付（PROVISIONAL）状態の予約を生成し予約番号（UUID）を発行
  - 登録完了後に `BookingRegisteredEvent` をトランザクションコミット後に発行（ADR-002 準拠）
- Spring Boot 4.x + ヘキサゴナルアーキテクチャ基盤（ポート＆アダプター）
- MyBatis + PostgreSQL（Flyway マイグレーション）
- Thymeleaf + htmx による画面（荷主名リアルタイム検索）
- Docker Compose によるローカル起動環境
- Spring Boot DevTools によるホットリロード開発環境
- ログイン画面の開発プロファイル向け自動入力

[0.1.0]: https://github.com/example/case-study-cargo-tracker-take-1/releases/tag/v0.1.0
