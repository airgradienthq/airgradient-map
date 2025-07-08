import { NestFactory } from '@nestjs/core';
import { AppModule } from './app.module';
import { LogLevel, Logger } from '@nestjs/common';
import { SwaggerModule, DocumentBuilder } from '@nestjs/swagger';

async function bootstrap() {
  // Setup logger
  const logger = new Logger('Bootstrap');
  const isProduction = process.env.NODE_ENV === 'production';
  const logLevels: LogLevel[] = isProduction
    ? ['error', 'warn', 'log']
    : ['error', 'warn', 'log', 'debug', 'verbose'];
  logger.log(process.env.NODE_ENV);

  const app = await NestFactory.create(AppModule, {
    logger: logLevels,
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

  // Enhanced Swagger setup
  const config = new DocumentBuilder()
    .setTitle('AirGradient Map API')
    .setDescription(
      `
**AirGradient Map API** - Access real-time and historical air quality data from the global AirGradient monitoring network.

## Measurement Types
- **pm25**: Fine particulate matter (≤2.5µm) in µg/m³
- **pm10**: Coarse particulate matter (≤10µm) in µg/m³  
- **atmp**: Ambient temperature in °C
- **rhum**: Relative humidity in %
- **rco2**: Carbon dioxide in ppm
- **o3**: Ozone in µg/m³
- **no2**: Nitrogen dioxide in µg/m³

## Coordinate System
All coordinates use **WGS84**: Longitude (-180° to +180°), Latitude (-90° to +90°)

## Data Sources
- AirGradient community sensors (CC-BY-SA 4.0)
- OpenAQ integration (CC-BY 4.0)
- Updates every 5-15 minutes
`,
    )
    .setVersion('1.0')
    .setContact('AirGradient Support', 'https://www.airgradient.com', 'support@airgradient.com')
    .addServer('https://map-data-int.airgradient.com', 'Integration Server')
    .addServer('http://localhost:3001', 'Local Development')
    .addTag('Measurements', 'Current and historical air quality measurements')
    .addTag('Locations', 'Information about monitoring locations and sensors')
    .build();

  const documentFactory = () => SwaggerModule.createDocument(app, config);
  SwaggerModule.setup('map/api/v1/docs', app, documentFactory);

  await app.listen(process.env.PORT ?? 3000);
  logger.log('Application Started');
}
bootstrap();
