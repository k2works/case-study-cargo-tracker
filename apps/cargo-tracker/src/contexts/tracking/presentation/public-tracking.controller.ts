import { Controller, Get, Param, Query, Res } from '@nestjs/common';
import type { Response } from 'express';
import { renderPage } from '../../../views/render.js';
import { PublicTrackingIndex, PublicTrackingShow } from '../../../views/tracking/PublicShow.js';
import { TrackingQueryService } from '../application/queryservices/tracking-query.service.js';
import { Public } from '../../../shared/presentation/auth/public.decorator.js';

/**
 * 公開貨物追跡コントローラ（US18）。認証不要（@Public・ADR-011）。
 * 荷主・荷受人が追跡番号のみで状態・現在地・イベント履歴を照会できる最小公開ページを提供する。
 * 予約 ID・荷主/荷受人情報など照会範囲外の情報は露出しない（PublicTrackingShow が最小表示）。
 */
@Controller('public/tracking')
@Public()
export class PublicTrackingController {
  constructor(private readonly queryService: TrackingQueryService) {}

  @Get()
  async index(@Query('trackingNumber') trackingNumber: string | undefined, @Res() res: Response): Promise<void> {
    // フォームからの GET（?trackingNumber=）は結果ページへリダイレクトする（PRG 相当）
    if (trackingNumber !== undefined && trackingNumber.trim().length > 0) {
      res.redirect(`/public/tracking/${encodeURIComponent(trackingNumber.trim())}`);
      return;
    }
    renderPage(res, PublicTrackingIndex());
  }

  @Get(':trackingNumber')
  async show(@Param('trackingNumber') trackingNumber: string, @Res() res: Response): Promise<void> {
    const detail = await this.queryService.findDetail(trackingNumber);
    if (detail === null) {
      renderPage(res, PublicTrackingIndex({ error: '追跡番号が見つかりません' }));
      return;
    }
    renderPage(res, PublicTrackingShow({ detail }));
  }
}
