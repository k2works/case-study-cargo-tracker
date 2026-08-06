import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import request from 'supertest';
import type { TestAgent, TestApp } from './test-app.js';
import { createTestApp, loginAsTestUser } from './test-app.js';
import { Role } from '../src/shared/domain/model/role.js';

describe('見積作成フロー (US01)', () => {
  let ctx: TestApp;
  let sales: TestAgent;

  function futureDate(days: number): string {
    const d = new Date(Date.now() + days * 24 * 60 * 60 * 1000);
    return d.toISOString().slice(0, 10);
  }

  beforeEach(async () => {
    ctx = await createTestApp();
    sales = await loginAsTestUser(ctx, { username: 'sales1', roles: [Role.SALES] });
  });

  afterEach(async () => {
    await ctx.app.close();
  });

  it('営業担当者は見積作成画面に到達できる', async () => {
    const res = await sales.get('/estimates/new');
    expect(res.status).toBe(200);
    expect(res.text).toContain('輸送見積作成');
    expect(res.text).toContain('出発地');
  });

  it('見積を作成するとルート候補付き詳細（見積番号）へ遷移する', async () => {
    const create = await sales.post('/estimates').type('form').send({
      origin: 'JPTYO',
      destination: 'USLAX',
      arrivalDeadline: futureDate(60),
      weightKg: '1000',
      cargoType: 'GENERAL',
    });
    expect(create.status).toBe(302);
    expect(create.headers.location).toMatch(/^\/estimates\/[0-9a-f-]{36}$/);

    const show = await sales.get(create.headers.location!);
    expect(show.status).toBe(200);
    expect(show.text).toContain('見積番号');
    expect(show.text).toContain('ルート候補');
    // スタブは 2 候補（直行・経由）を返す
    expect(show.text).toContain('直行');
    expect(show.text).toContain('SGSIN');
  });

  it('期限に間に合わない場合は警告が表示される', async () => {
    const create = await sales.post('/estimates').type('form').send({
      origin: 'JPTYO',
      destination: 'USLAX',
      arrivalDeadline: futureDate(3), // スタブ最短 14 日に間に合わない
      weightKg: '1000',
      cargoType: 'GENERAL',
    });
    const show = await sales.get(create.headers.location!);
    expect(show.text).toContain('間に合うルート候補が見つかりません');
  });

  it('危険物選択で htmx フラグメントに危険物申告フィールドが返る', async () => {
    const res = await sales.get('/estimates/fields?cargoType=HAZARDOUS');
    expect(res.status).toBe(200);
    expect(res.text).toContain('危険物クラス');
    expect(res.text).toContain('UN 番号');
  });

  it('出発地と目的地が同一なら見積作成エラー', async () => {
    const res = await sales.post('/estimates').type('form').send({
      origin: 'JPTYO',
      destination: 'JPTYO',
      arrivalDeadline: futureDate(60),
      weightKg: '1000',
      cargoType: 'GENERAL',
    });
    expect(res.status).toBe(200);
    expect(res.text).toContain('出発地と目的地');
  });

  it('見積は DB に保存される', async () => {
    await sales.post('/estimates').type('form').send({
      origin: 'JPOSA',
      destination: 'SGSIN',
      arrivalDeadline: futureDate(60),
      weightKg: '500',
      cargoType: 'REFRIGERATED',
    });
    const saved = await ctx.db.selectFrom('estimate').selectAll().execute();
    expect(saved).toHaveLength(1);
    expect(saved[0].cargoType).toBe('REFRIGERATED');
    const candidates = await ctx.db.selectFrom('route_candidate').selectAll().execute();
    expect(candidates.length).toBe(2);
  });
});
