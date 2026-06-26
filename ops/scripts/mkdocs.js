'use strict';

import { execSync } from 'child_process';
import fs from 'fs';
import path from 'path';
import { cleanDockerEnv, isDockerAvailable, openUrl } from './shared.js';

/**
 * docker compose コマンドを実行
 * @param {string} args - docker compose に渡す引数
 */
function dockerCompose(args) {
  execSync(`docker compose ${args}`, { stdio: 'inherit', env: cleanDockerEnv() });
}

/**
 * Docker が利用可能か確認し、不可なら警告メッセージを表示して false を返す
 * @returns {boolean} Docker が利用可能なら true
 */
function requireDocker() {
  if (isDockerAvailable()) {
    return true;
  }
  console.warn('Warning: Docker is not running. Skipping this task.');
  console.warn('Please start Docker Desktop and try again.');
  return false;
}

/**
 * MkDocs タスクを gulp に登録する
 * @param {import('gulp').Gulp} gulp - Gulp インスタンス
 */
export default function (gulp) {
  gulp.task('mkdocs:serve', async () => {
    if (!requireDocker()) return;
    console.log('Starting MkDocs server...');
    dockerCompose('up -d mkdocs');
    // daemon が応答するまで待機 (最大 180 秒)。進捗を可視化して「フリーズに見える」のを防ぐ
    process.stdout.write('Waiting for MkDocs to be ready');
    const start = Date.now();
    const timeoutMs = 180000;
    while (Date.now() - start < timeoutMs) {
      try {
        execSync('curl -sf http://localhost:8000/ -o /dev/null', { stdio: 'ignore' });
        const elapsed = ((Date.now() - start) / 1000).toFixed(1);
        console.log(`\nDocumentation is available at http://localhost:8000 (${elapsed}s)`);
        return;
      } catch {
        process.stdout.write('.');
        await new Promise((r) => setTimeout(r, 1500));
      }
    }
    console.log('\nWarning: MkDocs did not become ready within 180s. Check `docker compose logs mkdocs`.');
  });

  gulp.task('mkdocs:build', (done) => {
    if (!requireDocker()) { done(); return; }
    try {
      console.log('Building MkDocs documentation...');
      const siteDir = path.join(process.cwd(), 'site');
      if (fs.existsSync(siteDir)) {
        fs.rmSync(siteDir, { recursive: true, force: true });
      }
      dockerCompose('run --rm mkdocs mkdocs build');
      console.log('\nBuild completed.');
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('mkdocs:stop', (done) => {
    if (!requireDocker()) { done(); return; }
    try {
      console.log('Stopping MkDocs server...');
      // down ではなく stop を使う: コンテナを残しておけば次回の起動が高速になる
      dockerCompose('stop mkdocs');
      console.log('Stopped.');
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('mkdocs:open', (done) => {
    try {
      openUrl('http://localhost:8000');
      done();
    } catch (error) {
      done(error);
    }
  });
}
