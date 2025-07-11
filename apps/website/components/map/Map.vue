L
<template>
  <div class="map-info-btn-box">
    <UiIconButton
      :ripple="false"
      :size="ButtonSize.NORMAL"
      icon="mdi-information-outline"
      :style="'map'"
      @click="isLegendShown = !isLegendShown"
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
      @ready="onMapReady"
    >
    </LMap>
    <div v-if="isLegendShown" class="legend-box">
      <UiMapMarkersLegend />
      <UiColorsLegend />
    </div>
  </div>
  <DialogsLocationHistoryDialog v-if="locationHistoryDialog" :dialog="locationHistoryDialog" />
</template>

<script lang="ts" setup>
  import { computed, onMounted, ref } from 'vue';
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
  import { useStorage } from '@vueuse/core';

  const loading = ref<boolean>(false);
  const map = ref<typeof LMap>();
  const apiUrl = useRuntimeConfig().public.apiUrl;
  const generalConfigStore = useGeneralConfigStore();
  const { startRefreshInterval, stopRefreshInterval, isRefreshIntervalActive } = useIntervalRefresh(
    updateMapData,
    CURRENT_DATA_REFRESH_INTERVAL,
    {
      skipFirstRefresh: true,
      skipOnVisibilityHidden: true
    }
  );

  const locationHistoryDialogId = DialogId.LOCATION_HISTORY_CHART;
  const isLegendShown = useStorage('isLegendShown', true);

  const { urlState, setUrlState } = useUrlState();

  const locationHistoryDialog = computed(() => dialogStore.getDialog(locationHistoryDialogId));

  const measureSelectOptions: DropdownOption[] = [
    {
      label: MEASURE_LABELS_WITH_UNITS[MeasureNames.PM25],
      value: MeasureNames.PM25
    },
    {
      label: MEASURE_LABELS_WITH_UNITS[MeasureNames.PM_AQI],
      value: MeasureNames.PM_AQI
    },
    {
      label: MEASURE_LABELS_WITH_UNITS[MeasureNames.CO2],
      value: MeasureNames.CO2
    }
  ];

  let geoJsonMapData: GeoJsonObject;
  let mapInstance: L.Map;
  let markers: GeoJSON;

  const onMapReady = () => {
    setUpMapInstance();
    addGeocodeControl();
  };

  function setUpMapInstance(): void {
    if (!map.value) {
      return;
    }

    mapInstance = map.value.leafletObject;

    L.maplibreGL({
      style: 'https://tiles.openfreemap.org/styles/liberty',
      center: [Number(urlState.lat), Number(urlState.long)],
      zoom: Number(urlState.zoom)
    }).addTo(mapInstance);

    markers = L.geoJson(null, {
      pointToLayer: createMarker
    }).addTo(mapInstance);

    mapInstance.on('moveend', updateMap);
    mapInstance.whenReady(updateMap);
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

        mapInstance.flyTo(latlng, newZoom, {
          animate: true,
          duration: 0.8
        });
      }
    });

    return marker;
  }

  async function updateMap(): Promise<void> {
    if (loading.value || locationHistoryDialog.value?.isOpen) {
      return;
    }
    loading.value = true;

    setUrlState({
      zoom: mapInstance.getZoom(),
      lat: mapInstance.getCenter().lat.toFixed(2),
      long: mapInstance.getCenter().lng.toFixed(2)
    });

    if (isRefreshIntervalActive.value) {
      stopRefreshInterval();
    } else {
      startRefreshInterval();
    }

    await updateMapData();
  }

  async function updateMapData(): Promise<void> {
    try {
      const bounds: LatLngBounds = mapInstance.getBounds();
      const response = await $fetch<AGMapData>(`${apiUrl}/measurements/current/cluster`, {
        params: {
          xmin: bounds.getSouth(),
          ymin: bounds.getWest(),
          xmax: bounds.getNorth(),
          ymax: bounds.getEast(),
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
      markers.clearLayers();
      markers.addData(geoJsonData);
    } catch (error) {
      console.error('Failed to fetch map data:', error);
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
      keepResult: true
    });

    mapInstance.addControl(searchControl);
  }

  function handleMeasureChange(value: MeasureNames): void {
    const previousMeasure = generalConfigStore.selectedMeasure;
    useGeneralConfigStore().setSelectedMeasure(value);
    setUrlState({
      meas: value
    });

    if (
      [MeasureNames.PM25, MeasureNames.PM_AQI].includes(previousMeasure) &&
      [MeasureNames.PM25, MeasureNames.PM_AQI].includes(value)
    ) {
      markers.clearLayers();
      markers.addData(geoJsonMapData);
    } else {
      updateMap();
    }
  }

  onMounted(() => {
    if ([<MeasureNames>'pm02', <MeasureNames>'pm02_raw'].includes(urlState.meas)) {
      setUrlState({
        meas: MeasureNames.PM25
      });
    } else if (urlState.meas === <MeasureNames>'pi02') {
      setUrlState({
        meas: MeasureNames.PM_AQI
      });
    }
    useGeneralConfigStore().setSelectedMeasure(urlState.meas);
  });
</script>

<style lang="scss">
  #map {
    height: calc(100vh - 5px);
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
  }

  .leaflet-geosearch-bar form {
    padding-left: 0;
    background-image: none;
  }

  .leaflet-geosearch-bar form input {
    padding-left: 30px !important;
    background-image: url('/assets/images/icons/search.svg');
    background-position: 5px center;
    background-size: 20px;
    background-repeat: no-repeat;
    height: 36px !important;
    font-size: 16px !important;
  }

  .leaflet-control-geosearch .results.active {
    width: 100% !important;
    margin-left: 0 !important;
  }

  .results > .active,
  .leaflet-control-geosearch .results > :hover {
    color: var(--primary-color);
    border-radius: 4px;
    border-color: transparent;
  }

  .leaflet-control-geosearch .results > *::before {
    display: none !important;
  }

  .map-controls {
    position: absolute;
    top: 60px;
    right: 10px;
    z-index: 999;
    width: 300px;
  }

  .display-type-selector {
    width: 100%;
    height: 36px;
    padding: 0 12px;
    font-family: var(--secondary-font);
    font-size: 16px;
    border: 1px solid rgba(0, 0, 0, 0.2);
    border-radius: 4px;
    background: var(--main-white-color);
    color: var(--main-text-color);
    cursor: pointer;
    outline: none;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
    appearance: none;
    -webkit-appearance: none;
    -moz-appearance: none;
    background-image: url("data:image/svg+xml;charset=UTF-8,%3csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3e%3cpolyline points='6 9 12 15 18 9'%3e%3c/polyline%3e%3c/svg%3e");
    background-repeat: no-repeat;
    background-position: right 8px center;
    background-size: 16px;
    padding-right: 32px;

    &:hover {
      border-color: rgba(0, 0, 0, 0.3);
    }

    &:focus {
      border-color: var(--primary-color);
      box-shadow: 0 0 0 3px rgba(var(--primary-color), 0.1);
    }
  }

  .leaflet-geosearch-bar {
    margin-bottom: 8px !important;
  }

  .display-type-selector:-moz-focusring {
    color: transparent;
    text-shadow: 0 0 0 #000;
  }

  .display-type-selector::-ms-expand {
    display: none;
  }

  .legend-box {
    position: absolute;
    bottom: 30px;
    left: 50%;
    z-index: 400;
    width: 900px;
    transform: translateX(-50%);
    max-width: 90%;
    display: flex;
    gap: 20px;
    flex-direction: column;
    align-items: center;
    text-shadow: 1px 2px 2px rgb(108 108 108 / 43%);
  }

  .map-info-btn-box {
    position: absolute;
    top: 90px;
    left: 10px;
    z-index: 999;
  }
</style>
