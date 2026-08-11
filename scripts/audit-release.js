/**
 * Release audit: scans the repository and build artifacts for forbidden third-party plugin content.
 * TidePlayer must never bundle, recommend, or automatically enable any specific music platform plugin.
 */
const fs = require("fs");
const path = require("path");

const FORBIDDEN_IDS = [
  "com.qqmusic.source",
  "com.neteasecloudmusic.source",
  "com.applemusic.source",
  "com.kugou.source",
  "com.kuwo.source",
  "com.xiami.source",
  "com.kuaishou.source",
  "com.bilibili.source",
];

const FORBIDDEN_IN_ZIP = [
  ...FORBIDDEN_IDS.map((id) => new RegExp(id.replace(/\./g, "\\."))),
  /QQ.*Music/i,
  /NetEase/i,
  /Apple.*Music/i,
  /酷狗/i,
  /酷我/,
  /汽水/,
];

const ALLOWED_PLUGIN_DIRS = [
  path.join("scripts", "compat-plugins"),
];

const ALLOWED_TEST_FIXTURES = [
  path.join(
    "shared",
    "src",
    "commonTest",
    "kotlin",
    "io",
    "github",
    "julystar",
    "musicapp",
    "plugin",
    "management",
    "ManualMetadataServiceTest.kt",
  ),
];

let failures = 0;

function logError(msg) {
  console.error(`[AUDIT FAIL] ${msg}`);
  failures++;
}

function walkDir(dir, fn) {
  if (!fs.existsSync(dir)) return;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      const normalized = full.replace(/\\/g, "/");
      if (
        !normalized.includes("/node_modules/") &&
        !normalized.endsWith("/node_modules") &&
        !normalized.includes("/.git/") &&
        !normalized.endsWith("/.git")
      ) {
        walkDir(full, fn);
      }
    } else {
      fn(full);
    }
  }
}

function isAllowedPluginPath(p) {
  const norm = p.replace(/\\/g, "/").replace(/^\.\//, "");
  const allowedDirectory = ALLOWED_PLUGIN_DIRS.some((dir) => {
    const allowed = dir.replace(/\\/g, "/").replace(/^\.\//, "");
    return norm === allowed || norm.startsWith(`${allowed}/`);
  });
  const allowedTestFixture = ALLOWED_TEST_FIXTURES.some(
    (file) => norm === file.replace(/\\/g, "/"),
  );
  return allowedDirectory || allowedTestFixture;
}

function auditText(filePath, content) {
  const basename = path.basename(filePath);
  const normalized = filePath.replace(/\\/g, "/");

  // Design/ is a standalone prototype and is not bundled in application artifacts.
  if (normalized.startsWith("Design/")) return;
  if (normalized.includes("vendor/quickjs-ng")) return;
  if (normalized.includes("build/generated")) return;
  if (normalized.includes("target/")) return;
  if (filePath.endsWith(".o") || filePath.endsWith(".d") || filePath.endsWith(".exe")) return;

  for (const id of FORBIDDEN_IDS) {
    if (content.includes(id)) {
      if (filePath.includes("audit-release.js")) continue;
      if (isAllowedPluginPath(filePath)) continue;
      logError(`${filePath}: contains forbidden plugin ID "${id}"`);
    }
  }

  if (basename === "manifest.json" && !isAllowedPluginPath(filePath)) {
    try {
      const manifest = JSON.parse(content);
      const pluginId = manifest.id || "";
      if (manifest.apiVersion && pluginId) {
        logError(`${filePath}: manifest.json with apiVersion outside allowed dirs (id=${pluginId})`);
      }
    } catch {
      // Not a plugin manifest.
    }
  }
}

function matchesForbiddenZipPattern(value) {
  return FORBIDDEN_IN_ZIP.find((pattern) => pattern.test(value));
}

function readZipEntryNames(zipPath) {
  const data = fs.readFileSync(zipPath);
  const minEocdSize = 22;
  if (data.length < minEocdSize) return [];

  const start = Math.max(0, data.length - 0xffff - minEocdSize);
  let eocd = -1;
  for (let i = data.length - minEocdSize; i >= start; i--) {
    if (data.readUInt32LE(i) === 0x06054b50) {
      eocd = i;
      break;
    }
  }
  if (eocd < 0) return [];

  const centralDirSize = data.readUInt32LE(eocd + 12);
  const centralDirOffset = data.readUInt32LE(eocd + 16);
  const end = Math.min(data.length, centralDirOffset + centralDirSize);
  const names = [];
  let offset = centralDirOffset;
  while (offset + 46 <= end && data.readUInt32LE(offset) === 0x02014b50) {
    const nameLength = data.readUInt16LE(offset + 28);
    const extraLength = data.readUInt16LE(offset + 30);
    const commentLength = data.readUInt16LE(offset + 32);
    const nameStart = offset + 46;
    const nameEnd = nameStart + nameLength;
    if (nameEnd > data.length) break;
    names.push(data.subarray(nameStart, nameEnd).toString("utf8").replace(/\\/g, "/"));
    offset = nameEnd + extraLength + commentLength;
  }
  return names;
}

function auditZip(zipPath) {
  const name = path.basename(zipPath);
  const filenameMatch = matchesForbiddenZipPattern(name);
  if (filenameMatch) {
    logError(`${zipPath}: filename matches forbidden regex ${filenameMatch}`);
  }

  try {
    for (const entryName of readZipEntryNames(zipPath)) {
      const entryMatch = matchesForbiddenZipPattern(entryName);
      if (entryMatch) {
        logError(`${zipPath}: entry "${entryName}" matches forbidden regex ${entryMatch}`);
      }
    }
  } catch (error) {
    logError(`${zipPath}: cannot inspect zip entries (${error.message})`);
  }
}

console.log("[AUDIT] Scanning repository for forbidden plugin content...");

walkDir(".", (filePath) => {
  const ext = path.extname(filePath).toLowerCase();
  const textExts = [
    ".kt", ".kts", ".java", ".rs", ".toml", ".json", ".xml", ".yml", ".yaml",
    ".gradle", ".properties", ".md", ".txt", ".js", ".ts", ".mjs", ".cjs",
    ".html", ".css", ".sh", ".ps1", ".py", ".rb", ".cfg",
  ];

  if (filePath.endsWith(".zip") || filePath.endsWith(".apk") || filePath.endsWith(".ipa")) {
    auditZip(filePath);
    return;
  }

  if (!textExts.includes(ext)) return;

  try {
    auditText(filePath, fs.readFileSync(filePath, "utf-8"));
  } catch {
    // Binary or unreadable file.
  }
});

if (fs.existsSync("artifacts")) {
  walkDir("artifacts", (filePath) => {
    if (filePath.endsWith(".apk") || filePath.endsWith(".ipa") || filePath.endsWith(".zip")) {
      auditZip(filePath);
    }
  });
}

const testManifest = path.join("scripts", "compat-plugins", "test-metadata", "manifest.json");
if (!fs.existsSync(testManifest)) {
  logError("Missing required compat test plugin manifest at scripts/compat-plugins/test-metadata/manifest.json");
}

if (failures > 0) {
  console.error(`\n[AUDIT] ${failures} violation(s) found. Release blocked.`);
  process.exit(1);
}

console.log("[AUDIT] PASSED - no forbidden plugin content found.");
