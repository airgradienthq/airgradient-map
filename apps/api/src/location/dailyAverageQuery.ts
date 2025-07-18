import { ApiProperty } from '@nestjs/swagger';
import { IsDateString } from 'class-validator';

export class DailyAverageQuery {
    @ApiProperty({
        default: '2025-02-01',
        description: 'Start date in format "YYYY-MM-DD"',
    })
    @IsDateString()
    start: string;

    @ApiProperty({
        default: '2025-02-07',
        description: 'End date in format "YYYY-MM-DD"',
    })
    @IsDateString()
    end: string;
}