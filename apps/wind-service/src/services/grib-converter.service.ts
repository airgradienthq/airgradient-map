import { promisify } from 'util';
import { exec } from 'child_process';
import * as fs from 'fs';
import * as path from 'path';

const execAsync = promisify(exec);

export class GribConverterService {
  private readonly tempDir = path.join(process.cwd(), 'temp');

  async findGrib2Json(): Promise<string | null> {

    for (const cmd of [
      '/usr/local/bin/grib2json',
      'grib2json',
      '/usr/local/lib/node_modules/weacast-grib2json/bin/grib2json',
    ]) {
      try {
        await execAsync(`${cmd} --help 2>&1`);
        return cmd;
      } catch {
        // Continue searching
      }
    }

    console.log('grib2json not found');
    return null;
  }

  async convertGribToJson(grib2jsonPath: string, gribFile: string): Promise<any | null> {
    try {
      const outputU = path.join(this.tempDir, 'wind-u.json');
      const outputV = path.join(this.tempDir, 'wind-v.json');

      // Convert U-component
      await execAsync(
        `${grib2jsonPath} --names --data --fp 2 --fs 103 --fv 10.0 -o ${outputU} ${gribFile}`,
        { timeout: 60000 }
      );

      // Convert V-component
      await execAsync(
        `${grib2jsonPath} --names --data --fp 3 --fs 103 --fv 10.0 -o ${outputV} ${gribFile}`,
        { timeout: 60000 }
      );

      // Read and combine
      const uData = JSON.parse(fs.readFileSync(outputU, 'utf8'));
      const vData = JSON.parse(fs.readFileSync(outputV, 'utf8'));

      // Cleanup temp files
      fs.unlinkSync(outputU);
      fs.unlinkSync(outputV);
      fs.unlinkSync(gribFile);

      return [uData[0], vData[0]];
    } catch (error) {
      console.error('Conversion failed:', error);
      return null;
    }
  }
}