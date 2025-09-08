import { IsOptional, IsArray, Matches } from 'class-validator';
import { Transform } from 'class-transformer';
import { ApiProperty } from '@nestjs/swagger';

export class AveragesQueryDto {
  @ApiProperty({
    description:
      'Comma-separated string of time periods. Format: number + time unit. Units: m (minutes), h (hours), d (days), w (weeks). Minimum period: 15 minutes. Examples: "15m", "2h", "13d", "1w". Recommended periods: 6h, 24h, 7d, 30d, 90d. If omitted, returns default periods (6h, 24h, 7d, 30d, 90d).',
    example: '6h,24h,7d,13d',
    required: false,
    type: String,
  })
  @IsOptional()
  @Transform(({ value }) => {
    if (typeof value === 'string') {
      return value.split(',').map(p => p.trim());
    }
    return value;
  })
  @IsArray()
  @Matches(/^(?:1[5-9]|[2-9][0-9]|[1-9][0-9]{2,})m$|^\d+[hdw]$/, {
    each: true,
    message: 'Each period must follow format: number + unit (m/h/d/w). Minimum period is 15 minutes, e.g., "15m", "6h", "13d", "2w"',
  })
  periods?: string[];
}
