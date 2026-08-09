import { execSync } from "child_process";
import {
  writeFileSync,
  rmSync,
  mkdirSync,
  cpSync,
  renameSync,
} from "fs";
import path from "node:path";
import { ROOT, TARGETS } from "./base";
import { resolveAppVersion } from "./app-version";
import fs from "node:fs";
import zlib from "node:zlib";

function decodeAndDecompress(
  base64Encoded: string,
  outputFilePath: string,
): void {
  const decodedBuffer = Buffer.from(base64Encoded, "base64");
  const decompressed = zlib.brotliDecompressSync(decodedBuffer);
  fs.writeFileSync(outputFilePath, decompressed);
}

const {
  ANDROID_SIGN_JKS,
  ANDROID_SIGN_PASSWORD,
  ANDROID_SIGN_KEY_ALIAS,
  ANDROID_SIGN_KEY_PASSWORD,
} = process.env;

const rootDir = ROOT;
const version = resolveAppVersion(rootDir);
console.log(`App version: ${version.name} (${version.code})`);
const jksPath = path.resolve(rootDir, "androidApp/root.jks");
const keyPropertiesPath = path.resolve(rootDir, "androidApp/key.properties");
const srcDir = path.resolve(rootDir, "./androidApp/build/outputs/apk/release");
const dstDir = path.resolve(rootDir, "./artifacts/apk");

decodeAndDecompress(ANDROID_SIGN_JKS!, jksPath);

writeFileSync(
  keyPropertiesPath,
  `storePassword=${ANDROID_SIGN_PASSWORD}
keyPassword=${ANDROID_SIGN_KEY_PASSWORD}
keyAlias=${ANDROID_SIGN_KEY_ALIAS}
storeFile=androidApp/root.jks`,
);
console.log(`${keyPropertiesPath} written`);

execSync("./gradlew :androidApp:assembleRelease --info", {
  stdio: "inherit",
  cwd: rootDir,
});

rmSync(dstDir, { recursive: true, force: true });
mkdirSync(srcDir, { recursive: true });
console.log(srcDir);
cpSync(srcDir, dstDir, { recursive: true });

for (const target of TARGETS) {
  renameSync(
    path.join(dstDir, `androidApp-${target}-release.apk`),
    path.join(dstDir, `tideplayer-${target}-${version.name}.apk`),
  );
}
