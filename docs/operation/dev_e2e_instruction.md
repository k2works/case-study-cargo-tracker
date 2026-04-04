# Playwright E2E テストセットアップ手順書

## 概要

本ドキュメントは、Case Study Cargo Tracker の E2E（End-to-End）テスト環境を Playwright でセットアップする手順を説明します。

E2E テストはバックエンドアプリケーション（Spring Boot）が起動した状態でブラウザを自動操作し、ユーザー操作の一連のフローを検証します。

| 項目 | 内容 |
|------|------|
| テストフレームワーク | Playwright |
| 言語 | TypeScript |
| テスト対象 | `http://localhost:8080`（cargo-tracker） |
| テストディレクトリ | `apps/e2e` |
| ブラウザ | Chromium |

---

## 1. 前提条件

以下が完了していることを確認してください。

| 前提 | 確認方法 |
|------|---------|
| Node.js 22.x LTS | `node -v` |
| npm 10.x | `npm -v` |
| cargo-tracker が起動可能 | `cd apps/cargo-tracker && ./gradlew bootRun` |

---

## 2. ディレクトリ構造

```
apps/e2e/
├── package.json              # Playwright 依存関係・スクリプト
├── playwright.config.ts      # Playwright 設定
├── tsconfig.json             # TypeScript 設定
└── src/
    ├── fixtures.ts           # テストフィクスチャ（ログイン等の共通処理）
    ├── pages/                # Page Object Model
    │   └── LoginPage.ts      # ログインページ操作
    └── tests/                # テストスペック
        └── auth.spec.ts      # 認証テスト
```

---

## 3. セットアップ手順

### 3.1 プロジェクトの初期化

```bash
mkdir -p apps/e2e/src/pages apps/e2e/src/tests
cd apps/e2e
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
cd apps/e2e
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
    baseURL: process.env.BASE_URL || 'http://localhost:8080',
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
| `baseURL` | `http://localhost:8080` | テスト対象アプリケーション URL |
| `trace` | `on-first-retry` | リトライ時にトレースを記録 |
| `screenshot` | `only-on-failure` | 失敗時のみスクリーンショット取得 |
| `video` | `retain-on-failure` | 失敗時のみ動画を保持 |

### 3.6 .gitignore の作成

```
node_modules/
playwright-report/
test-results/
dist/
```

---

## 4. Page Object Model

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

## 5. テストフィクスチャ

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

## 6. テストの作成

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
    await expect(page).toHaveURL('/login?logout');
  });

  test('誤った認証情報でエラーが表示される', async ({ page }) => {
    const loginPage = new LoginPage(page);
    await loginPage.login('user', 'wrongpassword');
    await expect(page).toHaveURL('/login?error');
  });

  test('未認証でアクセスするとログインページにリダイレクトされる', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL(/\/login/);
  });
});
```

---

## 7. テストの実行

### 事前準備: cargo-tracker の起動

```bash
# ターミナル 1: アプリケーション起動
cd apps/cargo-tracker
./gradlew bootRun
```

### テスト実行

```bash
# ターミナル 2: E2E テスト
cd apps/e2e

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
BASE_URL=http://localhost:9080 npm test
```

---

## 8. テストレポート

テスト実行後、HTML レポートが自動生成されます。

| レポート | パス |
|---------|------|
| HTML レポート | `apps/e2e/playwright-report/index.html` |
| テスト結果 | `apps/e2e/test-results/` |

```bash
# レポートをブラウザで開く
cd apps/e2e
npm run report
```

失敗したテストにはスクリーンショット・トレース・動画が添付されます。

---

## 9. Page Object の追加ガイド

新しい画面の E2E テストを追加する際は、以下の手順に従います。

1. `src/pages/` に Page Object クラスを作成
2. 画面の主要な Locator をコンストラクタで定義
3. 画面操作をメソッドとして実装
4. `src/tests/` にテストスペックを作成
5. 必要に応じて `src/fixtures.ts` に共通フィクスチャを追加

```
src/pages/ShipperPage.ts    ← Page Object
src/tests/shipper.spec.ts   ← テストスペック
```

---

## 10. CI/CD 連携

GitHub Actions で E2E テストを実行する場合の設定例:

```yaml
- name: Install Playwright Browsers
  working-directory: apps/e2e
  run: npx playwright install --with-deps chromium

- name: Run E2E Tests
  working-directory: apps/e2e
  run: npm test
  env:
    BASE_URL: http://localhost:8080
```

> **Note**: CI 環境では `--with-deps` オプションでシステム依存ライブラリも含めてインストールします。

---

## トラブルシューティング

### ブラウザが見つからない

**問題**: `browserType.launch: Executable doesn't exist`

**解決策**:

```bash
cd apps/e2e
npx playwright install chromium
```

### アプリケーションに接続できない

**問題**: `page.goto: net::ERR_CONNECTION_REFUSED`

**解決策**: cargo-tracker が起動していることを確認する

```bash
curl http://localhost:8080/actuator/health
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

---

## 関連ドキュメント

- [アプリケーション開発環境セットアップ手順書](./dev_app_instrunction.md)
- [テスト戦略](../design/test_strategy.md)
- [技術スタック選定](../design/tech_stack.md)
