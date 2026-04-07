import { test as base, request as apiRequest } from '@playwright/test';
import { LoginPage } from './pages/LoginPage';

const BASE_URL = 'http://localhost:8080';

type Fixtures = {
  loggedIn: void;
};

export const test = base.extend<Fixtures>({
  loggedIn: async ({ page }, use) => {
    const ctx = await apiRequest.newContext({ baseURL: BASE_URL });
    await ctx.post('/api/test/reset');
    await ctx.dispose();

    const loginPage = new LoginPage(page);
    await loginPage.login('admin', 'admin');
    await use();
  },
});

export { expect } from '@playwright/test';
