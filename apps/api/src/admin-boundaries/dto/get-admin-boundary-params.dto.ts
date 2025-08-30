import { IsOptional, IsString, Length } from 'class-validator';
import { ApiProperty } from '@nestjs/swagger';
import { Transform } from 'class-transformer';

export class GetAdminBoundaryParamsDto {
  @ApiProperty({ description: 'Country name', example: 'Thailand', required: false })
  @Transform(({ value }) => (value === '{country}' ? undefined : value)) // for swagger
  @IsOptional()
  @IsString()
  @Length(2, 100)
  country?: string;

  @ApiProperty({ description: 'First administrative level', example: 'ChiangMai', required: false })
  @Transform(({ value }) => (value === '{level1}' ? undefined : value)) // for swagger
  @IsOptional()
  @IsString()
  @Length(2, 100)
  level1?: string;

  @ApiProperty({ description: 'Second administrative level', example: 'MaeRim', required: false })
  @Transform(({ value }) => (value === '{level2}' ? undefined : value)) // for swagger
  @IsOptional()
  @IsString()
  @Length(2, 100)
  level2?: string;

  @ApiProperty({ description: 'Third administrative level', example: 'RimTai', required: false })
  @Transform(({ value }) => (value === '{level3}' ? undefined : value)) // for swagger
  @IsOptional()
  @IsString()
  @Length(2, 100)
  level3?: string;
}
