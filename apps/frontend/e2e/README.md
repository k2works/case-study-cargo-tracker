# フロントエンド E2E テスト（Playwright）

Playwright ベースの E2E テスト群です。IT1 で認証（US00）と航海スケジュールナビゲーション（US24/US25）の UI 動作を確認します。

## 実行前提

E2E テストは実バックエンドに対して動作します。**事前に以下のサービスを起動してください**。

| サービス | ポート | 備考 |
| :--- | :--- | :--- |
| gatewayms | 8080 | API Gateway |
| authms | 8081 | JWT 発行・検証 |
| bookingms | 8082 | 荷主・予約 API（IT2 で追加） |
| routingms | 8083 | 航海スケジュール API |

```bash
# local-h2 プロファイルで各サービスを起動
./gradlew :authms:bootRun
./gradlew :bookingms:bootRun
./gradlew :routingms:bootRun
./gradlew :gatewayms:bootRun
```

Vite dev サーバー（:5173）は `playwright.config.ts` の `webServer` で自動起動します。

## テストデータ

authms の `schema.sql` 初期データとして `admin` ユーザー（パスワード: `password`、ロール: `ROLE_ADMIN`）が存在している必要があります。

## 実行コマンド

```bash
# 初回のみ: ブラウザバイナリのインストール
npx playwright install chromium

# 全テスト実行
npm run e2e

# UI モードでデバッグ
npm run e2e:ui

# 最後の実行レポートを表示
npm run e2e:report
```

## シナリオ一覧

| ファイル | シナリオ | 関連 US |
| :--- | :--- | :--- |
| `login.spec.ts` | ログイン・ログアウト・未認証リダイレクト・エラー表示 | US00（認証） |
| `login-voyage.spec.ts` | ログイン → 航海スケジュールメニュー・画面遷移確認 | US24/US25（航海スケジュール） |
| `booking.spec.ts` | 個人/法人荷主登録・割引率エラー、一般/危険物/冷凍貨物予約登録・条件フィールド表示・必須エラー | US02/US03/US04/US05（荷主・予約） |

## IT3 以降の追加予定

- 追跡情報照会・荷役記録の各機能シナリオを順次追加
