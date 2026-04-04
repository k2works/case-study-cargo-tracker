'use strict';

import { execSync } from 'child_process';

/**
 * DOCKER_HOST を除外した環境変数を返す
 * Docker Desktop 使用時に DOCKER_HOST が設定されていると接続エラーが発生するため除外する
 * @returns {Object} DOCKER_HOST を除外した環境変数
 */
export function cleanDockerEnv() {
  const env = { ...process.env };
  delete env.DOCKER_HOST;
  delete env.GIT_DIR;
  delete env.GIT_WORK_TREE;
  delete env.GIT_EXEC_PATH;
  env.SHELL = '/bin/bash';
  env.TERM = 'dumb';
  env.PATH = [
    '/opt/homebrew/bin',
    '/usr/local/bin',
    '/usr/bin',
    '/bin',
    '/usr/sbin',
    '/sbin',
    env.PATH || '',
  ].join(':');
  return env;
}

/**
 * Docker デーモンが利用可能か確認する
 * @returns {boolean} Docker が利用可能なら true
 */
export function isDockerAvailable() {
  try {
    execSync('docker info', { stdio: 'ignore', env: cleanDockerEnv() });
    return true;
  } catch {
    return false;
  }
}

/**
 * URL をデフォルトブラウザで開く（クロスプラットフォーム対応）
 * @param {string} url - 開く URL
 */
export function openUrl(url) {
  const platform = process.platform;
  const cmd =
    platform === 'win32' ? `start "" "${url}"` :
    platform === 'darwin' ? `open "${url}"` :
    `xdg-open "${url}"`;
  execSync(cmd, { stdio: 'ignore' });
}
