#!/usr/bin/env bash
# nginxWebUI release script — 在 dev 或 hotfix/* 分支執行
#
# 動作流程：
#   1. 驗證分支與工作區乾淨
#   2. 改 pom.xml 的 nginxWebUI 版本（不會誤改 parent solon-parent 版本）
#   3. commit "chore(release): bump version to X.Y.Z"
#
# 後續手動動作（主線觸發發版）：
#   git push origin <current-branch>:master     # push master → CI 版本閘控 build+push image + 自動打 tag v<version>
#
# 用法：scripts/release.sh 5.0.4

set -euo pipefail

VERSION="${1:-}"
if [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "用法：$0 <x.y.z>  （例如 $0 5.0.4）" >&2
  exit 1
fi

# === 1. 確認在允許的分支、工作區乾淨 ===
CURRENT_BRANCH="$(git branch --show-current)"
case "$CURRENT_BRANCH" in
  dev|hotfix/*)
    : ;;
  *)
    echo "錯誤：請在 dev 或 hotfix/* 分支執行（目前在 $CURRENT_BRANCH）" >&2
    exit 1
    ;;
esac

if ! git diff --quiet; then
  echo "錯誤：工作區有未 commit 的變更" >&2
  exit 1
fi
if ! git diff --cached --quiet; then
  echo "錯誤：staging area 有未 commit 的變更" >&2
  exit 1
fi

# === 2. 拉最新（同名遠端分支）===
echo "拉最新 origin/$CURRENT_BRANCH ..."
git pull --ff-only origin "$CURRENT_BRANCH"

# === 4. 改 pom.xml 版本 ===
# pom.xml 第一個 <version> 是 <parent>solon-parent 的版本，不能改！
# 要改的是 <artifactId>nginxWebUI</artifactId> 之後的那個 <version>
CURRENT_VER=$(grep -A1 'artifactId>nginxWebUI' pom.xml | grep version | grep -oP '\d+\.\d+\.\d+')
echo "從 nginxWebUI $CURRENT_VER 升級到 $VERSION"

if [ "$CURRENT_VER" = "$VERSION" ]; then
  echo "錯誤：pom.xml 已經是 $VERSION，無需升版" >&2
  exit 1
fi

# 用 awk：先找到 <artifactId>nginxWebUI</artifactId>、再改它後面的第一個 <version>
awk -v new="$VERSION" '
  /<artifactId>nginxWebUI<\/artifactId>/ { found=1 }
  found && !done && /<version>[0-9]+\.[0-9]+\.[0-9]+<\/version>/ {
    sub(/<version>[0-9]+\.[0-9]+\.[0-9]+<\/version>/, "<version>" new "</version>")
    done=1; found=0
  }
  { print }
' pom.xml > pom.xml.tmp && mv pom.xml.tmp pom.xml

# === 5. 驗證改寫成功且沒誤改 parent ===
NEW_VER=$(grep -A1 'artifactId>nginxWebUI' pom.xml | grep version | grep -oP '\d+\.\d+\.\d+')
if [ "$NEW_VER" != "$VERSION" ]; then
  echo "錯誤：pom.xml 改寫失敗（預期 $VERSION、實際 $NEW_VER）" >&2
  git checkout -- pom.xml
  exit 1
fi

# parent 版本必須維持 3.3.3（或當下的 Solon 版本）
PARENT_VER=$(awk '/<parent>/,/<\/parent>/' pom.xml | grep -oP '<version>\K[0-9.]+' | head -1)
if [ "$PARENT_VER" != "3.3.3" ]; then
  echo "錯誤：誤改了 parent (solon-parent) 版本（現在是 $PARENT_VER、應是 3.3.3）" >&2
  git checkout -- pom.xml
  exit 1
fi
echo "✓ pom.xml: nginxWebUI=$NEW_VER, parent solon-parent=$PARENT_VER"

# === commit（tag 由 CI 在 master push 時自動打，不在此打）===
git add pom.xml
git commit -m "chore(release): bump version to $VERSION"

echo ""
echo "✓ pom 已升到 $VERSION 並 commit。下一步："
echo "    git push origin $CURRENT_BRANCH:master"
echo "    # push master → CI 版本閘控 build+push（nginxwebui + nginxwebui-crowdsec）+ 自動打 tag v$VERSION"
