/**
 * Converts PM2.5 concentration to US AQI
 * Based on EPA's breakpoint table: https://www.epa.gov/system/files/documents/2024-02/pm-naaqs-air-quality-index-fact-sheet.pdf
 * @param {number} pm25 - PM2.5 concentration in μg/m³
 * @returns {number} US AQI value
 */
export function pm25ToAQI(pm25: number): number {
  let result = null;
  if ([undefined, null].includes(pm25)) {
    result = null;
  } else if (pm25 <= 9.0) {
    result = ((50 - 0) / (9.0 - 0.0)) * (pm25 - 0.0) + 0;
  } else if (pm25 <= 35.4) {
    result = ((100 - 51) / (35.4 - 9.1)) * (pm25 - 9.1) + 51;
  } else if (pm25 <= 55.4) {
    result = ((150 - 101) / (55.4 - 35.5)) * (pm25 - 35.5) + 101;
  } else if (pm25 <= 125.4) {
    result = ((200 - 151) / (125.4 - 55.5)) * (pm25 - 55.5) + 151;
  } else if (pm25 <= 225.4) {
    result = ((300 - 201) / (225.4 - 125.5)) * (pm25 - 125.5) + 201;
  } else {
    // Values above 500 should continue using the same SLOPE as the Hazardous category cw 2024-09-07
    result = ((500 - 301) / (325.4 - 225.5)) * (pm25 - 225.5) + 301;
  }
  if (result < 0) {
    result = 0;
  }
  return result && Number(result.toFixed(1));
}
