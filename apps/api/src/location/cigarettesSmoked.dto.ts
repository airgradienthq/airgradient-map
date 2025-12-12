import { ApiProperty } from '@nestjs/swagger';

export class CigarettesSmokedDto {
  @ApiProperty({
    description: 'Cigarette equivalent for last 24 hours',
    example: 0.49,
    nullable: true,
    required: false,
  })
  last24hours?: number | null;

  @ApiProperty({
    description: 'Cigarette equivalent for last 7 days',
    example: 2.15,
    nullable: true,
    required: false,
  })
  last7days?: number | null;

  @ApiProperty({
    description: 'Cigarette equivalent for last 30 days',
    example: 9.2,
    nullable: true,
    required: false,
  })
  last30days?: number | null;

  @ApiProperty({
    description: 'Cigarette equivalent for last 365 days',
    example: 111.99,
    nullable: true,
    required: false,
  })
  last365days?: number | null;

  // Index signature for custom timeframes (without decorator to avoid TS1206 error)
  [key: string]: number | null | undefined;

  constructor(cigarettes: Record<string, number | null>) {
    Object.assign(this, cigarettes);
  }
}
