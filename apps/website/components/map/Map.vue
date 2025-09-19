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

    <div class="wildfire-toggle-btn-box">
      <UiIconButton
        :ripple="false"
        :size="ButtonSize.NORMAL"
        icon="mdi-fire"
        :style="wildfireLayerEnabled ? 'primary' : 'light'"
        title="Toggle Wildfire Layer"
        @click="toggleWildfireLayer"
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
      >
      </LMap>
      <div v-if="isLegendShown" class="legend-box">
        <UiMapMarkersLegend />
        <UiColorsLegend />
        <UiWildfireLegend v-if="wildfireLayerEnabled" />
      </div>
    </div>
    <DialogsLocationHistoryDialog v-if="locationHistoryDialog" :dialog="locationHistoryDialog" />
  </div>
</template>

<script lang="ts" setup>
  import { computed, onMounted, onUnmounted, ref } from 'vue';
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
  import UiWildfireLegend from '~/components/ui/WildfireLegend.vue';
  import { useStorage } from '@vueuse/core';
  import { useApiErrorHandler } from '~/composables/shared/useApiErrorHandler';
  import { useWildfireData } from '~/composables/shared/useWildfireData';
  import { createVueDebounce } from '~/utils/debounce';

  const loading = ref<boolean>(false);
  const map = ref<typeof LMap>();
  const apiUrl = useRuntimeConfig().public.apiUrl;
  const generalConfigStore = useGeneralConfigStore();
  const { handleApiError } = useApiErrorHandler();
  const { fetchWildfires } = useWildfireData();

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
  const wildfireLayerEnabled = useStorage('wildfireLayerEnabled', false);

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
      label: MEASURE_LABELS_WITH_UNITS[MeasureNames.RCO2],
      value: MeasureNames.RCO2
    }
  ];

  const updateMapDebounced = createVueDebounce(updateMapData, 400);

  let geoJsonMapData: GeoJsonObject;
  let mapInstance: L.Map;
  let markers: GeoJSON;
  let wildfireMarkers: L.GeoJSON | null = null;

  const fireColors = {
    low: '#FFA500',
    medium: '#FF4500',
    high: '#FF0000',
    extreme: '#8B0000'
  };

  const normalizeLongitude = (lng: number): number => {
    while (lng > 180) lng -= 360;
    while (lng < -180) lng += 360;
    return lng;
  };

  const onMapReady = () => {
    setUpMapInstance();
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
        const attributionControl = L.control.attribution({
          prefix: attributionContent
        });
        attributionControl.addTo(mapInstance);
      }
    } catch (error) {
      console.warn('Failed to set custom attribution:', error);
    }

    L.maplibreGL({
      style: 'https://tiles.openfreemap.org/styles/liberty',
      center: [Number(urlState.lat), Number(urlState.long)],
      zoom: Number(urlState.zoom)
    }).addTo(mapInstance);

    markers = L.geoJson(null, {
      pointToLayer: createMarker
    }).addTo(mapInstance);

    mapInstance.on('moveend', () => {
      setUrlState({
        zoom: mapInstance.getZoom(),
        lat: mapInstance.getCenter().lat.toFixed(2),
        long: mapInstance.getCenter().lng.toFixed(2)
      });

      updateMapDebounced();
    });

    startRefreshInterval();
    mapInstance.whenReady(() => {
      updateMapData();
    });
  }

  function toggleWildfireLayer(): void {
    wildfireLayerEnabled.value = !wildfireLayerEnabled.value;
    console.log('Wildfire layer toggled:', wildfireLayerEnabled.value);

    if (wildfireLayerEnabled.value) {
      updateMapDebounced();
    } else {
      clearWildfireLayer();
    }
  }

  function clearWildfireLayer(): void {
    console.log('Clearing wildfire layer');
    if (wildfireMarkers && mapInstance) {
      mapInstance.removeLayer(wildfireMarkers);
      wildfireMarkers = null;
    }
  }

  function updateWildfireMarkers(wildfireData: any): void {
    console.log('updateWildfireMarkers called with:', wildfireData);
    console.log('Number of fire features:', wildfireData.features?.length || 0);

    clearWildfireLayer();

    if (!wildfireData.features || wildfireData.features.length === 0) {
      console.log('No fire data to display');
      return;
    }

    wildfireMarkers = L.geoJSON(wildfireData, {
      pointToLayer: (feature, latlng) => {
        const intensity = feature.properties.intensity;
        const baseSize = 6;
        const sizeMultiplier =
          intensity === 'extreme' ? 4 : intensity === 'high' ? 3 : intensity === 'medium' ? 2 : 1;

        return L.circleMarker(latlng, {
          radius: baseSize + sizeMultiplier,
          fillColor: fireColors[intensity],
          color: fireColors[intensity],
          weight: 2,
          opacity: 0.9,
          fillOpacity: 0.7
        });
      },
      onEachFeature: (feature, layer) => {
        const props = feature.properties;
        layer.bindPopup(`
          <div class="fire-popup">
            <h4>🔥 Active Fire</h4>
            <p><strong>Intensity:</strong> ${props.intensity.toUpperCase()}</p>
            <p><strong>Confidence:</strong> ${props.confidence}%</p>
            <p><strong>Fire Power:</strong> ${props.frp.toFixed(1)} MW</p>
            <p><strong>Temperature:</strong> ${props.brightness.toFixed(1)}K</p>
            <p><strong>Detected:</strong> ${props.acq_date} ${props.acq_time}Z</p>
            <p><strong>Satellite:</strong> ${props.satellite}</p>
          </div>
        `);
      }
    });

    if (mapInstance && wildfireMarkers) {
      wildfireMarkers.addTo(mapInstance);
      console.log('Wildfire layer added to map with', wildfireData.features.length, 'fires');
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

        mapInstance.flyTo(latlng, newZoom, {
          animate: true,
          duration: 0.8
        });
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

      const normalizedBounds = {
        north: bounds.getNorth(),
        south: bounds.getSouth(),
        east: normalizeLongitude(bounds.getEast()),
        west: normalizeLongitude(bounds.getWest())
      };

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

      if (wildfireLayerEnabled.value) {
        const wildfireData = await fetchWildfires({
          north: normalizedBounds.north,
          south: normalizedBounds.south,
          east: normalizedBounds.east,
          west: normalizedBounds.west,
          days: 1
        });

        if (wildfireData) {
          updateWildfireMarkers(wildfireData);
        }
      } else {
        clearWildfireLayer();
      }

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
      mapInstance.flyTo([lat, lng], 12, {
        animate: true,
        duration: 1.2
      });
    }
  }

  function handleGeolocationError(message: string): void {
    console.error('Geolocation error:', message);
  }

  onMounted(() => {
    generalConfigStore.setHeadless(window.location.href.includes('headless=true'));
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
  @include desktop {
    #map {
      height: calc(100svh - 117px);
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

  .leaflet-geosearch-bar {
    margin-bottom: 8px !important;
  }

  .legend-box {
    position: absolute;
    bottom: 35px;
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

  .wildfire-toggle-btn-box {
    position: absolute;
    top: 198px;
    left: 10px;
    z-index: 999;
  }

  .fire-popup {
    font-family: var(--secondary-font);

    h4 {
      margin: 0 0 8px 0;
      color: #8b0000;
    }

    p {
      margin: 4px 0;
      font-size: 12px;
    }
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

    .legend-box {
      bottom: 45px;
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
