import { LocationHistoryDataItem } from '~/types';
import { getCO2Color, getPM25Color } from '~/utils/colors';
import { MeasureNames } from '~/types';
import { pm25ToAQI } from '~/utils/aqi';
import { CHART_MIN_VISIBLE_VALUE } from '~/constants/shared/chart-min-visible-value';

interface UseChartjsDataParams {
  data: LocationHistoryDataItem[];
  measure: MeasureNames;
}

export function useChartjsData({ data, measure }: UseChartjsDataParams) {
  const chartLabels: string[] = [];
  const chartValues: number[] = [];
  const unProcessedChartValues: number[] = [];
  const barColors: string[] = [];

  const sortedData = [...data].sort(
    (a, b) => new Date(a.timebucket).getTime() - new Date(b.timebucket).getTime()
  );

  for (const item of sortedData) {
    const parsedValue =
      item.value !== null && item.value !== undefined ? parseFloat(item.value) : null;
    if (parsedValue === null || isNaN(parsedValue)) continue;

    const roundedValue = parseFloat(parsedValue.toFixed(2));
    chartLabels.push(item.timebucket);
    unProcessedChartValues.push(roundedValue);

    if (roundedValue === 0 && (measure === MeasureNames.PM25 || measure === MeasureNames.PM_AQI)) {
      chartValues.push(CHART_MIN_VISIBLE_VALUE);
      barColors.push(getPM25Color(CHART_MIN_VISIBLE_VALUE).bgColor);
    } else if (measure === MeasureNames.RCO2) {
      barColors.push(getCO2Color(roundedValue).bgColor);
      chartValues.push(roundedValue);
    } else {
      const valueToUse = measure === MeasureNames.PM_AQI ? pm25ToAQI(roundedValue) : roundedValue;
      chartValues.push(valueToUse);
      barColors.push(getPM25Color(roundedValue).bgColor);
    }
  }

  return {
    chartData: {
      labels: chartLabels,
      datasets: [
        {
          data: chartValues,
          backgroundColor: barColors,
          borderWidth: 0,
          barPercentage: 0.7,
          borderRadius: 3,
          borderSkipped: 'start' as const,
          categoryPercentage: 1.0,
          fill: false,
          minBarLength: 0
        }
      ]
    },
    chartValues: unProcessedChartValues
  };
}
