import { DataSource } from 'src/types';

function getEPACorrectedPM(rawPM: number, rawRhum: number): number {
  let result = 0;

  if ([undefined, null].includes(rawPM)) {
    return null;
  }

  if ([undefined, null].includes(rawRhum)) {
    return null;
  }

  if (rawPM === 0) {
    return rawPM;
  }

  if (rawPM < 30) {
    // AGraw <30:
    // PM2.5 = [0.524 x AGraw] – [0.0862 x RH] + 5.75
    result = 0.524 * rawPM - 0.0862 * rawRhum + 5.75;
  } else if (rawPM < 50) {
    // 30≤ AGraw <50:
    // PM2.5 = [0.786 x (AGraw/20 - 3/2) + 0.524 x (1 - (AGraw/20 - 3/2))] x AGraw – [0.0862 x RH] + 5.75
    //
    result =
      (0.786 * (rawPM / 20 - 3 / 2) + 0.524 * (1 - (rawPM / 20 - 3 / 2))) * rawPM -
      0.0862 * rawRhum +
      5.75;
  } else if (rawPM < 210) {
    // 50 ≤ AGraw <210:
    // PM2.5 = [0.786 x AGraw] – [0.0862 x RH] + 5.75
    //

    result = 0.786 * rawPM - 0.0862 * rawRhum + 5.75;
  } else if (rawPM < 260) {
    // 210 ≤ AGraw <260:
    // PM2.5 = [0.69 x (AGraw/50 – 21/5) + 0.786 x (1 - (AGraw/50 – 21/5))] x AGraw – [0.0862 x RH x (1 - (AGraw/50 – 21/5))] + [2.966 x (AGraw/50 –21/5)] + [5.75 x (1 - (AGraw/50 – 21/5))] + [8.84 x (10-4) x AGraw2x (AGraw/50 – 21/5)]
    //

    result =
      (0.69 * (rawPM / 50 - 21 / 5) + 0.786 * (1 - (rawPM / 50 - 21 / 5))) * rawPM -
      0.0862 * rawRhum * (1 - (rawPM / 50 - 21 / 5)) +
      2.966 * (rawPM / 50 - 21 / 5) +
      5.75 * (1 - (rawPM / 50 - 21 / 5)) +
      8.84 * 0.0001 * Math.pow(rawPM, 2) * (rawPM / 50 - 21 / 5);
  } else {
    // 260 ≤ AGraw:
    //   PM2.5 = 2.966 + [0.69 x AGraw] + [8.84 x 10-4 x AGraw2]
    result = 2.966 + 0.69 * rawPM + 8.84 * 0.0001 * Math.pow(rawPM, 2);
  }

  return Math.max(Number(result.toFixed(1)), 0);
}

const DATA_SOURCES_REQUIRING_EPA_PM_CORRECTION = new Set<DataSource>([
  DataSource.AIRGRADIENT,
  DataSource.DUSTBOY,
]);

export function getPMWithEPACorrectionIfNeeded(
  dataSource: DataSource,
  rawPM: number,
  rawRhum: number,
): number {
  return DATA_SOURCES_REQUIRING_EPA_PM_CORRECTION.has(dataSource)
    ? getEPACorrectedPM(rawPM, rawRhum)
    : rawPM;
}
