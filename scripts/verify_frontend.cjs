/* Run with Node.js. Compiles the real Vue templates without contacting business services. */
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../AI-dianping-frontend/html/hmdp');
const messages=[];
const context={module:{exports:{}},exports:{},console:{warn:(...x)=>messages.push(x.join(' ')),error:(...x)=>messages.push(x.join(' ')),log:()=>{}},
  setTimeout,clearTimeout,document:{createElement(){return {set innerHTML(s){this.textContent=s.replace(/&lt;/g,'<').replace(/&gt;/g,'>').replace(/&amp;/g,'&').replace(/&quot;/g,'"').replace(/&#39;/g,"'");}};}}};
vm.createContext(context);
vm.runInContext(fs.readFileSync(path.join(root,'js/vue.js'),'utf8'),context);
const Vue=context.module.exports;
let views=0,entries=0;
for(const file of fs.readdirSync(path.join(root,'views'))){
  if(!file.endsWith('.html'))continue;
  const source=fs.readFileSync(path.join(root,'views',file),'utf8');
  Vue.compile(source);views++;
  for(const match of source.matchAll(/(?:src|href)="(\/(?!api\/)[^"{}']+)"/g)){
    const relative=match[1].split(/[?#]/)[0];
    assert(fs.existsSync(path.join(root,relative)),`${file}: missing ${relative}`);
  }
}
for(const file of fs.readdirSync(root).filter(x=>x.endsWith('.html'))){
  const source=fs.readFileSync(path.join(root,file),'utf8');
  assert(source.includes('/js/app.js?v=20261029'),`${file}: stale entry point`);entries++;
}
for(const file of fs.readdirSync(path.join(root,'js')).filter(x=>x.endsWith('.js')&&x!=='vue.js'))
  new vm.Script(fs.readFileSync(path.join(root,'js',file),'utf8'),{filename:file});
assert.equal(messages.length,0,messages.join('\n'));
console.log(JSON.stringify({entries,views,templateCompilation:'passed',localLinks:'passed',javascriptSyntax:'passed'}));
