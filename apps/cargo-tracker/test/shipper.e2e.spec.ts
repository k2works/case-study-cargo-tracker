import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import request from 'supertest';
import type { TestApp } from './test-app.js';
import { createTestApp, seedUser } from './test-app.js';
import { Role } from '../src/shared/domain/model/role.js';

describe('荷主登録フロー (US02/US03)', () => {
  let ctx: TestApp;
  let salesAgent: ReturnType<typeof request.agent>;

  async function loginAs(username: string, roles: Role[]): Promise<ReturnType<typeof request.agent>> {
    await seedUser(ctx.db, { username, password: 'secret123', roles });
    const agent = request.agent(ctx.app.getHttpServer());
    await agent.post('/login').type('form').send({ username, password: 'secret123' });
    return agent;
  }

  beforeEach(async () => {
    ctx = await createTestApp();
    salesAgent = await loginAs('sales1', [Role.SALES]);
  });

  afterEach(async () => {
    await ctx.app.close();
  });

  it('営業担当者は荷主登録画面に到達できる', async () => {
    const res = await salesAgent.get('/shippers/new');
    expect(res.status).toBe(200);
    expect(res.text).toContain('荷主登録');
    expect(res.text).toContain('荷主種別');
  });

  it('営業以外のロールは 403 で拒否される', async () => {
    const billing = await loginAs('billing1', [Role.BILLING]);
    const res = await billing.get('/shippers/new');
    expect(res.status).toBe(403);
  });

  it('個人荷主を登録できる（PRG でダッシュボードへ）', async () => {
    const res = await salesAgent
      .post('/shippers')
      .type('form')
      .send({ shipperType: 'INDIVIDUAL', name: '山田太郎', email: 'yamada@example.com' });
    expect(res.status).toBe(302);
    expect(res.headers.location).toBe('/');

    const saved = await ctx.db
      .selectFrom('shipper')
      .selectAll()
      .where('email', '=', 'yamada@example.com')
      .executeTakeFirst();
    expect(saved?.shipperCode).toMatch(/^SHP-/);
    expect(saved?.shipperType).toBe('INDIVIDUAL');
  });

  it('法人荷主を契約番号・割引率つきで登録できる', async () => {
    await salesAgent.post('/shippers').type('form').send({
      shipperType: 'CORPORATE',
      name: '株式会社サンプル',
      email: 'corp@example.com',
      contractNumber: 'CT-001',
      discountRate: '20',
    });
    const saved = await ctx.db
      .selectFrom('shipper')
      .selectAll()
      .where('email', '=', 'corp@example.com')
      .executeTakeFirst();
    expect(saved?.shipperType).toBe('CORPORATE');
    expect(Number(saved?.discountRate)).toBeCloseTo(0.2, 4);
  });

  it('割引率が 30% を超えるとエラーを表示する（境界）', async () => {
    const res = await salesAgent.post('/shippers').type('form').send({
      shipperType: 'CORPORATE',
      name: '株式会社サンプル',
      email: 'corp2@example.com',
      contractNumber: 'CT-002',
      discountRate: '31',
    });
    expect(res.status).toBe(200);
    expect(res.text).toContain('割引率');
  });

  it('htmx: 法人選択で契約フィールドが返る', async () => {
    const res = await salesAgent.get('/shippers/fields?shipperType=CORPORATE');
    expect(res.status).toBe(200);
    expect(res.text).toContain('契約番号');
    expect(res.text).toContain('割引率');
  });

  it('Email 重複時は既存登録を案内する', async () => {
    await salesAgent
      .post('/shippers')
      .type('form')
      .send({ shipperType: 'INDIVIDUAL', name: '既存', email: 'dup@example.com' });
    const res = await salesAgent
      .post('/shippers')
      .type('form')
      .send({ shipperType: 'INDIVIDUAL', name: '新規', email: 'dup@example.com' });
    expect(res.status).toBe(200);
    expect(res.text).toContain('既に登録されています');
  });
});
