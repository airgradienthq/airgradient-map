<template>
  <v-snackbar
    v-model="isVisible"
    :timeout="toast?.timeout || defaultTimeout"
    :color="toastColor"
    :persistent="toast?.persistent"
    :location="defaultLocation"
    elevation="6"
    rounded="lg"
    class="toast-snackbar"
    multi-line
  >
    <div class="d-flex align-center">
      <v-icon 
        v-if="showIcon"
        :icon="toastIcon" 
        class="mr-3" 
        :size="iconSize" 
      />
      <span class="toast-message">{{ toast?.message }}</span>
    </div>

    <template v-if="showCloseButton" v-slot:actions>
      <v-btn
        :aria-label="closeButtonLabel"
        :icon="closeIcon"
        size="small"
        variant="text"
        @click="hideToast"
      />
    </template>
  </v-snackbar>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useToast } from '~/composables/useToast'

/**
 * Toast type variants for different notification states
 */
export enum ToastType {
  /** Success notification with green styling */
  SUCCESS = 'success',
  /** Error notification with red styling */
  ERROR = 'error',
  /** Warning notification with orange/yellow styling */
  WARNING = 'warning',
  /** Informational notification with blue styling */
  INFO = 'info'
}

/**
 * Toast position options for snackbar placement
 * Uses Vuetify's location prop values
 */
export enum ToastLocation {
  /** Top left corner */
  TOP_LEFT = 'top left',
  /** Top center */
  TOP_CENTER = 'top',
  /** Top right corner */
  TOP_RIGHT = 'top right',
  /** Bottom left corner */
  BOTTOM_LEFT = 'bottom left',
  /** Bottom center */
  BOTTOM_CENTER = 'bottom',
  /** Bottom right corner */
  BOTTOM_RIGHT = 'bottom right',
  /** Left center */
  LEFT = 'left',
  /** Right center */
  RIGHT = 'right'
}

/**
 * Current toast interface from useToast composable
 * This matches your existing implementation
 */
export interface CurrentToastConfig {
  /** Toast message content */
  message: string
  /** Toast notification type */
  type?: 'success' | 'error' | 'warning' | 'info'
  /** Auto-hide timeout in milliseconds (0 = no timeout) */
  timeout?: number
  /** Whether toast persists until manually closed */
  persistent?: boolean
}

/**
 * Props interface for GlobalToast component
 */
export interface GlobalToastProps {
  /** Default timeout for auto-hide (milliseconds) */
  defaultTimeout?: number
  /** Default location for toast placement */
  defaultLocation?: ToastLocation
  /** Whether to show icon by default */
  showIcon?: boolean
  /** Whether to show close button by default */
  showCloseButton?: boolean
  /** Default icon size */
  iconSize?: string | number
  /** Default close button label */
  closeButtonLabel?: string
  /** Default close icon */
  closeIcon?: string
}

/**
 * Global Toast Component
 * 
 * A reusable toast notification component built on Vuetify's v-snackbar.
 * Provides type-safe configurations and consistent styling across the application.
 * Works with the existing useToast composable.
 * 
 * @example Basic usage with useToast composable:
 * ```typescript
 * const { showToast } = useToast()
 * 
 * // Show success toast
 * showToast({
 *   message: 'Data saved successfully!',
 *   type: ToastType.SUCCESS
 * })
 * 
 * // Show error toast with custom timeout
 * showToast({
 *   message: 'Failed to load data',
 *   type: ToastType.ERROR,
 *   timeout: 6000
 * })
 * ```
 * 
 * @example Template usage:
 * ```vue
 * <template>
 *   <GlobalToast 
 *     :default-timeout="5000"
 *     :default-location="ToastLocation.TOP_CENTER"
 *   />
 * </template>
 * ```
 */

// Props with defaults
const props = withDefaults(defineProps<GlobalToastProps>(), {
  defaultTimeout: 4000,
  defaultLocation: ToastLocation.TOP_RIGHT,
  showIcon: true,
  showCloseButton: true,
  iconSize: 20,
  closeButtonLabel: 'Close notification',
  closeIcon: 'mdi-close'
})

// Use the toast composable
const { toast, hideToast } = useToast()

/**
 * Computed property for snackbar visibility
 * Handles both getting and setting visibility state
 */
const isVisible = computed({
  get: () => !!toast.value,
  set: (value: boolean) => {
    if (!value) hideToast()
  }
})

/**
 * Computed property for toast color based on type
 * Maps toast types to Vuetify color variants
 */
const toastColor = computed((): string => {
  switch (toast.value?.type) {
    case ToastType.SUCCESS:
    case 'success':
      return 'success'
    case ToastType.ERROR:
    case 'error':
      return 'error'
    case ToastType.WARNING:
    case 'warning':
      return 'warning'
    case ToastType.INFO:
    case 'info':
    default:
      return 'primary'
  }
})

/**
 * Computed property for toast icon based on type
 * Maps toast types to Material Design Icons
 */
const toastIcon = computed((): string => {
  switch (toast.value?.type) {
    case ToastType.SUCCESS:
    case 'success':
      return 'mdi-check-circle'
    case ToastType.ERROR:
    case 'error':
      return 'mdi-alert-circle'
    case ToastType.WARNING:
    case 'warning':
      return 'mdi-alert'
    case ToastType.INFO:
    case 'info':
    default:
      return 'mdi-information'
  }
})
</script>

<style scoped>
/* Minimal custom styles - leverage Vuetify and global typography */
.toast-snackbar {
  z-index: 9999;
}

.toast-message {
  font-weight: 500;
  line-height: 1.4;
  word-break: break-word;
}

/* Deep selectors for Vuetify component customization */
.toast-snackbar :deep(.v-snackbar__wrapper) {
  pointer-events: auto;
  max-width: 400px;
}

/* Responsive adjustments */
@media (max-width: 600px) {
  .toast-snackbar :deep(.v-snackbar__wrapper) {
    max-width: 90vw;
    margin-left: 5vw;
    margin-right: 5vw;
  }
}
</style>