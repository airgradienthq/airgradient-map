<template>
  <v-snackbar
    v-if="toast"
    v-model="toast.show"
    :color="getColor(toast?.type)"
    location="top right"
    timeout="5000"
    @update:model-value="onUpdate"
  >
    <div class="d-flex align-center">
      <v-icon :icon="getIcon(toast?.type)" class="mr-2" />
      {{ toast?.message }}
    </div>

    <template #actions>
      <v-btn icon="mdi-close" variant="text" @click="hideToast" />
    </template>
  </v-snackbar>
</template>

<script setup lang="ts">
  import { useToast } from '~/composables/useToast';

  /**
   * Toast notification types supported by the component
   */
  export type ToastType = 'success' | 'error' | 'warning' | 'info';

  /**
   * Toast color mapping for Vuetify components
   */
  export type ToastColor = 'success' | 'error' | 'warning' | 'info';

  /**
   * Toast icon mapping for Material Design Icons
   */
  export type ToastIcon = 'mdi-check-circle' | 'mdi-alert-circle' | 'mdi-alert' | 'mdi-information';

  const props = defineProps({
    /**
     * Custom location for the toast position.
     * @type {string}
     * @default 'top right'
     */
    location: {
      type: String,
      default: 'top right'
    },
    /**
     * Custom timeout duration in milliseconds.
     * @type {number | string}
     * @default 5000
     */
    timeout: {
      type: [Number, String],
      default: 5000
    },
    /**
     * Whether to show the close button.
     * @type {boolean}
     * @default true
     */
    closable: {
      type: Boolean,
      default: true
    }
  });

  // Toast composable for global state management
  const { toast, hideToast } = useToast();

  /**
   * Maps toast type to corresponding Vuetify color theme.
   * @param {ToastType} type - The toast type to map.
   * @returns {ToastColor} The corresponding Vuetify color theme.
   */
  const getColor = (type?: ToastType): ToastColor => {
    switch (type) {
      case 'success':
        return 'success';
      case 'error':
        return 'error';
      case 'warning':
        return 'warning';
      default:
        return 'info';
    }
  };

  /**
   * Maps toast type to corresponding Material Design Icon.
   * @param {ToastType} type - The toast type to map.
   * @returns {ToastIcon} The corresponding MDI icon name.
   */
  const getIcon = (type?: ToastType): ToastIcon => {
    switch (type) {
      case 'success':
        return 'mdi-check-circle';
      case 'error':
        return 'mdi-alert-circle';
      case 'warning':
        return 'mdi-alert';
      default:
        return 'mdi-information';
    }
  };

  /**
   * Handles the snackbar model value update event.
   * Triggers hideToast when the snackbar is closed.
   * @param {boolean} value - The new model value from v-snackbar.
   */
  const onUpdate = (value: boolean): void => {
    if (!value) {
      hideToast();
    }
  };
</script>
