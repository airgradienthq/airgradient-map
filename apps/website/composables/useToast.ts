import { readonly, ref } from 'vue';

export interface ToastOptions {
  message: string;
  type?: 'success' | 'error' | 'warning' | 'info';
  timeout?: number;
  persistent?: boolean;
}

const globalToast = ref<ToastOptions | null>(null);

export const useToast = () => {
  const showToast = (options: ToastOptions) => {
    globalToast.value = {
      type: 'info',
      timeout: 4000,
      persistent: false,
      ...options
    };
  };

  const showSuccess = (message: string, timeout = 4000) => {
    showToast({ message, type: 'success', timeout });
  };

  const showError = (message: string, timeout = 6000) => {
    showToast({ message, type: 'error', timeout });
  };

  const showWarning = (message: string, timeout = 5000) => {
    showToast({ message, type: 'warning', timeout });
  };

  const showInfo = (message: string, timeout = 4000) => {
    showToast({ message, type: 'info', timeout });
  };

  const hideToast = () => {
    globalToast.value = null;
  };

  return {
    toast: readonly(globalToast),
    showToast,
    showSuccess,
    showError,
    showWarning,
    showInfo,
    hideToast
  };
};
