import { ref, Ref } from 'vue';
import { GeolocationResult } from '~/types/shared/geolocation';

interface IpInfoResponse {
  loc: string;
  city: string;
  country: string;
}

interface IpApiResponse {
  lat: number;
  lon: number;
  city: string;
  country: string;
}

export const useGeolocation = () => {
  const isLoading: Ref<boolean> = ref(false);
  const error: Ref<string | null> = ref(null);

  const getCurrentLocation = async (): Promise<GeolocationResult | null> => {
    isLoading.value = true;
    error.value = null;

    const services = [
      {
        name: 'ipinfo.io',
        url: 'https://ipinfo.io/json',
        parser: (data: IpInfoResponse) => ({
          lat: parseFloat(data.loc.split(',')[0]),
          lng: parseFloat(data.loc.split(',')[1]),
          city: data.city,
          country: data.country
        })
      },
      {
        name: 'ip-api.com',
        url: 'http://ip-api.com/json/',
        parser: (data: IpApiResponse) => ({
          lat: data.lat,
          lng: data.lon,
          city: data.city,
          country: data.country
        })
      }
    ];

    for (const service of services) {
      try {
        const response = await fetch(service.url, {
          method: 'GET',
          mode: 'cors',
          headers: {
            Accept: 'application/json'
          }
        });

        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`);
        }

        const data = await response.json();
        const result = await service.parser(data);

        if (result.lat && result.lng) {
          return result;
        }
      } catch {
        continue;
      }
    }

    throw new Error('All geolocation services failed');
  };

  const getLocationWithErrorHandling = async (): Promise<GeolocationResult | null> => {
    try {
      isLoading.value = true;
      error.value = null;
      return await getCurrentLocation();
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Failed to get location';
      error.value = errorMessage;
      return null;
    } finally {
      isLoading.value = false;
    }
  };

  return {
    isLoading,
    error,
    getCurrentLocation: getLocationWithErrorHandling
  };
};
