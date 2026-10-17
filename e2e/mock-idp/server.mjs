// Mock OIDC identity provider for the opt-in full-login e2e suite.
// Zero dependencies: Node built-ins only.
//
// Endpoints:
//   GET  /__health          readiness probe (2xx JSON)
//   POST /__control         set the profile the next /authorize grants
//                           {sub, email, name, emailVerified} (sticky until changed)
//   GET  /authorize         consent page (shows the profile, Approve button)
//   GET  /approve?state=..  302 back to redirect_uri with code + state
//   POST /token             authorization_code grant -> access_token + RS256 id_token
//   GET  /jwks              RSA public key for id_token verification
//
// Env: MOCK_IDP_PORT (9099), MOCK_IDP_ISSUER, MOCK_IDP_CLIENT_ID (aud claim).
// The browser reaches it at http://localhost:<port>; account-service reaches it
// at http://host.docker.internal:<port> (Docker Desktop). MOCK_IDP_ISSUER must
// equal the account-service GENERIC_OIDC_ISSUER exactly (scripts/../enable-full-login.sh).
import { createServer } from 'node:http';
import { generateKeyPairSync, randomBytes, sign } from 'node:crypto';

const PORT = Number(process.env.MOCK_IDP_PORT ?? 9099);
const CLIENT_ID = process.env.MOCK_IDP_CLIENT_ID ?? 'dev-smoke-client';
const ISSUER = process.env.MOCK_IDP_ISSUER ?? `http://host.docker.internal:${PORT}`;
const KID = 'e2e-idp-1';

const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
const jwk = { ...publicKey.export({ format: 'jwk' }), kid: KID, use: 'sig', alg: 'RS256' };

let nextProfile = { sub: 'e2e-user-1', email: 'e2e-user-1@example.test', name: 'E2E User', emailVerified: true };
const pending = new Map(); // state -> { redirectUri, nonce, profile }
const codes = new Map(); // code -> { redirectUri, nonce, profile }

const b64u = (input) => Buffer.from(input).toString('base64url');

function idToken({ profile, nonce }) {
  const now = Math.floor(Date.now() / 1000);
  const header = { alg: 'RS256', typ: 'JWT', kid: KID };
  const claims = {
    iss: ISSUER,
    aud: CLIENT_ID,
    sub: profile.sub,
    name: profile.name,
    email: profile.email,
    email_verified: profile.emailVerified ?? true,
    nonce,
    iat: now,
    exp: now + 300,
  };
  const signingInput = `${b64u(JSON.stringify(header))}.${b64u(JSON.stringify(claims))}`;
  const signature = sign('sha256', Buffer.from(signingInput), privateKey);
  return `${signingInput}.${b64u(signature)}`;
}

function json(res, status, body) {
  res.writeHead(status, { 'content-type': 'application/json' });
  res.end(JSON.stringify(body));
}

function readBody(req) {
  return new Promise((resolve) => {
    let data = '';
    req.on('data', (chunk) => (data += chunk));
    req.on('end', () => resolve(data));
  });
}

function consentPage({ state, redirectUri, nonce, clientId }) {
  // Snapshot the profile at authorize time; the consent page must match it.
  const profile = { ...nextProfile };
  pending.set(state, { redirectUri, nonce, profile });
  return `<!doctype html><html lang="en"><head><meta charset="utf-8"><title>Mock IdP</title></head>
<body style="font-family: system-ui; max-width: 28rem; margin: 4rem auto;">
  <h1>Mock IdP</h1>
  <p>Approve sign-in to <code>${clientId}</code></p>
  <p>as <strong id="idp-email">${profile.email}</strong> (${profile.sub})</p>
  <form method="get" action="/approve"><input type="hidden" name="state" value="${state}">
  <button type="submit" id="idp-approve">Approve sign-in</button></form>
</body></html>`;
}

const server = createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host ?? 'localhost'}`);
  if (url.pathname !== '/__health') console.log(`${req.method} ${url.pathname}${url.search.slice(0, 80)}`);
  try {
    if (req.method === 'GET' && url.pathname === '/__health') {
      return json(res, 200, { ok: true, issuer: ISSUER, clientId: CLIENT_ID });
    }
    if (req.method === 'POST' && url.pathname === '/__control') {
      const patch = JSON.parse((await readBody(req)) || '{}');
      nextProfile = { ...nextProfile, ...patch };
      return json(res, 200, nextProfile);
    }
    if (req.method === 'GET' && url.pathname === '/jwks') {
      return json(res, 200, { keys: [jwk] });
    }
    if (req.method === 'GET' && url.pathname === '/authorize') {
      const state = url.searchParams.get('state');
      const redirectUri = url.searchParams.get('redirect_uri');
      if (!state || !redirectUri) return json(res, 400, { error: 'invalid_request' });
      const html = consentPage({
        state,
        redirectUri,
        nonce: url.searchParams.get('nonce') ?? '',
        clientId: url.searchParams.get('client_id') ?? '',
      });
      res.writeHead(200, { 'content-type': 'text/html; charset=utf-8' });
      return res.end(html);
    }
    if (req.method === 'GET' && url.pathname === '/approve') {
      const state = url.searchParams.get('state');
      const flow = state && pending.get(state);
      if (!flow) return json(res, 400, { error: 'invalid_state' });
      pending.delete(state);
      const code = randomBytes(24).toString('base64url');
      codes.set(code, flow);
      res.writeHead(302, { location: `${flow.redirectUri}?code=${encodeURIComponent(code)}&state=${encodeURIComponent(state)}` });
      return res.end();
    }
    if (req.method === 'POST' && url.pathname === '/token') {
      const body = new URLSearchParams(await readBody(req));
      if (body.get('grant_type') !== 'authorization_code') return json(res, 400, { error: 'unsupported_grant_type' });
      const flow = codes.get(body.get('code') ?? '');
      if (!flow) return json(res, 400, { error: 'invalid_grant' });
      codes.delete(body.get('code')); // single-use, like a real IdP
      return json(res, 200, {
        access_token: `mock-access-${randomBytes(12).toString('base64url')}`,
        token_type: 'Bearer',
        expires_in: 300,
        scope: 'openid profile email',
        id_token: idToken({ profile: flow.profile, nonce: flow.nonce }),
      });
    }
    return json(res, 404, { error: 'not_found', path: url.pathname });
  } catch (error) {
    return json(res, 500, { error: 'server_error', detail: String(error) });
  }
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`mock idp listening on http://0.0.0.0:${PORT} (issuer ${ISSUER}, client ${CLIENT_ID})`);
});
