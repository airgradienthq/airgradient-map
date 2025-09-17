import { NestFactory } from '@nestjs/core';
import { AppModule } from './app.module';
import { LogLevel, Logger } from '@nestjs/common';
import { SwaggerModule } from '@nestjs/swagger';
import { createSwaggerConfig } from './config/swagger.config';
import { NestExpressApplication } from '@nestjs/platform-express';
import { join } from 'path';

async function bootstrap(): Promise<void> {
  // Setup logger
  const logger = new Logger('Bootstrap');
  const isProduction = process.env.NODE_ENV === 'production';
  const logLevels: LogLevel[] = isProduction
    ? ['error', 'warn', 'log']
    : ['error', 'warn', 'log', 'debug', 'verbose'];
  logger.log(process.env.NODE_ENV);

  const app = await NestFactory.create<NestExpressApplication>(AppModule, {
    logger: logLevels,
  });

  app.useStaticAssets(join(__dirname, '..', 'public'), {
    prefix: '/data/',
    setHeaders: res => {
      res.set('Cache-Control', 'no-cache');
    },
  });

  // Define CORS
  const originList = isProduction
    ? ['https://www.airgradient.com', 'https://map.airgradient.com']
    : [
        'https://www.airgradient.com',
        'https://map-int.airgradient.com',
        'http://localhost:7777',
        'http://localhost:3000',
      ];
  app.enableCors({
    origin: originList,
  });

  // Setup Swagger
  const swaggerConfig = createSwaggerConfig();
  const documentFactory = () => SwaggerModule.createDocument(app, swaggerConfig);
  SwaggerModule.setup('map/api/v1/docs', app, documentFactory);

  await app.listen(process.env.PORT ?? 3001);
  logger.log('Application Started');
}
bootstrap();
