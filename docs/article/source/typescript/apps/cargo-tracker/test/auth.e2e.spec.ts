import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import request from 'supertest';
import type { TestApp } from './test-app.js';
import { createTestApp, seedUser } from './test-app.js';
import { Role } from '../src/shared/domain/model/role.js';

describe('認証フロー (US26/US27)', () => {
  let ctx: TestApp;
  let agent: ReturnType<typeof request.agent>;

  beforeEach(async () => {
    ctx = await createTestApp();
    agent = request.agent(ctx.app.getHttpServer());
    await seedUser(ctx.db, { username: 'sales1', password: 'secret123', roles: [Role.SALES] });
  });

  afterEach(async () => {
    await ctx.app.close();
  });

  it('ログイン画面を表示できる', async () => {
    const res = await agent.get('/login');
    expect(res.status).toBe(200);
    expect(res.text).toContain('ログイン');
    expect(res.text).toContain('利用者 ID');
  });

  it('正しい資格情報でログインしダッシュボードへ遷移する', async () => {
    const login = await agent
      .post('/login')
      .type('form')
      .send({ username: 'sales1', password: 'secret123' });
    expect(login.status).toBe(302);
    expect(login.headers.location).toBe('/');

    const dash = await agent.get('/');
    expect(dash.status).toBe(200);
    expect(dash.text).toContain('ダッシュボード');
    expect(dash.text).toContain('営業担当者');
  });

  it('資格情報不一致でエラーメッセージを表示する', async () => {
    const login = await agent
      .post('/login')
      .type('form')
      .send({ username: 'sales1', password: 'wrong' });
    expect(login.status).toBe(302);

    const page = await agent.get('/login');
    expect(page.text).toContain('利用者 ID またはパスワードが正しくありません');
  });

  it('未認証でダッシュボードにアクセスするとログイン画面へ誘導される', async () => {
    const res = await request(ctx.app.getHttpServer()).get('/');
    expect(res.status).toBe(302);
    expect(res.headers.location).toBe('/login');
  });

  it('無効化アカウントはログインできず案内メッセージを表示する', async () => {
    await seedUser(ctx.db, {
      username: 'disabled1',
      password: 'secret123',
      roles: [Role.SALES],
      enabled: false,
    });
    await agent.post('/login').type('form').send({ username: 'disabled1', password: 'secret123' });
    const page = await agent.get('/login');
    expect(page.text).toContain('無効化されています');
  });

  it('5回連続失敗でアカウントがロックされる', async () => {
    for (let i = 0; i < 5; i++) {
      await agent.post('/login').type('form').send({ username: 'sales1', password: 'wrong' });
    }
    const page = await agent.get('/login');
    expect(page.text).toContain('ロック');

    // ロック後は正しいパスワードでもログイン不可
    const retry = await agent
      .post('/login')
      .type('form')
      .send({ username: 'sales1', password: 'secret123' });
    expect(retry.headers.location).toBe('/login');
  });

  it('ログアウトでセッションが破棄されログイン画面に戻る', async () => {
    await agent.post('/login').type('form').send({ username: 'sales1', password: 'secret123' });
    const logout = await agent.post('/logout');
    expect(logout.status).toBe(302);
    expect(logout.headers.location).toBe('/login');

    const dash = await agent.get('/');
    expect(dash.headers.location).toBe('/login');
  });
});
