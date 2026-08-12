package com.cym.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.cym.service.NginxDocService;

public class NginxConfCheckerTest {

	private static NginxDocService svc;

	@BeforeAll
	public static void setUp() throws Exception {
		List<String> pages = new ArrayList<>();
		try (var paths = Files.list(Path.of("docs/nginxdocumentation"))) {
			for (Path p : paths.filter(x -> x.getFileName().toString().endsWith("page.md")).toList()) {
				pages.add(Files.readString(p));
			}
		}
		svc = new NginxDocService();
		svc.load(pages);
	}

	@Test
	public void check_合法設定不誤報() {
		String conf = """
				http {
				    server {
				        listen 80;
				        location / {
				            proxy_pass http://backend;
				        }
				    }
				}
				""";
		assertEquals(List.of(), NginxConfChecker.check(conf, svc));
	}

	@Test
	public void check_指令用在錯誤context要抓到() {
		String conf = """
				http {
				    proxy_pass http://backend;
				}
				""";
		List<String> problems = NginxConfChecker.check(conf, svc);
		assertEquals(1, problems.size());
		assertTrue(problems.get(0).contains("proxy_pass"));
		assertTrue(problems.get(0).contains("http"));
	}

	@Test
	public void check_拼錯的指令要抓到並給候選() {
		String conf = """
				http {
				    server {
				        proxy_pas http://backend;
				    }
				}
				""";
		List<String> problems = NginxConfChecker.check(conf, svc);
		assertEquals(1, problems.size());
		assertTrue(problems.get(0).contains("proxy_pass"), "應提示正確拼法");
	}

	@Test
	public void check_註解與空行不誤報() {
		assertEquals(List.of(), NginxConfChecker.check("# comment\n\n  # another\n", svc));
	}

	@Test
	public void check_語料沒寫context的指令不誤報() {
		// limit_req_status 來自手寫摘要頁,contexts 是空的。空 = 不知道,不是「哪裡都不能用」。
		String conf = """
				http {
				    server {
				        limit_req_status 429;
				    }
				}
				""";
		assertEquals(List.of(), NginxConfChecker.check(conf, svc));
	}

	@Test
	public void check_context寫any的指令到哪都合法() {
		// include 是全語料唯一 context 為 any 的指令。照字面比對 contexts.contains("http") 會失敗,
		// 而 include 幾乎出現在每一份 nginx.conf —— 這一條沒擋住,檢查器對任何真實設定都會噴錯。
		String conf = """
				include mime.types;
				http {
				    include realip.conf;
				    server {
				        include common.conf;
				        location / {
				            include proxy_params;
				        }
				    }
				}
				""";
		assertEquals(List.of(), NginxConfChecker.check(conf, svc));
	}

	@Test
	public void check_map區塊裡的對照資料不當成指令() {
		// map / geo / types / charset_map / split_clients 的內容是「值對值」的資料,不是指令。
		// 逐行當指令查會把 default、CN、'' 這些值報成拼錯的指令。
		String conf = """
				http {
				    map $http_upgrade $connection_upgrade {
				        default upgrade;
				        ''      close;
				    }
				    geo $remote_addr $is_internal {
				        default 0;
				        10.0.0.0/8 1;
				    }
				    charset_map koi8-r utf-8 {
				        C0  D18E;
				    }
				    split_clients "${remote_addr}" $variant {
				        50%  .one;
				        *    "";
				    }
				}
				""";
		assertEquals(List.of(), NginxConfChecker.check(conf, svc));
	}

	@Test
	public void check_location裡的if區塊兩種context都收() {
		// 官方對 if 用了兩個名字:rewrite 模組的 return/set 寫 if,另外 28 條(add_header、expires…)
		// 寫 if in location。只認其中一個,另一半就會在同一個 if 區塊裡被誤判。
		String conf = """
				http {
				    server {
				        location / {
				            if ($http_user_agent ~ MSIE) {
				                add_header X-Legacy 1;
				                return 403;
				            }
				        }
				    }
				}
				""";
		assertEquals(List.of(), NginxConfChecker.check(conf, svc));
	}

	@Test
	public void check_server層的if不放行location專屬指令() {
		// 「if 兩種 context 都收」不能退化成無條件放行:proxy_pass 只允許 location / if in location /
		// limit_except,直接掛在 server 的 if 底下是真的錯,nginx 會拒絕啟動。
		String conf = """
				http {
				    server {
				        if ($host = old.example.com) {
				            proxy_pass http://backend;
				        }
				    }
				}
				""";
		List<String> problems = NginxConfChecker.check(conf, svc);
		assertEquals(1, problems.size(), "實際:" + problems);
		assertTrue(problems.get(0).contains("proxy_pass"), problems.get(0));
	}

	@Test
	public void check_跨行字串的續行不當成指令() {
		// log_format 是 ConfService 會寫進 http 的參數之一,而且幾乎一定跨行。續行以 '$status 開頭、
		// 以 ; 結尾,形狀上就是一條指令 —— 但指令名不可能長這樣(語料 969 條全是 [a-z][a-z0-9_]*),
		// 所以寧可閉嘴。沒有這一條會冒出「未知指令 '$status,是否想寫 status ?」。
		String conf = """
				http {
				    log_format main '$remote_addr - $remote_user [$time_local] "$request" '
				                    '$status $body_bytes_sent "$http_referer"';
				}
				""";
		assertEquals(List.of(), NginxConfChecker.check(conf, svc));
	}

	@Test
	public void check_本專案自己產生的設定零誤報() {
		// 檢查器的第一個使用者是 nginxWebUI 自己。這份 conf 用的都是 ConfService / GeoipService
		// 實際會寫出來的指令(limit_req_zone、set_real_ip_from、geoip2 map、if 國別阻擋、stream proxy_pass)。
		// 這裡多出任何一則,就是使用者第一次用就會看到的誤報。
		String conf = """
				user  nginx;
				worker_processes  auto;

				events {
				    worker_connections  1024;
				}

				http {
				    include       mime.types;
				    default_type  application/octet-stream;
				    variables_hash_max_size 2048;

				    limit_conn_zone $binary_remote_addr zone=conn_limit:10m;
				    limit_req_zone $binary_remote_addr zone=req_limit:10m rate=10r/s;

				    include realip.conf;
				    set_real_ip_from 173.245.48.0/20;
				    real_ip_header CF-Connecting-IP;
				    real_ip_recursive on;

				    allow 10.0.0.0/8;
				    deny 45.148.10.0/24;

				    geoip2 /etc/nginx/geoip/GeoLite2-Country.mmdb {
				        auto_reload 60m;
				        $geoip2_data_country_code country iso_code;
				    }

				    map $geoip2_data_country_code $geo_block_global {
				        default 0;
				        CN 1;
				    }

				    upstream backend {
				        server 127.0.0.1:8080 weight=1 max_fails=2;
				        keepalive 32;
				    }

				    server {
				        listen 443 ssl;
				        server_name example.com;
				        ssl_certificate /home/nginxWebUI/cert/example.pem;
				        ssl_certificate_key /home/nginxWebUI/cert/example.key;
				        limit_req zone=req_limit burst=20 nodelay;
				        limit_conn conn_limit 10;
				        limit_req_status 429;

				        if ($geo_block_global = 1) {
				            return 403;
				        }

				        location / {
				            proxy_pass http://backend;
				            proxy_set_header Host $host;
				            proxy_set_header X-Real-IP $remote_addr;
				            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
				        }
				    }
				}

				stream {
				    limit_conn_zone $binary_remote_addr zone=s_conn_perip:10m;

				    upstream tcp_backend {
				        server 127.0.0.1:3306;
				    }

				    server {
				        listen 3306;
				        limit_conn s_conn_perip 10;
				        proxy_pass tcp_backend;
				    }
				}
				""";
		assertEquals(List.of(), NginxConfChecker.check(conf, svc));
	}

	@Test
	public void check_左大括號後面帶註解仍然推入區塊() {
		// 這是 } 帶註解那條的鏡像,而且更嚴重:區塊沒進 stack,裡面每一行都拿到錯的 context,
		// 配對的 } 又多 pop 一層,錯位一路擴散到檔尾 —— 一個 server { # api 就能讓整份判讀報廢。
		String conf = """
				http {           # global http
				    server {     # site A
				        listen 80;
				        location / {   # root
				            proxy_pass http://backend;
				        }
				    }
				}
				""";
		assertEquals(List.of(), NginxConfChecker.check(conf, svc));
	}

	@Test
	public void check_同一行開兩個區塊要推入兩層() {
		// 只 push 一層的話,裡面每一行都少算一層 —— 這份完全合法的設定會吐出
		// 「listen 不能用在 http」。那是誤報,和左大括號帶註解同一類,只是觸發窄。
		String conf = """
				http { server {
				    listen 80;
				}
				}
				""";
		assertEquals(List.of(), NginxConfChecker.check(conf, svc));
	}

	@Test
	public void check_正則裡的大括號不算開區塊() {
		// location ~ ^/api/v[0-9]{1,2}/ { 的大括號在正則裡。數大括號會多推一層,
		// 於是 worker_connections 落進看不懂的一層而靜音,錯反而報到後面的 listen 身上。
		// 用一條真的放錯層的指令當探針:推錯層數時,則數看似仍是 1,但行號與內容會不一樣。
		String conf = """
				http {
				    server {
				        location ~ ^/api/v[0-9]{1,2}/ {
				            worker_connections 1;
				        }
				        listen 80;
				    }
				}
				""";
		List<String> problems = NginxConfChecker.check(conf, svc);
		assertEquals(1, problems.size(), "實際:" + problems);
		assertTrue(problems.get(0).startsWith("第 4 行: worker_connections 不能用在 location"), problems.get(0));
	}

	@Test
	public void check_同行推入不會弄丟資料區塊與家族判斷() {
		// 逐段推入把「認不得就當 opaque」這段邏輯多寫了一份,也把最外層(家族判斷的依據)
		// 從第一段決定。兩者任一寫錯,map 的對照資料會開始被當指令、stream 會退化成 http。
		String dataBlock = """
				http { map $http_upgrade $connection_upgrade {
				    default upgrade;
				    ''      close;
				}
				}
				""";
		assertEquals(List.of(), NginxConfChecker.check(dataBlock, svc));

		String streamServer = """
				stream { server {
				    add_header X 1;
				}
				}
				""";
		List<String> problems = NginxConfChecker.check(streamServer, svc);
		assertEquals(1, problems.size(), "實際:" + problems);
		assertTrue(problems.get(0).startsWith("第 2 行: add_header 不能用在 stream 的 server"), problems.get(0));
	}

	@Test
	public void check_行尾註解不會讓真錯誤消失() {
		// 剝註解最省事的寫法是「含 # 的行整行跳過」,那會把這一類錯誤永久關掉,
		// 而且照樣通過其他每一條測試 —— 所以正反兩面都要有人盯。
		String conf = """
				http {
				    server {
				        proxy_pas http://backend;   # 打錯字,但後面有註解
				    }
				    worker_connections 1024;        # 放錯層,後面也有註解
				}
				""";
		List<String> problems = NginxConfChecker.check(conf, svc);
		assertEquals(2, problems.size(), "實際:" + problems);
		assertTrue(problems.get(0).startsWith("第 3 行: 未知指令 proxy_pas"), problems.get(0));
		assertTrue(problems.get(1).startsWith("第 5 行: worker_connections 不能用在 http"), problems.get(1));
	}

	@Test
	public void check_引號裡的井字號不是註解() {
		// 剝到引號裡去,這一行就會變成沒有分號結尾的殘句而被整行跳過。用一個「真的放錯層」的
		// 指令當探針:抓得到才代表這一行還完整地留在檢查流程裡。
		String conf = """
				http {
				    upstream u {
				        root "/var/www/#1";
				    }
				}
				""";
		List<String> problems = NginxConfChecker.check(conf, svc);
		assertEquals(1, problems.size(), "實際:" + problems);
		assertTrue(problems.get(0).startsWith("第 3 行: root 不能用在 upstream"), problems.get(0));

		// 跳脫的引號:少了跳脫處理,\" 會被當成關引號,後面的 # 就變註解,整行被砍成殘句而消失。
		// 同樣用真的放錯層的 root 當探針 —— 抓不到就代表這一行沒能走完檢查。
		String escaped = """
				http {
				    upstream u {
				        root "a\\"b #1";
				    }
				}
				""";
		List<String> escapedProblems = NginxConfChecker.check(escaped, svc);
		assertEquals(1, escapedProblems.size(), "實際:" + escapedProblems);
		assertTrue(escapedProblems.get(0).startsWith("第 3 行: root 不能用在 upstream"), escapedProblems.get(0));
	}

	@Test
	public void check_stream的server不放行http專屬指令() {
		// stream { server { } } 與 http { server { } } 對堆疊來說都只是字串 server,
		// 光看最內層會把 8 條純 HTTP 指令全放行。CLAUDE.md 把「HTTP-only 指令混進 stream」
		// 列為本 repo 反覆踩到的雷(ConfService 自動注入要略過 if/add_header、
		// migration streamDefTemplatesSanitized20260812 就是在清這個),不能沒有意見。
		String conf = """
				stream {
				    server {
				        listen 12345;
				        proxy_pass tcp_backend;
				        add_header X-Foo 1;
				        root /var/www;
				    }
				}
				""";
		List<String> problems = NginxConfChecker.check(conf, svc);
		assertEquals(2, problems.size(), "實際:" + problems);
		assertTrue(problems.get(0).startsWith("第 5 行: add_header 不能用在 stream 的 server"), problems.get(0));
		assertTrue(problems.get(1).startsWith("第 6 行: root 不能用在 stream 的 server"), problems.get(1));
	}

	@Test
	public void check_mail的server不放行http專屬指令() {
		String conf = """
				mail {
				    server {
				        listen 110;
				        protocol pop3;
				        add_header X-Foo 1;
				    }
				}
				""";
		List<String> problems = NginxConfChecker.check(conf, svc);
		assertEquals(1, problems.size(), "實際:" + problems);
		assertTrue(problems.get(0).startsWith("第 5 行: add_header 不能用在 mail 的 server"), problems.get(0));
	}

	@Test
	public void check_mgmt區塊的內容會被檢查() {
		// mgmt / oidc_provider / acme_issuer 不在區塊清單裡的話會整層落進 opaque,
		// 語料裡 41 條指令的 context 資料就是死的 —— 那不是「沒測到」,是結構上永遠不會檢查。
		String conf = """
				mgmt {
				    usage_report interval=30m;
				    state_path /var/lib/nginx/state;
				    listen 80;
				}
				""";
		List<String> problems = NginxConfChecker.check(conf, svc);
		assertEquals(1, problems.size(), "實際:" + problems);
		assertTrue(problems.get(0).startsWith("第 4 行: listen 不能用在 mgmt"), problems.get(0));
	}

	@Test
	public void check_acme與oidc區塊的指令合法() {
		String conf = """
				http {
				    acme_issuer letsencrypt {
				        uri https://acme-v02.api.letsencrypt.org/directory;
				        contact admin@example.com;
				    }
				    oidc_provider keycloak {
				        issuer https://kc.example.com/realms/x;
				        client_id nginx;
				    }
				}
				""";
		assertEquals(List.of(), NginxConfChecker.check(conf, svc));
	}

	@Test
	public void check_藏在抑制構造後面的錯也要抓到() {
		// 反向對照只擋得住「全面靜音」。抑制規則(剝註解、資料區塊、家族過濾)各自都可能
		// 一不小心從「不誤報」滑成「不報」,所以每一條抑制構造後面都要藏一個真錯誤。
		String conf = """
				http {                      # 帶註解的區塊行
				    server {                # 同上
				        proxy_pas http://x;    # 註解後面的錯字
				        map $a $b {
				            default 0;
				        }
				        add_header X 1;
				    }
				    worker_connections 1;   # 註解後面的放錯層
				}
				stream {
				    server {
				        root /var/www;
				    }
				}
				""";
		List<String> problems = NginxConfChecker.check(conf, svc);
		assertEquals(4, problems.size(), "實際:" + problems);
		assertTrue(problems.get(0).startsWith("第 3 行: 未知指令 proxy_pas"), problems.get(0));
		// 這一則是開區塊那一行本身被檢查之後才冒出來的,而且它是對的:map 的 context 只有 http,
		// 寫在 server 裡 nginx 會拒絕啟動。這份 fixture 從一開始就藏著這個錯,只是沒人看得到。
		assertTrue(problems.get(1).startsWith("第 4 行: map 不能用在 server"), problems.get(1));
		assertTrue(problems.get(2).startsWith("第 9 行: worker_connections 不能用在 http"), problems.get(2));
		assertTrue(problems.get(3).startsWith("第 13 行: root 不能用在 stream 的 server"), problems.get(3));
	}

	@Test
	public void check_真的寫錯的設定一則都不能漏() {
		// 上面那一堆「不要誤報」的規則,最容易的過關方式就是什麼都不報。這條反向對照
		// 盯著另一邊:每一行都是 nginx 會直接拒絕啟動的錯,一則都不該被放行。
		String conf = """
				http {
				    listen 80;
				    worker_connections 1024;
				    proxy_pass http://backend;
				    server {
				        proxy_pas http://backend;
				    }
				    upstream u {
				        root /var/www;
				    }
				}
				""";
		List<String> problems = NginxConfChecker.check(conf, svc);
		assertEquals(5, problems.size(), "實際:" + problems);
		assertTrue(problems.get(0).startsWith("第 2 行: listen 不能用在 http"), problems.get(0));
		assertTrue(problems.get(1).startsWith("第 3 行: worker_connections 不能用在 http"), problems.get(1));
		assertTrue(problems.get(2).startsWith("第 4 行: proxy_pass 不能用在 http"), problems.get(2));
		assertTrue(problems.get(3).startsWith("第 6 行: 未知指令 proxy_pas"), problems.get(3));
		assertTrue(problems.get(4).startsWith("第 9 行: root 不能用在 upstream"), problems.get(4));
		// 官方連結是使用者查證的唯一入口,訊息裡不能只有一句話。
		assertTrue(problems.get(0).contains("https://nginx.org/"), problems.get(0));
	}

	@Test
	public void check_右大括號後面帶註解仍然關掉區塊() {
		// 漏掉這個 pop,後面每一行的 context 都會少算一層 —— listen 會被當成寫在 location 裡。
		String conf = """
				http {
				    server {
				        location / {
				            proxy_pass http://backend;
				        }  # end location
				        listen 80;
				    }
				}
				""";
		assertEquals(List.of(), NginxConfChecker.check(conf, svc));
	}

	@Test
	public void check_一行收掉多層區塊不誤報() {
		// 一行只 pop 一次,} } 與 }} 都只收掉一層,後面每一行從此少算一層 —— 合法的
		// worker_processes 被斬釘截鐵地報成「不能用在 http」。
		//
		// 這一則比一般的漏判更糟:回報時附的但書講的是「大括號不平衡」與「} 與新區塊同行」,
		// 而這兩種情形在這裡都不成立(2 開 2 關、沒有新區塊)。讀者照著但書去數括號、
		// 發現是平衡的,只會更相信那個誤報。
		String spaced = """
				http {
				    server {
				        listen 80;
				    } }
				worker_processes 4;
				""";
		assertEquals(List.of(), NginxConfChecker.check(spaced, svc), "} }");

		String glued = """
				http {
				    server {
				        listen 80;
				    }}
				worker_processes 4;
				""";
		assertEquals(List.of(), NginxConfChecker.check(glued, svc), "}}");

		// 反面探針:逐個 pop 不能變成「見到 } 就把堆疊清光」。收完兩層之後是 main,
		// 這裡的 listen 是真的放錯層(它只能在 server),抓不到就代表 pop 過頭了。
		String probe = """
				http {
				    server {
				        listen 80;
				    } }
				listen 80;
				""";
		List<String> problems = NginxConfChecker.check(probe, svc);
		assertEquals(1, problems.size(), "實際:" + problems);
		assertTrue(problems.get(0).startsWith("第 5 行: listen 不能用在 main"), problems.get(0));
	}

	@Test
	public void check_區塊開錯位置要抓到() {
		// design doc 舉的頭號例子就是「if 寫在 http 層」。開區塊那一行原本只用來推堆疊、
		// 自己從來沒被檢查過,所以底下六種 nginx 會直接拒絕啟動的錯位全部靜音 —— 而同一個
		// 名字寫成指令行反而抓得到。context 已知、目前層級已知,這是能確定的事,不屬於
		// 「不確定就閉嘴」那一類。
		assertOneProblem("http {\n    if ($host = a) {\n    }\n}\n", "第 2 行: if 不能用在 http");
		assertOneProblem("http {\n    location / {\n    }\n}\n", "第 2 行: location 不能用在 http");
		assertOneProblem("http {\n    events {\n    }\n}\n", "第 2 行: events 不能用在 http");
		assertOneProblem("http {\n    server {\n        location / {\n            server {\n            }\n        }\n    }\n}\n",
				"第 4 行: server 不能用在 location");
		assertOneProblem("http {\n    server {\n        upstream u {\n        }\n    }\n}\n",
				"第 3 行: upstream 不能用在 server");
		assertOneProblem("http {\n    server {\n        limit_except GET {\n        }\n    }\n}\n",
				"第 3 行: limit_except 不能用在 server");
	}

	@Test
	public void check_合法的區塊位置與第三方區塊不誤報() {
		// 開區塊那一行開始被檢查之後,最容易的翻車方式有兩種:把合法的巢狀一起報掉,或把
		// 第三方模組的區塊名(geoip2 { })當成拼錯的指令 —— 所以開區塊那一行只比對 context,
		// 不查拼字候選。stream 底下的 upstream / server 也一併釘住,家族過濾錯了會在這裡紅。
		String conf = """
				events {
				}
				http {
				    map $a $b {
				        default 0;
				    }
				    geoip2 /etc/nginx/geoip/GeoLite2-Country.mmdb {
				        auto_reload 60m;
				    }
				    upstream backend {
				        server 127.0.0.1:8080;
				    }
				    server {
				        location / {
				            limit_except GET {
				                deny all;
				            }
				            if ($host = a) {
				                return 403;
				            }
				        }
				    }
				}
				stream {
				    upstream tcp_backend {
				        server 127.0.0.1:3306;
				    }
				    server {
				        listen 3306;
				    }
				}
				""";
		assertEquals(List.of(), NginxConfChecker.check(conf, svc));
	}

	@Test
	public void check_左大括號後面同行還有內容不讓堆疊漂移() {
		// server { listen 80; 不以 { 結尾,原本一層都不推,配對的 } 於是 pop 掉父層,
		// server_name / root 被算在 http。這一份的括號是平衡的、每個 } 也都已經獨立成行,
		// 連但書建議的「把 } 拆開再檢查一次」都驗不出來 —— 兩次結果一樣,誤報反而顯得可信。
		String conf = """
				http {
				    server { listen 80;
				        server_name a.com;
				        root /var/www;
				    }
				}
				""";
		assertEquals(List.of(), NginxConfChecker.check(conf, svc));

		// 深度要真的對齊:漂移的話後面這個 server 會落在錯的層,裡面的 root 就被報出來。
		String sibling = """
				http {
				    server { listen 80;
				        root /var/www;
				    }
				    server {
				        root /var/www;
				    }
				}
				""";
		assertEquals(List.of(), NginxConfChecker.check(sibling, svc));

		// 自己開自己關的一行(map $a $b { default 0; })不能推 —— 推了就再也不會被 pop,
		// 整份設定從那一行之後全部靜音。用一條真的放錯層的指令當探針。
		String selfClosed = """
				http {
				    map $a $b { default 0; }
				    upstream u {
				        root /var/www;
				    }
				}
				""";
		List<String> problems = NginxConfChecker.check(selfClosed, svc);
		assertEquals(1, problems.size(), "實際:" + problems);
		assertTrue(problems.get(0).startsWith("第 4 行: root 不能用在 upstream"), problems.get(0));
	}

	private void assertOneProblem(String conf, String expectedPrefix) {
		List<String> problems = NginxConfChecker.check(conf, svc);
		assertEquals(1, problems.size(), "實際:" + problems);
		assertTrue(problems.get(0).startsWith(expectedPrefix), problems.get(0));
	}

	@Test
	public void check_收尾大括號後面接著開新區塊() {
		// } location /b { 的括號同樣是平衡的。行首的 } pop 完就把整行剩下的部分丟掉,
		// 新區塊沒被推進堆疊,裡面的 alias 就被報成「不能用在 server」。
		String conf = """
				http {
				    server {
				        location /a {
				            proxy_pass http://backend;
				        } location /b {
				            alias /var/www/b;
				        }
				    }
				}
				""";
		assertEquals(List.of(), NginxConfChecker.check(conf, svc));
	}
}
