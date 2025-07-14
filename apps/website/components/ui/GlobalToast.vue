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
  import { ToastType, type ToastColor, type ToastIcon } from '~/types/shared/ui';

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
      case ToastType.SUCCESS:
        return 'success';
      case ToastType.ERROR:
        return 'error';
      case ToastType.WARNING:
        return 'warning';
      case ToastType.INFO:
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
      case ToastType.SUCCESS:
        return 'mdi-check-circle';
      case ToastType.ERROR:
        return 'mdi-alert-circle';
      case ToastType.WARNING:
        return 'mdi-alert';
      case ToastType.INFO:
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