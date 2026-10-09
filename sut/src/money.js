// Money is handled as integer minor units (BigInt) so no floating point value ever touches it.

const AMOUNT_PATTERN = /^(0|[1-9]\d{0,12})(\.\d{1,2})?$/;
const RATE_SCALE = 1_000_000n;

export const CURRENCIES = Object.freeze(['GEL', 'USD', 'EUR']);

/** Parses "12.5" style text into minor units. Returns null when the text is not a valid amount. */
export function parseAmount(text) {
  if (typeof text !== 'string' || !AMOUNT_PATTERN.test(text)) {
    return null;
  }
  const [whole, fraction = ''] = text.split('.');
  return BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0'));
}

/** Formats minor units as text with exactly two decimals. */
export function formatAmount(minor) {
  const negative = minor < 0n;
  const absolute = negative ? -minor : minor;
  const whole = absolute / 100n;
  const fraction = String(absolute % 100n).padStart(2, '0');
  return `${negative ? '-' : ''}${whole}.${fraction}`;
}

/** Turns a NUMERIC(12,6) rate such as "0.370000" into millionths. */
export function parseRate(text) {
  const [whole, fraction = ''] = String(text).split('.');
  return BigInt(whole) * RATE_SCALE + BigInt(fraction.padEnd(6, '0').slice(0, 6));
}

/**
 * Converts minor units with the given rate.
 * Rounds half up to the minor unit unless truncation is requested (seeded fault FX_TRUNCATE).
 */
export function convert(minor, rateText, { truncate = false } = {}) {
  const product = minor * parseRate(rateText);
  return truncate ? product / RATE_SCALE : (product + RATE_SCALE / 2n) / RATE_SCALE;
}
