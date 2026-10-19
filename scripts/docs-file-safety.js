'use strict';

const fs = require('node:fs');

// O_EXCL makes checking and creating one operation, including for dangling symlinks.
// An existing backup belongs to the first writer and is never opened for writing.
function createBackupOnce(file, text) {
  try {
    fs.writeFileSync(file, text, { encoding: 'utf8', flag: 'wx' });
  } catch (e) {
    if (e.code !== 'EEXIST') throw e;
  }
}

// Open without creating/truncating first, then validate that descriptor against the
// current name. Every mutation stays on the pinned inode even if the name changes.
function updateExistingStamp(file, text) {
  let fd;
  try {
    fd = fs.openSync(file, fs.constants.O_RDWR | (fs.constants.O_NOFOLLOW || 0));
    const opened = fs.fstatSync(fd, { bigint: true });
    if (!opened.isFile() || opened.nlink !== 1n) throw new Error(`拒絕不安全的 stamp：${file}`);
    const named = fs.lstatSync(file, { bigint: true });
    // Identity checking also protects platforms without O_NOFOLLOW (Windows).
    if (!named.isFile() || named.nlink !== 1n || opened.dev !== named.dev || opened.ino !== named.ino) {
      throw new Error(`stamp 在開啟時被替換：${file}`);
    }
    const data = Buffer.from(text, 'utf8');
    fs.writeFileSync(fd, data);
    fs.ftruncateSync(fd, data.length);
  } catch (e) {
    if (e.code !== 'ENOENT') throw e;
  } finally {
    if (fd !== undefined) fs.closeSync(fd);
  }
}

module.exports = { createBackupOnce, updateExistingStamp };
