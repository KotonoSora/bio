// Test-only MFE bundle stub (platform doc 10 contract): a real vertical
// registers through defineMfe, so the shell loader is exercised against the
// actual kit contract - custom element registration, context assignment,
// dispose on disconnect - without a network or a browser.
import { defineMfe } from '@resume-builder/mfe-kit';

defineMfe('rb-test-app', (root, context) => {
  root.textContent = `test-app:${context.locale}`;
  return () => root.replaceChildren();
});
