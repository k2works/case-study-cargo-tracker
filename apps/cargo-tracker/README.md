# Cargo Tracker（Rails 版）

国際貨物輸送管理システム。DDD + ヘキサゴナル + CQRS を Rails 8 + Packwerk（packs）で実装する。

## 技術スタック

- Ruby 3.4.4 / Rails 8.0（`.ruby-version` 参照）
- PostgreSQL 16（Docker Compose）
- Packwerk（Bounded Context ごとの pack・依存/公開面の静的検証）
- RSpec + Capybara（system spec）/ RuboCop / Brakeman / bundler-audit / SimpleCov

## セットアップ

```bash
# リポジトリルートで PostgreSQL を起動（ポート 5433）
docker compose up -d postgres

cd apps/cargo-tracker
bundle install
bin/rails db:prepare   # DB 作成・マイグレーション
bin/rails db:seed      # 開発用の利用者を投入
bin/rails server       # http://localhost:3000
```

## 動作確認用アカウント（開発環境）

`bin/rails db:seed` で 5 ロールの利用者を投入する。パスワードはすべて `password123`。
ログイン画面（開発環境のみ）にも一覧が表示され、既定で `sales` が入力される。

| 利用者 ID | ロール | 主な入口画面 |
|:--|:--|:--|
| `sales` | 営業担当者 | 荷主登録・貨物予約・見積・航路管理 |
| `handler` | 荷役作業員 | 荷役管理 |
| `tracker` | 追跡管理者 | 貨物追跡・例外管理・荷役管理 |
| `billing` | 経理担当者 | 請求管理 |
| `admin` | 管理者 | 管理設定（割引ポリシー） |

## テスト・品質チェック

```bash
bundle exec rspec                        # 全テスト
bundle exec rubocop                      # スタイル・静的解析
bundle exec brakeman -q --no-pager       # セキュリティ静的解析
bundle exec bundler-audit check --update # 依存脆弱性監査
bin/packwerk check                       # BC 依存/公開面の検証
```

## アーキテクチャ（packs）

Bounded Context ごとに `packs/<context>/app/{domain,application,infrastructure,public}` を配置する。

- `domain/` — Rails 非依存の PORO 集約・値オブジェクト・ポート（抽象）
- `application/` — ユースケース（コマンド/クエリ）
- `infrastructure/` — Active Record アダプタ・ACL アダプタ
- `public/` — パック外へ公開する API（`enforce_privacy` で内部を隠蔽）

認証・認可（`has_secure_password` + セッション + 5 ロール RBAC）は横断的共通基盤として
メインアプリ（`app/models`・`app/controllers`・`app/services`）に置く。

BC 間参照は公開 API と ACL 経由に限定する（ADR-0001/0003）。例: Booking → Shipper は
`ShipperExistenceChecker` ACL が `Shipper::Public::ShipperDirectory` を呼ぶ。

## 実装状況

- IT1: 認証（US26/US27）・荷主登録（US02/US03）・全ルートのプレースホルダ骨格
- IT2: 貨物予約（US04/US05/US06）・Booking Context・Shipper への ACL 境界

詳細は `docs/development/`（リリース計画・イテレーション計画・完了報告書）を参照。
