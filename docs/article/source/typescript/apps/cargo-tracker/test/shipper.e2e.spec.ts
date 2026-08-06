import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import request from 'supertest';
import type { TestAgent, TestApp } from './test-app.js';
import { createTestApp, loginAsTestUser } from './test-app.js';
import { Role } from '../src/shared/domain/model/role.js';

describe('荷主登録フロー (US02/US03)', () => {
  let ctx: TestApp;
  let salesAgent: TestAgent;

  beforeEach(async () => {
    ctx = await createTestApp();
    salesAgent = await loginAsTestUser(ctx, { username: 'sales1', roles: [Role.SALES] });
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
    const billing = await loginAsTestUser(ctx, { username: 'billing1', roles: [Role.BILLING] });
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

  it('住所・連絡先を入力して保存できる（US02 受入基準）', async () => {
    await salesAgent.post('/shippers').type('form').send({
      shipperType: 'INDIVIDUAL',
      name: '佐藤花子',
      email: 'sato@example.com',
      phone: '03-1234-5678',
      address: '東京都千代田区丸の内 1-1-1',
    });
    const saved = await ctx.db
      .selectFrom('shipper')
      .selectAll()
      .where('email', '=', 'sato@example.com')
      .executeTakeFirst();
    expect(saved?.phone).toBe('03-1234-5678');
    expect(saved?.address).toBe('東京都千代田区丸の内 1-1-1');
  });

  it('登録完了メッセージに発行された荷主 ID が表示される（US02 受入基準）', async () => {
    await salesAgent
      .post('/shippers')
      .type('form')
      .send({ shipperType: 'INDIVIDUAL', name: '鈴木一郎', email: 'suzuki@example.com' });
    // PRG 後のダッシュボードでフラッシュに荷主 ID が含まれる
    const dash = await salesAgent.get('/');
    expect(dash.text).toMatch(/荷主 ID: SHP-[0-9a-f]{8}/);
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
    expect(res.text).toContain('として登録されています');
    // 既存荷主 ID（荷主コード）が提示される（US02 受入基準）
    expect(res.text).toMatch(/SHP-[0-9a-f]{8}/);
  });
});
