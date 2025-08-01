import { Controller, Get, Post, Res } from '@nestjs/common';
import { Response } from 'express';
import * as path from 'path';
import { WindDataService } from './wind-data.service';

@Controller('wind-data')
export class WindDataController {
  constructor(private readonly windDataService: WindDataService) {}

  @Get('info')
  async getWindDataInfo() {
    return this.windDataService.getWindDataInfo();
  }

  @Post('update')
  async manualUpdate() {
    return this.windDataService.manualUpdate();
  }

  @Get('file')
  async getWindDataFile(@Res() res: Response) {
    const filePath = path.join(
      process.cwd(),
      'public',
      'data',
      'wind',
      'current-wind-surface-level-gfs-1.0.json',
    );
    res.set('Cache-Control', 'no-cache');
    res.set('Content-Type', 'application/json');
    res.sendFile(filePath);
  }

  @Get('check-grib2json')
  async checkGrib2JsonInstallation() {
    return this.windDataService.checkGrib2JsonInstallation();
  }
}
