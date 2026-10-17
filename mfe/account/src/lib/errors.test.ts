import { describe, expect, it } from 'vitest';
import { ApiError } from '@resume-builder/mfe-kit';
import { describeError, isUnauthenticated } from './errors';

describe('describeError', () => {
  it('renders Retry-After seconds for throttled', () => {
    expect(describeError(new ApiError(429, 'throttled', 'Too many', 42))).toBe(
      'Too many attempts. Try again in 42.',
    );
  });

  it('falls back to 60 seconds when Retry-After is absent', () => {
    expect(describeError(new ApiError(429, 'throttled', 'Too many'))).toBe('Too many attempts. Try again in 60.');
  });

  it('maps the frozen feature-01 codes', () => {
    expect(describeError(new ApiError(403, 'step_up_required', ''))).toBe("Confirm it's you to continue.");
    expect(describeError(new ApiError(409, 'email_conflict', ''))).toBe("This email can't be used here. Choose another.");
    expect(describeError(new ApiError(422, 'email_code_invalid', ''))).toBe(
      "That code didn't work. Check it or request a new one.",
    );
    expect(describeError(new ApiError(409, 'last_link_forbidden', ''))).toBe(
      'You need at least one sign-in method. Link another first.',
    );
    expect(describeError(new ApiError(404, 'session_not_found', ''))).toBe('That session no longer exists.');
  });

  it('renders a generic message for unknown failures and non-API errors', () => {
    expect(describeError(new ApiError(500, 'boom', ''))).toBe('Something went wrong. Try again.');
    expect(describeError(new Error('socket hang up'))).toBe('Something went wrong. Try again.');
    expect(describeError(new ApiError(503, 'unavailable', ''))).toBe('Temporarily unavailable. Try again.');
  });
});

describe('isUnauthenticated', () => {
  it('detects 401 only', () => {
    expect(isUnauthenticated(new ApiError(401, 'unauthenticated', ''))).toBe(true);
    expect(isUnauthenticated(new ApiError(403, 'step_up_required', ''))).toBe(false);
    expect(isUnauthenticated(new Error('x'))).toBe(false);
  });
});
