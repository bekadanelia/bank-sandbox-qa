import { randomUUID } from 'node:crypto';
import { createRequire } from 'node:module';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import express from 'express';
import { authenticate } from './auth.js';
import { config } from './config.js';
import { pool } from './db.js';
import { ApiError, errorHandler } from './errors.js';
import { accountsRouter } from './routes/accounts.js';
import { customersRouter } from './routes/customers.js';
import { fxRatesRouter } from './routes/fxRates.js';
import { transfersRouter } from './routes/transfers.js';

const PUBLIC_DIR = fileURLToPath(new URL('../public', import.meta.url));
const OPENAPI_FILE = fileURLToPath(new URL('../openapi.yaml', import.meta.url));
const SWAGGER_UI_DIR = path.dirname(createRequire(import.meta.url).resolve('swagger-ui-dist/package.json'));
const REQUEST_ID_PATTERN = /^[A-Za-z0-9._-]{8,64}$/;

// Swagger UI writes inline styles, so only the API docs get the relaxed style rule.
const CONTENT_SECURITY_POLICY = "default-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'";
const DOCS_CONTENT_SECURITY_POLICY = `${CONTENT_SECURITY_POLICY}; style-src 'self' 'unsafe-inline'; img-src 'self' data:`;

export function createApp() {
  const app = express();
  app.disable('x-powered-by');

  app.use(requestContext);
  app.get('/health', health);
  app.use('/api/v1', apiRouter());

  app.get('/openapi.yaml', (req, res) => res.type('application/yaml').sendFile(OPENAPI_FILE));
  app.use('/docs/assets', express.static(SWAGGER_UI_DIR, { index: false }));
  app.use(express.static(PUBLIC_DIR));

  app.use((req, res, next) => next(new ApiError(404, 'NOT_FOUND', 'Nothing is served at this address')));
  app.use(errorHandler);
  return app;
}

function apiRouter() {
  const api = express.Router();
  api.use((req, res, next) => {
    res.set('Cache-Control', 'no-store');
    next();
  });
  api.use(requireJsonBody);
  api.use(express.json({ limit: '16kb' }));
  api.use(customersRouter); // registration and login are public
  api.use(authenticate);
  api.use(accountsRouter);
  api.use(transfersRouter);
  api.use(fxRatesRouter);
  api.use((req, res, next) => next(new ApiError(404, 'NOT_FOUND', 'No such endpoint')));
  return api;
}

/** Gives every request an id, sets the security headers and writes one log line per request. */
function requestContext(req, res, next) {
  const supplied = req.get('X-Request-Id');
  req.id = supplied && REQUEST_ID_PATTERN.test(supplied) ? supplied : randomUUID();
  res.set({
    'X-Request-Id': req.id,
    'X-Content-Type-Options': 'nosniff',
    'X-Frame-Options': 'DENY',
    'Referrer-Policy': 'no-referrer',
    'Content-Security-Policy': req.path.startsWith('/docs') ? DOCS_CONTENT_SECURITY_POLICY : CONTENT_SECURITY_POLICY,
  });
  if (config.logRequests) {
    const startedAt = process.hrtime.bigint();
    res.on('finish', () => {
      console.log(
        JSON.stringify({
          level: 'info',
          requestId: req.id,
          method: req.method,
          path: req.path,
          status: res.statusCode,
          durationMs: Number((process.hrtime.bigint() - startedAt) / 1_000_000n),
        }),
      );
    });
  }
  next();
}

function requireJsonBody(req, res, next) {
  const hasBody = Number(req.get('Content-Length') ?? 0) > 0 || req.get('Transfer-Encoding') !== undefined;
  if (hasBody && !req.is('application/json')) {
    throw new ApiError(415, 'UNSUPPORTED_MEDIA_TYPE', 'Request bodies must be sent as application/json');
  }
  next();
}

async function health(req, res) {
  res.set('Cache-Control', 'no-store');
  try {
    await pool.query('SELECT 1');
    res.json({ status: 'UP', version: config.version, faults: config.faults.active });
  } catch {
    res.status(503).json({ status: 'DOWN', version: config.version, faults: config.faults.active });
  }
}
