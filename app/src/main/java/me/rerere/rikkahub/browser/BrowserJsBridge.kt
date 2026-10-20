package me.rerere.rikkahub.browser

import kotlinx.serialization.json.JsonPrimitive

/** Fixed application scripts only; no interface accepts AI JavaScript. */
internal object BrowserJsScripts {
    // Both readers use a clone without hidden DOM, attributes or form values.
    val cleanDocument = """
        function cleanDocument() {
          var blocked = /^(SCRIPT|STYLE|NOSCRIPT|TEMPLATE|IFRAME|OBJECT|EMBED|INPUT|TEXTAREA|SELECT)$/;
          function copyVisible(node) {
            if (node.nodeType === 3) return document.createTextNode(node.textContent || '');
            if (node.nodeType !== 1 || blocked.test(node.tagName)) return null;
            var s = getComputedStyle(node);
            if (node.hidden || node.getAttribute('aria-hidden') === 'true' ||
                s.display === 'none' || s.visibility === 'hidden' || s.visibility === 'collapse' ||
                s.contentVisibility === 'hidden' || s.opacity === '0') return null;
            var safeTag = /^(BODY|P|DIV|SPAN|ARTICLE|SECTION|MAIN|H[1-6]|UL|OL|LI|TABLE|TBODY|THEAD|TR|TD|TH|B|I|EM|STRONG|PRE|CODE|BR|BLOCKQUOTE)$/.test(node.tagName)
              ? node.tagName.toLowerCase() : 'span';
            var clone = document.createElement(safeTag);
            Array.from(node.childNodes).forEach(function(child) {
              var c = copyVisible(child); if (c) clone.appendChild(c);
            });
            if (/^(P|DIV|BR|LI|H[1-6]|TR|SECTION|ARTICLE)$/.test(node.tagName))
              clone.appendChild(document.createTextNode('\n'));
            return clone;
          }
          var doc = document.implementation.createHTMLDocument('');
          if (document.body) { var body = copyVisible(document.body); if (body) doc.body.replaceWith(body); }
          return doc;
        }
    """.trimIndent()

    val readText = """
        (function() { $cleanDocument
          return JSON.stringify({text: cleanDocument().body.textContent.trim().slice(0, 120000)});
        })()
    """.trimIndent()

    private val elementHelpers = """
        function visible(n) {
          if (!n || !n.isConnected || n.disabled || n.closest('[hidden],[aria-hidden="true"],[inert]')) return false;
          var r = n.getBoundingClientRect(), s = getComputedStyle(n);
          return r.width > 0 && r.height > 0 && s.display !== 'none' && s.visibility === 'visible' && s.opacity !== '0';
        }
        function info(n) {
          var tag = (n.tagName || '').toLowerCase();
          var type = (n.type || n.getAttribute('type') || '').toLowerCase();
          var text = [n.getAttribute('aria-label'), n.getAttribute('title'), n.getAttribute('placeholder'),
            n.getAttribute('name'), n.innerText].filter(Boolean).join(' ').replace(/\s+/g, ' ').trim().slice(0, 480);
          return {tag:tag, type:type, text:text};
        }
        function fingerprint(n) {
          var i = info(n);
          return JSON.stringify([i.tag, i.type, i.text, n.getAttribute('href'), n.getAttribute('formaction'), n.hasAttribute('download')]);
        }
        function highRisk(n) {
          var i = info(n);
          if (i.type === 'password') return 'password';
          if (i.type === 'file' || n.hasAttribute('download')) return 'file';
          if (i.type === 'submit' || i.type === 'image') return 'publish';
          if (n.control && /^(password|file)$/.test(n.control.type || '')) return n.control.type;
          var parent = n.parentElement && n.parentElement.closest('button,a,[role="button"],label');
          if (parent && parent !== n) {
            var ancestorRisk = highRisk(parent); if (ancestorRisk) return ancestorRisk;
          }
          var rules = [
            ['payment', /支付|付款|立即购买|立即購買|确认订单|確認訂單|\bpay\b|place order|checkout/i],
            ['delete', /删除|刪除|注销|註銷|解绑|解綁|撤销|撤銷|\bdelete\b|deactivate|remove account/i],
            ['authorization', /授权|授權|同意并继续|同意並繼續|\ballow\b|authorize|grant/i],
            ['publish', /发布|發佈|發布|发帖|發帖|提交|发送|傳送|\bpost\b|publish|submit|\bsend\b/i],
            ['transfer', /转账|轉帳|提现|提現|\btransfer\b|\bwithdraw\b/i],
            ['file', /上传|上傳|下载|下載|\bupload\b|\bdownload\b/i]
          ];
          for (var r of rules) if (r[1].test(i.text)) return r[0];
          return null;
        }
        function resolve(id) {
          var state = window.__rikkaActionState;
          if (!state || state.url !== location.href) return null;
          var entry = state.entries[id];
          return entry && visible(entry.node) && entry.fingerprint === fingerprint(entry.node) ? entry.node : null;
        }
    """.trimIndent()

    fun listClickables(max: Int, nonce: String): String = """
        (function() { $elementHelpers
          var entries = Object.create(null), result = [];
          var nodes = document.querySelectorAll('a,button,[role="button"],input,select,textarea,summary,[onclick]');
          for (var n of nodes) {
            if (result.length >= ${max.coerceIn(1, 60)}) break;
            if (!visible(n)) continue;
            var id = ${jsonString(nonce)} + '-' + result.length, i = info(n);
            n.setAttribute('data-rikka-id', id);
            entries[id] = {node:n, fingerprint:fingerprint(n)};
            result.push({id:id, tag:i.tag, type:i.type, text:i.text});
          }
          window.__rikkaActionState = {url:location.href, entries:entries};
          return JSON.stringify(result);
        })()
    """.trimIndent()

    fun inspect(id: String): String = """
        (function() { $elementHelpers
          var n = resolve(${jsonString(id)});
          return JSON.stringify(n ? info(n) : {error:'stale_id'});
        })()
    """.trimIndent()

    fun click(id: String): String = """
        (function() { $elementHelpers
          var n = resolve(${jsonString(id)});
          if (!n) return JSON.stringify({error:'stale_id'});
          var risk = highRisk(n); if (risk) return JSON.stringify({error:'high_risk', kind:risk});
          n.click(); return JSON.stringify({ok:true});
        })()
    """.trimIndent()

    fun typeInto(id: String, text: String): String = """
        (function() { $elementHelpers
          var n = resolve(${jsonString(id)});
          if (!n) return JSON.stringify({error:'stale_id'});
          var risk = highRisk(n); if (risk) return JSON.stringify({error:'high_risk', kind:risk});
          var i = info(n);
          if (!/^(input|textarea|select)$/.test(i.tag)) return JSON.stringify({error:'not_input'});
          var proto = i.tag === 'textarea' ? HTMLTextAreaElement.prototype :
            (i.tag === 'select' ? HTMLSelectElement.prototype : HTMLInputElement.prototype);
          Object.getOwnPropertyDescriptor(proto, 'value').set.call(n, ${jsonString(text)});
          n.dispatchEvent(new Event('input', {bubbles:true}));
          n.dispatchEvent(new Event('change', {bubbles:true}));
          return JSON.stringify({ok:true});
        })()
    """.trimIndent()

    fun scrollBy(dx: Int, dy: Int) = "(function(){window.scrollBy($dx,$dy);return JSON.stringify({ok:true});})()"
    val titleAndUrl = "(function(){return JSON.stringify({title:document.title||'',url:location.href,loading:document.readyState!=='complete'});})()"
    val selection = "(function(){var s=window.getSelection();var n=s&&s.anchorNode;var p=n&&(n.nodeType===1?n:n.parentElement);return JSON.stringify({text:p&&p.closest('input,textarea,[contenteditable]')?'':String(s||'').slice(0,120000)});})()"
    private fun jsonString(value: String) = JsonPrimitive(value).toString()
}
