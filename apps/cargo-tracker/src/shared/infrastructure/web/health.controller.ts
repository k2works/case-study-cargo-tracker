import { Controller, Get } from '@nestjs/common';
import { Public } from '../../presentation/auth/public.decorator.js';

/**
 * ヘルスチェックエンドポイント。認証不要（ADR-011）。
 * ロードバランサー・CI・ウォーキングスケルトンの疎通確認に用いる。
 */
@Controller('health')
@Public()
export class HealthController {
  @Get()
  check(): { status: string } {
    return { status: 'ok' };
  }
}
