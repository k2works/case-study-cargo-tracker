'use strict';

import fs from 'fs';
import path from 'path';
import { execSync, spawn } from 'child_process';
import { cleanDockerEnv } from './shared.js';

// ============================================
// 設定
// ============================================

/** アプリケーションのルートディレクトリ */
const APP_DIR = path.resolve('apps/cargo-tracker');

/** 本番 compose ファイル */
const COMPOSE_FILE = 'docker-compose.prod.yml';

/** 本番 env ファイル */
const ENV_FILE = '.env.production';

/** バックアップ出力ディレクトリ（APP_DIR 相対） */
const BACKUP_DIR = 'backups';

/** compose の基本コマンド（env-file 付き） */
const COMPOSE = `docker compose -f ${COMPOSE_FILE} --env-file ${ENV_FILE}`;

// ============================================
// ヘルパー関数
// ============================================

function run(command) {
  try {
    execSync(command, { cwd: APP_DIR, stdio: 'inherit', env: cleanDockerEnv() });
  } catch (err) {
    console.error(`エラー: ${command} が失敗しました (exit code: ${err.status})`);
    process.exit(1);
  }
}

function runForeground(command, args) {
  return spawn(command, args, { cwd: APP_DIR, stdio: 'inherit', env: cleanDockerEnv() });
}

/** .env.production の存在を確認し、無ければ案内して終了する */
function requireEnvFile() {
  const envPath = path.join(APP_DIR, ENV_FILE);
  if (!fs.existsSync(envPath)) {
    console.error(
      `エラー: ${ENV_FILE} がありません。\n` +
        `  cp ${path.join('apps/cargo-tracker', '.env.production.example')} ${path.join('apps/cargo-tracker', ENV_FILE)}\n` +
        `を実行し、秘密情報を設定してください。`
    );
    process.exit(1);
  }
}

/** DB 資格情報を .env.production から読む（バックアップ・リストア用） */
function readEnv() {
  const envPath = path.join(APP_DIR, ENV_FILE);
  const text = fs.readFileSync(envPath, 'utf8');
  const env = {};
  for (const line of text.split('\n')) {
    const m = line.match(/^\s*([A-Z_]+)\s*=\s*(.*)\s*$/);
    if (m) env[m[1]] = m[2];
  }
  return {
    user: env.POSTGRES_USER || 'cargo_tracker',
    db: env.POSTGRES_DB || 'cargo_tracker',
  };
}

// ============================================
// Gulp タスク
// ============================================

export default function (gulp) {
  // ------------------------------------------
  // ビルド・起動・停止
  // ------------------------------------------

  gulp.task('ops:prod:build', (done) => {
    requireEnvFile();
    run(`${COMPOSE} build`);
    done();
  });

  gulp.task('ops:prod:up', (done) => {
    requireEnvFile();
    console.log('本番スタックを起動します（マイグレーションは起動時に自動適用）');
    run(`${COMPOSE} up -d --build`);
    done();
  });

  gulp.task('ops:prod:down', (done) => {
    requireEnvFile();
    run(`${COMPOSE} down`);
    done();
  });

  gulp.task('ops:prod:restart', (done) => {
    requireEnvFile();
    run(`${COMPOSE} restart app`);
    done();
  });

  gulp.task('ops:prod:ps', (done) => {
    requireEnvFile();
    run(`${COMPOSE} ps`);
    done();
  });

  gulp.task('ops:prod:logs', () => {
    requireEnvFile();
    return runForeground('docker', [
      'compose', '-f', COMPOSE_FILE, '--env-file', ENV_FILE, 'logs', '-f', '--tail=200', 'app',
    ]);
  });

  gulp.task('ops:prod:psql', () => {
    requireEnvFile();
    const { user, db } = readEnv();
    return runForeground('docker', [
      'compose', '-f', COMPOSE_FILE, '--env-file', ENV_FILE, 'exec', 'db', 'psql', '-U', user, '-d', db,
    ]);
  });

  // ------------------------------------------
  // マイグレーション（アプリ起動時に自動適用。明示適用は app 再起動）
  // ------------------------------------------

  gulp.task('ops:prod:migrate', (done) => {
    requireEnvFile();
    console.log('アプリを再起動して forward-only マイグレーションを適用します');
    run(`${COMPOSE} up -d --no-deps app`);
    done();
  });

  // ------------------------------------------
  // バックアップ・リストア
  // ------------------------------------------

  gulp.task('ops:prod:backup', (done) => {
    requireEnvFile();
    const { user, db } = readEnv();
    const outDir = path.join(APP_DIR, BACKUP_DIR);
    fs.mkdirSync(outDir, { recursive: true });
    const stamp = new Date().toISOString().replace(/[:.]/g, '-');
    const outFile = path.join(BACKUP_DIR, `${db}_${stamp}.dump`);
    // カスタム形式（-Fc）でダンプ。pg_restore で選択的に復元できる。
    run(`${COMPOSE} exec -T db pg_dump -U ${user} -d ${db} -Fc -f /tmp/backup.dump`);
    run(`${COMPOSE} cp db:/tmp/backup.dump ${outFile}`);
    console.log(`バックアップを作成しました: apps/cargo-tracker/${outFile}`);
    done();
  });

  gulp.task('ops:prod:restore', (done) => {
    requireEnvFile();
    const file = process.env.RESTORE_FILE;
    if (!file) {
      console.error('エラー: RESTORE_FILE=<ダンプファイルパス> を指定してください（APP_DIR 相対）。');
      process.exit(1);
    }
    const { user, db } = readEnv();
    run(`${COMPOSE} cp ${file} db:/tmp/restore.dump`);
    // --clean で既存オブジェクトを削除してから復元する。
    run(`${COMPOSE} exec -T db pg_restore -U ${user} -d ${db} --clean --if-exists /tmp/restore.dump`);
    console.log(`リストアが完了しました: ${file}`);
    done();
  });

  // ------------------------------------------
  // ヘルプ
  // ------------------------------------------

  gulp.task('ops:prod:help', (done) => {
    console.log(`
=== 本番運用コマンド (apps/cargo-tracker / PostgreSQL) ===

  事前準備
    cp apps/cargo-tracker/.env.production.example apps/cargo-tracker/.env.production
    （.env.production を編集して秘密情報を設定）

  デプロイ
    ops:prod:build         本番イメージをビルド
    ops:prod:up            スタック起動（ビルド + マイグレーション自動適用）
    ops:prod:down          スタック停止・削除
    ops:prod:restart       アプリのみ再起動
    ops:prod:ps            稼働状況を表示
    ops:prod:logs          アプリのログを追尾（直近 200 行）

  データベース
    ops:prod:psql          psql で接続
    ops:prod:migrate       アプリ再起動でマイグレーション適用
    ops:prod:backup        pg_dump（カスタム形式）で backups/ にバックアップ
    ops:prod:restore       RESTORE_FILE=<パス> gulp ops:prod:restore で復元

  ops:prod:help            このヘルプを表示
`);
    done();
  });
}
