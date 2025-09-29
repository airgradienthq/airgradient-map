<template>
  <div class="map-wrapper">
    <div class="map-info-btn-box">
      <UiIconButton
        :ripple="false"
        :size="ButtonSize.NORMAL"
        icon="mdi-information-outline"
        :style="'light'"
        @click="isLegendShown = !isLegendShown"
      >
      </UiIconButton>
    </div>

    <div class="map-geolocation-btn-box">
      <UiGeolocationButton @location-found="handleLocationFound" @error="handleGeolocationError" />
    </div>

    <div class="wind-toggle-btn-box">
      <UiIconButton
        :ripple="false"
        :size="ButtonSize.NORMAL"
        icon="mdi-weather-windy"
        :style="'light'"
        @click="toggleWindLayer"
        title="Toggle Wind Layer"
      >
      </UiIconButton>
    </div>

    <UiProgressBar :show="loading"></UiProgressBar>
    <div id="map">
      <div class="map-controls">
        <UiDropdownControl
          :selected-value="generalConfigStore.selectedMeasure"
          :options="measureSelectOptions"
          :disabled="loading"
          @change="handleMeasureChange"
        >
        </UiDropdownControl>
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
        @move="onMapMove"
        @zoom="onMapMove"
      >
      </LMap>

      <WindVisualization
        v-if="windLayerEnabled && mapBounds"
        :width="mapSize.width"
        :height="mapSize.height"
        :bounds="mapBounds"
        :zoom="mapInstance?.getZoom() || 3"
        :wind-data-url="windDataUrl"
        :particle-count="windParticleCount"
        :velocity-scale="0.8"
        :is-moving="isMapMoving"
        class="wind-overlay"
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
  import { computed, onMounted, onUnmounted, ref, nextTick } from 'vue';
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

  const loading = ref<boolean>(false);
  const map = ref<typeof LMap>();
  const apiUrl = useRuntimeConfig().public.apiUrl;
  const generalConfigStore = useGeneralConfigStore();
  const { handleApiError } = useApiErrorHandler();

  const { startRefreshInterval, stopRefreshInterval } = useIntervalRefresh(
    updateMapData,
    CURRENT_DATA_REFRESH_INTERVAL,
    {
      skipFirstRefresh: true,
      skipOnVisibilityHidden: true
    }
  );

  const locationHistoryDialogId = DialogId.LOCATION_HISTORY_CHART;
  const isLegendShown = useStorage('isLegendShown', true);

  const windLayerEnabled = useStorage('windLayerEnabled', false);
  const mapSize = ref({ width: 800, height: 600 });
  const mapBounds = ref<{ north: number; south: number; east: number; west: number } | null>(null);
  const isMapMoving = ref(false);

  const { urlState, setUrlState } = useUrlState();

  const locationHistoryDialog = computed(() => dialogStore.getDialog(locationHistoryDialogId));

  const windDataUrl = computed(() => 'http://localhost:3001/map/api/v1/wind-data/file');
  const windParticleCount = computed(() => {
    const area = mapSize.value.width * mapSize.value.height;
    return Math.min(Math.max(Math.floor(area / 5000), 500), 3000);
  });

  const measureSelectOptions: DropdownOption[] = [
    { label: MEASURE_LABELS_WITH_UNITS[MeasureNames.PM25], value: MeasureNames.PM25 },
    { label: MEASURE_LABELS_WITH_UNITS[MeasureNames.PM_AQI], value: MeasureNames.PM_AQI },
    { label: MEASURE_LABELS_WITH_UNITS[MeasureNames.RCO2], value: MeasureNames.RCO2 }
  ];

  const updateMapDebounced = createVueDebounce(updateMapData, 400);

  let geoJsonMapData: GeoJsonObject;
  let mapInstance: L.Map;
  let markers: GeoJSON;

  const onMapReady = () => {
    setUpMapInstance();
    addGeocodeControl();
    updateMapDimensions();
  };

  function setUpMapInstance(): void {
    if (!map.value) return;

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

    L.maplibreGL({
      style: 'https://tiles.openfreemap.org/styles/liberty',
      center: [Number(urlState.lat), Number(urlState.long)],
      zoom: Number(urlState.zoom)
    }).addTo(mapInstance);

    markers = L.geoJson(null, { pointToLayer: createMarker }).addTo(mapInstance);

    mapInstance.on('movestart zoomstart', () => {
      isMapMoving.value = true;
    });
    mapInstance.on('moveend zoomend', () => {
      isMapMoving.value = false;
    });

    mapInstance.on('moveend', () => {
      setUrlState({
        zoom: mapInstance.getZoom(),
        lat: mapInstance.getCenter().lat.toFixed(2),
        long: mapInstance.getCenter().lng.toFixed(2)
      });

      updateMapDebounced();
    });

    mapInstance.on('resize', updateMapDimensions);

    startRefreshInterval();
    mapInstance.whenReady(() => {
      updateMapData();
    });
  }

  function updateMapDimensions(): void {
    if (!mapInstance) return;
    const container = mapInstance.getContainer();
    mapSize.value = { width: container.offsetWidth, height: container.offsetHeight };
    updateMapBounds();
  }

  function updateMapBounds(): void {
    if (!mapInstance) return;
    const bounds = mapInstance.getBounds();
    mapBounds.value = {
      north: bounds.getNorth(),
      south: bounds.getSouth(),
      east: bounds.getEast(),
      west: bounds.getWest()
    };
  }

  function onMapMove(): void {
    updateMapBounds();
  }

  function toggleWindLayer(): void {
    windLayerEnabled.value = !windLayerEnabled.value;
    if (windLayerEnabled.value) {
      nextTick(() => updateMapDimensions());
    }
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
        dialogStore.open(locationHistoryDialogId, { location: feature.properties });
      } else if (!isSensor) {
        const currentZoom = mapInstance.getZoom();
        const newZoom = Math.min(currentZoom + 2, DEFAULT_MAP_VIEW_CONFIG.maxZoom);
        mapInstance.flyTo(latlng, newZoom, { animate: true, duration: 0.8 });
      }
    });

    return marker;
  }

  async function updateMapData(): Promise<void> {
    if (loading.value || locationHistoryDialog.value?.isOpen) {
      return;
    }

    loading.value = true;

    try {
      const bounds: LatLngBounds = mapInstance.getBounds();
      const response = await $fetch<AGMapData>(`${apiUrl}/measurements/current/cluster`, {
        params: {
          xmin: bounds.getWest(),
          ymin: bounds.getSouth(),
          xmax: bounds.getEast(),
          ymax: bounds.getNorth(),
          zoom: mapInstance.getZoom(),
          measure:
            generalConfigStore.selectedMeasure === MeasureNames.PM_AQI
              ? MeasureNames.PM25
              : generalConfigStore.selectedMeasure
        },
        retry: 1
      });

      const geoJsonData: GeoJsonObject = convertToGeoJSON(response.data);
      geoJsonMapData = geoJsonData;

      requestAnimationFrame(() => {
        markers.clearLayers();
        markers.addData(geoJsonData);
      });
    } catch (error) {
      console.error('Failed to fetch map data:', error);
      handleApiError(error, 'Failed to load map data. Please try again.');
    } finally {
      loading.value = false;
    }
  }

  function addGeocodeControl(): void {
    const provider = new OpenStreetMapProvider();

    const searchControl = GeoSearchControl({
      provider,
      style: 'bar',
      autoClose: true,
      keepResult: true,
      searchLabel: 'Search'
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

  function disableScrollWheelZoomForHeadless(): void {
    if (generalConfigStore.headless) {
      mapInstance.scrollWheelZoom.disable();
    }
  }

  function handleLocationFound(lat: number, lng: number): void {
    if (mapInstance) {
      mapInstance.flyTo([lat, lng], 12, { animate: true, duration: 1.2 });
    }
  }

  function handleGeolocationError(message: string): void {
    console.error('Geolocation error:', message);
  }

  onMounted(() => {
    generalConfigStore.setHeadless(window.location.href.includes('headless=true'));
    if ([<MeasureNames>'pm02', <MeasureNames>'pm02_raw'].includes(urlState.meas)) {
      setUrlState({ meas: MeasureNames.PM25 });
    } else if (urlState.meas === <MeasureNames>'pi02') {
      setUrlState({ meas: MeasureNames.PM_AQI });
    }
    useGeneralConfigStore().setSelectedMeasure(urlState.meas);
  });

  onUnmounted(() => {
    stopRefreshInterval();
  });
</script>

<style lang="scss">
  .map-wrapper {
    position: relative;
  }

  #map {
    height: calc(100svh - 130px);
  }

  .wind-overlay {
    position: absolute;
    top: 0;
    left: 0;
    pointer-events: none;
    z-index: 200;
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
    background-image: url('/assets/images/icons/iconamoon_search-fill.svg');
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

  .map-info-btn-box {
    position: absolute;
    top: 110px;
    left: 10px;
    z-index: 999;
  }

  .map-geolocation-btn-box {
    position: absolute;
    top: 154px;
    left: 10px;
    z-index: 999;
  }

  .wind-toggle-btn-box {
    position: absolute;
    top: 198px;
    left: 10px;
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
