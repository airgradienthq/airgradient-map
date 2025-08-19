<template>
  <UiDialog
    :title="props.dialog?.data.location.locationName"
    :size="DialogSize.XL"
    :hideFooter="true"
    :dialog="props.dialog"
  >
    <template #header>
      <UiProgressBar :show="loading"></UiProgressBar>
      <v-card-title>
        <div class="d-flex align-center justify-center gap-2 gap-md-3 pl-2 py-2 pr-7 flex-wrap">
          <h5 class="m-0 location-name">{{ mapLocationData?.locationName }}</h5>
          <v-chip>
            {{
              mapLocationData?.sensorType === SensorType.reference ? 'Reference' : 'Small Sensor'
            }}</v-chip
          >
        </div>
      </v-card-title>
    </template>
    <template #body>
      <div style="height: 65px" class="chart-controls mb-5">
        <div>
          <div
            v-if="currentValueData"
            :style="{ backgroundColor: currentValueData.bgColor }"
            class="current-values py-2 px-3"
          >
            <div :class="currentValueData.textColor" class="main-current-value">
              <h4 :class="currentValueData.textColor" class="mb-2">
                {{ currentValueData.value }}
                <span class="unit-label">{{ currentValueData.unit }}</span>
              </h4>
              <p :class="currentValueData.textColor" class="mb-0 current-label">
                <span> Current <UiHTMLSafelabel :label="currentValueData.labelHTML" /> </span>
              </p>
            </div>
          </div>
        </div>

        <div class="d-flex align-center justify-center gap-2 hist-controls-container">
          <UiDropdownControl
            v-if="chartOptions && timezoneSelectShown"
            class="tz-control"
            width="200px"
            :selected-value="selectedHistoricalDataTimeZoneConfig.value"
            :options="historicalDataTimeZoneOptions"
            :disabled="loading"
            @change="handleHistoricalDataTimeZoneChange"
          >
          </UiDropdownControl>

          <UiDropdownControl
            v-if="chartOptions"
            class="period-control"
            :selected-value="generalConfigStore.selectedHistoryPeriod.value"
            :options="HISTORY_PERIODS"
            :disabled="loading"
            @change="handleChartPeriodChange"
          >
          </UiDropdownControl>
        </div>
      </div>
      <ClientOnly>
        <div class="chart-container">
          <Bar v-if="chartData && chartOptions" :data="chartData" :options="chartOptions" />
          <div
            v-else-if="!loading && historyError"
            class="d-flex flex-column align-center justify-center gap-2"
          >
            <v-icon color="error" size="48">mdi-chart-line-variant</v-icon>
            <p>Unable to load historical data</p>
            <UiButton variant="outlined" size="small" color="primary" @click="retryFetchHistory">
              Retry
            </UiButton>
          </div>
        </div>
      </ClientOnly>
      <div class="mt-4">
        <UiColorsLegend :size="ColorsLegendSize.SMALL" />
      </div>
      <p style="min-height: 20px" class="mb-0 mt-4">
        <small v-if="chartOptions">
          Air quality data for this location is provided by
          <span v-if="!locationDetails?.url">
            {{
              !locationDetails?.ownerName || locationDetails?.ownerName === 'unknown'
                ? ' an anonymous contributor '
                : locationDetails?.ownerName
            }}
          </span>
          <span v-else>
            <a :href="locationDetails?.url" target="_blank">
              {{
                !locationDetails?.ownerName || locationDetails?.ownerName === 'unknown'
                  ? ' an anonymous contributor '
                  : locationDetails?.ownerName
              }}
              <v-icon size="16">mdi-open-in-new</v-icon>
            </a>
          </span>
          via
          <span v-if="locationDetails?.dataSource === 'OpenAQ'">
            {{ locationDetails?.provider }} and
            <a href="https://openaq.org/" target="_blank">
              OpenAQ <v-icon size="16">mdi-open-in-new</v-icon></a
            >
          </span>

          <span v-if="locationDetails?.dataSource === 'AirGradient'">
            <a href="https://www.airgradient.com/" target="_blank">
              AirGradient
              <v-icon size="16">mdi-open-in-new</v-icon>
            </a>
          </span>

          under
          {{ locationDetails?.licenses[0] }}
        </small>
      </p>
    </template>
  </UiDialog>
</template>

<script setup lang="ts">
  import {
    ColorsLegendSize,
    DialogSize,
    HistoricalDataTimeZone,
    HistoricalDataTimeZoneConfig,
    HistoryBucket,
    HistoryPeriod,
    HistoryPeriodConfig,
    LocationDetails,
    MeasureNames,
    SensorType
  } from '~/types';
  import { onMounted, ref, Ref, watch, computed, onUnmounted } from 'vue';
  import { Bar } from 'vue-chartjs';
  import { ChartData, ChartOptions } from 'chart.js';

  import { DialogInstance, AGMapLocationData, LocationHistoryData } from '~/types';
  import { useGeneralConfigStore } from '~/store/general-config-store';
  import { useRuntimeConfig } from 'nuxt/app';
  import { getDateRangeFromToday } from '~/utils/date';
  import { HISTORY_PERIODS } from '~/constants/shared/chart-periods';
  import { useChartjsOptions } from '~/composables/shared/historical-data/useChartJsOptions';
  import { useChartjsData } from '~/composables/shared/historical-data/useChartJsData';
  import { MEASURE_LABELS_SUBSCRIPTS } from '~/constants/shared/measure-labels';
  import { pm25ToAQI } from '~/utils/aqi';
  import { getAQIColor, getCO2Color, getPM25Color } from '~/utils';
  import { MEASURE_UNITS } from '~/constants/shared/measure-units';
  import { useChartJsAnnotations } from '~/composables/shared/historical-data/useChartJsAnnotations';
  import { useIntervalRefresh } from '~/composables/shared/useIntervalRefresh';
  import {
    MINUTELY_HISTORICAL_DATA_REFRESH_INTERVAL,
    HOURLY_HISTORICAL_DATA_REFRESH_INTERVAL
  } from '~/constants/map/refresh-interval';
  import { HISTORICAL_DATA_TIMEZONE_OPTIONS } from '~/constants';
  import { useHistoricalDataTimezone } from '~/composables/shared/useHistoricalDataTimezone';
  import { AnnotationOptions } from 'chartjs-plugin-annotation';
  import { DateTime } from 'luxon';
  import { useApiErrorHandler } from '~/composables/shared/useApiErrorHandler';

  const props = defineProps<{
    dialog: DialogInstance<{ location: AGMapLocationData }>;
  }>();

  const apiUrl = useRuntimeConfig().public.apiUrl;
  const generalConfigStore = useGeneralConfigStore();
  const { getTimezoneLabel, userTimezone } = useHistoricalDataTimezone();
  const { handleApiError } = useApiErrorHandler();

  const mapLocationData: Ref<AGMapLocationData> = ref(null);
  const locationHistoryData: Ref<LocationHistoryData> = ref(null);
  const locationDetails: Ref<LocationDetails> = ref(null);
  const chartData: Ref<ChartData<'bar'>> = ref(null);
  const chartOptions: Ref<ChartOptions<'bar'>> = ref(null);
  const historyLoading: Ref<boolean> = ref(false);
  const detailsLoading: Ref<boolean> = ref(false);
  const historyError: Ref<boolean> = ref(false);
  const loading: Ref<boolean> = computed(() => historyLoading.value || detailsLoading.value);

  const timezoneSelectShown: Ref<boolean> = computed(() => {
    const userOffset = DateTime.now().toFormat('ZZ');
    const locationOffset = DateTime.now().setZone(locationDetails?.value?.timezone).toFormat('ZZ');
    return userOffset !== locationOffset;
  });

  const historicalDataTimeZoneOptions: Ref<HistoricalDataTimeZoneConfig[]> = computed(() => {
    return HISTORICAL_DATA_TIMEZONE_OPTIONS.map(option => {
      let label = option.label;
      const timezone =
        option.value === HistoricalDataTimeZone.USER
          ? userTimezone
          : locationDetails?.value?.timezone;
      if (timezone) {
        label = getTimezoneLabel(timezone);
      }
      return {
        value: option.value,
        label
      };
    });
  });

  const selectedHistoricalDataTimeZoneConfig: Ref<HistoricalDataTimeZoneConfig> = computed(() => {
    return historicalDataTimeZoneOptions.value.find(
      option => option.value === generalConfigStore.selectedHistoricalDataTimeZoneConfig
    );
  });

  const refreshIntervalDuration = computed(() => {
    const period = generalConfigStore.selectedHistoryPeriod;
    return period.defaultBucketSize === HistoryBucket.MINUTES_15
      ? MINUTELY_HISTORICAL_DATA_REFRESH_INTERVAL
      : HOURLY_HISTORICAL_DATA_REFRESH_INTERVAL;
  });

  const { startRefreshInterval, stopRefreshInterval, resetIntervalDuration } = useIntervalRefresh(
    () => {
      chartOptions.value.animation = false;
      return fetchLocationHistory(mapLocationData.value?.locationId);
    },
    refreshIntervalDuration.value,
    {
      skipFirstRefresh: true,
      onError: error => {
        handleApiError(error, 'Failed to refresh chart data');
      },
      skipOnVisibilityHidden: true
    }
  );

  const currentValueData: Ref<{
    bgColor: string;
    value: number;
    textColor: string;
    labelHTML: string;
    unit: string;
  }> = computed(() => {
    if (!locationHistoryData.value) {
      return null;
    }

    let colorConfig: { bgColor: string; textColorClass: string } = {
      bgColor: '',
      textColorClass: ''
    };

    let value = Math.round(mapLocationData.value.value);

    switch (generalConfigStore.selectedMeasure) {
      case MeasureNames.PM_AQI:
        value = pm25ToAQI(value);
        colorConfig = getAQIColor(value);
        break;
      case MeasureNames.CO2:
        colorConfig = getCO2Color(value);
        break;
      default:
        colorConfig = getPM25Color(value);
        break;
    }

    return {
      bgColor: colorConfig.bgColor,
      value: value,
      unit: MEASURE_UNITS[generalConfigStore.selectedMeasure],
      labelHTML: MEASURE_LABELS_SUBSCRIPTS[generalConfigStore.selectedMeasure],
      textColor: colorConfig.textColorClass
    };
  });

  async function fetchLocationDetails(locationId: number): Promise<void> {
    detailsLoading.value = true;
    try {
      const response = await $fetch<LocationDetails>(`${apiUrl}/locations/${locationId}`, {
        retry: 1
      });
      locationDetails.value = response;
    } catch (error) {
      handleApiError(error, 'Failed to load location details');
    } finally {
      detailsLoading.value = false;
    }
  }

  async function fetchLocationHistory(locationId: number): Promise<LocationHistoryData> {
    historyLoading.value = true;
    historyError.value = false;

    const { start, end } = getDateRangeFromToday(
      generalConfigStore.selectedHistoryPeriod.unit,
      generalConfigStore.selectedHistoryPeriod.count
    );
    const measure =
      generalConfigStore.selectedMeasure === MeasureNames.PM_AQI
        ? MeasureNames.PM25
        : generalConfigStore.selectedMeasure;

    try {
      const response = await $fetch<LocationHistoryData>(
        `${apiUrl}/locations/${locationId}/measures/history`,
        {
          params: {
            start,
            end,
            bucketSize: generalConfigStore.selectedHistoryPeriod.defaultBucketSize,
            measure
          },
          retry: 1
        }
      );
      locationHistoryData.value = response;
      return response;
    } catch (error) {
      historyError.value = true;
      handleApiError(error, 'Failed to load historical data');
      return null;
    } finally {
      historyLoading.value = false;
    }
  }

  function retryFetchHistory() {
    if (mapLocationData.value?.locationId) {
      fetchLocationHistory(mapLocationData.value.locationId);
    }
  }

  function handleHistoricalDataTimeZoneChange(timezone: HistoricalDataTimeZone) {
    useGeneralConfigStore().setSelectedHistoricalDataTimeZoneConfig(timezone);
  }

  function handleChartPeriodChange(period: HistoryPeriod) {
    chartOptions.value.animation = {
      duration: 100
    };
    const periodConfig = HISTORY_PERIODS.find(
      (periodConfig: HistoryPeriodConfig) => periodConfig.value === period
    );
    useGeneralConfigStore().setSelectedHistoryPeriod(periodConfig);
    fetchLocationHistory(mapLocationData.value.locationId);
    resetIntervalDuration(refreshIntervalDuration.value);
  }

  onMounted(() => {
    mapLocationData.value = props.dialog?.data.location;
    if (mapLocationData.value) {
      fetchLocationHistory(mapLocationData.value.locationId);
      fetchLocationDetails(mapLocationData.value.locationId);
      startRefreshInterval();
    }
  });

  onUnmounted(() => {
    stopRefreshInterval();
  });

  watch(locationHistoryData, (newData: LocationHistoryData) => {
    if (newData && newData.data) {
      const { chartData: data, chartValues } = useChartjsData({
        data: newData.data,
        measure: generalConfigStore.selectedMeasure
      });
      chartData.value = data;

      const annotations = useChartJsAnnotations({
        data: chartValues
      });

      chartOptions.value = useChartjsOptions({
        measure: generalConfigStore.selectedMeasure,
        animated: true,
        annotations,
        timezone:
          selectedHistoricalDataTimeZoneConfig.value.value === HistoricalDataTimeZone.USER
            ? userTimezone
            : locationDetails?.value?.timezone
      });
    }
  });

  watch(selectedHistoricalDataTimeZoneConfig, (newConfig: HistoricalDataTimeZoneConfig) => {
    if (!chartOptions.value) {
      return;
    }
    chartOptions.value = useChartjsOptions({
      measure: generalConfigStore.selectedMeasure,
      animated: true,
      annotations: chartOptions.value.plugins.annotation.annotations as Record<
        string,
        AnnotationOptions
      >,
      timezone:
        newConfig.value === HistoricalDataTimeZone.LOCAL ? locationDetails?.value?.timezone : null
    });
  });
</script>

<style lang="scss" scoped>
  .chart-container {
    height: 350px;
    width: 100%;
  }

  @media (max-width: 768px) {
    .chart-container {
      height: 270px;
    }
  }

  .location-name {
    white-space: normal;
    text-align: center;
  }

  .chart-controls {
    display: flex;
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
  }

  .period-control {
    max-width: 165px;
  }

  .tz-control {
    max-width: fit-content;
  }

  .current-values {
    border-radius: 5px;
    display: flex;
    gap: 15px;
    align-items: center;
    justify-content: center;
  }

  .text-dark .measure-icon {
    filter: brightness(0) invert(0);
  }

  .unit-label {
    font-size: var(--font-size-sm);
  }

  .current-label {
    font-size: var(--font-size-sm);
    font-weight: var(--font-weight-medium);
  }

  .hist-controls-container {
    @include tablet {
      flex-direction: column;
    }
  }

  .headless {
    .chart-container {
      height: 270px;
    }

    .chart-controls {
      margin-bottom: 25px !important;
      margin-top: -20px !important;
    }
  }
</style>
