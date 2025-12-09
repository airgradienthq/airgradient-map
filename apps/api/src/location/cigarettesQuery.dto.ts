import { IsOptional, IsArray, Matches } from 'class-validator';
import { Transform } from 'class-transformer';
import { ApiProperty } from '@nestjs/swagger';

export class CigarettesQueryDto {
  @ApiProperty({
    description:
      'Comma-separated string of timeframes in days. Format: number + "d" (days only). Examples: "1d", "7d", "30d", "365d". If omitted, returns default timeframes (1d, 7d, 30d, 365d).',
    example: '1d,7d,14d,30d',
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
  @Matches(/^\d+d$/, {
    each: true,
    message: 'Each timeframe must follow format: number + "d" (e.g., "1d", "7d", "30d", "365d")',
  })
  timeframes?: string[];
}
