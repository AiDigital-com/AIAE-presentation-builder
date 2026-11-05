// Pairs elements of equivalent slides in two saved presentations.get JSONs (document order, groups
// flattened) and writes an id map srcId -> dstId, checking that each pair's text is identical.
// Usage: node idmap.js <src.json> <dst.json> <out.json> srcSlide:dstSlide ...
const fs = require('fs');
const [, , srcFile, dstFile, outFile, ...pairs] = process.argv;
const load = f => { const d = JSON.parse(fs.readFileSync(f, 'utf8')); return (d.content || d).slides; };
const src = load(srcFile), dst = load(dstFile);
const flat = (els, out = []) => { for (const e of els || []) { out.push(e); if (e.elementGroup) flat(e.elementGroup.children, out); } return out; };
const txt = e => e.shape && e.shape.text ? (e.shape.text.textElements || []).map(t => t.textRun ? t.textRun.content : '').join('') : (e.table ? `table${e.table.rows}x${e.table.columns}` : (e.image ? 'image' : ''));
const map = {}; let bad = 0;
for (const p of pairs) {
  const [a, b] = p.split(':');
  const sa = src.find(s => s.objectId === a), sb = dst.find(s => s.objectId === b);
  if (!sa || !sb) throw new Error('slide not found ' + p);
  const ea = flat(sa.pageElements), eb = flat(sb.pageElements);
  if (ea.length !== eb.length) { console.log(`${p}: element count ${ea.length} vs ${eb.length}`); bad++; }
  ea.forEach((e, i) => {
    const f = eb[i]; if (!f) return;
    if (txt(e) !== txt(f)) { if (txt(e) || txt(f)) { console.log(`${p}: #${i} ${e.objectId} ${JSON.stringify(txt(e).slice(0, 50))} vs ${f.objectId} ${JSON.stringify(txt(f).slice(0, 50))}`); bad++; } }
    map[e.objectId] = f.objectId;
  });
}
fs.writeFileSync(outFile, JSON.stringify(map));
console.log('mapped', Object.keys(map).length, 'mismatches', bad);
