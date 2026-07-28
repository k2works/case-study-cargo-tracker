import 'reflect-metadata';
import './shared/presentation/auth/session.js';
import { NestFactory } from '@nestjs/core';
import type { NestExpressApplication } from '@nestjs/platform-express';
import { AppModule } from './app.module.js';
import { createSessionMiddleware } from './shared/infrastructure/config/session.config.js';
import { DATABASE, type AppDatabase } from './shared/infrastructure/database/database.js';
import { seedDefaultUsers } from './shared/infrastructure/database/seed.js';

async function bootstrap(): Promise<void> {
  const app = await NestFactory.create<NestExpressApplication>(AppModule);
  app.use(createSessionMiddleware());

  // 開発（pg-mem）時はデフォルトユーザーを投入する
  if (!process.env.DATABASE_URL) {
    const db = app.get<AppDatabase>(DATABASE);
    await seedDefaultUsers(db);
  }

  const port = process.env.PORT ?? 8080;
  await app.listen(port);
}

void bootstrap();
