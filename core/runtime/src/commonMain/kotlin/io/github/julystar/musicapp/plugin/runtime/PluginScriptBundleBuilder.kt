package io.github.julystar.musicapp.plugin.runtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

class PluginScriptBundleBuilder(
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) {
    fun build(plugin: PluginRuntimeDescriptor): PluginScriptBundle {
        val root = plugin.directory.toPath(normalize = true)
        val manifestFile = root / "manifest.json"
        val manifest = if (fileSystem.exists(manifestFile)) {
            Json.parseToJsonElement(fileSystem.read(manifestFile) { readUtf8() }) as JsonObject
        } else JsonObject(emptyMap())
        val i18n = PluginI18n.read(root, manifest, fileSystem)
        val includeSources = mutableListOf<Pair<String, String>>()
        plugin.includeDirs.forEach { declared ->
            val directory = resolveUnder(root, declared)
            if (fileSystem.exists(directory)) {
                fileSystem.listRecursively(directory)
                    .filter { path ->
                        path.name.endsWith(".js", ignoreCase = true) &&
                            fileSystem.metadata(path).isRegularFile
                    }
                    .sortedBy { path -> path.relativePathTo(directory) }
                    .forEach { path ->
                        includeSources += path.relativePathTo(root) to
                            fileSystem.read(path) { readUtf8() }
                    }
            }
        }

        val declaredIncludesJson = buildJsonArray {
            includeSources
                .map { (path, _) -> path }
                .distinct()
                .forEach { path -> add(path) }
        }.toString()
        val includeBootstrap = """
            (function() {
              var __lyricoDeclaredIncludes = $declaredIncludesJson;
              var __lyricoDeclaredIncludeMap = Object.create(null);
              __lyricoDeclaredIncludes.forEach(function(path) {
                __lyricoDeclaredIncludeMap[path] = true;
              });
              globalThis.include = function(path) {
                path = String(path || "");
                if (!Object.prototype.hasOwnProperty.call(__lyricoDeclaredIncludeMap, path)) {
                  throw new Error("Include path is not declared in includeDirs: " + path);
                }
              };
            })();
        """.trimIndent()

        val segments = mutableListOf(
            "host-bootstrap.js" to HOST_BOOTSTRAP,
            "i18n-bootstrap.js" to i18n.bootstrap(),
            "include-bootstrap.js" to includeBootstrap,
        )
        segments += includeSources

        val entry = resolveUnder(root, plugin.entryFile)
        require(fileSystem.metadata(entry).isRegularFile) { "invalid entry file" }
        segments += plugin.entryFile to fileSystem.read(entry) { readUtf8() }
        val source = segments.joinToString("\n\n") { (name, code) ->
            "$code\n//# sourceURL=plugin://${plugin.pluginId}/$name"
        }
        return PluginScriptBundle(
            pluginId = plugin.pluginId,
            source = source,
            filename = "plugin://${plugin.pluginId}/${plugin.entryFile}",
            sourceHash = source.encodeUtf8().sha256().hex(),
        )
    }

    private fun resolveUnder(root: Path, relative: String): Path {
        val relativePath = relative.toPath(normalize = true)
        require(!relativePath.isAbsolute) { "plugin path must be relative: $relative" }
        val resolved = (root / relative).normalized()
        require(resolved.isUnderOrSame(root)) { "plugin path escapes plugin root: $relative" }
        return resolved
    }

    companion object {
        val HOST_BOOTSTRAP = """
            (function(g){
              var nativeHostCall=g.__lyricoHostCall;
              if(typeof nativeHostCall!=='function'){throw new Error('Host API unavailable')}
              try{delete g.__lyricoHostCall}catch(_){g.__lyricoHostCall=undefined}
              function h(n,p){return JSON.parse(nativeHostCall(n,JSON.stringify(p||{}))).value}
              function o(x){x=x||{};return {headers:x.headers||{},contentType:x.contentType,connectTimeoutMs:x.connectTimeoutMs,readTimeoutMs:x.readTimeoutMs,followRedirects:x.followRedirects,bodyBase64:x.bodyBase64||'',bodyBytes:x.bodyBytes||null}}
              function withContentType(headers,contentType){var out={},has=false;headers=headers||{};Object.keys(headers).forEach(function(k){out[k]=headers[k];if(String(k).toLowerCase()==='content-type')has=true});if(!has)out['Content-Type']=contentType;return out}
              function body(url,b,x,defaultContentType){x=o(x);var contentType=x.contentType||defaultContentType;return {url:String(url||''),body:b==null?'':String(b),bodyBase64:x.bodyBase64,bodyBytes:x.bodyBytes,contentType:contentType,headers:withContentType(x.headers,contentType),connectTimeoutMs:x.connectTimeoutMs,readTimeoutMs:x.readTimeoutMs,followRedirects:x.followRedirects}}
              function redact(value){var text=String(value==null?'':value);text=text.replace(/(authorization|cookie|set-cookie|password|passwd|secret|token|api[-_]?key)(\s*[:=]\s*)([^\s,;}]*)/ig,'$1$2[REDACTED]');text=text.replace(/(bearer\s+)[A-Za-z0-9._~+\/-]+/ig,'$1[REDACTED]');return text.length>2048?text.slice(0,2048)+'…[truncated]':text}
              g.app={getInfo:function(){return h('app.info')},getUserAgent:function(){return h('app.userAgent')}};g.runtime={getInfo:function(){return h('runtime.info')}};
              var P=g.Platform={app:g.app,runtime:g.runtime};
              P.cache={get:function(k){return h('cache.get',{key:String(k||'')})},set:function(k,v,t){return h('cache.set',{key:String(k||''),value:v==null?'':String(v),ttlMs:Number(t||0)})},remove:function(k){return h('cache.remove',{key:String(k||'')})},clear:function(){return h('cache.clear')}};
              P.crypto={md5:function(x){return h('crypto.md5',{text:String(x||'')})},aesEcbPkcs5EncryptBase64:function(x,k){return h('crypto.aesEcbPkcs5EncryptBase64',{text:String(x||''),key:String(k||'')})},aesEcbPkcs5EncryptHex:function(x,k){return h('crypto.aesEcbPkcs5EncryptHex',{text:String(x||''),key:String(k||'')})},aesEcbPkcs5DecryptBase64ToText:function(x,k){return h('crypto.aesEcbPkcs5DecryptBase64ToText',{base64:String(x||''),key:String(k||'')})}};
              P.base64={encodeText:function(x){return h('base64.encodeText',{text:String(x||'')})},decodeText:function(x){return h('base64.decodeText',{base64:String(x||'')})},dropBytes:function(x,n){return h('base64.dropBytes',{base64:String(x||''),count:n||0})},decodeBytes:function(x){return h('base64.decodeBytes',{base64:String(x||'')})},encodeBytes:function(x){return h('base64.encodeBytes',{bytes:Array.from(x||[])})},encodeUrlText:function(x){return h('base64.encodeUrlText',{text:String(x||'')})},decodeUrlText:function(x){return h('base64.decodeUrlText',{base64Url:String(x||'')})},encodeUrlBytes:function(x){return h('base64.encodeUrlBytes',{bytes:Array.from(x||[])})},decodeUrlBytes:function(x){return h('base64.decodeUrlBytes',{base64Url:String(x||'')})},toUrl:function(x){return h('base64.toUrl',{base64:String(x||'')})},fromUrl:function(x){return h('base64.fromUrl',{base64Url:String(x||'')})}};
              P.bytes={xor:function(x,k){return h('bytes.xor',{bytes:Array.from(x||[]),key:Array.from(k||[])})},xorBase64:function(x,k){return h('bytes.xorBase64',{base64:String(x||''),key:Array.from(k||[])})}};P.compression={inflateBytesToText:function(x){return h('compression.inflateBytesToText',{bytes:Array.from(x||[])})},inflateBase64ToText:function(x){return h('compression.inflateBase64ToText',{base64:String(x||'')})}};
              P.http={getText:function(u,x){x=o(x);return h('http.getText',{url:String(u||''),headers:x.headers,connectTimeoutMs:x.connectTimeoutMs,readTimeoutMs:x.readTimeoutMs,followRedirects:x.followRedirects})},postText:function(u,b,x){return h('http.postText',body(u,b,x,'application/json; charset=utf-8'))},postBytes:function(u,b,x){return h('http.postBytes',body(u,b,x,'application/octet-stream'))},get:function(u,x){x=o(x);return h('http.get',{url:String(u||''),headers:x.headers,connectTimeoutMs:x.connectTimeoutMs,readTimeoutMs:x.readTimeoutMs,followRedirects:x.followRedirects})},post:function(u,b,x){return h('http.post',body(u,b,x,'application/json; charset=utf-8'))},getBytes:function(u,x){x=o(x);return h('http.getBytes',{url:String(u||''),headers:x.headers,connectTimeoutMs:x.connectTimeoutMs,readTimeoutMs:x.readTimeoutMs,followRedirects:x.followRedirects})},postBytesResponse:function(u,b,x){return h('http.postBytesResponse',body(u,b,x,'application/octet-stream'))}};
              P.xml={getRootAttributes:function(x){return h('xml.getRootAttributes',{xml:String(x||'')})},findElements:function(x,q){return h('xml.findElements',{xml:String(x||''),query:q||{}})},replaceChildrenByAttr:function(x,q){return h('xml.replaceChildrenByAttr',{xml:String(x||''),options:q||{}})},removeElements:function(x,q){return h('xml.removeElements',{xml:String(x||''),query:q||{}})}};
              function log(n,t,m){if(m===undefined){m=t;t='PlatformPlugin'}return h(n,{tag:redact(t||'PlatformPlugin'),message:redact(m||'')})}P.log={debug:function(t,m){return log('log.debug',t,m)},warn:function(t,m){return log('log.warn',t,m)},error:function(t,m){return log('log.error',t,m)}};
              g.cache=P.cache;
            })(globalThis);
        """.trimIndent()
    }
}

private fun Path.isUnderOrSame(root: Path): Boolean {
    val target = normalized().toString().trimEnd('/', '\\')
    val rootText = root.normalized().toString().trimEnd('/', '\\')
    return target == rootText || target.startsWith("$rootText/") || target.startsWith("$rootText\\")
}

private fun Path.relativePathTo(root: Path): String {
    val target = normalized().toString()
    val rootText = root.normalized().toString().trimEnd('/', '\\')
    require(
        target.trimEnd('/', '\\') == rootText ||
            target.startsWith("$rootText/") ||
            target.startsWith("$rootText\\"),
    ) { "path is not under root" }
    return target.removePrefix(rootText).trimStart('/', '\\')
}
