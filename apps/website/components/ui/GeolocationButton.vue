<template>
  <UiIconButton
    :ripple="false"
    :size="ButtonSize.NORMAL"
    :disabled="isLoading"
    icon="mdi-navigation"
    :style="'light'"
    class="rotated-navigation-icon"
    @click="handleGeolocation"
  >
  </UiIconButton>
</template>

<script setup lang="ts">
  import { ButtonSize } from '~/types';
  import { useGeolocation } from '~/composables/shared/useGeolocation';

  interface GeolocationButtonEmits {
    locationFound: [lat: number, lng: number];
    error: [message: string];
  }

  const emit = defineEmits<GeolocationButtonEmits>();

  const { isLoading, error, getCurrentLocation } = useGeolocation();

  /**
   * Handle geolocation button click
   */
  const handleGeolocation = async () => {
    const location = await getCurrentLocation();

    if (location) {
      emit('locationFound', location.lat, location.lng);
    } else if (error.value) {
      emit('error', error.value);
    }
  };
</script>

<style scoped>
  .rotated-navigation-icon :deep(.v-icon),
  .rotated-navigation-icon :deep(svg) {
    transform: rotate(45deg);
  }
</style>
