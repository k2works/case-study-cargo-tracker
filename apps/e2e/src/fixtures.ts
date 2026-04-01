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
