import { Module } from '@nestjs/common';
import { HealthController } from './shared/infrastructure/web/health.controller.js';
import { SecurityModule } from './shared/security.module.js';

/**
 * ルートモジュール（合成ルート）。
 * 各境界付けられたコンテキストのモジュールをここで配線する。
 */
@Module({
  imports: [SecurityModule],
  controllers: [HealthController],
  providers: [],
})
export class AppModule {}
