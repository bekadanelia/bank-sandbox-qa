import { randomBytes, scrypt, timingSafeEqual } from 'node:crypto';
import { promisify } from 'node:util';
import jwt from 'jsonwebtoken';
import { config } from './config.js';
import { unauthorized } from './errors.js';

const scryptAsync = promisify(scrypt);
// Deliberately cheap: this is a sandbox that registers hundreds of throwaway customers per test run.
const SCRYPT_OPTIONS = { N: 4096, r: 8, p: 1 };
const KEY_LENGTH = 32;
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

export async function hashPassword(password) {
  const salt = randomBytes(16);
  const hash = await scryptAsync(password, salt, KEY_LENGTH, SCRYPT_OPTIONS);
  return `scrypt$${salt.toString('base64')}$${hash.toString('base64')}`;
}

export async function verifyPassword(password, stored) {
  const [scheme, salt, hash] = String(stored).split('$');
  if (scheme !== 'scrypt' || !salt || !hash) {
    return false;
  }
  const expected = Buffer.from(hash, 'base64');
  const actual = await scryptAsync(password, Buffer.from(salt, 'base64'), expected.length, SCRYPT_OPTIONS);
  return timingSafeEqual(actual, expected);
}

export function issueToken(customerId) {
  return jwt.sign({}, config.jwtSecret, {
    algorithm: 'HS256',
    subject: customerId,
    expiresIn: config.jwtTtlSeconds,
  });
}

/** Express middleware: requires "Authorization: Bearer <token>" and exposes req.customerId. */
export function authenticate(req, res, next) {
  const match = /^Bearer (\S+)$/.exec(req.get('Authorization') ?? '');
  if (!match) {
    throw unauthorized();
  }
  let payload;
  try {
    // Pinning the algorithm rejects tokens that claim "alg": "none" or another scheme.
    payload = jwt.verify(match[1], config.jwtSecret, { algorithms: ['HS256'] });
  } catch {
    throw unauthorized();
  }
  if (typeof payload.sub !== 'string' || !UUID_PATTERN.test(payload.sub)) {
    throw unauthorized();
  }
  req.customerId = payload.sub;
  next();
}
