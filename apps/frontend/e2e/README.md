# フロントエンド E2E テスト（Playwright）

US-UI-r で構築された Playwright ベースの E2E テスト群です。IT2 で 3 シナリオ（ログイン → 荷主登録 / 予約登録 / 航海登録）が稼働しており、IT3 以降の改修でリグレッションを検知するセーフティネットとして機能します。

## 実行前提

E2E テストは実バックエンドに対して動作します。**事前に以下のサービスを起動してください**。

### 推奨：local-docker プロファイルでフル起動

```bash
# Axon Server / PostgreSQL / authms / bookingms / routingms / gatewayms を一括起動
gulp local-docker:up
```

`apps/docker-compose.yml` に authms / bookingms / routingms / gatewayms / Axon Server / PostgreSQL が含まれており、`gulp local-docker:up` 一発で稼働します（IT2 完了時点の構成、ADR-0009 で `STANDALONE_DCB=true` を設定済み）。

| サービス | ポート | 備考 |
| :--- | :--- | :--- |
| Axon Server | 8024 / 8124 | `STANDALONE_DCB=true` で起動（ADR-0009） |
| PostgreSQL | 5432 | `auth_db / booking_read_db / routing_read_db` 等を自動作成 |
| authms | 8081 | JWT 発行・検証 |
| bookingms | 8082 | Cargo Aggregate + Read Model |
| routingms | 8083 | Voyage Aggregate + Read Model |
| gatewayms | 8080 | API Gateway（JWT フィルター） |

### 代替：bootRun で個別起動

特定のサービスを IDE デバッグしたい場合は `./gradlew :<service>:bootRun` で起動します。その場合は `local-h2` プロファイルで Axon Server を使わない構成になるため、E2E は `local-docker` を推奨。

Vite dev サーバー（:3000）は `playwright.config.ts` の `webServer` で自動起動します（手動起動不要）。

## テストデータ

V005 マイグレーションで投入される `admin` ユーザー（パスワード: `password`）を使ってログインします。シナリオ内で生成する荷主は `Date.now()` ベースの一意な名前・メールを使うため、繰り返し実行しても重複登録は発生しません。

## 実行コマンド

```bash
# 初回のみ: ブラウザバイナリのインストール
npx playwright install chromium

# 全テスト実行（CLI レポート）
npm run test:e2e

# UI モードでデバッグ
npm run test:e2e:ui

# 最後の実行レポートを表示
npm run test:e2e:report
```

## シナリオ一覧

| ファイル | シナリオ | 関連 US |
| :--- | :--- | :--- |
| `login-shipper.spec.ts` | ログイン → 個人荷主登録 → 一覧表示 | US00（ログイン）+ US02（荷主登録） |
| `login-booking.spec.ts` | ログイン → 貨物予約登録 → 一覧表示 | US00 + US04（貨物予約） |
| `login-voyage.spec.ts` | ログイン → 航海スケジュール新規登録 → 一覧表示 | US00 + US24（航海登録） |

IT3 以降の追加予定: US01 見積（S03/S04）、US06 予約引き渡し（S10）、US07 航海検索（S11）、US25 既存航海更新（S12 編集 URL）。

## 失敗時の調査

- 失敗時は `playwright-report/` にレポート、`test-results/` にスクリーンショット・動画・トレースが残ります（`.gitignore` 済み）。
- `npm run test:e2e:report` で HTML レポートを開いて確認できます。
- 401 や接続エラーが出る場合はバックエンドサービスが起動していない可能性が高いです（`/api/v1/auth/login` がプロキシで authms に到達しているか確認）。

## 既知の制約

- **CI 統合は未完了**: IT2 時点では GitHub Actions ワークフローへの組み込みは見送り。IT3 以降で local-docker をベースとした CI ジョブを検討する。
- **シナリオの拡充は IT3 以降**: IT2 で 3 シナリオ（荷主 / 予約 / 航海）まで整備。US01 / US06 / US07 / US25 等は IT3 以降で順次追加する。
