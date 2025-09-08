import { reactive, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { DEFAULT_MAP_VIEW_CONFIG, DEFAULT_URL_CONFIG } from '~/constants';

/**
 * Syncs reactive URL state with the route query parameters.
 *
 * This composable provides a two-way binding between a reactive `urlState` object
 * and the route's query parameters. Changes to either will update the other
 * without causing full page reloads.
 *
 * @returns {{
 *   urlState: Record<string, any>,
 *   setUrlState: (newState: Partial<typeof urlState>) => void
 * }}
 */
export const useUrlState = () => {
  const route = useRoute();
  const router = useRouter();

  // Initialize the reactive URL state with defaults and current query
  const urlState = reactive({
    zoom: DEFAULT_MAP_VIEW_CONFIG.zoom,
    long: DEFAULT_MAP_VIEW_CONFIG.center[1],
    lat: DEFAULT_MAP_VIEW_CONFIG.center[0],
    meas: DEFAULT_URL_CONFIG.meas,
    wind_layer: String(DEFAULT_MAP_VIEW_CONFIG.wind_layer),
    org: DEFAULT_URL_CONFIG.org,
    ...route.query
  });

  // Watch for changes to urlState and sync to URL query
  watch(
    urlState,
    newState => {
      router.replace({
        query: {
          ...route.query,
          ...newState
        }
      });
    },
    { deep: true }
  );

  // Watch for changes in URL query and update the urlState
  watch(
    () => route.query,
    newQuery => {
      Object.assign(urlState, newQuery);
    },
    { deep: true }
  );

  /**
   * Update the reactive URL state.
   * Triggers the watch that updates the query string in the URL.
   *
   * @param {Partial<typeof urlState>} newState
   */
  const setUrlState = (newState: Partial<typeof urlState>) => {
    Object.assign(urlState, newState);
  };

  return {
    urlState,
    setUrlState
  };
};
