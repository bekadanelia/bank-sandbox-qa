import { validationError } from './errors.js';
import { parseAmount } from './money.js';

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
// PostgreSQL text cannot hold a NUL byte, and no field here has a use for the other control characters.
const CONTROL_CHARACTERS = /[\u0000-\u001f\u007f]/;

export const isUuid = (value) => typeof value === 'string' && UUID_PATTERN.test(value);

/**
 * Collects every problem in a request before failing, so the client gets the full list at once.
 * Usage: const v = check(req.body); const email = v.text('email'); v.done();
 */
export function check(source) {
  const input = source !== null && typeof source === 'object' && !Array.isArray(source) ? source : {};
  const details = [];
  const fail = (field, issue) => {
    details.push({ field, issue });
    return undefined;
  };

  const text = (field, { min = 1, max = 255, pattern, issue } = {}) => {
    const value = input[field];
    if (value === undefined || value === null) {
      return fail(field, 'is required');
    }
    if (typeof value !== 'string') {
      return fail(field, 'must be a string');
    }
    if (value.length < min || value.length > max) {
      return fail(field, `must be ${min} to ${max} characters long`);
    }
    if (CONTROL_CHARACTERS.test(value)) {
      return fail(field, 'must not contain control characters');
    }
    if (pattern && !pattern.test(value)) {
      return fail(field, issue ?? 'has an invalid format');
    }
    return value;
  };

  return {
    fail,
    text,
    optionalText(field, options) {
      return input[field] === undefined || input[field] === null ? null : text(field, options);
    },
    oneOf(field, allowed) {
      const value = input[field];
      if (value === undefined || value === null) {
        return fail(field, 'is required');
      }
      return allowed.includes(value) ? value : fail(field, `must be one of ${allowed.join(', ')}`);
    },
    uuid(field) {
      const value = input[field];
      if (value === undefined || value === null) {
        return fail(field, 'is required');
      }
      return isUuid(value) ? value.toLowerCase() : fail(field, 'must be a UUID');
    },
    /** A positive decimal sent as a string with at most two decimals. Returns minor units. */
    amount(field) {
      const value = input[field];
      if (value === undefined || value === null) {
        return fail(field, 'is required');
      }
      if (typeof value !== 'string') {
        return fail(field, 'must be a string such as "10.50"');
      }
      const minor = parseAmount(value);
      if (minor === null) {
        return fail(field, 'must be a decimal with at most 2 decimal places');
      }
      return minor > 0n ? minor : fail(field, 'must be greater than zero');
    },
    done() {
      if (details.length > 0) {
        throw validationError(details);
      }
    },
  };
}
