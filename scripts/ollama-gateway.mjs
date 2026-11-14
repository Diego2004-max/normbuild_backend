import { createServer, request } from 'node:http';
import { timingSafeEqual } from 'node:crypto';

const token = process.env.NORMBUILD_OLLAMA_TOKEN;
if (!token || Buffer.byteLength(token) < 32) throw new Error('NORMBUILD_OLLAMA_TOKEN must contain at least 32 bytes');
const expected = Buffer.from(`Bearer ${token}`);
const allowedPaths = new Set(['/api/generate', '/api/embeddings']);

createServer((req, res) => {
  const provided = Buffer.from(req.headers.authorization ?? '');
  if (provided.length !== expected.length || !timingSafeEqual(provided, expected)) {
    res.writeHead(401).end();
    return;
  }
  if (req.method !== 'POST' || !allowedPaths.has(req.url)) {
    res.writeHead(404).end();
    return;
  }
  const chunks = [];
  let bytes = 0;
  let rejected = false;
  req.on('data', chunk => {
    bytes += chunk.length;
    if (bytes > 1024 * 1024) {
      if (!rejected) res.writeHead(413).end();
      rejected = true;
    } else if (!rejected) chunks.push(chunk);
  });
  req.on('end', () => {
    if (rejected) return;
    const upstream = request({ hostname: '127.0.0.1', port: 11434, path: req.url, method: 'POST', headers: { 'content-type': 'application/json' } }, response => {
      res.writeHead(response.statusCode ?? 502, { 'content-type': response.headers['content-type'] ?? 'application/json' });
      response.pipe(res);
    });
    upstream.on('error', () => { if (!res.headersSent) res.writeHead(502); res.end(); });
    upstream.end(Buffer.concat(chunks));
  });
}).listen(11436, '127.0.0.1');
