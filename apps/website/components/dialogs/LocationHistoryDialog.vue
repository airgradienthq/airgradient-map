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
              mapLocationData?.sensorType === SensorType.reference
                ? $t('reference')
                : $t('small_sensor')
            }}</v-chip
          >
        </div>
      </v-card-title>
    </template>
    <template #body>
      <div>
        <div style="height: 65px" class="chart-controls mb-4 mb-md-5">
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
                  <span
                    >{{ $t('current') }} <UiHTMLSafelabel :label="currentValueData.labelHTML" />
                  </span>
                </p>
              </div>
            </div>
          </div>

          <div class="d-flex align-center justify-center gap-1 gap-md-2 hist-controls-container">
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
              :translate="true"
              :disabled="loading"
              @change="handleChartPeriodChange"
            >
            </UiDropdownControl>
          </div>
        </div>

        <div v-if="showOutlierExplainPanel" class="outlier-explain mb-4">
          <div class="d-flex align-center justify-space-between gap-2 flex-wrap">
            <div class="d-flex align-center gap-2 flex-wrap">
              <v-chip
                v-if="outlierExplain"
                :color="outlierExplain.isOutlier ? 'error' : 'success'"
                size="small"
              >
                {{ outlierExplain.isOutlier ? 'OUTLIER' : 'INLIER' }}
              </v-chip>
              <small v-if="outlierExplain">{{ outlierExplain.decision?.message }}</small>
              <small v-else-if="outlierExplainLoading">Loading outlier details…</small>
              <small v-else-if="outlierExplainError">Unable to load outlier details</small>
            </div>

            <UiButton
              v-if="outlierExplainError && !outlierExplainLoading"
              variant="outlined"
              size="small"
              color="primary"
              @click="retryFetchOutlierExplain"
            >
              Retry
            </UiButton>
          </div>

          <div v-if="outlierExplain" class="outlier-explain-details mt-2">
            <div class="outlier-explain-section">
              <div class="section-title">Input</div>
              <div class="kv">
                <span class="k">PM2.5 (raw)</span>
                <span class="v">{{ outlierExplain.pm25 }}</span>
              </div>
              <div class="kv">
                <span class="k">Measured at</span>
                <span class="v">{{ outlierExplain.measuredAt }}</span>
              </div>
              <div class="kv">
                <span class="k">Stored outlier flag</span>
                <span class="v">{{ outlierExplain.storedIsPm25Outlier }}</span>
              </div>
              <div class="kv">
                <span class="k">Data source</span>
                <span class="v">{{ outlierExplain.dataSource }}</span>
              </div>
            </div>

            <div class="outlier-explain-section">
              <div class="section-title">Spatial check</div>
              <div class="kv">
                <span class="k">Neighbors</span>
                <span class="v">
                  {{ outlierExplain.checks?.spatial?.neighborCount }} (min
                  {{ outlierExplain.params?.minNearbyCount }})
                </span>
              </div>
              <div class="kv">
                <span class="k">Mean / Stddev</span>
                <span class="v">
                  {{ outlierExplain.checks?.spatial?.mean ?? '—' }} /
                  {{ outlierExplain.checks?.spatial?.stddev ?? '—' }}
                </span>
              </div>
              <div class="kv">
                <span class="k">Mode</span>
                <span class="v">{{ outlierExplain.checks?.spatial?.mode ?? '—' }}</span>
              </div>
              <div v-if="outlierExplain.checks?.spatial?.mode === 'zscore'" class="kv">
                <span class="k">Z-score</span>
                <span class="v">
                  {{ outlierExplain.checks?.spatial?.zScore ?? '—' }} (threshold
                  {{ outlierExplain.params?.zScoreThreshold }})
                </span>
              </div>
              <div v-else-if="outlierExplain.checks?.spatial?.mode === 'absolute'" class="kv">
                <span class="k">|Δ|</span>
                <span class="v">
                  {{ outlierExplain.checks?.spatial?.absoluteDelta ?? '—' }} (threshold
                  {{ outlierExplain.params?.absoluteThreshold }})
                </span>
              </div>
              <div v-if="outlierExplain.checks?.spatial?.note" class="note">
                {{ outlierExplain.checks?.spatial?.note }}
              </div>
            </div>

            <div class="outlier-explain-section">
              <div class="section-title">Same-value check</div>
              <div class="kv">
                <span class="k">Enabled</span>
                <span class="v">{{ outlierExplain.checks?.sameValue?.enabled }}</span>
              </div>
              <div class="kv">
                <span class="k">Window</span>
                <span class="v">{{ outlierExplain.checks?.sameValue?.windowHours }} h</span>
              </div>
              <div class="kv">
                <span class="k">Min count</span>
                <span class="v">{{ outlierExplain.checks?.sameValue?.minCount }}</span>
              </div>
              <div class="kv">
                <span class="k">Count / Distinct</span>
                <span class="v">
                  {{ outlierExplain.checks?.sameValue?.measurementCount ?? '—' }} /
                  {{ outlierExplain.checks?.sameValue?.distinctCount ?? '—' }}
                </span>
              </div>
              <div class="kv">
                <span class="k">Result</span>
                <span class="v">{{ outlierExplain.checks?.sameValue?.isOutlier }}</span>
              </div>
              <div v-if="outlierExplain.checks?.sameValue?.note" class="note">
                {{ outlierExplain.checks?.sameValue?.note }}
              </div>
            </div>
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
        <div class="mt-2 mt-md-4">
          <UiColorsLegend :size="ColorsLegendSize.SMALL" />
        </div>
        <p style="min-height: 20px" class="mb-0 mt-2 mt-md-4">
          <small v-if="chartOptions">
            {{ $t('aq_provided_by') }}
            <span v-if="locationDetails?.ownerName && locationDetails?.ownerName !== 'unknown'">
              <span v-if="locationDetails?.url">
                <a :href="locationDetails?.url" target="_blank">
                  {{ locationDetails?.ownerName }}
                  <v-icon size="16">mdi-open-in-new</v-icon>
                </a>
              </span>
              <span v-else>
                {{ locationDetails?.ownerName }}
              </span>
              via
            </span>
            <span v-if="dataSourceAttribution">
              <span v-if="locationDetails?.provider !== locationDetails?.dataSource">
                {{ locationDetails?.provider }} and
              </span>
              <template v-if="dataSourceAttribution.url">
                <a :href="dataSourceAttribution.url" target="_blank">
                  {{ dataSourceAttribution.label }}
                  <v-icon size="16">mdi-open-in-new</v-icon>
                </a>
              </template>
              <template v-else>
                {{ dataSourceAttribution.label }}
              </template>
            </span>

            {{ $t('under') }}
            <span v-if="licenseAttribution">
              <template v-if="licenseAttribution.url">
                <a :href="licenseAttribution.url" target="_blank">
                  {{ licenseAttribution.label }}
                  <v-icon size="16">mdi-open-in-new</v-icon>
                </a>
              </template>
              <template v-else>
                {{ licenseAttribution.label }}
              </template>
            </span>
          </small>
        </p>
      </div>
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
  import { useNuxtApp } from '#imports';
  import { LICENSE_MAP, DATA_SOURCE_MAP } from '~/constants/map/attribution';
  import { useRoute } from 'vue-router';

  const props = defineProps<{
    dialog: DialogInstance<{ location: AGMapLocationData; outlierParams?: Record<string, any> }>;
  }>();

  const runtimeConfig = useRuntimeConfig();
  const apiUrl = runtimeConfig.public.apiUrl as string;
  const headers = { 'data-permission-context': runtimeConfig.public.trustedContext as string };

  const generalConfigStore = useGeneralConfigStore();
  const { getTimezoneLabel, userTimezone } = useHistoricalDataTimezone();
  const { handleApiError } = useApiErrorHandler();

  const mapLocationData: Ref<AGMapLocationData> = ref(null);
  const locationHistoryData: Ref<LocationHistoryData> = ref(null);
  const locationDetails: Ref<LocationDetails> = ref(null);
  const dataSourceAttribution = computed(() => {
    const sourceKey = locationDetails.value?.dataSource as keyof typeof DATA_SOURCE_MAP | undefined;
    if (sourceKey && DATA_SOURCE_MAP[sourceKey]) {
      return DATA_SOURCE_MAP[sourceKey];
    }
    if (locationDetails.value?.dataSource) {
      return { label: locationDetails.value.dataSource, url: null };
    }
    return null;
  });
  const licenseAttribution = computed(() => {
    const license = locationDetails.value?.licenses?.[0];
    if (!license) {
      return locationDetails.value ? { label: 'own license', url: null } : null;
    }
    const licenseKey = license as keyof typeof LICENSE_MAP;
    if (LICENSE_MAP[licenseKey]) {
      return LICENSE_MAP[licenseKey];
    }
    return { label: license, url: null };
  });
  const chartData: Ref<ChartData<'bar'>> = ref(null);
  const chartOptions: Ref<ChartOptions<'bar'>> = ref(null);
  const historyLoading: Ref<boolean> = ref(false);
  const detailsLoading: Ref<boolean> = ref(false);
  const historyError: Ref<boolean> = ref(false);
  const loading: Ref<boolean> = computed(() => historyLoading.value || detailsLoading.value);

  const { $i18n } = useNuxtApp();
  const route = useRoute();

  const isDebugMode = computed(() => route.query.debug === 'true');
  const isPmMeasure = computed(() =>
    [MeasureNames.PM25, MeasureNames.PM_AQI].includes(generalConfigStore.selectedMeasure)
  );

  const showOutlierExplainPanel = computed(
    () => isDebugMode.value && isPmMeasure.value && Boolean(mapLocationData.value?.locationId)
  );

  const outlierExplainLoading = ref(false);
  const outlierExplainError = ref(false);
  const outlierExplain = ref<any>(null);

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
      case MeasureNames.RCO2:
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
        retry: 1,
        headers: headers
      });
      locationDetails.value = response;
    } catch (error) {
      handleApiError(error, 'Failed to load location details');
    } finally {
      detailsLoading.value = false;
    }
  }

  async function fetchOutlierExplain(locationId: number): Promise<void> {
    outlierExplainLoading.value = true;
    outlierExplainError.value = false;

    try {
      const response = await $fetch<any>(`${apiUrl}/locations/${locationId}/outliers/pm25/explain`, {
        params: props.dialog?.data?.outlierParams ?? {},
        retry: 1,
        headers: headers
      });
      outlierExplain.value = response;
    } catch (error) {
      outlierExplainError.value = true;
    } finally {
      outlierExplainLoading.value = false;
    }
  }

  function retryFetchOutlierExplain() {
    if (mapLocationData.value?.locationId) {
      fetchOutlierExplain(mapLocationData.value.locationId);
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
            measure,
            excludeOutliers: generalConfigStore.excludeOutliers
          },
          retry: 1,
          headers: headers
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
      if (showOutlierExplainPanel.value) {
        fetchOutlierExplain(mapLocationData.value.locationId);
      }
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
        data: chartValues,
        translate: $i18n.t
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
      height: 250px;
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

  .outlier-explain {
    border: 1px solid rgba(0, 0, 0, 0.12);
    border-radius: 8px;
    padding: 12px 14px;
  }

  .outlier-explain-details {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
    gap: 10px;
  }

  .outlier-explain-section {
    border: 1px solid rgba(0, 0, 0, 0.08);
    border-radius: 8px;
    padding: 10px 12px;
  }

  .section-title {
    font-size: 12px;
    font-weight: var(--font-weight-medium);
    margin-bottom: 6px;
  }

  .kv {
    display: grid;
    grid-template-columns: 1fr auto;
    gap: 12px;
    font-size: 12px;
    line-height: 1.3;
    margin: 2px 0;
  }

  .k {
    opacity: 0.75;
  }

  .v {
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono',
      'Courier New', monospace;
    text-align: right;
    overflow-wrap: anywhere;
  }

  .note {
    margin-top: 6px;
    font-size: 11px;
    opacity: 0.8;
  }

  .headless {
    .chart-container {
      height: 270px;
    }

    @media (max-width: 768px) {
      &.embedded .chart-container {
        height: 200px;
      }
    }

    .chart-controls {
      margin-bottom: 25px !important;
      margin-top: -20px !important;

      @include desktop {
        margin-bottom: 25px !important;
        margin-top: -5px !important;
      }
    }
  }
</style>
