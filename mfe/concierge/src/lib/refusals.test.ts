import { describe, expect, it } from 'vitest';
import { BOUNDARY_REASONS, refusalKey } from './refusals';

// Copy mapping is contract-ish: the BA states the boundary message verbatim, and
// the e2e asserts the rendered notice contains "outside what we can do".
describe('refusal copy mapping (BA 05)', () => {
  it('maps the specific reasons to their own copy keys', () => {
    expect(refusalKey('off_menu')).toBe('off_menu');
    expect(refusalKey('over_volume')).toBe('over_volume');
    expect(refusalKey('client_cancel')).toBe('client_cancel');
  });

  it('collapses the four boundary categories to the boundary copy', () => {
    for (const reason of BOUNDARY_REASONS) {
      expect(refusalKey(reason)).toBe('boundary');
    }
  });

  it('never renders a refusal without guidance', () => {
    expect(refusalKey(null)).toBe('boundary');
    expect(refusalKey('something_else')).toBe('boundary');
  });
});
