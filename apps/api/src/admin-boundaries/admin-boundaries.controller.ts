import { Controller, Get, Param, ValidationPipe } from '@nestjs/common';
import { UsePipes } from '@nestjs/common';
import { AdminBoundariesService } from './admin-boundaries.service';
import { GetAdminBoundaryParamsDto } from './dto/get-admin-boundary-params.dto';
import { GetAdminBoundaryResponseDto } from './dto/get-admin-boundary-response.dto';

@Controller('map/api/v1/admin-boundaries')
export class AdminBoundariesController {
  constructor(private readonly adminBoundariesService: AdminBoundariesService) {}

  @Get('{/:country}{/:level1}{/:level2}{/:level3}')
  @UsePipes(new ValidationPipe({ transform: true }))
  async getAdminBoundaries(
    @Param() getAdminBoundaryParamsDto: GetAdminBoundaryParamsDto,
  ): Promise<GetAdminBoundaryResponseDto> {
    return this.adminBoundariesService.getAdminBoundaries(getAdminBoundaryParamsDto);
  }
}
