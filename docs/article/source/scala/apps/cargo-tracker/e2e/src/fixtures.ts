import { test as base } from '@playwright/test';
import { LoginPage } from './pages/LoginPage';

type Fixtures = {
  loggedIn: void;
};

const ADMIN_USER = process.env.E2E_USER || 'admin';
const ADMIN_PASSWORD = process.env.E2E_PASSWORD || 'Adm1nPass!';

export const test = base.extend<Fixtures>({
  loggedIn: async ({ page }, use) => {
    const loginPage = new LoginPage(page);
    await loginPage.login(ADMIN_USER, ADMIN_PASSWORD);
    await use();
  },
});

export { expect } from '@playwright/test';
