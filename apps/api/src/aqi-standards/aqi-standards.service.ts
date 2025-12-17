import { Injectable, Logger } from '@nestjs/common';
import { existsSync, readFileSync } from 'fs';
import { createHash } from 'crypto';
import { resolve } from 'path';
import { AqiStandardsDto } from './aqi-standards.dto';

@Injectable()
export class AqiStandardsService {
  private readonly logger = new Logger(AqiStandardsService.name);
  private readonly payload: AqiStandardsDto;
  private readonly etag: string;

  constructor() {
    const filePath = this.resolveFilePath();
    const fileContent = readFileSync(filePath, 'utf8');
    this.payload = JSON.parse(fileContent) as AqiStandardsDto;
    this.etag = this.generateEtag(fileContent);
  }

  getAqiStandards(): { payload: AqiStandardsDto; etag: string } {
    return { payload: this.payload, etag: this.etag };
  }

  private resolveFilePath(): string {
    const filePath = resolve(__dirname, '../../../shared/aqi-bands/aqi_standards.json');
    if (!existsSync(filePath)) {
      this.logger.error(`AQI standards file not found at ${filePath}`);
      throw new Error('AQI standards file is missing');
    }
    return filePath;
  }

  private generateEtag(content: string): string {
    return `W/"${createHash('sha1').update(content).digest('hex')}"`;
  }
}
