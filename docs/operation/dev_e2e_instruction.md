# Playwright E2E テストセットアップ手順書

## 概要

本ドキュメントは、Case Study Cargo Tracker（Scala 版）の E2E（End-to-End）テスト環境を Playwright でセットアップする手順を説明します。

E2E テストはバックエンドアプリケーション（Play Framework）が起動した状態でブラウザを自動操作し、ユーザー操作の一連のフロー（htmx の部分更新・ポーリングを含む）を検証します。

| 項目 | 内容 |
|------|------|
| テストフレームワーク | Playwright |
| 言語 | TypeScript |
| テスト対象 | `http://localhost:9000`（cargo-tracker） |
| テストディレクトリ | `apps/cargo-tracker/e2e` |
| ブラウザ | Chromium |

E2E はテストピラミッドの最上層（全体の約 5%）であり、優先シナリオは [テスト戦略](../design/test_strategy.md) の 3 件（US13 予約確定 / US15 荷役記録 / US18 追跡照会）です。

---

## 1. 前提条件

以下が完了していることを確認してください。

| 前提 | 確認方法 |
|------|---------|
| Node.js 22.x LTS | `node -v` |
| npm 10.x | `npm -v` |
| PostgreSQL が起動している | `docker compose ps` |
| cargo-tracker が起動可能 | `cd apps/cargo-tracker && sbt run` |

---

## 2. ディレクトリ構造

```
apps/cargo-tracker/e2e/
├── package.json              # Playwright 依存関係・スクリプト
├── playwright.config.ts      # Playwright 設定
├── tsconfig.json             # TypeScript 設定
└── src/
    ├── fixtures.ts           # テストフィクスチャ（ログイン等の共通処理）
    ├── helpers/
    │   └── htmx.ts           # htmx ポーリング待機ユーティリティ
    ├── pages/                # Page Object Model
    │   └── LoginPage.ts      # ログインページ操作
    └── tests/                # テストスペック
        └── auth.spec.ts      # 認証テスト
```

---

## 3. セットアップ手順

### 3.1 プロジェクトの初期化

```bash
mkdir -p apps/cargo-tracker/e2e/src/pages apps/cargo-tracker/e2e/src/tests apps/cargo-tracker/e2e/src/helpers
cd apps/cargo-tracker/e2e
```

### 3.2 package.json の作成

```json
{
  "name": "cargo-tracker-e2e",
  "version": "1.0.0",
  "description": "E2E tests for Cargo Tracker using Playwright",
  "scripts": {
    "test": "playwright test",
    "test:ui": "playwright test --ui-port=8932 --ui",
    "test:headed": "playwright test --headed",
    "test:debug": "playwright test --debug",
    "report": "playwright show-report"
  },
  "devDependencies": {
    "@playwright/test": "^1.44.0",
    "@types/node": "^20.0.0"
  }
}
```

### 3.3 依存パッケージのインストール

```bash
cd apps/cargo-tracker/e2e
npm install

# Playwright ブラウザをインストール
npx playwright install chromium
```

### 3.4 TypeScript 設定

`tsconfig.json`:

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "commonjs",
    "lib": ["ES2022"],
    "strict": true,
    "esModuleInterop": true,
    "resolveJsonModule": true,
    "outDir": "./dist",
    "rootDir": "."
  },
  "include": ["src/**/*", "playwright.config.ts"]
}
```

### 3.5 Playwright 設定

`playwright.config.ts`:

```typescript
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './src/tests',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,
  reporter: [
    ['html', { outputFolder: 'playwright-report' }],
    ['list'],
  ],
  use: {
    baseURL: process.env.BASE_URL || 'http://localhost:9000',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    locale: 'ja-JP',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
```

**主要な設定:**

| 設定 | 値 | 説明 |
|------|-----|------|
| `testDir` | `./src/tests` | テストファイルの配置先 |
| `fullyParallel` | `false` | テスト間の状態依存を考慮して直列実行 |
| `workers` | `1` | 単一ワーカーで実行 |
| `retries` | CI のみ `2` | フレイキー対策。リトライで成功したテストは flaky としてレポートに記録される |
| `baseURL` | `http://localhost:9000` | テスト対象アプリケーション URL（Play のデフォルトポート） |
| `trace` | `on-first-retry` | リトライ時にトレースを記録 |
| `screenshot` | `only-on-failure` | 失敗時のみスクリーンショット取得 |
| `video` | `retain-on-failure` | 失敗時のみ動画を保持 |

> **フレイキー対策の運用ルール**（[テスト戦略](../design/test_strategy.md)）: 同一テストが 1 週間に 2 回以上 flaky になった場合は修正タスクを起票します。待機は自動リトライ付きアサーション（`expect(...).toHaveText` 等）に限定し、固定 `sleep` は禁止です。

### 3.6 .gitignore の作成

```
node_modules/
playwright-report/
test-results/
dist/
```

---

## 4. htmx ポーリングへの対応

追跡詳細の 30 秒ポーリング（`hx-trigger="every 30s"`）をテストするため、待機ユーティリティを共通化します。テスト環境ではポーリング間隔を 5 秒に短縮します（間隔は Twirl テンプレートに設定値として注入）。

`src/helpers/htmx.ts`:

```typescript
import { Page } from '@playwright/test';

// htmx ポーリング完了を待機するユーティリティ
export async function waitForHtmxUpdate(page: Page, selector: string, timeout = 10000) {
  await page.waitForFunction(
    (sel) => {
      const el = document.querySelector(sel);
      return el && !el.hasAttribute('hx-request');
    },
    selector,
    { timeout }
  );
}
```

---

## 5. Page Object Model

テストコードの保守性を高めるため、Page Object Model（POM）パターンを採用します。画面操作を Page クラスに集約し、テストコードは Page クラスを通じて操作します。

### LoginPage の例

`src/pages/LoginPage.ts`:

```typescript
import { Page, Locator } from '@playwright/test';

export class LoginPage {
  readonly page: Page;
  readonly usernameInput: Locator;
  readonly passwordInput: Locator;
  readonly submitButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.usernameInput = page.locator('input[name="username"]');
    this.passwordInput = page.locator('input[name="password"]');
    this.submitButton = page.locator('button[type="submit"]');
  }

  async goto() {
    await this.page.goto('/login');
  }

  async login(username: string, password: string) {
    await this.goto();
    await this.usernameInput.fill(username);
    await this.passwordInput.fill(password);
    await this.submitButton.click();
  }
}
```

---

## 6. テストフィクスチャ

共通の前処理（ログイン等）はフィクスチャとして定義し、テスト間で再利用します。

`src/fixtures.ts`:

```typescript
import { test as base } from '@playwright/test';
import { LoginPage } from './pages/LoginPage';

type Fixtures = {
  loggedIn: void;
};

export const test = base.extend<Fixtures>({
  loggedIn: async ({ page }, use) => {
    const loginPage = new LoginPage(page);
    await loginPage.login('admin', 'admin');
    await use();
  },
});

export { expect } from '@playwright/test';
```

**使い方:** テストファイルで `@playwright/test` の代わりに `../fixtures` から `test` と `expect` をインポートし、`loggedIn` フィクスチャを引数に含めると、テスト実行前にログインが完了した状態になります。

---

## 7. テストの作成

### 認証テストの例

`src/tests/auth.spec.ts`:

```typescript
import { test, expect } from '../fixtures';
import { LoginPage } from '../pages/LoginPage';

test.describe('認証', () => {
  test('正しい認証情報でログインできる', async ({ page, loggedIn }) => {
    await expect(page).toHaveURL('/');
  });

  test('ログアウトできる', async ({ page, loggedIn }) => {
    await page.locator('form[action="/logout"] button[type="submit"]').click();
    await expect(page).toHaveURL(/\/login/);
  });

  test('誤った認証情報でエラーが表示される', async ({ page }) => {
    const loginPage = new LoginPage(page);
    await loginPage.login('user', 'wrongpassword');
    await expect(page.locator('.alert-danger')).toBeVisible();
  });

  test('未認証でアクセスするとログインページにリダイレクトされる', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL(/\/login/);
  });
});
```

### 公開追跡（未認証）テストの例

US18 の公開追跡ページは認証不要のため、`loggedIn` フィクスチャを使わずにテストします。

```typescript
test('追跡番号で公開照会でき、個人情報が表示されない', async ({ page }) => {
  await page.goto('/public/tracking/TRK-20260612-1234');
  await expect(page.locator('.status-badge')).toBeVisible();
  await expect(page.locator('body')).not.toContainText('荷主住所');
});
```

---

## 8. テストの実行

### 事前準備: cargo-tracker の起動

```bash
# ターミナル 1: PostgreSQL とアプリケーションの起動
docker compose up -d postgres
cd apps/cargo-tracker
sbt run
```

### テスト実行

```bash
# ターミナル 2: E2E テスト
cd apps/cargo-tracker/e2e

# 全テスト実行（ヘッドレス）
npm test

# UI モード（ブラウザ上でテスト選択・実行）
npm run test:ui

# ブラウザ表示付きで実行
npm run test:headed

# デバッグモード（ステップ実行）
npm run test:debug

# テストレポート表示
npm run report
```

### 環境変数で接続先を変更

```bash
# 別のポートや環境に接続する場合
BASE_URL=http://localhost:9001 npm test
```

---

## 9. テストレポート

テスト実行後、HTML レポートが自動生成されます。

| レポート | パス |
|---------|------|
| HTML レポート | `apps/cargo-tracker/e2e/playwright-report/index.html` |
| テスト結果 | `apps/cargo-tracker/e2e/test-results/` |

```bash
# レポートをブラウザで開く
cd apps/cargo-tracker/e2e
npm run report
```

失敗したテストにはスクリーンショット・トレース・動画が添付されます。

---

## 10. Page Object の追加ガイド

新しい画面の E2E テストを追加する際は、以下の手順に従います。

1. `src/pages/` に Page Object クラスを作成
2. 画面の主要な Locator をコンストラクタで定義
3. 画面操作をメソッドとして実装
4. `src/tests/` にテストスペックを作成
5. 必要に応じて `src/fixtures.ts` に共通フィクスチャを追加

```
src/pages/BookingPage.ts    ← Page Object
src/tests/booking.spec.ts   ← テストスペック
```

---

## 11. CI/CD 連携

GitHub Actions で E2E テストを実行する場合の設定例（ステージングデプロイ後に実行）:

```yaml
- name: Install Playwright Browsers
  working-directory: apps/cargo-tracker/e2e
  run: npx playwright install --with-deps chromium

- name: Run E2E Tests
  working-directory: apps/cargo-tracker/e2e
  run: npm test
  env:
    BASE_URL: http://localhost:9000
```

> **Note**: CI 環境では `--with-deps` オプションでシステム依存ライブラリも含めてインストールします。

---

## トラブルシューティング

### ブラウザが見つからない

**問題**: `browserType.launch: Executable doesn't exist`

**解決策**:

```bash
cd apps/cargo-tracker/e2e
npx playwright install chromium
```

### アプリケーションに接続できない

**問題**: `page.goto: net::ERR_CONNECTION_REFUSED`

**解決策**: cargo-tracker が起動していることを確認する

```bash
curl http://localhost:9000/health
```

### テストがタイムアウトする

**問題**: テストが 30 秒でタイムアウトする

**解決策**: `playwright.config.ts` にタイムアウト設定を追加

```typescript
export default defineConfig({
  timeout: 60000,        // テスト全体のタイムアウト
  expect: {
    timeout: 10000,      // expect のタイムアウト
  },
  // ...
});
```

### htmx ポーリングのテストが不安定

**問題**: ポーリング更新を待つテストがタイミングによって失敗する

**解決策**:

- `waitForHtmxUpdate` ヘルパーを使用し、固定 `sleep` を使わない
- テスト環境のポーリング間隔（5 秒）が適用されているか確認する
- それでも不安定な場合は flaky として記録し、修正タスクを起票する

---

## 関連ドキュメント

- [アプリケーション開発環境セットアップ手順書](./dev_app_instruction.md)
- [コントローラー E2E テストセットアップ手順書](./dev_e2e_api_instruction.md)
- [テスト戦略](../design/test_strategy.md)
- [技術スタック選定](../design/tech_stack.md)
