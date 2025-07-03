import { onUnmounted } from 'vue';

/**
 * Must be created withing the `setup()` function of Vue component.
 */
export function createVueDebounce<T>(
  func: (param?: T) => Promise<void>,
  delayMs: number
): (param?: T) => void {
  let timer: ReturnType<typeof setTimeout> | null = null;

  onUnmounted(() => {
    if (timer !== null) clearTimeout(timer);
  });

  return (param?: T) => {
    if (timer !== null) {
      clearTimeout(timer);
      timer = null;
    }

    timer = setTimeout(async () => {
      await func(param).catch(err => console.error('Debounce function error:', err));
      timer = null;
    }, delayMs);
  };
}
