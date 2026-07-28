import 'reflect-metadata';
import './shared/presentation/auth/session.js';
import { NestFactory } from '@nestjs/core';
import type { NestExpressApplication } from '@nestjs/platform-express';
import { AppModule } from './app.module.js';
import { createSessionMiddleware } from './shared/infrastructure/config/session.config.js';

async function bootstrap(): Promise<void> {
  const app = await NestFactory.create<NestExpressApplication>(AppModule);
  app.use(createSessionMiddleware());
  const port = process.env.PORT ?? 8080;
  await app.listen(port);
}

void bootstrap();
