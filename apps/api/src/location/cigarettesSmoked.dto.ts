import { ApiProperty } from '@nestjs/swagger';

export class CigarettesSmokedDto {
  @ApiProperty()
  last24hours: number;

  @ApiProperty()
  last7days: number;

  @ApiProperty()
  last30days: number;

  @ApiProperty()
  last365days: number;

  constructor(partial: Partial<CigarettesSmokedDto>) {
    Object.assign(this, partial);
  }
}
