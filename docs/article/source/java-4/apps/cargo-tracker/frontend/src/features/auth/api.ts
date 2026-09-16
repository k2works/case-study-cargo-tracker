import { commandClient } from '@/shared/api/client';
import { isRole, type Role } from '@/shared/auth/roles';

interface LoginResponseBody {
  readonly token: string;
  readonly username: string;
  readonly displayName: string;
  readonly roles: readonly string[];
  readonly shipperId: string | null;
}

export interface LoginResult {
  readonly token: string;
  readonly username: string;
  readonly displayName: string;
  readonly roles: readonly Role[];
}

export async function login(username: string, password: string): Promise<LoginResult> {
  const body = await commandClient<LoginResponseBody>('/auth/login', { username, password });

  // 知らないロールは捨てる。名簿に無いものを通すと、増やし忘れたロールほど
  // 何にも守られないまま画面へ入れてしまう。
  const roles = body.roles.filter((role): role is Role => isRole(role));

  return {
    token: body.token,
    username: body.username,
    displayName: body.displayName,
    roles,
  };
}
