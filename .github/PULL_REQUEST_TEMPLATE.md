<!-- 開發規範見 Linear.rule.md。PR 目標分支是 dev；不要開 dev → master 的 PR。 -->

## 關聯

<!-- Linear Plan 編號，如 E-32（PR 會自動連單並改狀態）；沒有單就寫「無」 -->

## 變更類型

- [ ] feat：新功能
- [ ] fix：錯誤修正
- [ ] refactor：重構
- [ ] perf：效能
- [ ] docs：文件
- [ ] test：測試
- [ ] build / ci：建置、依賴、CI
- [ ] chore：雜項

## 變更說明

<!-- 做了什麼、為什麼 -->

## 自測清單

- [ ] `mvn clean package` 通過（含 JUnit）
- [ ] `npm run test:fast` 通過；新功能或行為變更已附 Playwright spec
- [ ] 新增的使用者可見字串已同步三份 `messages*.properties`
- [ ] 沒有 `<a href="javascript:">` 假連結；沒有引用外網 CDN
- [ ] 沒有破壞既有商業邏輯；有 DB migration 的話可重複執行
- [ ] 破壞性變更已在下方說明，並同步文件

## 破壞性變更

<!-- 沒有就寫「無」 -->
