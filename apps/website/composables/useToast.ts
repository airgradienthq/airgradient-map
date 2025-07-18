import { ref } from 'vue';
import { ToastType, type Toast } from '~/types/shared/ui';

const toast = ref<Toast | null>(null);

export const useToast = () => {
  const showToast = (message: string, type: ToastType = ToastType.INFO) => {
    toast.value = { message, type, show: true };
  };

  const hideToast = () => {
    if (toast.value) {
      toast.value.show = false;
    }
  };

  const showSuccess = (message: string) => showToast(message, ToastType.SUCCESS);
  const showError = (message: string) => showToast(message, ToastType.ERROR);
  const showWarning = (message: string) => showToast(message, ToastType.WARNING);
  const showInfo = (message: string) => showToast(message, ToastType.INFO);

  return {
    toast,
    showSuccess,
    showError,
    showWarning,
    showInfo,
    hideToast
  };
};
