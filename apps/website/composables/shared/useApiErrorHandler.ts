import { useToast } from '~/composables/useToast';
interface ApiError {
  response?: {
    status?: number;
  };
}

const hasResponse = (error: unknown): error is ApiError => {
  return (
    error &&
    typeof error === 'object' &&
    'response' in error
  );
};

export const useApiErrorHandler = () => {
  const { showError } = useToast();

  const handleApiError = (error: unknown, customMessage?: string) => {
    if (customMessage) {
      showError(customMessage);
      return;
    }

    const status = hasResponse(error) ? error.response?.status : undefined;
    
    switch (status) {
      case 404:
        showError('Data not found');
        break;
      case 500:
      case 502:
      case 503:
        showError('Server error. Please try again later.');
        break;
      default:
        showError('Request failed. Please try again.');
    }
  };

  return { handleApiError };
};