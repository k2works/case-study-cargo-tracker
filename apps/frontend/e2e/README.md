# フロントエンド E2E テスト（Playwright）

US-UI-r で構築された Playwright ベースの E2E テスト群です。IT3 以降のフロントエンド改修でリグレッションを検知するためのセーフティネットとして機能します。

## 実行前提

E2E テストは実バックエンドに対して動作します。**事前に以下のサービスを起動してください**。

| サービス | ポート | 起動方法 |
| :--- | :--- | :--- |
| Axon Server | 8024/8124 | `docker compose -f apps/docker-compose.yml up -d axonserver` |
| PostgreSQL | 5432 | `docker compose -f apps/docker-compose.yml up -d postgresql` |
| authms | 8081 | `./gradlew :authms:bootRun` |
| bookingms | 8082 | `./gradlew :bookingms:bootRun` |
| gatewayms | 8080 | `./gradlew :gatewayms:bootRun` |

`apps/docker-compose.yml` に authms / gatewayms / frontend が含まれていない理由は、IT2 時点でフル統合が未完了のため。IT3 で完全な docker compose 統合を予定。

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

## 失敗時の調査

- 失敗時は `playwright-report/` にレポート、`test-results/` にスクリーンショット・動画・トレースが残ります（`.gitignore` 済み）。
- `npm run test:e2e:report` で HTML レポートを開いて確認できます。
- 401 や接続エラーが出る場合はバックエンドサービスが起動していない可能性が高いです（`/api/v1/auth/login` がプロキシで authms に到達しているか確認）。

## 既知の制約

- **CI 統合は未完了**: IT2 では時間枠の都合で GitHub Actions ワークフローは追加していません。IT3 で `apps/docker-compose.yml` の authms/gatewayms 組み込みと併せて CI 化する予定。
- **シナリオ 1 件のみ**: IT2 では最小限のセーフティネットとして 1 シナリオに絞っています。US04 / US24 が実装された IT3 以降で追加シナリオを増やします。
