import { Controller, Get } from '@nestjs/common';

/**
 * ヘルスチェックエンドポイント。
 * ロードバランサー・CI・ウォーキングスケルトンの疎通確認に用いる。
 */
@Controller('health')
export class HealthController {
  @Get()
  check(): { status: string } {
    return { status: 'ok' };
  }
}
