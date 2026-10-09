import { Router } from 'express';
import { authenticate, issueToken } from '../auth.js';
import { config } from '../config.js';
import { unauthorized } from '../errors.js';
import { findCustomer, registerCustomer, toCustomerJson, verifyCredentials } from '../services/customers.js';
import { check } from '../validate.js';

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export const customersRouter = Router();

customersRouter.post('/customers', async (req, res) => {
  const v = check(req.body);
  const email = v.text('email', { min: 3, max: 254, pattern: EMAIL_PATTERN, issue: 'must be a valid email address' });
  const fullName = v.text('fullName', { min: 2, max: 100 });
  const password = v.text('password', { min: 8, max: 72 });
  v.done();
  const customer = await registerCustomer({ email, fullName, password });
  res.status(201).json(toCustomerJson(customer));
});

customersRouter.post('/auth/login', async (req, res) => {
  const v = check(req.body);
  const email = v.text('email', { max: 254 });
  const password = v.text('password', { max: 72 });
  v.done();
  const customerId = await verifyCredentials(email, password);
  res.json({ accessToken: issueToken(customerId), tokenType: 'Bearer', expiresIn: config.jwtTtlSeconds });
});

customersRouter.get('/customers/me', authenticate, async (req, res) => {
  const customer = await findCustomer(req.customerId);
  if (!customer) {
    throw unauthorized();
  }
  res.json(toCustomerJson(customer));
});
