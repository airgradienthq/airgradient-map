import { Controller, Get, Post } from '@nestjs/common';
import { WindDataService } from './wind-data.service';

@Controller('map/api/v1/wind-data')
export class WindDataController {
  constructor(private readonly windDataService: WindDataService) {}

  @Get('info')
  async getWindDataInfo() {
    return this.windDataService.getWindDataInfo();
  }

  @Post('update')
  async updateWindData() {
    return this.windDataService.updateWindData();
  }

  @Get('file')
  async getWindDataFile() {
    return this.windDataService.getWindData();
  }

  @Get('check-grib2json')
  async checkGrib2Json() {
    return this.windDataService.checkGrib2JsonAvailability();
  }
}