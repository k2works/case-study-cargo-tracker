import { Module } from '@nestjs/common';
import { AuthenticationService } from './infrastructure/auth/authentication.service.js';
import { BcryptPasswordVerifier } from './infrastructure/auth/bcrypt-password-verifier.js';
import { KyselyUserRepository } from './infrastructure/auth/kysely-user-repository.js';
import type { UserRepository, PasswordVerifier } from './infrastructure/auth/user-account.js';
import { AppDatabaseModule } from './infrastructure/database/database.module.js';
import { DATABASE } from './infrastructure/database/database.js';
import type { AppDatabase } from './infrastructure/database/database.js';
import { AuthController } from './presentation/auth/auth.controller.js';
import { HomeController } from './presentation/auth/home.controller.js';
import { PlaceholderController } from './presentation/placeholder/placeholder.controller.js';

export const USER_REPOSITORY = Symbol('USER_REPOSITORY');
export const PASSWORD_VERIFIER = Symbol('PASSWORD_VERIFIER');

/**
 * 認証基盤（Security）の配線。
 * 横断的な認証・認可を提供する。BC ではない。
 */
@Module({
  imports: [AppDatabaseModule],
  controllers: [AuthController, HomeController, PlaceholderController],
  providers: [
    {
      provide: USER_REPOSITORY,
      useFactory: (db: AppDatabase): UserRepository => new KyselyUserRepository(db),
      inject: [DATABASE],
    },
    {
      provide: PASSWORD_VERIFIER,
      useClass: BcryptPasswordVerifier,
    },
    {
      provide: AuthenticationService,
      useFactory: (users: UserRepository, verifier: PasswordVerifier): AuthenticationService =>
        new AuthenticationService(users, verifier),
      inject: [USER_REPOSITORY, PASSWORD_VERIFIER],
    },
  ],
  exports: [AuthenticationService],
})
export class SecurityModule {}
