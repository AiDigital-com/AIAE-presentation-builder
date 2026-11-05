// Compact listing of slide elements from a saved presentations.get JSON: id | kind | text.
const fs=require('fs');const [,,file,...slideIds]=process.argv;
const d=JSON.parse(fs.readFileSync(file,'utf8'));const slides=(d.content||d).slides;
const txt=sh=>((sh&&sh.text&&sh.text.textElements)||[]).map(e=>e.textRun?e.textRun.content:'').join('').replace(/\n/g,' ⏎ ').replace(/\u000b/g,' ↵ ').trim();
function walk(els,depth,out){for(const e of els||[]){const pad='  '.repeat(depth);
 if(e.elementGroup){out.push(pad+e.objectId+' [group]');walk(e.elementGroup.children,depth+1,out);continue;}
 if(e.table){out.push(pad+e.objectId+' [table '+e.table.rows+'x'+e.table.columns+']');continue;}
 if(e.image){out.push(pad+e.objectId+' [image]');continue;}
 const t=txt(e.shape);if(t)out.push(pad+e.objectId+' | '+t.slice(0,140));}}
for(const s of slides){if(slideIds.length&&!slideIds.includes(s.objectId))continue;const out=[];walk(s.pageElements,0,out);console.log('\n### '+s.objectId+'\n'+out.join('\n'));}
