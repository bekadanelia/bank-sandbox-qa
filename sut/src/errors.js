export class ApiError extends Error {
  /**
   * @param {number} status HTTP status
   * @param {string} code stable machine readable code
   * @param {string} message human readable explanation
   * @param {{field: string, issue: string}[]} [details] field level problems
   * @param {object} [extra] additional top level properties, for example transferId
   */
  constructor(status, code, message, details, extra) {
    super(message);
    this.status = status;
    this.code = code;
    this.details = details;
    this.extra = extra;
  }
}

const RULE_MESSAGES = Object.freeze({
  SAME_ACCOUNT: 'Source and target accounts must be different',
  ACCOUNT_NOT_ACTIVE: 'Both accounts must be active',
  LIMIT_EXCEEDED: 'The amount exceeds the limit for a single transfer',
  AMOUNT_TOO_SMALL: 'The converted amount would be zero',
  INSUFFICIENT_FUNDS: 'The source account does not have enough funds',
  TARGET_ACCOUNT_NOT_FOUND: 'The target account does not exist',
  ACCOUNT_LIMIT_REACHED: 'A customer can hold at most 5 accounts',
});

export const validationError = (details) =>
  new ApiError(400, 'VALIDATION_ERROR', 'The request is not valid', details);

export const unauthorized = () =>
  new ApiError(401, 'UNAUTHORIZED', 'A valid bearer token is required');

export const notFound = (code, message) => new ApiError(404, code, message);

export const conflict = (code, message) => new ApiError(409, code, message);

/** A request that is well formed but breaks a business rule. */
export const ruleViolation = (code, extra) => new ApiError(422, code, RULE_MESSAGES[code] ?? code, undefined, extra);

// Express identifies error middleware by its four arguments, so `next` must stay in the signature.
export function errorHandler(err, req, res, next) {
  let failure = err;
  if (!(failure instanceof ApiError)) {
    if (err?.type === 'entity.parse.failed') {
      failure = new ApiError(400, 'MALFORMED_JSON', 'The request body is not valid JSON');
    } else if (err?.type === 'entity.too.large') {
      failure = new ApiError(413, 'PAYLOAD_TOO_LARGE', 'The request body is too large');
    } else if (Number.isInteger(err?.status) && err.status >= 400 && err.status < 500) {
      // Remaining client errors raised by Express itself, for example a URL that cannot be decoded.
      failure = new ApiError(err.status, 'BAD_REQUEST', 'The request could not be processed');
    } else {
      console.error(
        JSON.stringify({ level: 'error', requestId: req.id, message: err?.message, stack: err?.stack }),
      );
      failure = new ApiError(500, 'INTERNAL_ERROR', 'Unexpected error');
    }
  }
  const body = { code: failure.code, message: failure.message, requestId: req.id, ...failure.extra };
  if (failure.details?.length) {
    body.details = failure.details;
  }
  res.status(failure.status).json(body);
}
