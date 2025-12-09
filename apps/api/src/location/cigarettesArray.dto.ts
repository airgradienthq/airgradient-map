import { ApiProperty } from '@nestjs/swagger';

export class CigaretteTimeframeDto {
  @ApiProperty({
    description: 'Timeframe label (e.g., "1d", "7d", "30d")',
    example: '7d',
  })
  timeframe: string;

  @ApiProperty({
    description: 'Cigarette equivalent value for this timeframe',
    example: 2.15,
    nullable: true,
  })
  value: number | null;

  constructor(timeframe: string, value: number | null) {
    this.timeframe = timeframe;
    this.value = value;
  }
}

export class CigarettesArrayDto {
  @ApiProperty({
    description: 'Location identifier',
    example: 123,
  })
  locationId: number;

  @ApiProperty({
    description: 'Array of cigarette equivalents for each requested timeframe',
    type: [CigaretteTimeframeDto],
    example: [
      { timeframe: '1d', value: 0.49 },
      { timeframe: '7d', value: 2.15 },
      { timeframe: '30d', value: 9.2 },
      { timeframe: '365d', value: 111.99 },
    ],
  })
  cigarettes: CigaretteTimeframeDto[];

  constructor(locationId: number, cigarettes: CigaretteTimeframeDto[]) {
    this.locationId = locationId;
    this.cigarettes = cigarettes;
  }
}
