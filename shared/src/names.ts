const MARKS = /\p{M}/gu;

/** A name with accents dropped and capitals ignored, for its first comparison. */
function plainName(name: string): string {
  return name.normalize("NFD").replace(MARKS, "").toLowerCase();
}

function byCodeUnits(first: string, second: string): number {
  if (first < second) {
    return -1;
  } else {
    return Number(first > second);
  }
}

/**
 * The order of two topic or section names, the same on every platform: by
 * the names with accents and capitals ignored, then with capitals ignored,
 * then as written, each in UTF-16 code-unit order. A locale's collation is
 * not used, since engines order punctuation such as `7-1` and `7.1` apart.
 */
export function compareNames(first: string, second: string): number {
  return (
    byCodeUnits(plainName(first), plainName(second)) ||
    byCodeUnits(first.toLowerCase(), second.toLowerCase()) ||
    byCodeUnits(first, second)
  );
}
