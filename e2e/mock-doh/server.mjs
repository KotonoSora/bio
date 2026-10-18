// Mock DoH resolver for the opt-in custom-domains serving e2e suite
// (feature 04 TXT verification). Zero dependencies: Node built-ins only.
//
// Endpoints:
//   GET    /__health              readiness probe (2xx JSON)
//   POST   /__control             register one TXT answer {name, value}
//   DELETE /__control             clear every registered answer
//   GET    /resolve?name=&type=TXT  dns.google-shaped JSON DoH response
//
// Env: MOCK_DOH_PORT (9097). profile-service reaches it at
// http://host.docker.internal:<port>/resolve (DOMAINS_DOH_RESOLVER, set by
// e2e/scripts/enable-domains-pro.sh); answers match the queried FQDN exactly
// (trailing dot insensitive). Unknown names answer NXDOMAIN with an empty
// answer list - the same fail-closed shape the production resolver returns.
import { createServer } from 'node:http';

const PORT = Number(process.env.MOCK_DOH_PORT ?? 9097);
const records = new Map(); // normalized fqdn (no trailing dot) -> TXT value

const norm = (name) => String(name ?? '').trim().toLowerCase().replace(/\.$/, '');

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

const server = createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host ?? 'localhost'}`);
  if (url.pathname !== '/__health') console.log(`${req.method} ${url.pathname}${url.search.slice(0, 120)}`);
  try {
    if (req.method === 'GET' && url.pathname === '/__health') {
      return json(res, 200, { ok: true, records: records.size });
    }
    if (req.method === 'POST' && url.pathname === '/__control') {
      const { name, value } = JSON.parse((await readBody(req)) || '{}');
      if (!name || !value) return json(res, 400, { error: 'name and value are required' });
      records.set(norm(name), String(value));
      return json(res, 200, { name: norm(name), count: records.size });
    }
    if (req.method === 'DELETE' && url.pathname === '/__control') {
      records.clear();
      return json(res, 200, { count: 0 });
    }
    if (req.method === 'GET' && url.pathname === '/resolve') {
      const name = norm(url.searchParams.get('name'));
      const type = url.searchParams.get('type') ?? 'TXT';
      const value = name ? records.get(name) : undefined;
      if (type !== 'TXT' || value === undefined) {
        return json(res, 200, { Status: 3, TC: false, Answer: [] });
      }
      // dns.google shape: raw RDATA, TXT wrapped in escaped quotes.
      return json(res, 200, {
        Status: 0,
        TC: false,
        Answer: [{ name: `${name}.`, type: 16, TTL: 60, data: `"${value}"` }],
      });
    }
    return json(res, 404, { error: 'not_found', path: url.pathname });
  } catch (error) {
    return json(res, 500, { error: 'server_error', detail: String(error) });
  }
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`mock doh listening on http://0.0.0.0:${PORT}`);
});
