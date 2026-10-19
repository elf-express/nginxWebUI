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

// r+ never creates or truncates a path. Validate the opened inode before writing,
// then keep every mutation on that descriptor even if the name is replaced.
function updateExistingStamp(file, text) {
  let fd;
  try {
    const before = fs.lstatSync(file, { bigint: true });
    if (!before.isFile() || before.nlink !== 1n) throw new Error(`拒絕不安全的 stamp：${file}`);
    fd = fs.openSync(file, fs.constants.O_RDWR | (fs.constants.O_NOFOLLOW || 0));
    const opened = fs.fstatSync(fd, { bigint: true });
    // Identity checking also protects platforms without O_NOFOLLOW (Windows).
    if (!opened.isFile() || opened.nlink !== 1n || opened.dev !== before.dev || opened.ino !== before.ino) {
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
