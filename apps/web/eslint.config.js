import tseslint from 'typescript-eslint';

// F1：feature 同士を import しない（合成は pages で行う）。F2：画面が GraphQL を直接呼ばない。
// 注意：同じルール（no-restricted-imports）を複数のブロックで設定すると、後のブロックが前を置き換える（結合されない）。
// そのため、ファイルの種類ごとに、適用する制限をここで組み立てて 1 つのブロックにする。
// 規則を足したら、src/lint-tests/eslint-rules.test.ts に「違反するコードで赤になる」検査も足す。
const features = ['auth', 'booking', 'routing', 'tracking', 'billing', 'public-tracking'];

const f1 = (name) => [
  {
    group: ['@/features/*', `!@/features/${name}`, `!@/features/${name}/*`],
    message: `F1: feature ${name} から他の feature を import しない。合成は pages で行う`,
  },
  {
    group: ['../../*'],
    message: 'F1: 相対パスで feature の外へ出ない。共通のものは @/ から import する',
  },
];

const f2Paths = [{ name: 'graphql-request', message: 'F2: 画面から GraphQL を直接呼ばない。feature/api のフックを通す' }];
const f2Patterns = [{ group: ['@/lib/graphql-client'], message: 'F2: 画面から GraphQL のクライアントを直接使わない' }];

const restrict = (files, { paths = [], patterns = [] }) => ({
  files,
  rules: { 'no-restricted-imports': ['error', { paths, patterns }] },
});

export default tseslint.config(
  { ignores: ['dist', 'node_modules', 'src/types/gql-*/**', 'playwright-report', 'test-results'] },
  ...tseslint.configs.recommended,
  // F2：feature の外にある画面の層
  restrict(['src/pages/**/*.{ts,tsx}', 'src/components/**/*.{ts,tsx}', 'src/layouts/**/*.{ts,tsx}'], {
    paths: f2Paths,
    patterns: f2Patterns,
  }),
  // F1：feature の中のすべて。さらに、feature の components には F2 も（結合して 1 つのブロックにする）
  ...features.flatMap((name) => [
    restrict([`src/features/${name}/**/*.{ts,tsx}`], { patterns: f1(name) }),
    restrict([`src/features/${name}/components/**/*.{ts,tsx}`], {
      paths: f2Paths,
      patterns: [...f1(name), ...f2Patterns],
    }),
  ]),
);
