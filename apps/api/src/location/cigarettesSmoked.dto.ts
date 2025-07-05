import { ApiProperty } from '@nestjs/swagger';

class CigarettesSmoked {
  @ApiProperty()
  last24hours: number;

  @ApiProperty()
  last7days: number;

  @ApiProperty()
  last30days: number;

  @ApiProperty()
  last365days: number;

  constructor(partial: Partial<CigarettesSmoked>) {
    Object.assign(this, partial);
  }
}

export default CigarettesSmoked;