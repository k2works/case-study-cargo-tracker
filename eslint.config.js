/**
 * ESLint 設定（運用スクリプトの静的解析）
 *
 * **SonarQube の置き換え**（IT5・ふりかえり Try T9）。
 *
 * SonarQube は Flix を解析できないため、対象はもともと `ops/scripts/**` の
 * Node.js に限られていた。にもかかわらず、実行にローカルサーバとトークンを
 * 要するため **IT3・IT4 と 2 イテレーション連続で実行されなかった**。
 * 通していない条件を「品質ゲート」と呼び続けると、定義が形骸化する。
 *
 * 同じ対象を、サーバもトークンも要らず CI で必ず走る形に置き換える。
 * 検査する範囲を狭めたのではなく、**実際に通す**ようにした。
 *
 * `arch-lint` と `trace-lint` を対象に含めるのは、検査器のバグが
 * 「検査をパスしているのに違反している」状態を生むためである（IT2 で実際に発生）。
 * **検査器のコードこそ静的解析にかける価値がある。**
 */
export default [
  {
    files: ['ops/scripts/**/*.js', 'gulpfile.js', 'eslint.config.js'],
    languageOptions: {
      ecmaVersion: 2023,
      sourceType: 'module',
      globals: {
        console: 'readonly',
        process: 'readonly',
        URL: 'readonly',
        URLSearchParams: 'readonly',
        fetch: 'readonly',
        setTimeout: 'readonly',
        clearTimeout: 'readonly',
        Buffer: 'readonly',
        __dirname: 'readonly',
      },
    },
    linterOptions: {
      // 使われていない抑制コメントを検出する。**抑制が残ったまま原因が
      // 消えている**状態は、次に同じ箇所を読む人を誤解させる
      reportUnusedDisableDirectives: 'error',
    },
    rules: {
      // ---- 実際にバグになるもの ----
      'no-undef': 'error',
      'no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
      'no-const-assign': 'error',
      'no-dupe-keys': 'error',
      'no-dupe-args': 'error',
      'no-duplicate-case': 'error',
      'no-unreachable': 'error',
      'no-fallthrough': 'error',
      'no-self-compare': 'error',
      'no-unsafe-negation': 'error',
      'use-isnan': 'error',
      'valid-typeof': 'error',
      // 比較の取り違えは黙って通る。`'0' == 0` が真になる種類の事故を防ぐ
      eqeqeq: 'error',
      // 例外を握り潰すと、失敗が「何も起きなかった」と区別できなくなる
      'no-empty': ['error', { allowEmptyCatch: false }],
      // 検査器が `return` の書き忘れで undefined を返すと、違反 0 件で緑になる
      'consistent-return': 'error',
      'array-callback-return': 'error',
    },
  },
];
