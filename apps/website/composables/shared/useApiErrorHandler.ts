import { useToast } from '~/composables/useToast'

export const useApiErrorHandler = () => {
  const { showError } = useToast()

  const handleApiError = (error: any, customMessage?: string) => {
    if (customMessage) {
      showError(customMessage)
      return
    }

    const status = error?.response?.status
    switch (status) {
      case 404:
        showError('Data not found')
        break
      case 500:
      case 502:
      case 503:
        showError('Server error. Please try again later.')
        break
      default:
        showError('Request failed. Please try again.')
    }
  }

  return { handleApiError }
}