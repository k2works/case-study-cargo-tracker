import type { CodegenConfig } from '@graphql-codegen/cli';

// F3：GraphQL の型は、サーバーが書き出したスキーマの固定ファイル（schema/*.graphql）から生成する。
// スキーマが変わったら（Scala 側の SchemaSnapshotSpec が赤になる）、書き出し直してここを再生成し、差分を見る。
// Query と Mutation は別のエンドポイント・別のスキーマなので、別々に生成する。
const shared = {
  preset: 'client' as const,
  presetConfig: { fragmentMasking: false },
  config: { useTypeImports: true, enumsAsTypes: true, scalars: { Long: 'number' } },
};

const config: CodegenConfig = {
  generates: {
    'src/types/gql-query/': { ...shared, schema: 'schema/query.graphql', documents: ['src/**/*.query.ts'] },
    'src/types/gql-command/': { ...shared, schema: 'schema/command.graphql', documents: ['src/**/*.command.ts'] },
  },
  ignoreNoDocuments: true,
};

export default config;
