#!/usr/bin/env node
// カバレッジ CI ゲート（IT1 Try#1・ADR: ドメイン 85% / 全体 80%）。
// coverlet で収集した cobertura を ReportGenerator でマージし、
// 全体とドメイン層（CargoTracker.*.Domain*）の行カバレッジが閾値を満たすか検証する。
//
// 前提: 事前に `dotnet test --collect:"XPlat Code Coverage"` が実行され、
//       各テストプロジェクトの TestResults に coverage.cobertura.xml が生成されていること。

const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const APP_DIR = path.resolve(__dirname, '..', '..');
const TOTAL_THRESHOLD = 80.0;
const DOMAIN_THRESHOLD = 85.0;

function run(cmd) {
  execSync(cmd, { cwd: APP_DIR, stdio: 'inherit' });
}

function lineCoverage(summaryPath) {
  const data = JSON.parse(fs.readFileSync(path.join(APP_DIR, summaryPath), 'utf8'));
  return data.summary.linecoverage;
}

// 全体カバレッジ
run(
  'dotnet tool run reportgenerator -reports:"**/coverage.cobertura.xml" ' +
    '-targetdir:"coverage-report" -reporttypes:"Html;JsonSummary"'
);
// ドメイン層のみ（namespace CargoTracker.*.Domain* でフィルタ）
run(
  'dotnet tool run reportgenerator -reports:"**/coverage.cobertura.xml" ' +
    '-targetdir:"coverage-report-domain" -reporttypes:"JsonSummary" ' +
    '-classfilters:"+CargoTracker.*.Domain*"'
);

const total = lineCoverage('coverage-report/Summary.json');
const domain = lineCoverage('coverage-report-domain/Summary.json');

const results = [
  { name: '全体', value: total, threshold: TOTAL_THRESHOLD },
  { name: 'ドメイン層', value: domain, threshold: DOMAIN_THRESHOLD },
];

let failed = false;
console.log('\nカバレッジゲート結果:');
for (const r of results) {
  const ok = r.value >= r.threshold;
  const mark = ok ? 'OK  ' : 'NG  ';
  console.log(`  ${mark} ${r.name}: ${r.value.toFixed(1)}% （閾値 ${r.threshold}%）`);
  if (!ok) failed = true;
}

if (failed) {
  console.error('\nカバレッジゲート失敗: 閾値を下回る項目があります。');
  process.exit(1);
}
console.log('\nカバレッジゲート合格。');
