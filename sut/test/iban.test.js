import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { hasAccountNumberLayout, hasValidCheckDigits } from '../src/iban.js';

describe('account numbers', () => {
  it('accepts the ISO 13616 reference IBAN for Georgia', () => {
    // Published example: GE29 NB00 0000 0101 9049 17
    assert.equal(hasValidCheckDigits('GE29NB0000000101904917'), true);
  });

  it('detects a single wrong digit', () => assert.equal(hasValidCheckDigits('GE29NB0000000101904918'), false));

  it('detects two swapped digits', () => assert.equal(hasValidCheckDigits('GE29NB0000000101909417'), false));

  it('accepts only the layout this bank issues', () => {
    assert.equal(hasAccountNumberLayout('GE29ZZ0000001000000001'), true);
    assert.equal(hasAccountNumberLayout('GE29NB0000000101904917'), false);
    assert.equal(hasAccountNumberLayout('ge29zz0000001000000001'), false);
    assert.equal(hasAccountNumberLayout('GE29ZZ000000100000000'), false);
    assert.equal(hasAccountNumberLayout(null), false);
  });
});
