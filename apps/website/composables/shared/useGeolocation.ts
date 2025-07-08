import { ref, Ref } from 'vue';

export interface GeolocationResult {
  lat: number;
  lng: number;
  city?: string;
  country?: string;
}

/**
 * Composable for IP-based geolocation functionality
 * @returns Object with geolocation methods and state
 */
export const useGeolocation = () => {
  const isLoading: Ref<boolean> = ref(false);
  const error: Ref<string | null> = ref(null);

  /**
   * Get user's location using IP-based geolocation
   * @returns Promise<GeolocationResult | null>
   */
  const getCurrentLocation = async (): Promise<GeolocationResult | null> => {
    isLoading.value = true;
    error.value = null;

    try {
      // Try browser geolocation first (more accurate)
      if (navigator.geolocation) {
        try {
          const position = await new Promise<GeolocationPosition>((resolve, reject) => {
            navigator.geolocation.getCurrentPosition(resolve, reject, {
              timeout: 5000,
              enableHighAccuracy: false
            });
          });

          return {
            lat: position.coords.latitude,
            lng: position.coords.longitude
          };
        } catch {
          // Fall back to IP geolocation if browser geolocation fails
          console.log('Browser geolocation failed, falling back to IP geolocation');
        }
      }

      // Fallback to IP-based geolocation
      const response = await fetch('https://ipapi.co/json/');

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const data = await response.json();

      if (data.error) {
        throw new Error(data.reason || 'Geolocation service error');
      }

      return {
        lat: data.latitude,
        lng: data.longitude,
        city: data.city,
        country: data.country_name
      };
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Failed to get location';
      error.value = errorMessage;
      console.error('Geolocation error:', errorMessage);
      return null;
    } finally {
      isLoading.value = false;
    }
  };

  return {
    isLoading,
    error,
    getCurrentLocation
  };
};
