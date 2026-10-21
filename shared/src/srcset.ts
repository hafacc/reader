// a srcset candidate: its address after any separators, then descriptors up to a comma
const CANDIDATE_START = /[\s,]*(?<address>\S+)/uy;
const DESCRIPTORS = /(?<descriptors>[^,]*),?/uy;
const TRAILING_COMMAS = /,+$/u;
const WIDTH_DESCRIPTOR = /^(?<width>\d+)w$/iu;
const DENSITY_DESCRIPTOR = /^(?<density>\d+(?:\.\d+)?|\.\d+)x$/iu;
const WHITESPACE = /\s+/u;

/** One picture offered by a `srcset`. */
export interface SrcsetCandidate {
  /** The picture's address. */
  address: string;
  /** Its width or density, such as `424w` or `2x`; empty when not given. */
  descriptors: string;
}

/**
 * The candidates of a `srcset`, read as the HTML spec does: an address runs
 * to the next whitespace, so it may hold commas (as CDN addresses and data
 * addresses do), and its descriptors run to the next comma.
 */
export function srcsetCandidates(srcset: string): SrcsetCandidate[] {
  const candidates: SrcsetCandidate[] = [];
  let position = 0;
  CANDIDATE_START.lastIndex = position;
  let start = CANDIDATE_START.exec(srcset);
  while (start !== null) {
    let address = start.groups?.["address"] ?? "";
    let descriptors = "";
    position = CANDIDATE_START.lastIndex;
    if (address.endsWith(",")) {
      address = address.replace(TRAILING_COMMAS, "");
    } else {
      DESCRIPTORS.lastIndex = position;
      descriptors = (
        DESCRIPTORS.exec(srcset)?.groups?.["descriptors"] ?? ""
      ).trim();
      position = DESCRIPTORS.lastIndex;
    }
    if (address !== "") {
      candidates.push({ address, descriptors });
    }
    CANDIDATE_START.lastIndex = position;
    start = CANDIDATE_START.exec(srcset);
  }
  return candidates;
}

/** How large a candidate is: its width in pixels when known (else 0), and its pixel density. */
export interface CandidateSize {
  /** Width in pixels; 0 when neither a width nor a density with a known width gives it. */
  width: number;
  /** Pixel density, 1 unless an `x` descriptor says otherwise. */
  density: number;
}

/**
 * The size a candidate's descriptors give, with `declaredWidth` (the image's
 * `width` attribute, 0 when unknown) turning a density into a width.
 */
export function candidateSize(
  descriptors: string,
  declaredWidth: number,
): CandidateSize {
  let width = 0;
  let density = 1;
  for (const token of descriptors.split(WHITESPACE)) {
    const widthMatch = WIDTH_DESCRIPTOR.exec(token)?.groups?.["width"];
    const densityMatch = DENSITY_DESCRIPTOR.exec(token)?.groups?.["density"];
    if (widthMatch !== undefined) {
      width = Number(widthMatch);
    } else if (densityMatch !== undefined) {
      density = Number(densityMatch);
    }
  }
  return { width: width || density * declaredWidth, density };
}

/** Whether a picture of size `next` is larger than one of size `current`: by width, then by density. */
export function isLarger(next: CandidateSize, current: CandidateSize): boolean {
  return next.width === current.width
    ? next.density > current.density
    : next.width > current.width;
}
