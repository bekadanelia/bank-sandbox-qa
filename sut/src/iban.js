// Account numbers follow the IBAN layout: GE + 2 check digits + bank code ZZ + 16 digits.
const ACCOUNT_NUMBER_PATTERN = /^GE\d{2}ZZ\d{16}$/;

export const hasAccountNumberLayout = (value) => typeof value === 'string' && ACCOUNT_NUMBER_PATTERN.test(value);

/**
 * ISO 13616 check: move the first four characters to the end, replace letters with numbers
 * (A=10 ... Z=35) and the result must leave a remainder of 1 when divided by 97.
 */
export function hasValidCheckDigits(accountNumber) {
  const rearranged = accountNumber.slice(4) + accountNumber.slice(0, 4);
  const digits = rearranged.replace(/[A-Z]/g, (letter) => String(letter.charCodeAt(0) - 55));
  return BigInt(digits) % 97n === 1n;
}
