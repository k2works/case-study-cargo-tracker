import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import type { NestExpressApplication } from '@nestjs/platform-express';
import { Logger } from '@nestjs/common';

const here = dirname(fileURLToPath(import.meta.url));
const APP_ROOT = join(here, '..', '..', '..', '..');

/** ライブリロードが有効かどうか（Layout のスクリプト注入判定に用いる） */
export function isLiveReloadEnabled(): boolean {
  return process.env.NODE_ENV !== 'production';
}

/** ブラウザに注入する livereload クライアントスクリプトの URL */
export const LIVERELOAD_SCRIPT_URL = 'http://localhost:35729/livereload.js';

/**
 * 開発時のライブリロードサーバーを起動する。
 * src・public を監視し、変更時に接続中ブラウザへリロードを通知する。
 * クライアントスクリプトの注入は Layout が担う（isLiveReloadEnabled）。
 *
 * NODE_ENV=production では何もしない。node --watch によるサーバー再起動と併用する。
 */
export async function enableLiveReload(_app: NestExpressApplication): Promise<void> {
  if (!isLiveReloadEnabled()) {
    return;
  }
  const logger = new Logger('LiveReload');
  try {
    const { createServer } = await import('livereload');
    const lrServer = createServer({ exts: ['ts', 'tsx', 'css', 'js'], delay: 100 });
    lrServer.watch([join(APP_ROOT, 'src'), join(APP_ROOT, 'public')]);

    // node --watch によるサーバー再起動後、再接続したブラウザを一度リロードする
    setTimeout(() => lrServer.refresh('/'), 800);

    logger.log('ライブリロードを有効化しました（ポート 35729）');
  } catch (error) {
    logger.warn(`ライブリロードの初期化に失敗しました: ${String(error)}`);
  }
}
