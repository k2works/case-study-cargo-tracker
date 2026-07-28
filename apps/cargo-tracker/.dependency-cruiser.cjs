/**
 * dependency-cruiser 設定。
 * ヘキサゴナルアーキテクチャの依存方向と BC 独立性を検証する。
 * ルールは tech_stack.md「dependency-cruiser 最低限の検証ルール」に準拠する。
 */
module.exports = {
  forbidden: [
    {
      name: 'domain-not-to-infrastructure',
      comment: 'ドメイン層はインフラ層に依存してはならない',
      severity: 'error',
      from: { path: 'src/contexts/[^/]+/domain' },
      to: { path: 'src/contexts/[^/]+/infrastructure' },
    },
    {
      name: 'domain-not-to-application',
      comment: 'ドメイン層はアプリケーション層に依存してはならない',
      severity: 'error',
      from: { path: 'src/contexts/[^/]+/domain' },
      to: { path: 'src/contexts/[^/]+/application' },
    },
    {
      name: 'domain-not-to-nestjs',
      comment: 'ドメイン層は NestJS に依存してはならない',
      severity: 'error',
      from: { path: 'src/contexts/[^/]+/domain' },
      to: { path: 'node_modules/@nestjs' },
    },
    {
      name: 'application-not-to-infrastructure',
      comment: 'アプリケーション層はインフラ層を直接参照してはならない（Port 経由）',
      severity: 'error',
      from: { path: 'src/contexts/[^/]+/application' },
      to: { path: 'src/contexts/[^/]+/infrastructure' },
    },
    {
      name: 'no-cross-context',
      comment: '異なる Bounded Context 間でモジュールを直接参照してはならない（ACL/Event 経由のみ）',
      severity: 'error',
      from: { path: 'src/contexts/([^/]+)/' },
      to: {
        path: 'src/contexts/([^/]+)/',
        pathNot: [
          'src/contexts/$1/',
          'src/contexts/[^/]+/application/outboundservices/acl',
        ],
      },
    },
    {
      name: 'shared-not-to-contexts',
      comment: '共有カーネル（shared）は各 Bounded Context に依存してはならない（逆流禁止）',
      severity: 'error',
      from: { path: 'src/shared/' },
      to: { path: 'src/contexts/' },
    },
    {
      name: 'no-circular',
      comment: '循環依存を禁止する',
      severity: 'error',
      from: {},
      to: { circular: true },
    },
  ],
  options: {
    doNotFollow: { path: 'node_modules' },
    tsConfig: { fileName: 'tsconfig.json' },
    tsPreCompilationDeps: true,
    enhancedResolveOptions: {
      extensions: ['.ts', '.tsx', '.js'],
    },
  },
};
