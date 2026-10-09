import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { FAULTS, parseFaults } from '../src/faults.js';

describe('parseFaults', () => {
  it('enables nothing by default', () => {
    assert.deepEqual(parseFaults(undefined).active, []);
    assert.deepEqual(parseFaults('').active, []);
  });

  it('accepts a comma separated list in any case', () => {
    const faults = parseFaults(' fx_truncate, NO_ROW_LOCK ');
    assert.deepEqual(faults.active, ['FX_TRUNCATE', 'NO_ROW_LOCK']);
    assert.equal(faults.has('FX_TRUNCATE'), true);
    assert.equal(faults.has('IDOR_ACCOUNT_READ'), false);
  });

  it('refuses to start with a misspelled fault, so a typo cannot hide as a healthy run', () => {
    assert.throws(() => parseFaults('FX_TRUNCATED'), /Unknown fault/);
  });

  it('describes every fault', () => {
    for (const description of Object.values(FAULTS)) {
      assert.ok(description.length > 10);
    }
  });
});
