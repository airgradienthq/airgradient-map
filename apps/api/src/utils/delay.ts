  /**
   * Helper function to create delay
   */
  export function delayHelper(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
  }

  /**
   * Helper function to create timeout promise
   */
  export function timeoutHelper(ms: number): Promise<never> {
    return new Promise((_, reject) => setTimeout(() => reject(new Error('Request timeout')), ms));
  }
