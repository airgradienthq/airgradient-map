import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';

class MeasurementCluster {
  @ApiProperty({ description: "Either 'cluster' or 'sensor'" })
  type: string;

  @ApiProperty()
  latitude: number;

  @ApiProperty()
  longitude: number;

  @ApiPropertyOptional({ description: 'Field available if type is cluster' })
  sensorsCount?: number;

  @ApiPropertyOptional({ description: 'Field available if type is sensor' })
  locationId?: number;

  @ApiPropertyOptional({ description: 'Field available if type is sensor' })
  locationName?: string;

  @ApiPropertyOptional({
    description:
      "The type of the sensor, either 'Reference' or `Small Sensor`. Field available if type is sensor",
  })
  sensorType?: string;

  @ApiProperty({
    description:
      'Measurement value based on measure query provided; if type is cluster, value is averaged',
  })
  value: number;

  constructor(clusters: any) {
    if (clusters.properties.cluster) {
      this.type = 'cluster';
      this.sensorsCount = clusters.properties.point_count;
      this.value = Math.round(clusters.properties.sum / clusters.properties.point_count);
    } else {
      this.type = 'sensor';
      this.locationId = clusters.properties.locationId;
      this.locationName = clusters.properties.locationName;
      this.sensorType = clusters.properties.sensorType;
      this.value = Math.round(clusters.properties.value);
    }

    this.latitude = clusters.geometry.coordinates[0];
    this.longitude = clusters.geometry.coordinates[1];
  }
}

export default MeasurementCluster;
