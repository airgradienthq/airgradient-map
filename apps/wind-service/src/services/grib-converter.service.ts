import { promisify } from 'util';
import { exec } from 'child_process';
import * as fs from 'fs';
import * as path from 'path';
import { converterLogger } from '../utils/logger';

const execAsync = promisify(exec);

export class GribConverterService {
  private readonly tempDir = path.join(process.cwd(), 'temp');

  async findGrib2Json(): Promise<string | null> {
    const searchPaths = [
      '/usr/local/bin/grib2json',
      'grib2json',
      '/usr/local/lib/node_modules/weacast-grib2json/bin/grib2json',
    ];

    converterLogger.debug('Searching for grib2json', { paths: searchPaths });

    for (const cmd of searchPaths) {
      try {
        await execAsync(`${cmd} --help 2>&1`);
        converterLogger.info('Found grib2json', { path: cmd });
        return cmd;
      } catch {
        // Continue searching
      }
    }

    converterLogger.error('grib2json not found in any search paths', { searchPaths });
    return null;
  }

  async convertGribToJson(grib2jsonPath: string, gribFile: string): Promise<any | null> {
    const outputU = path.join(this.tempDir, 'wind-u.json');
    const outputV = path.join(this.tempDir, 'wind-v.json');

    converterLogger.info('Starting GRIB to JSON conversion', {
      gribFile: path.basename(gribFile),
      converter: grib2jsonPath
    });

    try {
      // Convert U-component (eastward wind)
      converterLogger.debug('Converting U-component (eastward wind)');
      await execAsync(
        `${grib2jsonPath} --names --data --fp 2 --fs 103 --fv 10.0 -o ${outputU} ${gribFile}`,
        { timeout: 60000 }
      );

      // Convert V-component (northward wind)
      converterLogger.debug('Converting V-component (northward wind)');
      await execAsync(
        `${grib2jsonPath} --names --data --fp 3 --fs 103 --fv 10.0 -o ${outputV} ${gribFile}`,
        { timeout: 60000 }
      );

      // Read and parse JSON files
      converterLogger.debug('Reading converted JSON files');
      const uData = JSON.parse(fs.readFileSync(outputU, 'utf8'));
      const vData = JSON.parse(fs.readFileSync(outputV, 'utf8'));

      // Cleanup temp files
      converterLogger.debug('Cleaning up temporary files');
      fs.unlinkSync(outputU);
      fs.unlinkSync(outputV);
      fs.unlinkSync(gribFile);

      converterLogger.info('GRIB to JSON conversion completed successfully', {
        uDataPoints: uData[0]?.data?.length || 0,
        vDataPoints: vData[0]?.data?.length || 0
      });

      return [uData[0], vData[0]];
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Unknown error';
      converterLogger.error('GRIB conversion failed', {
        error: errorMessage,
        gribFile: path.basename(gribFile)
      });

      // Cleanup temp files on error
      try {
        if (fs.existsSync(outputU)) fs.unlinkSync(outputU);
        if (fs.existsSync(outputV)) fs.unlinkSync(outputV);
        if (fs.existsSync(gribFile)) fs.unlinkSync(gribFile);
      } catch (cleanupError) {
        converterLogger.warn('Failed to cleanup temp files after error', {
          error: cleanupError instanceof Error ? cleanupError.message : 'Unknown error'
        });
      }

      return null;
    }
  }
}