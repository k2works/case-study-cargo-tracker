import { Module } from '@nestjs/common';
import { HealthController } from './shared/infrastructure/web/health.controller.js';

/**
 * ルートモジュール（合成ルート）。
 * 各境界付けられたコンテキストのモジュールをここで配線する。
 */
@Module({
  imports: [],
  controllers: [HealthController],
  providers: [],
})
export class AppModule {}
