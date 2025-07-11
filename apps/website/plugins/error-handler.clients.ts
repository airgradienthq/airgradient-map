import { useToast } from '~/composables/useToast';
import { defineNuxtPlugin } from 'nuxt/app';

export default defineNuxtPlugin(() => {
  if (process.client) {
    // Global unhandled error catcher
    window.addEventListener('unhandledrejection', event => {
      const { showError } = useToast();

      // Only show toast if error isn't already handled
      if (!event.reason?.handled) {
        showError('An unexpected error occurred');
      }
    });

    // Network status monitoring
    const { showError, showSuccess } = useToast();

    window.addEventListener('offline', () => {
      showError('Connection lost. Please check your internet.');
    });

    window.addEventListener('online', () => {
      showSuccess('Connection restored');
    });
  }
});
