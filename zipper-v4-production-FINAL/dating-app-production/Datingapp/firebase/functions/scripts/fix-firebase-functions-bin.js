/**
 * npm sometimes installs `node_modules/.bin/firebase-functions` as a copy of the script.
 * Then `require("../runtime/loader")` resolves from `.bin/` and fails (MODULE_NOT_FOUND).
 * Replace it with a symlink to `firebase-functions/lib/bin/firebase-functions.js`.
 */
const fs = require("fs");
const path = require("path");

const root = path.join(__dirname, "..");
const binDir = path.join(root, "node_modules", ".bin");
const binPath = path.join(binDir, "firebase-functions");
const target = path.join(
  root,
  "node_modules",
  "firebase-functions",
  "lib",
  "bin",
  "firebase-functions.js"
);

if (!fs.existsSync(target)) {
  process.exit(0);
}

try {
  const stat = fs.existsSync(binPath) ? fs.lstatSync(binPath) : null;
  if (stat && stat.isSymbolicLink()) {
    const cur = fs.readlinkSync(binPath);
    const resolved = path.resolve(binDir, cur);
    if (resolved === target) {
      process.exit(0);
    }
  }
  if (fs.existsSync(binPath)) {
    fs.unlinkSync(binPath);
  }
  const rel = path.relative(binDir, target);
  fs.symlinkSync(rel, binPath);
  console.log("fix-firebase-functions-bin: symlinked .bin/firebase-functions ->", rel);
} catch (e) {
  console.warn("fix-firebase-functions-bin: skipped —", e.message);
}
