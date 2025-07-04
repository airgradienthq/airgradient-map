import { useToast } from '@/composables/useToast';

export const useApiErrorHandler = () => {
  const { showError, showWarning } = useToast();

  const handleApiError = (error: unknown, customMessage?: string) => {
    console.error('API Error:', error);

    if (customMessage) {
      showError(customMessage);
      return;
    }

    if (error.response) {
      const status = error.response.status;
      const message = error.response.data?.message || error.response.statusText;

      switch (status) {
        case 400:
          showError(`Invalid request: ${message}`);
          break;
        case 404:
          showError('Data not found. Please try again.');
          break;
        case 429:
          showWarning('Too many requests. Please wait a moment and try again.');
          break;
        case 500:
        case 502:
        case 503:
          showError('Server error. Please try again later.');
          break;
        default:
          showError(`Request failed: ${message}`);
      }
    } else if (error.request) {
      showError('Network error. Please check your connection.');
    } else {
      showError('An unexpected error occurred.');
    }
  };

  return { handleApiError };
};
