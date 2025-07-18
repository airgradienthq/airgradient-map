import { ApiProperty } from '@nestjs/swagger';

export class LocationDailyAverageDto {
  @ApiProperty({
    example: '1',
  })
  locationId: number;

  @ApiProperty({
    example: '2025-04-16',
  })
  startDate: string;

  @ApiProperty({
    example: '2025-04-20',
  })
  endDate: string;

  @ApiProperty({
    type: 'array',
    items: {
      type: 'object',
      properties: {
        date: { type: 'string', example: '2025-07-16' },
        avgPM25: {
          type: 'number',
          nullable: true,
          example: 27.5,
        },
      },
    },
  })
  dailyAverages: { date: string; [key: string]: string | number | null }[];

  constructor(partial: Partial<LocationDailyAverageDto>) {
    Object.assign(this, partial);
  }
}
