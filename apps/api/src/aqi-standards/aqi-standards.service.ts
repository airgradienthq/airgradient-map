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
    const candidates = [
      // When assets are copied next to compiled JS (dist/src/aqi-standards)
      resolve(__dirname, './aqi_standards.json'),
      // Fallback to source tree (useful in watch/dev or if asset copy failed)
      resolve(__dirname, '../aqi-standards/aqi_standards.json'),
      resolve(process.cwd(), 'src/aqi-standards/aqi_standards.json'),
    ];

    for (const path of candidates) {
      if (existsSync(path)) {
        return path;
      }
    }

    const attempted = candidates.join(', ');
    this.logger.error(`AQI standards file not found. Tried: ${attempted}`);
    throw new Error('AQI standards file is missing');
  }

  private generateEtag(content: string): string {
    return `W/"${createHash('sha1').update(content).digest('hex')}"`;
  }
}
