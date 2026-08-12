const { test, expect } = require('@playwright/test');
const { spawn, execFileSync } = require('child_process');
const path = require('path');
const fs = require('fs');
const http = require('http');
const { PROJECT_ROOT, JAR_PATH } = require('./helpers');

// MCP 端點的兩種型態各起一個 server 驗:
//
//   1) 共用的測試 server(port 18080,global-setup 起的)**沒有** --mcp.token
//      → /mcp 必須 404。這條守的是「既有部署升級後行為零變化」——這個功能唯一會碰到
//        既有使用者的地方。
//   2) 本檔自己另起一個帶 token 的 server(port 18081)
//      → 驗認證通過之後的所有東西。Task 5 動的是 AppFilter.doFilterDo(所有請求的必經之路),
//        只驗 404 等於完全沒碰到新增的認證分支與其後的一切。
//
// 端點用 STREAMABLE_STATELESS,所以不需要 initialize 握手、回應是純 JSON。
// 若日後改成有狀態的 STREAMABLE,測試要先取 Mcp-Session-Id 並剝掉 SSE 的 data: 前綴。
//
// 必要 header:Accept 要同時列 application/json 與 text/event-stream,少了一律 400 空 body
// 且不告訴你原因(MCP streamable 規格要求 client 兩種都能收)。

const MCP_PORT = 18081;
const MCP_BASE = `http://localhost:${MCP_PORT}`;
const MCP_TOKEN = 'e2e-mcp-token-9f3a';
// 放在已被 .gitignore 的 test-data/ 底下,不必為了這支測試再加一條忽略規則。
// helpers.cleanTestData() 只刪 test-data/sqlite.db,不會動到這個子目錄。
const MCP_DATA_DIR = path.join(__dirname, 'test-data', 'mcp').replace(/\\/g, '/');

const MCP_HEADERS = {
	'Accept': 'application/json, text/event-stream',
	'Content-Type': 'application/json',
};

let mcpProcess = null;

function waitForReady(timeoutMs = 90000) {
	return new Promise((resolve, reject) => {
		const start = Date.now();
		const check = () => {
			if (Date.now() - start > timeoutMs) {
				return reject(new Error(`MCP 測試 server 未在 ${timeoutMs}ms 內啟動`));
			}
			const req = http.get(MCP_BASE, () => resolve());
			req.on('error', () => setTimeout(check, 1000));
			req.end();
		};
		setTimeout(check, 3000);
	});
}

// 對 /mcp 送一筆 JSON-RPC。token 傳 null 代表完全不帶 Authorization header。
async function rpc(request, body, token = MCP_TOKEN) {
	const headers = { ...MCP_HEADERS };
	if (token !== null) {
		headers['Authorization'] = `Bearer ${token}`;
	}
	return request.post(`${MCP_BASE}/mcp`, {
		headers,
		data: body,
		failOnStatusCode: false,
	});
}

// tools/call 的回應是 JSON-RPC 包了一層 MCP content 陣列,取文字要剝兩層。
function toolText(json) {
	return (json.result.content || []).map((c) => c.text).join('\n');
}

// 這裡刻意不用 describe.serial:config 已是 workers: 1 + retries: 0,同檔本來就依序跑,
// 而 serial 的額外效果只有「第一條失敗後其餘全 skip」—— 共用 server 的 404 若回歸,
// 報表會變成「1 failed, 7 skipped」,看不出認證與工具清單是不是也一起壞了。
test.describe('MCP 端點', () => {
	test.describe('未設定 token(共用測試 server)', () => {
		test('POST /mcp 回 404,且 body 不吐旗標名', async ({ request, baseURL }) => {
			const res = await request.post(new URL('/mcp', baseURL).toString(), {
				headers: MCP_HEADERS,
				data: { jsonrpc: '2.0', id: 1, method: 'tools/list' },
				failOnStatusCode: false,
			});
			expect(res.status()).toBe(404);
			// Task 5 最後一輪刻意把 body 從「…加上 --mcp.token 即可開啟」縮成「MCP 端點未啟用。」:
			// 匿名一次 GET 就能問出「這台有什麼功能、旗標叫什麼」是不必要的資訊揭露。
			// 那是安全決策,不能只靠有人記得。
			const body = await res.text();
			expect(body).not.toContain('mcp.token');
			expect(body).not.toContain('--mcp');
		});

		test('GET /mcp 也回 404', async ({ request, baseURL }) => {
			const res = await request.get(new URL('/mcp', baseURL).toString(), { failOnStatusCode: false });
			expect(res.status()).toBe(404);
		});
	});

	test.describe('已設定 token(本檔另起的 server)', () => {
		test.beforeAll(async () => {
			// 起 server + 解析 150 頁 markdown 建索引,比預設 60s 久,拉高這個 hook 的 timeout。
			test.setTimeout(180000);

			fs.rmSync(MCP_DATA_DIR, { recursive: true, force: true });
			fs.mkdirSync(MCP_DATA_DIR, { recursive: true });

			// PATH 上的 java 可能是 Java 8(跑不動 Java 17 jar);優先用 JAVA_HOME(同 helpers.js)
			const javaBin = process.env.JAVA_HOME
				? path.join(process.env.JAVA_HOME, 'bin', 'java')
				: 'java';

			mcpProcess = spawn(javaBin, [
				'-jar',
				'-Dfile.encoding=UTF-8',
				JAR_PATH,
				`--server.port=${MCP_PORT}`,
				`--project.home=${MCP_DATA_DIR}/`,
				'--project.skipSeedFetch=true',
				`--mcp.token=${MCP_TOKEN}`,
			], { cwd: PROJECT_ROOT, stdio: 'pipe' });

			mcpProcess.stderr.on('data', () => {});
			mcpProcess.stdout.on('data', () => {});

			await waitForReady();
		});

		test.afterAll(() => {
			if (mcpProcess) {
				if (process.platform === 'win32') {
					try {
						execFileSync('taskkill', ['/F', '/PID', String(mcpProcess.pid), '/T'], { stdio: 'ignore' });
					} catch (e) {
						// 已經自己結束了
					}
				} else {
					mcpProcess.kill('SIGTERM');
				}
				mcpProcess = null;
			}
			// 跑完就收掉,不要每次跑都在工作目錄留下一份 DB/log(雖然被 gitignore 蓋著)。
			// maxRetries:Windows 上 taskkill 回來後 JVM 可能還握著 sqlite.db 的 handle。
			// 清不掉不該讓整個 suite 紅 —— 這是善後,不是被測行為。
			try {
				fs.rmSync(MCP_DATA_DIR, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 });
			} catch (e) {
				console.warn(`清除 ${MCP_DATA_DIR} 失敗(不影響測試結果):${e.message}`);
			}
		});

		test('不帶 Authorization 回 401,body 說明帶法但不吐旗標名', async ({ request }) => {
			const res = await rpc(request, { jsonrpc: '2.0', id: 1, method: 'tools/list' }, null);
			expect(res.status()).toBe(401);
			// 401 與 404 的取捨不同:走到這裡的人已經知道端點存在,告訴他該帶什麼 header 是標準做法
			// (空 body 的 401 極難查)。但設定鍵本身仍然不該出現 —— 那是「怎麼開啟」而不是「怎麼認證」。
			const body = await res.text();
			expect(body).toContain('Bearer');
			expect(body).not.toContain('mcp.token');
		});

		test('token 錯誤回 401', async ({ request }) => {
			const res = await rpc(request, { jsonrpc: '2.0', id: 1, method: 'tools/list' }, 'wrong-token');
			expect(res.status()).toBe(401);
		});

		test('正確 Bearer 的 tools/list 回 5 個工具', async ({ request }) => {
			const res = await rpc(request, { jsonrpc: '2.0', id: 1, method: 'tools/list' });
			expect(res.status()).toBe(200);
			// Content-Type 明寫過才有這條:少了它 client 端可能不當成 JSON 解。
			expect(res.headers()['content-type']).toContain('application/json');

			const json = await res.json();
			const names = json.result.tools.map((t) => t.name).sort();
			expect(names).toEqual([
				'nginx_check_config',
				'nginx_context',
				'nginx_directive',
				'nginx_module',
				'nginx_search',
			]);
		});

		// 摘要頁來源的 defaultValue=null 代表「這頁沒列出」,不是「沒有預設值」。
		// limit_req_status 實際預設是 503,講成「預設值:無」就是對 AI 講錯 nginx 語意——
		// 這正是整個功能要防的事,所以獨立守一條。
		test('nginx_directive(limit_req_status) 說「文件未列出」而不是「無預設值」', async ({ request }) => {
			const res = await rpc(request, {
				jsonrpc: '2.0', id: 2, method: 'tools/call',
				params: { name: 'nginx_directive', arguments: { name: 'limit_req_status' } },
			});
			expect(res.status()).toBe(200);

			const json = await res.json();
			expect(json.result.isError).toBe(false);
			const text = toolText(json);
			expect(text).toContain('預設值:文件未列出');
			expect(text).not.toContain('預設值:無');
		});

		// 模型很常把數字參數送成字串。實測 solon-ai-mcp 會把可解析的字串轉成 Integer,
		// 所以這裡走的是正常路徑(不是 isError)——把它釘住,日後換版本若改成拒收會被這條抓到。
		test('limit 送成字串 "5" 仍正常截斷', async ({ request }) => {
			const res = await rpc(request, {
				jsonrpc: '2.0', id: 3, method: 'tools/call',
				params: { name: 'nginx_context', arguments: { context: 'upstream', limit: '5' } },
			});
			expect(res.status()).toBe(200);

			const json = await res.json();
			expect(json.result.isError).toBe(false);
			const text = toolText(json);
			expect(text).toContain('以下列出前 5 條');
			expect(text.split('\n').filter((l) => l.startsWith('- '))).toHaveLength(5);
		});

		// AppFilter 的全域回歸:Task 5 改的是所有請求的必經之路,而且新分支只在帶 token 時才走到。
		// 「加了 MCP 不會弄壞後台」比 MCP 自己的功能更重要,所以在這個實例上一併驗。
		test('後台頁面不受 MCP 分支影響', async ({ request }) => {
			const root = await request.get(MCP_BASE, { failOnStatusCode: false });
			expect(root.status()).toBe(200);

			const login = await request.get(`${MCP_BASE}/adminPage/login`, { failOnStatusCode: false });
			expect(login.status()).toBe(200);
			// frontInterceptor 準備的 i18n / ctx 屬性若沒跑,Freemarker 會渲染失敗(500)而不是回這頁,
			// 所以驗得到表單欄位就等於驗到「非 /mcp 的路徑仍走完整的 frontInterceptor」。
			const html = await login.text();
			expect(html).toContain('id="name"');
			expect(html).toContain('id="pass"');
		});
	});
});
