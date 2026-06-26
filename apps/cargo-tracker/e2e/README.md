# Cargo Tracker (Haskell 版) E2E テスト

Playwright によるエンドツーエンドテスト。

## 前提

- Node.js 20+
- Cargo Tracker サーバが `http://localhost:8080` で稼働 (別ターミナルで `npm run start:dev`)
- Postgres 接続が成功し永続化が動作している状態

## セットアップ

```bash
cd apps/cargo-tracker/e2e
npm install
npx playwright install chromium
```

## 実行

```bash
npm test            # ヘッドレス
npm run test:headed # ブラウザ表示
npm run test:ui     # Playwright UI
npm run report      # HTML レポート表示
```

## ベース URL の変更

```bash
BASE_URL=http://localhost:8080 npm test
```

## シナリオ

| ファイル | 内容 |
| --- | --- |
| `home.spec.ts` | ホーム画面 / Health |
| `shipper-registration.spec.ts` | US02 個人 / US03 法人 |
| `voyage-registration.spec.ts` | US24 多区間航海 |
| `booking-registration.spec.ts` | US04 (荷主登録 → 貨物予約) |
