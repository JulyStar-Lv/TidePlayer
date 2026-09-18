#!/usr/bin/env node

const fs = require("fs");
const path = require("path");

const MIB = 1024 * 1024;

function parseArgs(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 1) {
    const key = argv[index];
    if (!key.startsWith("--")) continue;
    options[key.slice(2)] = argv[index + 1];
    index += 1;
  }
  return options;
}

function walkFiles(root) {
  if (!root || !fs.existsSync(root)) return [];
  const files = [];
  const pending = [root];
  while (pending.length > 0) {
    const current = pending.pop();
    for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
      const fullPath = path.join(current, entry.name);
      if (entry.isDirectory()) pending.push(fullPath);
      else if (entry.isFile()) files.push(fullPath);
    }
  }
  return files;
}

function byteSize(files) {
  return files.reduce((total, file) => total + fs.statSync(file).size, 0);
}

function directorySize(directory) {
  return byteSize(walkFiles(directory));
}

function formatMiB(bytes) {
  return `${(bytes / MIB).toFixed(2)} MiB`;
}

function findApplicationImage(binariesRoot) {
  if (!fs.existsSync(binariesRoot)) return null;
  const pending = [binariesRoot];
  const candidates = [];
  while (pending.length > 0) {
    const current = pending.pop();
    const macApp = path.extname(current) === ".app"
      && fs.existsSync(path.join(current, "Contents", "app"));
    const portableApp = fs.existsSync(path.join(current, "app"))
      || fs.existsSync(path.join(current, "lib", "app"));
    if (macApp || portableApp) candidates.push(current);

    if (macApp) continue;
    for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
      if (entry.isDirectory()) pending.push(path.join(current, entry.name));
    }
  }

  candidates.sort((left, right) => {
    const leftRelease = left.includes(`${path.sep}main-release${path.sep}`) ? 1 : 0;
    const rightRelease = right.includes(`${path.sep}main-release${path.sep}`) ? 1 : 0;
    return rightRelease - leftRelease || right.length - left.length;
  });
  return candidates[0] || null;
}

function applicationDirectories(imageRoot) {
  const candidates = [
    {
      app: path.join(imageRoot, "Contents", "app"),
      runtime: path.join(imageRoot, "Contents", "runtime"),
    },
    {
      app: path.join(imageRoot, "app"),
      runtime: path.join(imageRoot, "runtime"),
    },
    {
      app: path.join(imageRoot, "lib", "app"),
      runtime: path.join(imageRoot, "lib", "runtime"),
    },
  ];
  return candidates.find((candidate) => fs.existsSync(candidate.app)) || candidates[0];
}

function findEndOfCentralDirectory(buffer) {
  const minimumSize = 22;
  const start = Math.max(0, buffer.length - 0xffff - minimumSize);
  for (let offset = buffer.length - minimumSize; offset >= start; offset -= 1) {
    if (buffer.readUInt32LE(offset) === 0x06054b50) return offset;
  }
  return -1;
}

function readZipEntries(zipPath) {
  const buffer = fs.readFileSync(zipPath);
  const eocd = findEndOfCentralDirectory(buffer);
  if (eocd < 0) return [];

  const centralDirectorySize = buffer.readUInt32LE(eocd + 12);
  const centralDirectoryOffset = buffer.readUInt32LE(eocd + 16);
  const end = Math.min(buffer.length, centralDirectoryOffset + centralDirectorySize);
  const entries = [];
  let offset = centralDirectoryOffset;
  while (offset + 46 <= end && buffer.readUInt32LE(offset) === 0x02014b50) {
    const compressedSize = buffer.readUInt32LE(offset + 20);
    const uncompressedSize = buffer.readUInt32LE(offset + 24);
    const nameLength = buffer.readUInt16LE(offset + 28);
    const extraLength = buffer.readUInt16LE(offset + 30);
    const commentLength = buffer.readUInt16LE(offset + 32);
    const nameStart = offset + 46;
    const nameEnd = nameStart + nameLength;
    if (nameEnd > buffer.length) break;
    entries.push({
      name: buffer.subarray(nameStart, nameEnd).toString("utf8").replace(/\\/g, "/"),
      compressedSize,
      uncompressedSize,
    });
    offset = nameEnd + extraLength + commentLength;
  }
  return entries;
}

function findInstaller(root, explicitPath) {
  if (explicitPath) return path.resolve(explicitPath);
  const extensions = [".dmg", ".exe", ".deb", ".rpm", ".msi", ".pkg"];
  const candidates = walkFiles(root).filter((file) =>
    extensions.includes(path.extname(file).toLowerCase()) || file.endsWith(".tar.gz"),
  );
  candidates.sort((left, right) => fs.statSync(right).mtimeMs - fs.statSync(left).mtimeMs);
  return candidates[0] || null;
}

function inferFormat(installer, explicitFormat) {
  if (explicitFormat) return explicitFormat.toUpperCase();
  if (!installer) return "N/A";
  if (installer.endsWith(".tar.gz")) return "APPIMAGE";
  return path.extname(installer).slice(1).toUpperCase();
}

function inferPlatform(explicitPlatform) {
  if (explicitPlatform) return explicitPlatform;
  if (process.platform === "darwin") return "macOS";
  if (process.platform === "win32") return "Windows";
  return "Linux";
}

function inferArchitecture(explicitArchitecture) {
  if (explicitArchitecture) return explicitArchitecture;
  return process.arch === "x64" ? "x86_64" : process.arch;
}

function printComponent(label, bytes) {
  console.log(`${label}\n${formatMiB(bytes)}`);
}

const options = parseArgs(process.argv.slice(2));
const binariesRoot = path.resolve(options.root || "desktopApp/build/compose/binaries");
const imageRoot = options.image ? path.resolve(options.image) : findApplicationImage(binariesRoot);
if (!imageRoot || !fs.existsSync(imageRoot)) {
  console.error(`Desktop application image not found under ${binariesRoot}`);
  process.exit(2);
}

const { app: appDirectory, runtime: runtimeDirectory } = applicationDirectories(imageRoot);
const imageFiles = walkFiles(imageRoot);
const appFiles = walkFiles(appDirectory);
const jarFiles = appFiles.filter((file) => path.extname(file).toLowerCase() === ".jar");
const zipEntries = new Map();
for (const jar of jarFiles) zipEntries.set(jar, readZipEntries(jar));

const skikoFiles = appFiles.filter((file) => /skiko/i.test(path.basename(file)));
const sqliteFiles = appFiles.filter((file) => /sqlite/i.test(path.basename(file)));
const jnaFiles = appFiles.filter((file) => /^jna(?:-platform)?-/i.test(path.basename(file)));
const rustFiles = appFiles.filter((file) => {
  if (/(?:lib)?app_backend\.(?:dll|so|dylib)$/i.test(path.basename(file))) return true;
  return (zipEntries.get(file) || []).some((entry) =>
    /(?:^|\/)(?:lib)?app_backend\.(?:dll|so|dylib)$/i.test(entry.name),
  );
});
const rustPayloadBytes = rustFiles.reduce((total, file) => {
  if (path.extname(file).toLowerCase() !== ".jar") return total + fs.statSync(file).size;
  return total + (zipEntries.get(file) || [])
    .filter((entry) => /(?:^|\/)(?:lib)?app_backend\.(?:dll|so|dylib)$/i.test(entry.name))
    .reduce((entryTotal, entry) => entryTotal + entry.uncompressedSize, 0);
}, 0);
const composeResourceBytes = jarFiles.reduce((total, file) => total + (zipEntries.get(file) || [])
  .filter((entry) => entry.name.startsWith("composeResources/"))
  .reduce((entryTotal, entry) => entryTotal + entry.compressedSize, 0), 0);
const notoEntries = jarFiles.flatMap((file) => (zipEntries.get(file) || [])
  .filter((entry) => /noto_sans_sc_wght\.ttf$/i.test(entry.name))
  .map((entry) => `${file}:${entry.name}`));

const installer = findInstaller(binariesRoot, options.installer);
const packageBytes = installer && fs.existsSync(installer) ? fs.statSync(installer).size : null;
const platform = inferPlatform(options.platform);
const architecture = inferArchitecture(options.arch);
const format = inferFormat(installer, options.format);
const maxMiB = Number(options["max-mib"] || process.env.DESKTOP_RELEASE_MAX_MIB || 50);
const gate = (options.gate || process.env.DESKTOP_RELEASE_SIZE_GATE || "warn").toLowerCase();

console.log("=== TidePlayer Desktop Size Report ===");
console.log(`Platform: ${platform}`);
console.log(`Architecture: ${architecture}`);
console.log(`Application image: ${imageRoot}`);
console.log("");
printComponent("Application image", byteSize(imageFiles));
console.log("");
printComponent("runtime/", directorySize(runtimeDirectory));
console.log("");
printComponent("lib/ (application files)", directorySize(appDirectory));
console.log("");
printComponent("Compose / Skiko", byteSize(skikoFiles));
console.log("");
printComponent("Rust native library (packaged)", byteSize(rustFiles));
console.log(`Rust native payload (uncompressed)\n${formatMiB(rustPayloadBytes)}`);
console.log("");
printComponent("SQLite native library", byteSize(sqliteFiles));
console.log("");
printComponent("JNA", byteSize(jnaFiles));
console.log("");
printComponent("JVM jars", byteSize(jarFiles));
console.log("");
printComponent("Compose resources (jar payload)", composeResourceBytes);
console.log(`Noto Sans SC packaged: ${notoEntries.length === 0 ? "no" : "YES"}`);
for (const entry of notoEntries) console.log(`  ${entry}`);
console.log("");
console.log("Top 30 files:");
imageFiles
  .map((file) => ({ file, bytes: fs.statSync(file).size }))
  .sort((left, right) => right.bytes - left.bytes)
  .slice(0, 30)
  .forEach(({ file, bytes }) => {
    console.log(`${formatMiB(bytes).padStart(10)}  ${path.relative(imageRoot, file)}`);
  });

console.log("");
console.log("Installer:");
if (packageBytes === null) {
  console.log("Not found");
} else {
  console.log(`${installer}\n${packageBytes} bytes\n${formatMiB(packageBytes)}`);
  console.log("");
  console.log("Platform | Architecture | Format | Package bytes | Package MiB");
  console.log(`${platform} | ${architecture} | ${format} | ${packageBytes} | ${(packageBytes / MIB).toFixed(2)}`);
}

let failed = false;
if (packageBytes !== null && packageBytes > maxMiB * MIB) {
  const message = `Desktop ${format} is ${formatMiB(packageBytes)}, above the ${maxMiB.toFixed(2)} MiB limit.`;
  if (gate === "fail") {
    console.error(`ERROR: ${message}`);
    failed = true;
  } else if (gate === "warn") {
    console.warn(`WARNING: ${message}`);
  }
}

if (notoEntries.length > 0) {
  console.error("ERROR: Desktop package still contains noto_sans_sc_wght.ttf.");
  failed = true;
}

if (failed) process.exit(1);
