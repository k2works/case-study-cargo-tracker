import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

/**
 * クラスタ E2E は、自分が作ったデータを候補の一覧から名指しで探さない。
 *
 * <p><b>クラスタは作り直さずに使い続ける。</b> Event Store が真実なので投影だけ
 * 消しても戻り、実行のたびにデータが積み上がる。経路探索は推奨順の上位 20 件で
 * 打ち切る（ADR-0007）ので、同じ区間の航海が 20 本を超えると
 * <b>自分が登録した航海が候補に出るとは限らなくなる</b>。</p>
 *
 * <p>IT7 のクローズで実測した——同じ区間の航海が 84 本あり、2 つのテストが
 * 「自分の航海が候補に無い」で落ちた。ある日を境に落ち始めるので、原因を
 * 追うのに時間がかかる。</p>
 *
 * <p><b>経路設計者がするのは「候補を見て 1 つ選ぶ」こと</b>で、特定の航海を
 * 探すことではない。先頭の候補（`candidate-1`）を選べば、積み上がりに強い。</p>
 *
 * <p>予約や航海の<b>一覧</b>から名指しで探すのは構わない（上限で切られない）。
 * ここで見るのは候補の一覧だけである。</p>
 */
const SPEC = 'e2e/cluster.spec.ts';

describe('クラスタ E2E の書き方', () => {
  it('候補の一覧から自分の航海を名指しで探していない', () => {
    const source = readFileSync(SPEC, 'utf-8');

    // 候補を選ぶ直前の行に `hasText: voyageNumber` があると、その航海が
    // 上位 20 件に入っている前提になる。
    const named = [...source.matchAll(
      /locator\('tr',\s*\{\s*hasText:\s*voyageNumber\s*\}\)[\s\S]{0,200}?getByRole\('radio'\)/g,
    )];

    expect(named, '候補は先頭（candidate-1）を選ぶ。積み上がったデータで落ちる')
      .toHaveLength(0);
  });

  it('候補を選ぶ検査が実際にある（この検査が空振りしていない）', () => {
    const source = readFileSync(SPEC, 'utf-8');

    expect(source, 'クラスタ E2E に経路確定の通しが無いなら、上の検査は何も守っていない')
      .toContain("getByTestId('candidate-1')");
  });
});
