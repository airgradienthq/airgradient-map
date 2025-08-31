import { AnnotationOptions } from 'chartjs-plugin-annotation';
import { useWindowSize } from '@vueuse/core';

import { MEASURE_UNITS } from '~/constants/shared/measure-units';
import { useGeneralConfigStore } from '~/store/general-config-store';
import { HistoryPeriodConfig, MeasureNames } from '~/types';
import { getChartFontSize, getColorForMeasure } from '~/utils';
import { pm25ToAQI } from '~/utils/aqi';

export function useChartJsAnnotations({
  data,
  showWHO = true,
  showAverage = true
}: {
  data: number[];
  showWHO?: boolean;
  showAverage?: boolean;
}): Record<string, AnnotationOptions> {
  const { width } = useWindowSize();

  const { selectedHistoryPeriod: period, selectedMeasure: measure } = useGeneralConfigStore();

  const fontSize = getChartFontSize(measure);
  const { avgXAdjust, WHOXAdjust } = getAnnotationLabelXAdjust(measure, width.value);

  const annotations: Record<string, AnnotationOptions> = {};

  if (showWHO && (measure === MeasureNames.PM25 || measure === MeasureNames.PM_AQI)) {
    annotations.who = createWHOAnnotation(measure, fontSize, WHOXAdjust, width.value);
  }

  if (showAverage) {
    const avgData = getAveragesData(data, measure, period);
    if (avgData) {
      annotations.avgLine = createAverageAnnotation(avgData, fontSize, avgXAdjust, width.value);
    }
  }

  return annotations;
}

function getAveragesData(
  data: number[],
  measure: MeasureNames,
  period: HistoryPeriodConfig
): {
  avgValue: number;
  avgColor: string;
  avgBgColor: string;
  avgPeriodLabel: string;
  avgLabel: string;
} | null {
  if (!data.length) return null;

  const isCO2 = measure === MeasureNames.CO2;
  const averageApproximation = isCO2 ? 0 : 1;
  const avgLabel = MEASURE_UNITS[measure] || '';

  const total = data.reduce((sum, val) => sum + val, 0);
  let avgValue = total / data.length;

  avgValue = Number(avgValue.toFixed(averageApproximation));

  if (measure === MeasureNames.PM_AQI) {
    avgValue = pm25ToAQI(avgValue);
  }

  const avgColor = getColorForMeasure(measure, avgValue, 700).bgColor;
  const avgBgColor = getColorForMeasure(measure, avgValue, 100).bgColor;

  let avgPeriodLabel = '';
  if (window.innerWidth > 450) {
    avgPeriodLabel = period.label.replace('Last', '').trim();
  }

  return {
    avgValue,
    avgColor,
    avgBgColor,
    avgLabel,
    avgPeriodLabel
  };
}

function getAnnotationLabelXAdjust(
  measure: string,
  width: number
): {
  avgXAdjust: number;
  WHOXAdjust: number;
} {
  const isPM25 = measure === MeasureNames.PM25 || measure === MeasureNames.PM_AQI;

  if (width < 450) return { avgXAdjust: isPM25 ? 160 : 3, WHOXAdjust: 1 };
  if (width < 768) return { avgXAdjust: isPM25 ? 210 : 10, WHOXAdjust: 10 };

  return { avgXAdjust: isPM25 ? 300 : 10, WHOXAdjust: 10 };
}

function createWHOAnnotation(
  measure: string,
  fontSize: number,
  xAdjust: number,
  width: number
): AnnotationOptions {
  const isAQI = measure === MeasureNames.PM_AQI;
  const yValue = isAQI ? 21 : 5;
  const label = width < 450 ? 'WHO Annual AQ Guideline' : 'WHO Annual Air Quality Guideline';

  return {
    display: true,
    drawTime: 'afterDatasetsDraw',
    type: 'line',
    yMin: yValue,
    yMax: yValue,
    borderColor: '#005121',
    borderWidth: 2,
    label: {
      display: true,
      backgroundColor: '#D2F7D3',
      position: 'start',
      padding: width < 450 ? { x: 5, y: 3 } : { x: 10, y: 8 },
      borderColor: '#005121',
      borderWidth: 2,
      color: '#212121',
      font: { family: '"Cabin", sans-serif', size: fontSize },
      xAdjust,
      content: label
    }
  };
}

function createAverageAnnotation(
  data: ReturnType<typeof getAveragesData>,
  fontSize: number,
  xAdjust: number,
  width: number
): AnnotationOptions {
  const { avgValue, avgColor, avgBgColor, avgLabel, avgPeriodLabel } = data;

  return {
    display: true,
    drawTime: 'afterDatasetsDraw',
    type: 'line',
    yMin: avgValue,
    yMax: avgValue,
    borderColor: avgColor,
    borderWidth: 2,
    borderDash: [2, 2],
    label: {
      display: true,
      backgroundColor: avgBgColor,
      position: 'start',
      padding: width < 450 ? { x: 5, y: 3 } : { x: 10, y: 8 },
      borderColor: avgColor,
      borderWidth: 2,
      color: avgColor,
      font: { family: '"Cabin", sans-serif', size: fontSize },
      xAdjust,
      content: `${avgPeriodLabel} Average: ${avgValue}${avgLabel}`
    }
  };
}
