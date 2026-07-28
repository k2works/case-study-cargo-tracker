import { Body, Controller, Get, Logger, Post, Query, Req, Res } from '@nestjs/common';
import type { Request, Response } from 'express';
import { AuthenticationService } from '../../infrastructure/auth/authentication.service.js';
import { renderPage } from '../../../views/render.js';
import { Login } from '../../../views/auth/Login.js';

const MESSAGES = {
  INVALID_CREDENTIALS: '利用者 ID またはパスワードが正しくありません',
  LOCKED:
    'アカウントが一時ロックされました。しばらくしてから再度お試しいただくか、管理者へお問い合わせください',
  DISABLED: 'このアカウントは無効化されています。管理者へお問い合わせください',
} as const;

/**
 * 認証コントローラ（US26 ログイン / US27 ログアウト）。
 * セッションベース認証。成功・失敗を pino（NestJS Logger）に記録する。
 */
@Controller()
export class AuthController {
  private readonly logger = new Logger(AuthController.name);

  constructor(private readonly authService: AuthenticationService) {}

  @Get('login')
  showLogin(
    @Req() req: Request,
    @Res() res: Response,
    @Query('timeout') timeout?: string,
  ): void {
    if (req.session.user) {
      res.redirect('/');
      return;
    }
    const flashError = req.session.flash?.error;
    req.session.flash = {};
    // 開発・デモ環境（pg-mem + シード）ではシードユーザーを初期入力する
    const isDev = !process.env.DATABASE_URL;
    renderPage(
      res,
      Login({
        error: flashError,
        timeout: timeout !== undefined,
        defaultUsername: isDev ? 'sales' : undefined,
        defaultPassword: isDev ? 'password' : undefined,
        showDevAccounts: isDev,
      }),
    );
  }

  @Post('login')
  async login(
    @Body('username') username: string,
    @Body('password') password: string,
    @Req() req: Request,
    @Res() res: Response,
  ): Promise<void> {
    const result = await this.authService.authenticate(username ?? '', password ?? '');

    if (result.outcome === 'SUCCESS') {
      req.session.user = result.user;
      this.logger.log(`ログイン成功: username=${username}`);
      res.redirect('/');
      return;
    }

    this.logger.warn(`ログイン失敗: username=${username} outcome=${result.outcome}`);
    req.session.flash = { error: MESSAGES[result.outcome] };
    res.redirect('/login');
  }

  @Post('logout')
  logout(@Req() req: Request, @Res() res: Response): void {
    const username = req.session.user?.username;
    req.session.destroy(() => {
      this.logger.log(`ログアウト: username=${username ?? '(unknown)'}`);
      res.redirect('/login');
    });
  }
}
