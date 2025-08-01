export function escapeSingleQuote(str: string) {
  if (str === null) {
    return null;
  }
  return str.replace(/'/g, "''");
}
