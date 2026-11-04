import { ESLint } from 'eslint';
import { describe, expect, it } from 'vitest';

/**
 * F1・F2 のメタ検査：ルールが「違反するコードで本当に赤になる」ことを確かめる。
 * 緑のコードだけを検査していると、ルールが空振りしていても気づけない。
 */
const eslint = new ESLint({ cwd: process.cwd() });

async function rulesBroken(code: string, filePath: string): Promise<string[]> {
  const [result] = await eslint.lintText(code, { filePath });
  return (result?.messages ?? []).filter((m) => m.ruleId === 'no-restricted-imports').map((m) => m.message);
}

describe('F1: feature 同士を import しない', () => {
  it('他の feature を絶対パス（@/）で import すると違反', async () => {
    const msgs = await rulesBroken("import { x } from '@/features/routing/api/hooks';\nexport const y = x;\n", 'src/features/booking/components/A.tsx');
    expect(msgs.length).toBeGreaterThan(0);
  });

  it('他の feature を相対パスで import しても違反', async () => {
    const msgs = await rulesBroken("import { x } from '../../routing/api/hooks';\nexport const y = x;\n", 'src/features/booking/components/A.tsx');
    expect(msgs.length).toBeGreaterThan(0);
  });

  it('自分の feature の中は import してよい', async () => {
    const msgs = await rulesBroken("import { x } from '@/features/booking/api/hooks';\nimport { z } from '../api/hooks';\nexport const y = [x, z];\n", 'src/features/booking/components/A.tsx');
    expect(msgs).toEqual([]);
  });

  it('pages は feature を組み合わせてよい（合成は pages で行う）', async () => {
    const msgs = await rulesBroken("import { a } from '@/features/booking';\nimport { b } from '@/features/routing';\nexport const y = [a, b];\n", 'src/pages/Home.tsx');
    expect(msgs).toEqual([]);
  });
});

describe('F2: 画面が GraphQL を直接呼ばない', () => {
  it('pages・components・layouts・feature の components が graphql-request を import すると違反', async () => {
    for (const file of ['src/pages/P.tsx', 'src/components/ui/B.tsx', 'src/layouts/L.tsx', 'src/features/booking/components/C.tsx']) {
      const msgs = await rulesBroken("import { request } from 'graphql-request';\nexport const r = request;\n", file);
      expect(msgs.length, file).toBeGreaterThan(0);
    }
  });

  it('GraphQL クライアント（lib）の直接利用も、画面からは違反', async () => {
    const msgs = await rulesBroken("import { queryClient } from '@/lib/graphql-client';\nexport const q = queryClient;\n", 'src/pages/P.tsx');
    expect(msgs.length).toBeGreaterThan(0);
  });

  it('lib と feature の api では graphql-request を使ってよい', async () => {
    for (const file of ['src/lib/graphql-client.ts', 'src/features/booking/api/hooks.ts']) {
      const msgs = await rulesBroken("import { request } from 'graphql-request';\nexport const r = request;\n", file);
      expect(msgs, file).toEqual([]);
    }
  });
});
