/**
 * Navigation POST for flows that hand off to the IdP (sign-in, step-up, link,
 * recovery). fetch() cannot follow a cross-origin redirect, and a full form
 * navigation keeps the server-issued 302 authoritative. `csrf_token` is the
 * form-field half of the edge double-submit (never JS-readable session state).
 */
export function postForm(action: string, fields: Record<string, string>): void {
  const form = document.createElement('form');
  form.method = 'POST';
  form.action = action;
  form.style.display = 'none';
  for (const [name, value] of Object.entries(fields)) {
    const input = document.createElement('input');
    input.type = 'hidden';
    input.name = name;
    input.value = value;
    form.appendChild(input);
  }
  document.body.appendChild(form);
  form.submit();
}

/** Current SPA path (with query) as a `returnTo` — always inside the server allowlist. */
export function currentReturnTo(): string {
  return window.location.pathname + window.location.search;
}
