import { ref } from 'vue';
import { useRuntimeConfig } from 'nuxt/app';
import type { WildfireGeoJSON, WildfireQueryParams } from '~/types/shared/wildfire';
import { useApiErrorHandler } from './useApiErrorHandler';

export const useWildfireData = () => {
  const { handleApiError } = useApiErrorHandler();
  const config = useRuntimeConfig();

  const wildfireData = ref<WildfireGeoJSON | null>(null);
  const isLoading = ref(false);
  const lastUpdate = ref<Date | null>(null);

  const normalizeLongitude = (lng: number): number => {
    while (lng > 180) lng -= 360;
    while (lng < -180) lng += 360;
    return lng;
  };

  const validateBounds = (params: WildfireQueryParams): WildfireQueryParams => {
    const normalizedParams = {
      ...params,
      north: Math.min(Math.max(params.north, -90), 90),
      south: Math.min(Math.max(params.south, -90), 90),
      east: normalizeLongitude(params.east),
      west: normalizeLongitude(params.west)
    };

    if (normalizedParams.north <= normalizedParams.south) {
      console.warn('Invalid latitude bounds, swapping north/south');
      [normalizedParams.north, normalizedParams.south] = [
        normalizedParams.south,
        normalizedParams.north
      ];
    }

    return normalizedParams;
  };

  const fetchWildfires = async (params: WildfireQueryParams): Promise<WildfireGeoJSON | null> => {
    isLoading.value = true;

    try {
      // Validate and normalize parameters
      const validatedParams = validateBounds(params);

      console.log('Original bounds:', params);
      console.log('Validated bounds:', validatedParams);

      const queryParams = new URLSearchParams({
        north: validatedParams.north.toString(),
        south: validatedParams.south.toString(),
        east: validatedParams.east.toString(),
        west: validatedParams.west.toString(),
        days: (validatedParams.days || 1).toString(),
        source: validatedParams.source || 'VIIRS_SNPP_NRT'
      });

      const response = await $fetch<WildfireGeoJSON>(
        `${config.public.apiUrl}/wildfire/geojson?${queryParams}`,
        {
          timeout: 15000,
          retry: 1
        }
      );

      console.log('Wildfire API response:', response);
      console.log('Number of fires:', response.features?.length || 0);

      wildfireData.value = response;
      lastUpdate.value = new Date();
      return response;
    } catch (error: unknown) {
      console.error('Wildfire fetch error:', error);

      if (error && typeof error === 'object' && 'response' in error) {
        const apiError = error as { response?: { status?: number; data?: unknown } };
        if (apiError.response?.status === 400) {
          console.error('Bad request - likely invalid bounds:', apiError.response.data);
        }
      }

      handleApiError(error, 'Failed to load wildfire data');
      return null;
    } finally {
      isLoading.value = false;
    }
  };

  return {
    wildfireData,
    isLoading,
    lastUpdate,
    fetchWildfires
  };
};
