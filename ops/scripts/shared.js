'use strict';

import { execSync } from 'child_process';
import { existsSync } from 'fs';
import { resolve } from 'path';

/**
 * DOCKER_HOST を除外した環境変数を返す
 * Docker Desktop 使用時に DOCKER_HOST が設定されていると接続エラーが発生するため除外する
 * @returns {Object} DOCKER_HOST を除外した環境変数
 */
export function cleanDockerEnv() {
  const env = { ...process.env };
  delete env.DOCKER_HOST;
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

/**
 * Gradle wrapper を実行するコマンドを返す
 *
 * Windows の cmd.exe は `./gradlew` を解釈できず
 * 「'.' は、内部コマンドまたは外部コマンド ... として認識されていません」で失敗する。
 * さらに Git Bash 等から起動した環境では `NoDefaultCurrentDirectoryInExePath=1` が
 * 設定されており、cmd.exe はカレントディレクトリから `.bat` を解決しない。
 * このため Windows では絶対パスを引用して渡す（シェル経由で実行するため、
 * パスに空白が含まれても壊れないようにする）。
 * wrapper が無い環境ではシステムの gradle にフォールバックする。
 * @param {string} dir - wrapper を探すディレクトリ
 * @returns {string} 実行するコマンド
 */
export function gradleCommand(dir = '.') {
  if (process.platform === 'win32') {
    const wrapper = resolve(dir, 'gradlew.bat');
    return existsSync(wrapper) ? `"${wrapper}"` : 'gradle';
  }
  return existsSync(resolve(dir, 'gradlew')) ? './gradlew' : 'gradle';
}
