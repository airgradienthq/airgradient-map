import { ref, readonly } from 'vue'

const toast = ref<{
  message: string
  type: 'success' | 'error' | 'warning' | 'info'
  show: boolean
} | null>(null)

export const useToast = () => {
  const showToast = (message: string, type: 'success' | 'error' | 'warning' | 'info' = 'info') => {
    toast.value = { message, type, show: true }
  }

  const hideToast = () => {
    if (toast.value) {
      toast.value.show = false
    }
  }

  const showSuccess = (message: string) => showToast(message, 'success')
  const showError = (message: string) => showToast(message, 'error')
  const showWarning = (message: string) => showToast(message, 'warning')
  const showInfo = (message: string) => showToast(message, 'info')

  return {
    toast: readonly(toast),
    showSuccess,
    showError,
    showWarning,
    showInfo,
    hideToast
  }
}