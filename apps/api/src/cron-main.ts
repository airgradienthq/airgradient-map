import { NestFactory } from '@nestjs/core';
import { LogLevel, Logger } from '@nestjs/common';
import { CronModule } from './cron.module';

async function bootstrap(): Promise<void> {
  // Setup logger
  const logger = new Logger('Bootstrap');
  const isProduction = process.env.NODE_ENV === 'production';
  const logLevels: LogLevel[] = isProduction
    ? ['error', 'warn', 'log']
    : ['error', 'warn', 'log', 'debug', 'verbose'];
  logger.log(process.env.NODE_ENV);

  await NestFactory.createApplicationContext(CronModule, {
    logger: logLevels,
  });

  logger.log('Task Started');
}
bootstrap();
