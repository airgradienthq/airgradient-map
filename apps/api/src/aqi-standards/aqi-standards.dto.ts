import { ApiProperty } from '@nestjs/swagger';

export class AqiRangeDto {
  @ApiProperty({ description: 'Minimum value of the range', example: 0 })
  min!: number;

  @ApiProperty({ description: 'Maximum value of the range, null means open-ended', example: 35.4, nullable: true })
  max!: number | null;

  @ApiProperty({ description: 'Color key for this range', example: 'green' })
  color!: string;

  @ApiProperty({
    description: 'Localized labels for this range',
    type: 'object',
    additionalProperties: { type: 'string' },
    example: {
      en: 'Moderate',
      de: 'Mäßig',
    },
  })
  labels!: Record<string, string>;
}

export class AqiStandardDto {
  @ApiProperty({ description: 'Unique identifier of the standard', example: 'us_epa' })
  id!: string;

  @ApiProperty({ description: 'Display name of the standard', example: 'US EPA' })
  displayName!: string;

  @ApiProperty({ description: 'Pollutant measured', example: 'pm25' })
  pollutant!: string;

  @ApiProperty({ description: 'Ranges that define the AQI bands', type: [AqiRangeDto] })
  ranges!: AqiRangeDto[];
}

export class AqiStandardsDto {
  @ApiProperty({
    description: 'Which standards should be visible to clients',
    type: 'object',
    additionalProperties: { type: 'boolean' },
    example: { us_epa: true, german_lqi: false },
  })
  visibility!: Record<string, boolean>;

  @ApiProperty({
    description: 'Color palette used by standards, keyed by color id',
    type: 'object',
    additionalProperties: { type: 'string' },
    example: { green: '#58D32F', yellow: '#FFDA3E' },
  })
  colors!: Record<string, string>;

  @ApiProperty({
    description: 'Available AQI standards',
    type: [AqiStandardDto],
  })
  standards!: AqiStandardDto[];

  @ApiProperty({
    description: 'Supported locales for labels',
    type: [String],
    example: ['en', 'de', 'es'],
  })
  locales!: string[];
}
