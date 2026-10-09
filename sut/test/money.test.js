import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { convert, formatAmount, parseAmount, parseRate } from '../src/money.js';

describe('parseAmount', () => {
  const accepted = [
    ['0.01', 1n],
    ['1', 100n],
    ['1.5', 150n],
    ['10.50', 1050n],
    ['9999999999999.99', 999999999999999n],
  ];
  for (const [text, minor] of accepted) {
    it(`reads ${text} as ${minor} minor units`, () => assert.equal(parseAmount(text), minor));
  }

  const refused = ['', ' 1', '1 ', '01', '1.', '.5', '1.234', '-1', '+1', '1e3', '1,000', 'abc', '10000000000000'];
  for (const text of refused) {
    it(`refuses ${JSON.stringify(text)}`, () => assert.equal(parseAmount(text), null));
  }

  it('refuses values that are not text, so a float can never get in', () => {
    assert.equal(parseAmount(10.5), null);
    assert.equal(parseAmount(null), null);
  });
});

describe('formatAmount', () => {
  it('always prints two decimals', () => {
    assert.equal(formatAmount(0n), '0.00');
    assert.equal(formatAmount(5n), '0.05');
    assert.equal(formatAmount(123456n), '1234.56');
  });

  it('keeps the sign of amounts below one unit', () => assert.equal(formatAmount(-5n), '-0.05'));
});

describe('convert', () => {
  it('reads a rate with six decimals exactly', () => assert.equal(parseRate('1.072500'), 1072500n));

  it('rounds half up to the minor unit', () => {
    assert.equal(convert(300n, '0.915000'), 275n); // 2.745 becomes 2.75, banker's rounding would give 2.74
    assert.equal(convert(1002n, '0.370000'), 371n); // 3.7074 becomes 3.71
    assert.equal(convert(101n, '0.370000'), 37n); // 0.3737 becomes 0.37
  });

  it('can round a tiny amount down to zero', () => assert.equal(convert(1n, '0.370000'), 0n));

  it('truncates only when the seeded fault asks for it', () => {
    assert.equal(convert(1002n, '0.370000', { truncate: true }), 370n);
  });

  it('does not lose precision on the largest amount the API accepts', () => {
    assert.equal(convert(999999999999999n, '2.890000'), 2889999999999997n);
  });
});
