const fs = require("node:fs");
const path = require("node:path");

const root = path.resolve(__dirname, "..");
const excludedDirectories = new Set([
  ".git",
  ".gradle",
  ".idea",
  ".kotlin",
  "build",
  "node_modules",
  "target",
  "artifacts",
]);
const legacyAllowed = [
  /^README(?:\.zh-CN)?\.md$/,
  /^androidApp\/src\/main\/AndroidManifest\.xml$/,
  /^iosApp\/Info\.plist$/,
  /^scripts\/audit-branding\.js$/,
  /^docs\/branding\/(?:legacy-identifiers|rename-audit|external-migration-checklist)\.md$/,
  /^shared\/src\/commonMain\/kotlin\/io\/github\/julystar\/musicapp\/migration\/Legacy[^/]+\.kt$/,
  /^shared\/src\/desktopMain\/kotlin\/io\/github\/julystar\/musicapp\/migration\/DesktopDataMigration\.kt$/,
  /^shared\/src\/desktopTest\/kotlin\/io\/github\/julystar\/musicapp\/migration\/DesktopDataMigrationTest\.kt$/,
];
const originalLegacyPatterns = [
  /TideTunes/,
  /tidetunes/,
  /TIDETUNES_/,
  /com\.github\.tidetunes/,
  /uniffi\.tidetunes/,
  /tidetunes[_-]backend/,
];
const brandedInternalType =
  /\b(?:TidePlayer|MelodyTrove)(?:Application|Database|Theme|View|Screen|State|Repository|Service|Plugin|Convention|Module|Extension|Handler)\b/;
const errors = [];

function relative(file) {
  return path.relative(root, file).split(path.sep).join("/");
}

function walk(directory) {
  const files = [];
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    if (entry.isDirectory() && excludedDirectories.has(entry.name)) continue;
    const absolute = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      files.push(...walk(absolute));
    } else if (entry.isFile()) {
      files.push(absolute);
    }
  }
  return files;
}

function isLegacyAllowed(file) {
  return legacyAllowed.some((pattern) => pattern.test(file));
}

function readText(file) {
  const buffer = fs.readFileSync(file);
  if (buffer.includes(0)) return null;
  return buffer.toString("utf8");
}

function requireContent(file, pattern, description) {
  const absolute = path.join(root, file);
  if (!fs.existsSync(absolute)) {
    errors.push(`${file}: missing ${description}`);
    return;
  }
  const content = fs.readFileSync(absolute, "utf8");
  if (!pattern.test(content)) {
    errors.push(`${file}: missing ${description}`);
  }
}

for (const absolute of walk(root)) {
  const file = relative(absolute);
  if (/tidetunes/i.test(file)) {
    errors.push(`${file}: original legacy brand remains in a file or directory name`);
  }

  const content = readText(absolute);
  if (content === null) continue;
  if (!isLegacyAllowed(file)) {
    for (const pattern of originalLegacyPatterns) {
      if (pattern.test(content)) {
        errors.push(`${file}: legacy identifier ${pattern} is not allow-listed`);
        break;
      }
    }
  }
  if (/\.(?:kt|java|swift|rs)$/.test(file) && brandedInternalType.test(content)) {
    errors.push(`${file}: product brand is used in an internal technical type`);
  }
}

requireContent(
  "androidApp/build.gradle.kts",
  /namespace\s*=\s*"io\.github\.julystar\.musicapp"[\s\S]*applicationId\s*=\s*"io\.github\.julystar\.musicapp"/,
  "fixed Android namespace and application ID",
);
requireContent(
  "androidApp/src/main/res/values/strings.xml",
  /<string name="app_name">TidePlayer<\/string>/,
  "TidePlayer Android app label",
);
requireContent(
  "androidApp/src/main/AndroidManifest.xml",
  /android:name="\.AppApplication"[\s\S]*@style\/Theme\.App[\s\S]*android:scheme="tideplayer"[\s\S]*android:scheme="melodytrove"[\s\S]*android:scheme="tidetunes"/,
  "generic Android application/theme names and current plus legacy deep links",
);
requireContent(
  "shared/build.gradle.kts",
  /baseName\s*=\s*"SharedKit"[\s\S]*binaryOption\("bundleId",\s*"io\.github\.julystar\.musicapp\.shared"\)/,
  "SharedKit framework identity",
);
requireContent(
  "iosApp/Info.plist",
  /<key>CFBundleDisplayName<\/key>\s*<string>TidePlayer<\/string>[\s\S]*<string>tideplayer<\/string>[\s\S]*<string>melodytrove<\/string>[\s\S]*<string>tidetunes<\/string>/,
  "TidePlayer iOS display name and current plus legacy URL schemes",
);
requireContent(
  "iosApp/App.xcodeproj/project.pbxproj",
  /productName = TidePlayer;[\s\S]*PRODUCT_BUNDLE_IDENTIFIER = io\.github\.julystar\.musicapp;[\s\S]*PRODUCT_NAME = TidePlayer;/,
  "TidePlayer App product and fixed bundle ID",
);
requireContent(
  "desktopApp/build.gradle.kts",
  /packageName\s*=\s*"TidePlayer"/,
  "TidePlayer Desktop package name",
);
requireContent(
  "rust-libs/app-backend/Cargo.toml",
  /name\s*=\s*"app-backend"[\s\S]*name\s*=\s*"app_backend"/,
  "generic Rust package and library names",
);
requireContent(
  "shared/src/commonMain/kotlin/io/github/julystar/musicapp/database/AppDatabase.kt",
  /const val APP_DATABASE_VERSION = 19/,
  "unchanged Room schema version 19",
);
requireContent(
  "shared/src/commonMain/kotlin/io/github/julystar/musicapp/migration/AppIdentifiers.kt",
  /BRAND_NAME = "TidePlayer"[\s\S]*BRAND_SLUG = "tideplayer"[\s\S]*PACKAGE_ID = "io\.github\.julystar\.musicapp"[\s\S]*DATABASE_FILE = "library\.db"[\s\S]*PREFERENCES_FILE = "settings\.preferences_pb"/,
  "canonical TidePlayer and stable technical identifiers",
);
requireContent(
  "scripts/build-apk.ts",
  /`tideplayer-\$\{target\}-\$\{version\.name\}\.apk`/,
  "TidePlayer Android release artifact name",
);

const schemaDirectory = path.join(
  root,
  "shared/schemas/io.github.julystar.musicapp.database.AppDatabase",
);
if (!fs.existsSync(schemaDirectory)) {
  errors.push("shared/schemas: AppDatabase schema directory is missing");
}

if (errors.length > 0) {
  console.error(`Branding audit failed with ${errors.length} issue(s):`);
  for (const error of errors) console.error(`- ${error}`);
  process.exit(1);
}

console.log("Branding audit passed: TidePlayer product names and generic technical identifiers are consistent.");
