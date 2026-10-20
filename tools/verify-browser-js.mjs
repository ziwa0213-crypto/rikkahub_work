// Offline contract fixture, NOT Android/WebView or signed-in website validation.
// First run :app:testDebugUnitTest, then:
// node tools/verify-browser-js.mjs app/build/browser-js-contract/scripts.json
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
const scripts = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
let tests = 0;
function check(name, body) { body(); tests++; console.log(`PASS ${name}`); }
class Element {
  constructor(tag, text = '', attrs = {}, children = []) {
    this.nodeType = 1; this.tagName = tag.toUpperCase(); this.attrs = {...attrs};
    this.type = attrs.type || ''; this.disabled = false; this.isConnected = true;
    this.hidden = false; this.parentElement = null; this.clicks = 0;
    this.innerText = text; this.style = {display:'block', visibility:'visible', opacity:'1'};
    this.childNodes = children.length ? children : [{nodeType:3, textContent:text}];
    this.childNodes.forEach(n => n.parentElement = this);
  }
  getAttribute(key) { return this.attrs[key] ?? null; }
  hasAttribute(key) { return key in this.attrs; }
  setAttribute(key, value) { this.attrs[key] = value; }
  closest() { return null; }
  getBoundingClientRect() { return {width:100, height:32}; }
  click() { this.clicks++; }
  appendChild(n) { this.childNodes.push(n); return n; }
  get textContent() { return this.childNodes.map(n => n.textContent).join(''); }
  dispatchEvent() {}
}
function fixture(nodes = []) {
  let document;
  const context = vm.createContext({
    location:{href:'https://example.test/article'},
    getComputedStyle: n => n.style,
    Event: class Event {},
    HTMLInputElement: Element, HTMLTextAreaElement: Element, HTMLSelectElement: Element,
  });
  document = {
    title:'Example', readyState:'complete',
    body:new Element('body', 'Public article'),
    querySelectorAll: () => nodes,
    createTextNode: text => ({nodeType:3, textContent:text}),
    createElement: tag => new Element(tag),
    implementation:{createHTMLDocument: () => {
      const doc = {body:new Element('body')};
      doc.body.replaceWith = node => { doc.body = node; };
      return doc;
    }},
  };
  context.document = document; context.window = context;
  context.getSelection = () => ({anchorNode:null, toString: () => ''});
  return {context, document, run: key => JSON.parse(vm.runInContext(scripts[key], context))};
}
Object.defineProperty(Element.prototype, 'value', {
  get() { throw new Error('Reader must never inspect any input value'); },
  set(value) { this.writtenValue = value; },
});
check('ordinary link can be listed, inspected and clicked', () => {
  const node = new Element('a', 'Read article', {href:'/article'}), f = fixture([node]);
  assert.equal(f.run('list')[0].text, 'Read article');
  assert.equal(f.run('inspect').tag, 'a');
  assert.equal(f.run('click').ok, true); assert.equal(node.clicks, 1);
});
check('new list invalidates old IDs', () => {
  const node = new Element('a', 'Read article'), f = fixture([node]);
  f.run('list'); f.run('relist'); assert.equal(f.run('click').error, 'stale_id');
});
check('changed/disconnected nodes and URL navigation invalidate IDs', () => {
  const node = new Element('a', 'Read article'), f = fixture([node]);
  f.run('list'); node.attrs.href = '/different'; assert.equal(f.run('click').error, 'stale_id');
  f.run('list'); node.isConnected = false; assert.equal(f.run('click').error, 'stale_id');
  node.isConnected = true; f.run('list'); f.context.location.href += '/new';
  assert.equal(f.run('click').error, 'stale_id'); assert.equal(node.clicks, 0);
});
check('payment/delete/authorization/publish/transfer/download refused', () => {
  for (const text of ['Pay now', '删除账号', '授權', 'Publish', 'Transfer', 'Download']) {
    const node = new Element('button', text, {type:'button'}), f = fixture([node]);
    f.run('list'); assert.equal(f.run('click').error, 'high_risk', text); assert.equal(node.clicks, 0);
  }
});
check('password/file/default-submit and download attributes refused', () => {
  for (const node of [new Element('input', '', {type:'password'}), new Element('input', '', {type:'file'}),
    new Element('button', 'Continue', {type:'submit'}), new Element('a', 'Get', {download:''})]) {
    const f = fixture([node]); f.run('list');
    assert.equal(f.run('click').error, 'high_risk'); assert.equal(f.run('type').error, 'high_risk');
    assert.equal(node.clicks, 0); assert.equal(node.writtenValue, undefined);
  }
});
check('search input supports native setter without reading old value', () => {
  const node = new Element('input', '', {type:'search', placeholder:'Search'}), f = fixture([node]);
  f.run('list'); assert.equal(f.run('type').ok, true); assert.equal(node.writtenValue, 'safe query');
});
check('hidden/disabled controls are not listed', () => {
  const hidden = new Element('a', 'hidden'); hidden.style.display = 'none';
  const disabled = new Element('button', 'disabled'); disabled.disabled = true;
  assert.equal(fixture([hidden, disabled]).run('list').length, 0);
});
check('body extraction omits script, hidden DOM and all form values', () => {
  const f = fixture();
  const hidden = new Element('div', 'HIDDEN_SECRET'); hidden.style.display = 'none';
  f.document.body = new Element('body', '', {}, [new Element('p', 'Public article'), hidden,
    new Element('script', 'SCRIPT_SECRET'), new Element('input', 'PASSWORD_SECRET', {type:'password'}),
    new Element('textarea', 'FORM_SECRET'), new Element('template', 'TEMPLATE_SECRET')]);
  const text = f.run('read').text;
  assert.match(text, /Public article/); assert.doesNotMatch(text, /SECRET/);
});
console.log(`${tests} offline script fixture checks passed; real WebView/device acceptance not performed.`);
