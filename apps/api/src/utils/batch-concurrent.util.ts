/**
 * Process items concurrently in batches to avoid overwhelming resources (e.g., database connection pool)
 *
 * @param items - Array of items to process
 * @param concurrencyLimit - Max number of concurrent operations (should match pool size)
 * @param processFn - Async function to process each item
 * @returns Array of results in the same order as input items
 *
 * @example
 * const results = await batchConcurrent(
 *   [1, 2, 3, 4, 5],
 *   2,
 *   async (item) => await fetchData(item)
 * );
 */
export async function batchConcurrent<T, R>(
  items: T[],
  concurrencyLimit: number,
  processFn: (item: T) => Promise<R>,
): Promise<R[]> {
  const results: R[] = [];

  for (let i = 0; i < items.length; i += concurrencyLimit) {
    const batch = items.slice(i, i + concurrencyLimit);
    const batchResults = await Promise.all(batch.map(processFn));
    results.push(...batchResults);

    // Yield control back to event loop between sub-batches
    // This allows other I/O operations to proceed and prevents blocking
    if (i + concurrencyLimit < items.length) {
      await new Promise(resolve => setImmediate(resolve));
    }
  }

  return results;
}
