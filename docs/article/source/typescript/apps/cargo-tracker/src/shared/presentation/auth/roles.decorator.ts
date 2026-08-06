import { SetMetadata } from '@nestjs/common';
import type { Role } from '../../domain/model/role.js';

export const ROLES_KEY = 'roles';

/** ハンドラ・コントローラに必要ロールを付与する（RolesGuard が参照する） */
export const Roles = (...roles: Role[]) => SetMetadata(ROLES_KEY, roles);
