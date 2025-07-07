import { useToast } from '@/composables/useToast'

/**
 * HTTP error response interface for API errors
 */
export interface ApiErrorResponse {
  /** HTTP status code */
  status: number
  /** Response status text */
  statusText: string
  /** Response data containing error details */
  data?: {
    /** Error message from the server */
    message?: string
    /** Additional error details */
    error?: string
    /** Error code */
    code?: string
  }
}

/**
 * HTTP error interface (compatible with Axios/Fetch errors)
 */
export interface ApiError {
  /** Response object containing status and data */
  response?: ApiErrorResponse
  /** Request object (for network errors) */
  request?: any
  /** Error message */
  message?: string
  /** Error name/type */
  name?: string
  /** Original error cause */
  cause?: unknown
}

/**
 * Type guard to check if error is an API error with response
 */
const isApiErrorWithResponse = (error: unknown): error is ApiError & { response: ApiErrorResponse } => {
  return (
    typeof error === 'object' &&
    error !== null &&
    'response' in error &&
    typeof (error as any).response === 'object' &&
    'status' in (error as any).response
  )
}

/**
 * Type guard to check if error is an API error with request
 */
const isApiErrorWithRequest = (error: unknown): error is ApiError & { request: any } => {
  return (
    typeof error === 'object' &&
    error !== null &&
    'request' in error &&
    !('response' in error)
  )
}

/**
 * Type guard to check if error has a message property
 */
const hasErrorMessage = (error: unknown): error is { message: string } => {
  return (
    typeof error === 'object' &&
    error !== null &&
    'message' in error &&
    typeof (error as any).message === 'string'
  )
}

/**
 * API Error Handler Composable
 * 
 * Provides centralized error handling for API requests with proper TypeScript typing.
 * Automatically displays appropriate toast messages based on error type and status code.
 * 
 * @example Basic usage:
 * ```typescript
 * const { handleApiError } = useApiErrorHandler()
 * 
 * try {
 *   await apiCall()
 * } catch (error) {
 *   handleApiError(error)
 * }
 * ```
 * 
 * @example With custom message:
 * ```typescript
 * const { handleApiError } = useApiErrorHandler()
 * 
 * try {
 *   await saveUserData()
 * } catch (error) {
 *   handleApiError(error, 'Failed to save user data')
 * }
 * ```
 */
export const useApiErrorHandler = () => {
  const { showError, showWarning } = useToast()

  /**
   * Handle API errors with appropriate user feedback
   * 
   * @param error - The error object from API call
   * @param customMessage - Optional custom error message to display
   */
  const handleApiError = (error: unknown, customMessage?: string): void => {
    console.error('API Error:', error)

    // Use custom message if provided
    if (customMessage) {
      showError(customMessage)
      return
    }

    // Handle errors with HTTP response
    if (isApiErrorWithResponse(error)) {
      const { status, statusText, data } = error.response
      const serverMessage = data?.message || data?.error || statusText

      switch (status) {
        case 400:
          showError(`Invalid request: ${serverMessage}`)
          break
        case 401:
          showError('Authentication required. Please log in.')
          break
        case 403:
          showError('Access denied. You don\'t have permission for this action.')
          break
        case 404:
          showError('Data not found. Please try again.')
          break
        case 409:
          showError(`Conflict: ${serverMessage}`)
          break
        case 422:
          showError(`Validation error: ${serverMessage}`)
          break
        case 429:
          showWarning('Too many requests. Please wait a moment and try again.')
          break
        case 500:
        case 502:
        case 503:
        case 504:
          showError('Server error. Please try again later.')
          break
        default:
          showError(`Request failed: ${serverMessage}`)
      }
      return
    }

    // Handle network errors (request sent but no response received)
    if (isApiErrorWithRequest(error)) {
      showError('Network error. Please check your connection.')
      return
    }

    // Handle errors with message property
    if (hasErrorMessage(error)) {
      showError(`Error: ${error.message}`)
      return
    }

    // Handle string errors
    if (typeof error === 'string') {
      showError(error)
      return
    }

    // Fallback for unknown error types
    showError('An unexpected error occurred.')
  }

  /**
   * Handle API errors silently (log only, no toast)
   * 
   * @param error - The error object from API call
   * @param context - Optional context for debugging
   */
  const logApiError = (error: unknown, context?: string): void => {
    const prefix = context ? `[${context}]` : '[API Error]'
    console.error(prefix, error)
  }

  /**
   * Check if error is a specific HTTP status code
   * 
   * @param error - The error object to check
   * @param statusCode - The status code to check for
   * @returns True if error has the specified status code
   */
  const isHttpError = (error: unknown, statusCode: number): boolean => {
    return isApiErrorWithResponse(error) && error.response.status === statusCode
  }

  /**
   * Extract error message from any error type
   * 
   * @param error - The error object
   * @returns Extracted error message or default message
   */
  const getErrorMessage = (error: unknown): string => {
    if (isApiErrorWithResponse(error)) {
      return error.response.data?.message || error.response.statusText || 'Unknown error'
    }
    
    if (hasErrorMessage(error)) {
      return error.message
    }
    
    if (typeof error === 'string') {
      return error
    }
    
    return 'An unexpected error occurred'
  }

  return {
    handleApiError,
    logApiError,
    isHttpError,
    getErrorMessage
  }
}