import { Global, Module } from '@nestjs/common';
import { DATABASE, createPostgresDatabase, type AppDatabase } from './database.js';
import { createPgMemDatabase } from './pgmem-database.js';

/**
 * DATABASE プロバイダを提供する。
 * DATABASE_URL があれば実 PostgreSQL、なければ pg-mem（ローカル開発デフォルト）を用いる。
 */
@Global()
@Module({
  providers: [
    {
      provide: DATABASE,
      useFactory: (): AppDatabase => {
        const url = process.env.DATABASE_URL;
        if (url) {
          return createPostgresDatabase(url);
        }
        return createPgMemDatabase().db;
      },
    },
  ],
  exports: [DATABASE],
})
export class AppDatabaseModule {}
