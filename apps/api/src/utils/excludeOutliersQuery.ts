import { IsBoolean, IsOptional } from 'class-validator';
import { ApiProperty } from '@nestjs/swagger';
import { Transform } from 'class-transformer';

class ExcludeOutliersQuery {
  @ApiProperty({
    required: false,
    description: 'Exclude outliers from the measurement data or not',
  })
  @IsOptional()
  @IsBoolean()
  @Transform(({ value }) => {
    if (value === 'true' || value === true) return true;
    if (value === 'false' || value === false) return false;
    return Boolean(value);
  })
  excludeOutliers: boolean = true;
}

export default ExcludeOutliersQuery;
