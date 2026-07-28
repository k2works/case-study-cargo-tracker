import bcrypt from 'bcryptjs';
import type { PasswordVerifier } from './user-account.js';

/** bcrypt によるパスワード照合・ハッシュ生成アダプター */
export class BcryptPasswordVerifier implements PasswordVerifier {
  async verify(rawPassword: string, passwordHash: string): Promise<boolean> {
    return bcrypt.compare(rawPassword, passwordHash);
  }

  /** シード・登録用のハッシュ生成 */
  static async hash(rawPassword: string): Promise<string> {
    return bcrypt.hash(rawPassword, 10);
  }
}
