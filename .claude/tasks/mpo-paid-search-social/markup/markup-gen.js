// Builds Slides batchUpdate requests from a markup spec, using paragraph offsets read from a saved
// presentations.get JSON (textRun contents, groups included).
// Usage: node markup-gen.js <saved.json> <spec.js> <idPrefix> <section[,section…]> [--idmap <map.json>]
const fs = require('fs'); const path = require('path');
const [, , file, specFile, idPrefix, sections, ...rest] = process.argv;
const idmap = rest[0] === '--idmap' ? JSON.parse(fs.readFileSync(rest[1], 'utf8')) : null;
const d = JSON.parse(fs.readFileSync(file, 'utf8')); const slides = (d.content || d).slides;
const spec = require(path.resolve(specFile));
const texts = {};
const walk = els => { for (const e of els || []) {
  if (e.elementGroup) walk(e.elementGroup.children);
  if (e.shape && e.shape.text) texts[e.objectId] = (e.shape.text.textElements || []).map(t => t.textRun ? t.textRun.content : '').join('');
} };
for (const s of slides) walk(s.pageElements);
const idOf = n => { const id = idPrefix + n; return idmap ? (idmap[id] || (() => { throw new Error('no idmap for ' + id); })()) : id; };
const paras = t => { const out = []; let s = 0; for (let i = 0; i < t.length; i++) if (t[i] === '\n' || t[i] === '\u000b') { out.push([s, i]); s = i + 1; } if (s < t.length) out.push([s, t.length]); return out; };
// Replace [s,e) with tok, keeping the style of the character at s.
const replaceRange = (objectId, s, e, tok) => {
  if (e <= s) return [{ insertText: { objectId, insertionIndex: s, text: tok } }];
  const L = tok.length; const r = [{ insertText: { objectId, insertionIndex: s + 1, text: tok } }];
  if (e > s + 1) r.push({ deleteText: { objectId, textRange: { type: 'FIXED_RANGE', startIndex: s + 1 + L, endIndex: e + L } } });
  r.push({ deleteText: { objectId, textRange: { type: 'FIXED_RANGE', startIndex: s, endIndex: s + 1 } } });
  return r;
};
const reqs = [];
for (const sec of sections.split(',')) {
  const items = spec[sec]; if (!items) throw new Error('no section ' + sec);
  // group per element so paragraph edits run last-to-first
  const byEl = new Map(); for (const it of items) { const k = it.id; if (!byEl.has(k)) byEl.set(k, []); byEl.get(k).push(it); }
  for (const [n, its] of byEl) {
    const srcId = idPrefix + n; const objectId = idOf(n);
    for (const it of its.filter(x => x.mode === 'all')) reqs.push({ deleteText: { objectId, textRange: { type: 'ALL' } } }, { insertText: { objectId, insertionIndex: 0, text: it.tok } });
    for (const it of its.filter(x => x.mode === 'alt')) reqs.push({ updatePageElementAltText: { objectId, title: it.title } });
    const ranged = its.filter(x => x.mode === 'para' || x.mode === 'prefix');
    if (!ranged.length) continue;
    const t = texts[objectId] !== undefined ? texts[objectId] : texts[srcId]; if (t === undefined) throw new Error('no text for ' + srcId);
    const ps = paras(t);
    const ops = ranged.map(it => {
      if (it.mode === 'para') { const p = ps[it.i]; if (!p) throw new Error(srcId + ' has no paragraph ' + it.i); return [p[0], p[1], it.tok]; }
      const p = ps[0]; const body = t.slice(p[0], p[1]); if (!body.startsWith(it.prefix)) throw new Error(srcId + ' does not start with ' + JSON.stringify(it.prefix) + ': ' + JSON.stringify(body));
      return [p[0] + it.prefix.length, p[1], it.tok];
    }).sort((a, b) => b[0] - a[0]);
    for (const [s, e, tok] of ops) reqs.push(...replaceRange(objectId, s, e, tok));
  }
}
console.log(JSON.stringify(reqs));
