import { ApiProperty } from '@nestjs/swagger';

export class CigarettesSmokedDto {
  @ApiProperty()
  last24hours: number | null;

  @ApiProperty()
  last7days: number | null;

  @ApiProperty()
  last30days: number | null;

  @ApiProperty()
  last365days: number | null;

  constructor(partial: Partial<CigarettesSmokedDto>) {
    Object.assign(this, partial);
  }
}
