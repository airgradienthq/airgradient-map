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
  import { ToastType } from '~/types/shared/ui';

  // Toast composable for global state management
  const { toast, hideToast } = useToast();

  /**
   * Maps toast type to corresponding Vuetify color theme.
   */
  const getColor = (type?: ToastType): string => {
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
   */
  const getIcon = (type?: ToastType): string => {
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
   */
  const onUpdate = (value: boolean): void => {
    if (!value) {
      hideToast();
    }
  };
</script>