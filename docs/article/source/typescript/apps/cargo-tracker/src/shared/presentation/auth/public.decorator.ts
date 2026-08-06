import { SetMetadata } from '@nestjs/common';

/** 公開ルートを示すメタデータキー（グローバル AuthenticatedGuard が参照する。ADR-011） */
export const IS_PUBLIC_KEY = 'isPublic';

/**
 * ハンドラ・コントローラを認証不要（公開）として明示する（ADR-011）。
 * グローバル AuthenticatedGuard はこのメタデータが真なら認証をスキップする。
 */
export const Public = () => SetMetadata(IS_PUBLIC_KEY, true);
