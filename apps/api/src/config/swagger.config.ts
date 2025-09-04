import { DocumentBuilder } from '@nestjs/swagger';

export const createSwaggerConfig = () => {
  const isProduction = process.env.NODE_ENV === 'production';
  
  // Build the base configuration
  const builder = new DocumentBuilder()
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
- **o3**: Ozone 
- **no2**: Nitrogen dioxide 

## Coordinate System
All coordinates use **WGS84**: Longitude (-180° to +180°), Latitude (-90° to +90°)

## Data Sources
- AirGradient community sensors (CC-BY-SA 4.0)
- OpenAQ integration (CC-BY 4.0)
- Updates every 5-15 minutes
`,
    )
    .setVersion('1.0')
    .setContact(
      'AirGradient Support',
      'https://www.airgradient.com/support/',
      'support@airgradient.com',
    );

  if (isProduction) {
    builder
      .addServer('https://map-data-int.airgradient.com', 'Integration Server')
  } else {
    builder
      .addServer('http://localhost:3001', 'Local Development')
      .addServer('https://map-data-int.airgradient.com', 'Integration Server');
  }

  return builder
    .addTag('Measurements', 'Current and historical air quality measurements')
    .addTag('Locations', 'Information about monitoring locations and sensors')
    .build();
};
