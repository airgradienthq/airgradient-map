export function escapeSingleQuote(str: string): string | null {
  if (str === null) {
    return null;
  }
  return str.replace(/'/g, "''");
}
