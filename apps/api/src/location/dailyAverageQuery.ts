import { ApiProperty } from '@nestjs/swagger';
import { IsDateString } from 'class-validator';

export class DailyAverageQuery {
    @ApiProperty({
        description: 'Start date in format "YYYY-MM-DD"',
        example: '2025-04-16',
    })
    @IsDateString()
    start: string;

    @ApiProperty({
        description: 'End date in format "YYYY-MM-DD"',
        example: '2025-04-20',
    })
    @IsDateString()
    end: string;
}