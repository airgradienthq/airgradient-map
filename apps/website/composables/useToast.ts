import { readonly, ref } from 'vue'

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
 * Toast options interface for configuring notifications
 */
export interface ToastOptions {
  /** Toast message content */
  message: string
  /** Toast notification type */
  type?: ToastType | 'success' | 'error' | 'warning' | 'info'
  /** Auto-hide timeout in milliseconds (0 = no timeout) */
  timeout?: number
  /** Whether toast persists until manually closed */
  persistent?: boolean
}

/**
 * Internal toast state (readonly for external consumers)
 */
const globalToast = ref<ToastOptions | null>(null)

/**
 * Toast Composable
 * 
 * Provides a centralized toast notification system for the application.
 * Manages global toast state and provides convenient methods for different toast types.
 * 
 * @example Basic usage:
 * ```typescript
 * const { showSuccess, showError } = useToast()
 * 
 * // Show success message
 * showSuccess('Data saved successfully!')
 * 
 * // Show error with custom timeout
 * showError('Failed to load data', 8000)
 * ```
 * 
 * @example Advanced usage:
 * ```typescript
 * const { showToast } = useToast()
 * 
 * // Custom toast configuration
 * showToast({
 *   message: 'Processing your request...',
 *   type: ToastType.INFO,
 *   persistent: true
 * })
 * ```
 * 
 * @example Reactive toast state:
 * ```vue
 * <template>
 *   <GlobalToast />
 * </template>
 * 
 * <script setup>
 * const { toast } = useToast()
 * // toast is reactive and updates the GlobalToast component
 * </script>
 * ```
 */
export const useToast = () => {
  /**
   * Show a toast notification with custom options
   * 
   * @param options - Toast configuration options
   */
  const showToast = (options: ToastOptions): void => {
    globalToast.value = {
      type: ToastType.INFO,
      timeout: 4000,
      persistent: false,
      ...options
    }
  }

  /**
   * Show a success toast notification
   * 
   * @param message - Success message to display
   * @param timeout - Auto-hide timeout in milliseconds (default: 4000)
   */
  const showSuccess = (message: string, timeout = 4000): void => {
    showToast({ 
      message, 
      type: ToastType.SUCCESS, 
      timeout 
    })
  }

  /**
   * Show an error toast notification
   * 
   * @param message - Error message to display
   * @param timeout - Auto-hide timeout in milliseconds (default: 6000)
   */
  const showError = (message: string, timeout = 6000): void => {
    showToast({ 
      message, 
      type: ToastType.ERROR, 
      timeout 
    })
  }

  /**
   * Show a warning toast notification
   * 
   * @param message - Warning message to display
   * @param timeout - Auto-hide timeout in milliseconds (default: 5000)
   */
  const showWarning = (message: string, timeout = 5000): void => {
    showToast({ 
      message, 
      type: ToastType.WARNING, 
      timeout 
    })
  }

  /**
   * Show an info toast notification
   * 
   * @param message - Info message to display
   * @param timeout - Auto-hide timeout in milliseconds (default: 4000)
   */
  const showInfo = (message: string, timeout = 4000): void => {
    showToast({ 
      message, 
      type: ToastType.INFO, 
      timeout 
    })
  }

  /**
   * Hide the current toast notification
   */
  const hideToast = (): void => {
    globalToast.value = null
  }

  /**
   * Check if a toast is currently visible
   * 
   * @returns True if toast is visible, false otherwise
   */
  const isToastVisible = (): boolean => {
    return globalToast.value !== null
  }

  /**
   * Get the current toast configuration
   * 
   * @returns Current toast options or null if no toast is active
   */
  const getCurrentToast = (): ToastOptions | null => {
    return globalToast.value
  }

  return {
    /** Readonly reactive reference to current toast state */
    toast: readonly(globalToast),
    /** Show toast with custom options */
    showToast,
    /** Show success toast */
    showSuccess,
    /** Show error toast */
    showError,
    /** Show warning toast */
    showWarning,
    /** Show info toast */
    showInfo,
    /** Hide current toast */
    hideToast,
    /** Check if toast is visible */
    isToastVisible,
    /** Get current toast configuration */
    getCurrentToast
  }
}