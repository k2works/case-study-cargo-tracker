import { Module } from '@nestjs/common';
import { ServeStaticModule } from '@nestjs/serve-static';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { HealthController } from './shared/infrastructure/web/health.controller.js';
import { SecurityModule } from './shared/security.module.js';
import { ShipperModule } from './contexts/shipper/shipper.module.js';

const rootDir = dirname(fileURLToPath(import.meta.url));

/**
 * ルートモジュール（合成ルート）。
 * 各境界付けられたコンテキストのモジュールをここで配線する。
 */
@Module({
  imports: [
    ServeStaticModule.forRoot({
      // src/ から見た ../public。ビルド後（dist/）も同階層構成のため相対解決する。
      rootPath: join(rootDir, '..', 'public'),
      serveRoot: '/',
    }),
    SecurityModule,
    ShipperModule,
  ],
  controllers: [HealthController],
  providers: [],
})
export class AppModule {}
