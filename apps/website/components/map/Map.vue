<template>
  <div class="map-wrapper">
    <div class="map-extra-controls-box">
      <UiIconButton
        :ripple="false"
        :size="ButtonSize.NORMAL"
        icon="mdi-information-outline"
        :style="'light'"
        :active="isLegendShown"
        @click="isLegendShown = !isLegendShown"
      >
      </UiIconButton>

      <UiGeolocationButton @location-found="handleLocationFound" @error="handleGeolocationError" />

      <UiIconButton
        :ripple="false"
        :size="ButtonSize.NORMAL"
        customIcon="wind-icon.svg"
        :active="windLayerEnabled"
        title="Toggle Wind Layer"
        @click="toggleWindLayer"
      >
      </UiIconButton>

      <UiIconButton
        v-if="generalConfigStore.embedded"
        :ripple="false"
        :size="ButtonSize.NORMAL"
        icon="mdi-open-in-new"
        :style="'light'"
        @click="handleOpenFullscreen"
      >
      </UiIconButton>

      <UiIconButton
        v-if="isDebugMode"
        :ripple="false"
        :size="ButtonSize.NORMAL"
        :icon="generalConfigStore.excludeOutliers ? 'mdi-filter' : 'mdi-filter-off'"
        :style="'light'"
        :title="
          generalConfigStore.excludeOutliers
            ? 'Experimental: Outliers filtered'
            : 'Show all data points'
        "
        @click="
          () => {
            generalConfigStore.setExcludeOutliers(!generalConfigStore.excludeOutliers);
            updateMapData();
          }
        "
      >
      </UiIconButton>
    </div>

    <UiProgressBar :show="(loading && loaderShown) || windLoading"></UiProgressBar>
    <div id="map">
      <div class="map-controls">
        <UiDropdownControl
          :selected-value="generalConfigStore.selectedMeasure"
          :options="measureSelectOptions"
          :disabled="loading && loaderShown"
          @change="handleMeasureChange"
        >
        </UiDropdownControl>
      </div>

      <div
        v-if="generalConfigStore.excludeOutliers && isPmMeasure"
        class="map-outlier-controls"
      >
        <div class="panel-title">Outlier sensitivity</div>
        <div v-if="showOutlierStats" class="control">
          <div class="control-label">
            <span class="control-label-with-info">
              Show hidden only
              <i
                class="mdi mdi-information-outline outlier-info-icon"
                :title="outlierControlTooltips.showHiddenMonitorsOnly"
              />
            </span>
            <input
              v-model="showHiddenMonitorsOnly"
              type="checkbox"
              aria-label="Show hidden monitors only"
              @change="handleOutlierParamsChange"
            />
          </div>
        </div>
        <div class="control">
          <div class="control-label">
            <span class="control-label-with-info">
              Radius
              <i
                class="mdi mdi-information-outline outlier-info-icon"
                :title="outlierControlTooltips.outlierRadiusKm"
              />
            </span>
            <span>{{ outlierRadiusKm }} km</span>
          </div>
          <input
            v-model.number="outlierRadiusKm"
            type="range"
            min="2"
            max="300"
            step="1"
            :aria-valuenow="outlierRadiusKm"
            aria-label="Outlier radius in kilometers"
            @input="handleOutlierParamsChange"
          />
        </div>
        <div class="control">
          <div class="control-label">
            <span class="control-label-with-info">
              Time window
              <i
                class="mdi mdi-information-outline outlier-info-icon"
                :title="outlierControlTooltips.outlierWindowHours"
              />
            </span>
            <span>±{{ outlierWindowHours }} h</span>
          </div>
          <input
            v-model.number="outlierWindowHours"
            type="range"
            min="0.5"
            max="12"
            step="0.5"
            :aria-valuenow="outlierWindowHours"
            aria-label="Time window in hours"
            @input="handleOutlierParamsChange"
          />
        </div>

        <div v-if="showOutlierStats" class="control">
          <div class="control-label">
            <span class="control-label-with-info">
              Min nearby
              <i
                class="mdi mdi-information-outline outlier-info-icon"
                :title="outlierControlTooltips.outlierMinNearby"
              />
            </span>
            <span>{{ outlierMinNearby }}</span>
          </div>
          <input
            v-model.number="outlierMinNearby"
            type="range"
            min="1"
            max="20"
            step="1"
            :aria-valuenow="outlierMinNearby"
            aria-label="Minimum nearby points"
            @input="handleOutlierParamsChange"
          />
        </div>

        <div v-if="showOutlierStats" class="control">
          <div class="control-label">
            <span class="control-label-with-info">
              Z-score threshold
              <i
                class="mdi mdi-information-outline outlier-info-icon"
                :title="outlierControlTooltips.outlierZScoreThreshold"
              />
            </span>
            <span>{{ outlierZScoreThreshold.toFixed(1) }}</span>
          </div>
          <input
            v-model.number="outlierZScoreThreshold"
            type="range"
            min="0.5"
            max="6"
            step="0.1"
            :aria-valuenow="outlierZScoreThreshold"
            aria-label="Z-score threshold"
            @input="handleOutlierParamsChange"
          />
        </div>

        <div v-if="showOutlierStats" class="control">
          <div class="control-label">
            <span class="control-label-with-info">
              Absolute threshold
              <i
                class="mdi mdi-information-outline outlier-info-icon"
                :title="outlierControlTooltips.outlierAbsoluteThreshold"
              />
            </span>
            <span>{{ outlierAbsoluteThreshold }} µg/m³</span>
          </div>
          <input
            v-model.number="outlierAbsoluteThreshold"
            type="range"
            min="0"
            max="200"
            step="1"
            :aria-valuenow="outlierAbsoluteThreshold"
            aria-label="Absolute threshold"
            @input="handleOutlierParamsChange"
          />
        </div>

        <div v-if="showOutlierStats" class="control">
          <div class="control-label">
            <span class="control-label-with-info">
              Z-score min mean
              <i
                class="mdi mdi-information-outline outlier-info-icon"
                :title="outlierControlTooltips.outlierZScoreMinMean"
              />
            </span>
            <span>{{ outlierZScoreMinMean }} µg/m³</span>
          </div>
          <input
            v-model.number="outlierZScoreMinMean"
            type="range"
            min="0"
            max="200"
            step="1"
            :aria-valuenow="outlierZScoreMinMean"
            aria-label="Z-score minimum mean"
            @input="handleOutlierParamsChange"
          />
        </div>

        <div v-if="showOutlierStats" class="control">
          <div class="control-label">
            <span class="control-label-with-info">
              Filter outlier neighbors
              <i
                class="mdi mdi-information-outline outlier-info-icon"
                :title="outlierControlTooltips.outlierUseStoredOutlierFlagForNeighbors"
              />
            </span>
            <input
              v-model="outlierUseStoredOutlierFlagForNeighbors"
              type="checkbox"
              aria-label="Filter neighbors using stored outlier flags"
              @change="handleOutlierParamsChange"
            />
          </div>
        </div>

        <div v-if="showOutlierStats" class="control">
          <div class="control-label">
            <span class="control-label-with-info">
              Same-value check
              <i
                class="mdi mdi-information-outline outlier-info-icon"
                :title="outlierControlTooltips.outlierEnableSameValueCheck"
              />
            </span>
            <input
              v-model="outlierEnableSameValueCheck"
              type="checkbox"
              aria-label="Enable same-value check"
              @change="handleOutlierParamsChange"
            />
          </div>
        </div>

        <div v-if="showOutlierStats && outlierEnableSameValueCheck" class="control">
          <div class="control-label">
            <span class="control-label-with-info">
              Same-value window
              <i
                class="mdi mdi-information-outline outlier-info-icon"
                :title="outlierControlTooltips.outlierSameValueWindowHours"
              />
            </span>
            <span>{{ outlierSameValueWindowHours }} h</span>
          </div>
          <input
            v-model.number="outlierSameValueWindowHours"
            type="range"
            min="1"
            max="48"
            step="1"
            :aria-valuenow="outlierSameValueWindowHours"
            aria-label="Same-value window hours"
            @input="handleOutlierParamsChange"
          />
        </div>

        <div v-if="showOutlierStats && outlierEnableSameValueCheck" class="control">
          <div class="control-label">
            <span class="control-label-with-info">
              Same-value min count
              <i
                class="mdi mdi-information-outline outlier-info-icon"
                :title="outlierControlTooltips.outlierSameValueMinCount"
              />
            </span>
            <span>{{ outlierSameValueMinCount }}</span>
          </div>
          <input
            v-model.number="outlierSameValueMinCount"
            type="range"
            min="1"
            max="24"
            step="1"
            :aria-valuenow="outlierSameValueMinCount"
            aria-label="Same-value minimum count"
            @input="handleOutlierParamsChange"
          />
        </div>

        <div v-if="showOutlierStats && outlierEnableSameValueCheck" class="control">
          <div class="control-label">
            <span class="control-label-with-info">
              Same-value include zero
              <i
                class="mdi mdi-information-outline outlier-info-icon"
                :title="outlierControlTooltips.outlierSameValueIncludeZero"
              />
            </span>
            <input
              v-model="outlierSameValueIncludeZero"
              type="checkbox"
              aria-label="Include zeros in same-value check"
              @change="handleOutlierParamsChange"
            />
          </div>
        </div>

        <div v-if="showOutlierStats" class="control outlier-stats">
          <div class="control-label">
            <span>{{ visibleMonitorsLabel }}</span>
            <span>{{ outlierViewportStats.visibleMonitors }}</span>
          </div>
          <div class="control-label">
            <span>{{ hiddenMonitorsLabel }}</span>
            <span>{{ outlierViewportStats.hiddenMonitors ?? '…' }}</span>
          </div>
          <div class="control-label">
            <span>Total monitors</span>
            <span>{{ outlierViewportStats.totalMonitors ?? '…' }}</span>
          </div>
        </div>
      </div>

      <LMap
        ref="map"
        class="map"
        :maxBoundsViscosity="DEFAULT_MAP_VIEW_CONFIG.maxBoundsViscosity"
        :maxBounds="DEFAULT_MAP_VIEW_CONFIG.maxBounds"
        :zoom="Number(urlState.zoom)"
        :max-zoom="DEFAULT_MAP_VIEW_CONFIG.maxZoom"
        :min-zoom="DEFAULT_MAP_VIEW_CONFIG.minZoom"
        :center="[Number(urlState.lat), Number(urlState.long)]"
        :attributionControl="true"
        @ready="onMapReady"
      >
      </LMap>

      <WindVisualization
        v-if="mapInstance && isMapFullyReady"
        :map="mapInstance"
        :enabled="windLayerEnabled"
        @loading-change="handleWindLoadingChange"
      />

      <div v-if="isLegendShown" class="legend-overlay">
        <div class="legend-container">
          <UiMapMarkersLegend class="markers-legend" />
          <UiColorsLegend class="colors-legend" :is-white-mode="windLayerEnabled" />
        </div>
      </div>
    </div>

    <DialogsLocationHistoryDialog v-if="locationHistoryDialog" :dialog="locationHistoryDialog" />
  </div>
</template>

<script lang="ts" setup>
  import { watch, computed, onMounted, ref, onUnmounted } from 'vue';
  import L, { DivIcon, GeoJSON, LatLngBounds, LatLngExpression } from 'leaflet';
  import 'leaflet/dist/leaflet.css';
  import '@maplibre/maplibre-gl-leaflet';
  import 'maplibre-gl/dist/maplibre-gl.css';
  import { GeoJsonObject } from 'geojson';
  import { LMap } from '@vue-leaflet/vue-leaflet';
  import { useRuntimeConfig } from 'nuxt/app';
  import { GeoSearchControl, OpenStreetMapProvider } from 'leaflet-geosearch';

  import { convertToGeoJSON } from '~/utils/';
  import {
    AGMapData,
    MeasureNames,
    AGMapDataItemType,
    SensorType,
    DropdownOption,
    DialogId,
    ButtonSize
  } from '~/types';
  import { DEFAULT_MAP_VIEW_CONFIG, MEASURE_LABELS_WITH_UNITS } from '~/constants';
  import { useUrlState } from '~/composables/shared/ui/useUrlState';
  import { getColorForMeasure } from '~/utils/colors';
  import { pm25ToAQI } from '~/utils/aqi';
  import { useGeneralConfigStore } from '~/store/general-config-store';
  import { dialogStore } from '~/composables/shared/ui/useDialog';
  import { useIntervalRefresh } from '~/composables/shared/useIntervalRefresh';
  import { CURRENT_DATA_REFRESH_INTERVAL } from '~/constants/map/refresh-interval';
  import UiMapMarkersLegend from '~/components/ui/MapMarkersLegend.vue';
  import UiGeolocationButton from '~/components/ui/GeolocationButton.vue';
  import WindVisualization from '~/components/map/WindVisualization.vue';
  import { useStorage } from '@vueuse/core';
  import { useApiErrorHandler } from '~/composables/shared/useApiErrorHandler';
  import { createVueDebounce } from '~/utils/debounce';
  import { useNuxtApp } from '#imports';
  const loading = ref<boolean>(false);
  const windLoading = ref<boolean>(false);
  const isMapFullyReady = ref<boolean>(false);
  const loaderShown = ref<boolean>(true);
  const map = ref<typeof LMap>();

  const runtimeConfig = useRuntimeConfig();
  const apiUrl = runtimeConfig.public.apiUrl as string;
  const headers = { 'data-permission-context': runtimeConfig.public.trustedContext as string };

  const generalConfigStore = useGeneralConfigStore();
  const { handleApiError } = useApiErrorHandler();

  const { startRefreshInterval, stopRefreshInterval } = useIntervalRefresh(
    () => updateMapData(false),
    CURRENT_DATA_REFRESH_INTERVAL,
    {
      skipFirstRefresh: true,
      skipOnVisibilityHidden: true
    }
  );

  const locationHistoryDialogId = DialogId.LOCATION_HISTORY_CHART;
  const isLegendShown = useStorage('isLegendShown', true);
  const showHiddenMonitorsOnly = useStorage('showHiddenMonitorsOnly', false);
  const outlierMinNearby = useStorage('outlierMinNearby', 3);
  const outlierZScoreThreshold = useStorage('outlierZScoreThreshold', 2);
  const outlierAbsoluteThreshold = useStorage('outlierAbsoluteThreshold', 30);
  const outlierZScoreMinMean = useStorage('outlierZScoreMinMean', 50);
  const outlierUseStoredOutlierFlagForNeighbors = useStorage(
    'outlierUseStoredOutlierFlagForNeighbors',
    false
  );
  const outlierEnableSameValueCheck = useStorage('outlierEnableSameValueCheck', true);
  const outlierSameValueWindowHours = useStorage('outlierSameValueWindowHours', 24);
  const outlierSameValueMinCount = useStorage('outlierSameValueMinCount', 3);
  const outlierSameValueIncludeZero = useStorage('outlierSameValueIncludeZero', false);

  const { urlState, setUrlState } = useUrlState();

  const isDebugMode = computed(() => {
    const debugValue = urlState.debug;

    if (debugValue === undefined) {
      return false;
    }

    if (debugValue === 'false') {
      return false;
    }

    if (debugValue === 'true') {
      return true;
    }

    return false;
  });

  const isPmMeasure = computed(() =>
    [MeasureNames.PM25, MeasureNames.PM_AQI].includes(generalConfigStore.selectedMeasure)
  );

  const outlierRadiusKm = computed({
    get: () => generalConfigStore.outlierRadiusKm,
    set: value => generalConfigStore.setOutlierRadiusKm(value)
  });

  const outlierWindowHours = computed({
    get: () => generalConfigStore.outlierWindowHours,
    set: value => generalConfigStore.setOutlierWindowHours(value)
  });

  type OutlierViewportStats = {
    visibleMonitors: number;
    hiddenMonitors: number | null;
    totalMonitors: number | null;
  };

  const outlierViewportStats = ref<OutlierViewportStats>({
    visibleMonitors: 0,
    hiddenMonitors: null,
    totalMonitors: null
  });

  const unfilteredMonitorTotalCache = new Map<string, number>();
  let updateMapRequestId = 0;

  const showOutlierStats = computed(
    () => isDebugMode.value && generalConfigStore.excludeOutliers && isPmMeasure.value
  );

  const outlierControlTooltips = {
    showHiddenMonitorsOnly:
      'Shows only monitors currently flagged as outliers (normally hidden when filtering is enabled).',
    outlierRadiusKm:
      'Search radius for nearby monitors used to compute the spatial baseline. Larger = more neighbors but less local.',
    outlierWindowHours:
      'Time window (±hours) used when matching nearby measurements to the current timestamp. Larger = more matches but less time-accurate.',
    outlierMinNearby:
      'Minimum number of nearby monitors required to compute spatial stats. If not met, spatial outlier detection is skipped.',
    outlierZScoreThreshold:
      'When neighborhood mean is high, flags if |value-mean|/stddev exceeds this. Lower = more sensitive.',
    outlierAbsoluteThreshold:
      'When neighborhood mean is low, flags if |value-mean| exceeds this (µg/m³). Lower = more sensitive at low PM.',
    outlierZScoreMinMean:
      'Switch point: use Z-score mode when neighborhood mean ≥ this; otherwise use absolute threshold mode.',
    outlierUseStoredOutlierFlagForNeighbors:
      'Excludes neighbors already flagged (stored outlier flag) from the baseline. Helps avoid contaminated baselines but can reduce neighbor count.',
    outlierEnableSameValueCheck:
      'Flags monitors whose PM2.5 stays exactly constant over the lookback window (common stuck-sensor signal).',
    outlierSameValueWindowHours:
      'Lookback window for the same-value check. Shorter catches flatlines sooner; longer requires a longer flatline.',
    outlierSameValueMinCount:
      'Minimum number of measurements required in the same-value window to trigger a same-value outlier.',
    outlierSameValueIncludeZero:
      'If enabled, constant PM2.5 = 0 can be flagged as a same-value outlier (useful for detecting permanent zeros).'
  } as const;

  const visibleMonitorsLabel = computed(() =>
    showHiddenMonitorsOnly.value ? 'Visible outliers' : 'Visible monitors'
  );

  const hiddenMonitorsLabel = computed(() =>
    showHiddenMonitorsOnly.value ? 'Hidden inliers' : 'Hidden outliers'
  );

  const locationHistoryDialog = computed(() => dialogStore.getDialog(locationHistoryDialogId));

  const windLayerEnabled = computed(() => urlState.wind_layer === 'true');

  const measureSelectOptions: DropdownOption[] = [
    { label: MEASURE_LABELS_WITH_UNITS[MeasureNames.PM25], value: MeasureNames.PM25 },
    { label: MEASURE_LABELS_WITH_UNITS[MeasureNames.PM_AQI], value: MeasureNames.PM_AQI },
    { label: MEASURE_LABELS_WITH_UNITS[MeasureNames.RCO2], value: MeasureNames.RCO2 }
  ];

  const updateMapDebounced = createVueDebounce(updateMapData, 400);
  const handleOutlierParamsChange = () => updateMapDebounced();

  let geoJsonMapData: GeoJsonObject;
  let mapInstance: L.Map | null = null;
  let markers: GeoJSON;
  let mapLibreLayer: any = null;
  let currentMapStyle = DEFAULT_MAP_VIEW_CONFIG.light_map_style_url;
  let styleUpdateInProgress = false;
  let searchControl: any;

  const { $i18n } = useNuxtApp();

  function handleWindLoadingChange(isLoading: boolean): void {
    windLoading.value = isLoading;
  }

  const onMapReady = async () => {
    await setUpMapInstance();
    addGeocodeControl();
  };

  function setUpMapInstance(): void {
    if (!map.value) {
      return;
    }

    mapInstance = map.value.leafletObject;

    disableScrollWheelZoomForHeadless();

    try {
      const attributionContent = `
      <span style="font-size: 10px; margin-right: 4px;">🇺🇦</span>
      <a target="_blank" href="https://leafletjs.com/">Leaflet</a> |
      © <a target="_blank" href="https://www.airgradient.com/">AirGradient</a> |
      © <a target="_blank" href="https://openaq.org/">OpenAQ</a>
    `;
      if (mapInstance.attributionControl) {
        mapInstance.attributionControl.setPrefix(attributionContent);
      } else {
        const attributionControl = L.control.attribution({ prefix: attributionContent });
        attributionControl.addTo(mapInstance);
      }
    } catch (e) {
      console.warn('Attribution init failed:', e);
    }

    currentMapStyle = windLayerEnabled.value
      ? DEFAULT_MAP_VIEW_CONFIG.dark_map_style_url
      : DEFAULT_MAP_VIEW_CONFIG.light_map_style_url;

    mapLibreLayer = L.maplibreGL({
      style: currentMapStyle,
      center: [Number(urlState.lat), Number(urlState.long)],
      zoom: Number(urlState.zoom)
    });

    mapLibreLayer.addTo(mapInstance);

    markers = L.geoJson(null, { pointToLayer: createMarker }).addTo(mapInstance);

    mapInstance.on('moveend', () => {
      setUrlState({
        zoom: mapInstance!.getZoom(),
        lat: mapInstance!.getCenter().lat.toFixed(2),
        long: mapInstance!.getCenter().lng.toFixed(2)
      });

      updateMapDebounced();
    });

    startRefreshInterval();

    mapInstance.whenReady(async () => {
      await updateBaseMapStyle();
      await updateMapData(true);

      setTimeout(() => {
        isMapFullyReady.value = true;
      }, 200);
    });
  }

  function toggleWindLayer(): void {
    setUrlState({ wind_layer: String(!windLayerEnabled.value) });
  }

  function createMarker(feature: GeoJSON.Feature, latlng: LatLngExpression): L.Marker {
    let displayValue: number = feature.properties?.value;
    if (
      (displayValue || displayValue === 0) &&
      generalConfigStore.selectedMeasure === MeasureNames.PM_AQI
    ) {
      displayValue = pm25ToAQI(displayValue);
    }

    const colorConfig: { bgColor: string; textColorClass: string } = getColorForMeasure(
      generalConfigStore.selectedMeasure,
      displayValue
    );

    const isSensor: boolean = feature.properties?.type === AGMapDataItemType.sensor;
    const isReference: boolean = feature.properties?.sensorType === SensorType.reference;

    const markerSize = isSensor ? 24 : 36;

    const icon: DivIcon = L.divIcon({
      html: `<div class="ag-marker${!isSensor ? ' is-cluster' : ''}${isReference ? ' is-reference' : ''} ${colorConfig?.textColorClass}" 
            style="background-color: ${colorConfig?.bgColor}">
            <span>${Math.round(displayValue)}</span>
          </div>`,
      className: `marker-box ${!isSensor ? 'is-cluster-marker-box' : ''}`,
      iconSize: L.point(markerSize, markerSize)
    });

    const marker = L.marker(latlng, { icon });

    marker.on('click', () => {
      if (isSensor && feature.properties) {
        const outlierExplainParams =
          isPmMeasure.value && (generalConfigStore.excludeOutliers || isDebugMode.value)
            ? {
                outlierRadiusMeters: generalConfigStore.outlierRadiusKm * 1000,
                outlierWindowHours: generalConfigStore.outlierWindowHours,
                outlierMinNearby: outlierMinNearby.value,
                outlierAbsoluteThreshold: outlierAbsoluteThreshold.value,
                outlierZScoreThreshold: outlierZScoreThreshold.value,
                outlierZScoreMinMean: outlierZScoreMinMean.value,
                outlierUseStoredOutlierFlagForNeighbors:
                  outlierUseStoredOutlierFlagForNeighbors.value,
                outlierEnableSameValueCheck: outlierEnableSameValueCheck.value,
                outlierSameValueWindowHours: outlierSameValueWindowHours.value,
                outlierSameValueMinCount: outlierSameValueMinCount.value,
                outlierSameValueIncludeZero: outlierSameValueIncludeZero.value
              }
            : undefined;

        dialogStore.open(locationHistoryDialogId, {
          location: feature.properties,
          outlierParams: outlierExplainParams
        });
      } else if (!isSensor) {
        const currentZoom = mapInstance!.getZoom();
        const newZoom = Math.min(currentZoom + 2, DEFAULT_MAP_VIEW_CONFIG.maxZoom);
        mapInstance!.flyTo(latlng, newZoom, { animate: true, duration: 0.8 });
      }
    });

    return marker;
  }

  function countMonitorsInResponseData(data: AGMapData['data']): number {
    return data.reduce((sum, item) => sum + ('sensorsCount' in item ? item.sensorsCount : 1), 0);
  }

  function buildViewportCacheKey(bounds: LatLngBounds, measure: MeasureNames): string {
    return [
      bounds.getWest().toFixed(3),
      bounds.getSouth().toFixed(3),
      bounds.getEast().toFixed(3),
      bounds.getNorth().toFixed(3),
      String(measure)
    ].join(',');
  }

  async function updateMapData(showLoader = true): Promise<void> {
    if (loading.value || locationHistoryDialog.value?.isOpen || !mapInstance) {
      return;
    }

    const requestId = ++updateMapRequestId;
    loading.value = true;
    loaderShown.value = showLoader;

    try {
      const bounds: LatLngBounds = mapInstance.getBounds();
      const requestMeasure: MeasureNames =
        generalConfigStore.selectedMeasure === MeasureNames.PM_AQI
          ? MeasureNames.PM25
          : generalConfigStore.selectedMeasure;

      const viewportCacheKey = buildViewportCacheKey(bounds, requestMeasure);
      const cachedUnfilteredTotal = unfilteredMonitorTotalCache.get(viewportCacheKey);
      const shouldFetchUnfilteredTotal = showOutlierStats.value && cachedUnfilteredTotal === undefined;

      const outlierParams =
        generalConfigStore.excludeOutliers && isPmMeasure.value
          ? {
              outlierRadiusMeters: generalConfigStore.outlierRadiusKm * 1000,
              outlierWindowHours: generalConfigStore.outlierWindowHours,
              outliersOnly: showHiddenMonitorsOnly.value,
              outlierMinNearby: outlierMinNearby.value,
              outlierAbsoluteThreshold: outlierAbsoluteThreshold.value,
              outlierZScoreThreshold: outlierZScoreThreshold.value,
              outlierZScoreMinMean: outlierZScoreMinMean.value,
              outlierUseStoredOutlierFlagForNeighbors:
                outlierUseStoredOutlierFlagForNeighbors.value,
              outlierEnableSameValueCheck: outlierEnableSameValueCheck.value,
              outlierSameValueWindowHours: outlierSameValueWindowHours.value,
              outlierSameValueMinCount: outlierSameValueMinCount.value,
              outlierSameValueIncludeZero: outlierSameValueIncludeZero.value
            }
          : {};

      const baseParams = {
        xmin: bounds.getWest(),
        ymin: bounds.getSouth(),
        xmax: bounds.getEast(),
        ymax: bounds.getNorth(),
        zoom: mapInstance.getZoom(),
        measure: requestMeasure
      };

      const unfilteredTotalPromise = shouldFetchUnfilteredTotal
        ? $fetch<AGMapData>(`${apiUrl}/measurements/current/cluster`, {
            params: {
              ...baseParams,
              excludeOutliers: false
            },
            retry: 1,
            headers: headers
          })
        : null;

      const response = await $fetch<AGMapData>(`${apiUrl}/measurements/current/cluster`, {
        params: {
          ...baseParams,
          excludeOutliers: generalConfigStore.excludeOutliers,
          ...outlierParams
        },
        retry: 1,
        headers: headers
      });

      if (requestId !== updateMapRequestId) {
        return;
      }

      const geoJsonData: GeoJsonObject = convertToGeoJSON(response.data);
      geoJsonMapData = geoJsonData;

      requestAnimationFrame(() => {
        markers.clearLayers();
        markers.addData(geoJsonData);
      });

      if (showOutlierStats.value) {
        const visibleMonitors = countMonitorsInResponseData(response.data);
        outlierViewportStats.value.visibleMonitors = visibleMonitors;

        const totalMonitors = cachedUnfilteredTotal ?? null;
        outlierViewportStats.value.totalMonitors = totalMonitors;
        outlierViewportStats.value.hiddenMonitors =
          totalMonitors === null ? null : Math.max(0, totalMonitors - visibleMonitors);

        if (unfilteredTotalPromise) {
          const unfilteredResponse = await unfilteredTotalPromise;
          if (requestId !== updateMapRequestId) {
            return;
          }

          const unfilteredTotal = countMonitorsInResponseData(unfilteredResponse.data);
          unfilteredMonitorTotalCache.set(viewportCacheKey, unfilteredTotal);
          outlierViewportStats.value.totalMonitors = unfilteredTotal;
          outlierViewportStats.value.hiddenMonitors = Math.max(0, unfilteredTotal - visibleMonitors);
        }
      }
    } catch (error) {
      console.error('Failed to fetch map data:', error);
      handleApiError(error, 'Failed to load map data. Please try again.');
    } finally {
      loading.value = false;
    }
  }

  function addGeocodeControl(): void {
    if (!mapInstance) return;

    const provider = new OpenStreetMapProvider();

    searchControl = GeoSearchControl({
      provider,
      style: 'bar',
      autoClose: true,
      keepResult: true,
      searchLabel: $i18n.t('search_placeholder')
    });

    mapInstance.addControl(searchControl);
  }

  function handleMeasureChange(value: MeasureNames): void {
    const previousMeasure = generalConfigStore.selectedMeasure;
    useGeneralConfigStore().setSelectedMeasure(value);
    setUrlState({ meas: value });

    if (
      [MeasureNames.PM25, MeasureNames.PM_AQI].includes(previousMeasure) &&
      [MeasureNames.PM25, MeasureNames.PM_AQI].includes(value)
    ) {
      markers.clearLayers();
      markers.addData(geoJsonMapData);
    } else {
      updateMapDebounced();
    }
  }

  async function updateBaseMapStyle(): Promise<void> {
    if (!mapLibreLayer || styleUpdateInProgress) return;

    const targetStyle = windLayerEnabled.value
      ? DEFAULT_MAP_VIEW_CONFIG.dark_map_style_url
      : DEFAULT_MAP_VIEW_CONFIG.light_map_style_url;

    if (currentMapStyle === targetStyle) return;

    styleUpdateInProgress = true;

    try {
      const maplibreMap =
        typeof mapLibreLayer.getMaplibreMap === 'function'
          ? mapLibreLayer.getMaplibreMap()
          : mapLibreLayer._glMap;

      if (maplibreMap && typeof maplibreMap.setStyle === 'function') {
        await new Promise<void>(resolve => {
          const onStyleLoad = () => {
            maplibreMap.off('styledata', onStyleLoad);
            resolve();
          };
          maplibreMap.on('styledata', onStyleLoad);
          maplibreMap.setStyle(targetStyle);
        });

        currentMapStyle = targetStyle;
      }
    } catch (error) {
      console.error('Failed to update base map style:', error);
    } finally {
      styleUpdateInProgress = false;
    }
  }

  function disableScrollWheelZoomForHeadless(): void {
    if (generalConfigStore.headless && mapInstance) {
      mapInstance.scrollWheelZoom.disable();
    }
  }

  function handleLocationFound(lat: number, lng: number): void {
    if (mapInstance) {
      mapInstance.flyTo([lat, lng], 12, { animate: true, duration: 1.2 });
    }
  }

  function handleOpenFullscreen(): void {
    window.open(
      window.location.href
        .replace('headless=true', 'headless=false')
        .replace('embedded=true', 'embedded=false'),
      '_blank'
    );
  }

  function handleGeolocationError(message: string): void {
    console.error('Geolocation error:', message);
  }

  onMounted(() => {
    generalConfigStore.setHeadless(window.location.href.includes('headless=true'));
    generalConfigStore.setEmbedded(window.location.href.includes('embedded=true'));
    if ([<MeasureNames>'pm02', <MeasureNames>'pm02_raw'].includes(urlState.meas)) {
      setUrlState({ meas: MeasureNames.PM25 });
    } else if (urlState.meas === <MeasureNames>'pi02') {
      setUrlState({ meas: MeasureNames.PM_AQI });
    }
    useGeneralConfigStore().setSelectedMeasure(urlState.meas);
  });

  watch(
    () => windLayerEnabled.value,
    async enabled => {
      if (isMapFullyReady.value) {
        await updateBaseMapStyle();
      }
    }
  );

  watch($i18n.locale, () => {
    if (mapInstance && searchControl) {
      mapInstance.removeControl(searchControl);
      addGeocodeControl();
    }
  });

  onUnmounted(() => {
    stopRefreshInterval();
    mapLibreLayer = null;
    currentMapStyle = DEFAULT_MAP_VIEW_CONFIG.light_map_style_url;
    mapInstance = null;
    isMapFullyReady.value = false;
    styleUpdateInProgress = false;
  });
</script>

<style lang="scss">
  .map-wrapper {
    position: relative;
  }

  #map {
    height: calc(100svh - 130px);
  }

  .map-outlier-controls {
    position: absolute;
    bottom: 12px;
    right: 12px;
    z-index: 450;
    padding: 14px 16px;
    width: 320px;
    max-width: calc(100% - 24px);
    max-height: calc(100svh - 200px);
    overflow-y: auto;
    background: rgba(18, 30, 45, 0.92);
    border: 1px solid rgba(255, 255, 255, 0.08);
    border-radius: 12px;
    box-shadow: 0 10px 30px rgba(0, 0, 0, 0.25);
    color: #f4f7ff;
  }

  .map-outlier-controls .panel-title {
    font-weight: 600;
    letter-spacing: 0.01em;
    margin-bottom: 10px;
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 14px;
    text-transform: uppercase;
  }

  .map-outlier-controls .control {
    display: flex;
    flex-direction: column;
    gap: 6px;
    margin-bottom: 12px;
  }

  .map-outlier-controls .control:last-of-type {
    margin-bottom: 0;
  }

  .map-outlier-controls .control-label {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: 13px;
  }

  .map-outlier-controls .control-label-with-info {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    min-width: 0;
  }

  .map-outlier-controls .outlier-info-icon {
    font-size: 16px;
    line-height: 1;
    opacity: 0.75;
    cursor: help;
    flex: 0 0 auto;
  }

  .map-outlier-controls .outlier-info-icon:hover {
    opacity: 1;
  }

  .map-outlier-controls input[type='range'] {
    accent-color: #7bc8f6;
    width: 100%;
  }

  .map-outlier-controls input[type='checkbox'] {
    accent-color: #7bc8f6;
    width: 16px;
    height: 16px;
  }

  @media (max-width: 768px) {
    .map-outlier-controls {
      position: fixed;
      top: auto;
      bottom: 12px;
      left: 12px;
      right: 12px;
      width: auto;
    }
  }

  .legend-overlay {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    pointer-events: none;
    z-index: 500;
    display: flex;
    align-items: flex-end;
    justify-content: center;
    padding-bottom: 30px;
  }

  .legend-container {
    display: flex;
    flex-direction: column;
    gap: 20px;
    align-items: center;
    padding: 20px;
    max-width: 90%;
    width: fit-content;
  }

  .markers-legend {
    margin-bottom: 10px;
  }

  .colors-legend {
    width: 100%;
    min-width: 300px;
  }

  @include desktop {
    #map {
      height: calc(100svh - 117px);
    }

    .legend-container {
      flex-direction: row;
      gap: 40px;
      padding: 20px 30px;
      max-width: 800px;
    }

    .colors-legend {
      min-width: 400px;
    }
  }

  .headless {
    #map {
      height: calc(100svh - 5px) !important;
    }
  }

  .marker-box {
    background: none !important;
    border: none !important;
  }

  .ag-marker {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    font-size: 12px;
    justify-content: center;
    font-weight: 600;
    border-radius: 4px;
    color: var(--main-white-color);
  }

  .is-cluster {
    border-radius: 50%;
    font-size: 14px;
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
    overflow: hidden;
    cursor: pointer;

    &:hover {
      opacity: 0.9;
      transform: scale(1.05);
      transition: all 0.2s ease;
    }
  }

  .is-reference {
    border: 2px solid var(--main-white-color);
    box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.2);
  }

  .ag-marker-tooltip {
    font-family: var(--secondary-font);
    padding: 0;
    border: none;
    border-radius: 8px;
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
    background: var(--main-white-color);
    backdrop-filter: blur(8px);
    min-width: 180px;

    &::before {
      display: none;
    }

    &::after {
      content: '';
      position: absolute;
      bottom: -6px;
      left: 50%;
      transform: translateX(-50%) rotate(45deg);
      width: 12px;
      height: 12px;
      background: var(--main-white-color);
      box-shadow: 2px 2px 4px rgba(0, 0, 0, 0.05);
    }

    .marker-tooltip {
      .tooltip-header {
        background: var(--primary-color);
        color: var(--main-white-color);
        padding: 8px 12px;
        font-weight: 600;
        font-size: 14px;
        border-top-left-radius: 8px;
        border-top-right-radius: 8px;
        text-align: center;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        max-width: 100%;
      }

      .tooltip-content {
        padding: 12px;
        text-align: center;

        .measurement {
          display: flex;
          align-items: baseline;
          justify-content: center;
          gap: 8px;

          .value {
            font-size: 24px;
            font-weight: 700;
            color: var(--main-text-color);
            line-height: 1;
          }

          .unit {
            font-size: 12px;
            color: var(--dark-grey);
            font-weight: 500;
            white-space: nowrap;
          }
        }
      }
    }
  }

  .leaflet-tooltip {
    z-index: 1000 !important;
  }

  .leaflet-geosearch-bar {
    width: 300px !important;
    max-width: 300px !important;
    margin: 10px 10px 0 auto !important;
    margin-bottom: 8px !important;
  }

  .leaflet-geosearch-bar form {
    background-image: none;
    border-radius: 100px;
    border: 2px solid var(--grayColor400) !important;
    height: 40px;
    padding: 0;
    overflow: hidden;
  }

  .leaflet-geosearch-bar form input {
    background-image: url('/assets/images/icons/search-fill.svg');
    background-position: left 5px top 1px;
    background-size: 16px;
    background-repeat: no-repeat;
    font-size: var(--font-size-base) !important;
    font-weight: var(--font-weight-medium);
    font-family: var(--primary-font);
    padding-left: 25px !important;
    height: 22px !important;
    padding-right: 50px;
    text-overflow: ellipsis;
    margin: 9px 8px 10px 8px;
    min-width: auto;
    width: calc(100% - 16px);
  }

  .leaflet-geosearch-bar form.open {
    border-radius: 20px;
    height: auto;
    padding-bottom: 0px;
  }

  .leaflet-geosearch-bar form.open .results {
    border-bottom-left-radius: 18px;
    border-bottom-right-radius: 18px;
    padding: 0;
    overflow: hidden;

    div {
      font-family: var(--primary-font);
      border: none;
      padding: 5px 12px;
      text-align: left;
    }

    div:hover {
      background-color: var(--primaryColor500);
      color: var(--main-white-color);
    }
  }

  .leaflet-geosearch-bar form input::placeholder {
    color: var(--grayColor400);
    font-family: var(--primary-font);
  }

  .leaflet-geosearch-bar .reset {
    margin-right: 10px;
    margin-top: 2px;
    font-size: 18px;
    background-color: transparent !important;
  }

  .leaflet-geosearch-bar form input:placeholder-shown + .reset {
    height: 40px !important;
    display: none;
  }

  .map-controls {
    position: absolute;
    top: 60px;
    right: 10px;
    z-index: 999;
    width: 300px;
  }

  .map-extra-controls-box {
    position: absolute;
    top: 110px;
    left: 10px;
    display: flex;
    flex-direction: column;
    gap: 5px;
    z-index: 999;
  }

  @media (max-width: 779px) {
    .leaflet-control-geosearch {
      display: flex;
    }

    .leaflet-geosearch-bar form {
      width: 323px !important;
    }

    .leaflet-geosearch-bar form input {
      background-position: left 5px center;
      padding-left: 14px;
    }
  }

  .leaflet-control-zoom {
    border: 2px solid var(--grayColor400) !important;
    border-radius: 100px !important;
    background-color: var(--main-white-color) !important;
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1) !important;
    padding: 8px 0 !important;
    width: 40px;
    display: flex;
    flex-direction: column;
    align-items: center;
    color: var(--main-text-color) !important;
    gap: 10px;
  }

  .leaflet-control-zoom a {
    background-color: transparent !important;
    border: none !important;
    width: 100% !important;
  }

  .leaflet-control-zoom a.leaflet-disabled {
    color: var(--grayColor500) !important;
  }

  .leaflet-control-zoom a.leaflet-control-zoom-in:hover:not(.leaflet-disabled) {
    color: var(--main-text-color) !important;
    position: relative !important;
  }

  .leaflet-control-zoom a.leaflet-control-zoom-in:hover:not(.leaflet-disabled)::before {
    content: '' !important;
    position: absolute !important;
    top: -8px !important;
    left: 0 !important;
    right: 0 !important;
    bottom: -5px !important;
    background-color: var(--grayColor200) !important;
    border-radius: 100px 100px 0 0 !important;
    z-index: -1 !important;
  }

  .leaflet-control-zoom a.leaflet-control-zoom-out:hover:not(.leaflet-disabled) {
    color: var(--main-text-color) !important;
    position: relative !important;
  }

  .leaflet-control-zoom a.leaflet-control-zoom-out:hover:not(.leaflet-disabled)::before {
    content: '' !important;
    position: absolute !important;
    top: -5px !important;
    left: 0 !important;
    right: 0 !important;
    bottom: -8px !important;
    background-color: var(--grayColor200) !important;
    border-radius: 0 0 100px 100px !important;
    z-index: -1 !important;
  }

  .leaflet-control-zoom::after {
    content: '';
    position: absolute;
    left: 0;
    top: 50%;
    transform: translateY(-50%);
    width: 100%;
    height: 1px;
    background-color: var(--grayColor400);
    z-index: 1;
  }

  .leaflet-control-attribution {
    background-color: var(--main-white-color);
    border-radius: 4px !important;
    padding: 4px 8px !important;
    text-align: center;
    word-wrap: break-word !important;
  }
</style>
