# page

> Source: https://nginx.org/en/docs/njs/node_modules.html

---

## 目錄

- [Using node modules with njs](#using-node-modules-with-njs)
    - [Environment](#environment)
    - [Protobufjs](#protobufjs)
    - [DNS-packet](#dns-packet)

---

## njs使用node模塊

通常，開發人員希望使用第三方代碼，通常以某種庫的形式提供。在JavaScript世界中，模塊的概念相對較新，因此直到最近才有標準。許多平台（瀏覽器）仍然不支持模塊，這使得代碼重用變得更加困難。本文居間了在njs中重用[Node.js](https://nodejs.org/)代碼的方法。

> >本文中的示例使用了出現在[njs](https://nginx.org/en/docs/njs/index.html)[0.3.8](https://nginx.org/en/docs/njs/changes.html#njs0.3.8)中的功能

當第三方代碼添加到njs時，可能會出現一些問題：

-   相互引用的多個文件及其依賴關係
-   特定於平台的API
-   現代標準語言結構

好消息是這些問題並不是njs特有的新問題。JavaScript開發人員在嘗試支持多個具有非常不同屬性的不同平台時，每天都會遇到這些問題。有一些工具旨在解決上述問題。

-   相互引用的多個文件及其依賴關係
    
    這可以通過將所有相互依賴的代碼合併到一個文件中來解決。像[browserify](http://browserify.org/)或[webpack](https://webpack.js.org/)這樣的工具接受整個項目，並生成一個包含您的代碼和所有依賴項的文件。
    
-   特定於平台的API
    
    您可以使用多個庫以平台無關的方式實現這些API（儘管會犧牲性能）。特定的功能也可以使用[polyfill](https://polyfill.io/v3/)方法實現。
    
-   現代標準語言結構
    
    這樣的代碼可以編譯：這意味著執行許多轉換，根據舊的標準重寫新的語言特性。2例如，[babel](https://babeljs.io/)project可以用於此目的。
    

在本指南中，我們將使用兩個相對較大的npm託管庫：

-   [protobufjs](https://www.npmjs.com/package/protobufjs) -用於創建和解析[gRPC](https://grpc.io/)協議使用的protobuf消息的庫
-   [dns-packet](https://www.npmjs.com/package/dns-packet) -用於處理DNS協議數據包的庫

#### Environment

> >本文檔主要採用通用方法，避免了關於Node.js和JavaScript的具體最佳實踐建議。在執行此處建議的步驟之前，請確保查閱相應包的手冊。

首先（假設Node.js已經安裝並運行），讓我們創建一個空項目並安裝一些依賴項;下面的命令假設我們在工作目錄中：

```nginx
>$mkdirmy_project && cdmy_project
$ npx許可choose_your_license_here >許可
$ npx個忽略節點

$ cat >數據包. json <<EOF
{
  「姓名」：        「foobar」，
  "version":     "0.0.1",
  "description": "",
  「主要」：        「索引. js」，
  "keywords":    [],
  「作者」：      「某個用戶名（some.emailexample.com https://example.com)",
  "license":     "some_license_here",
  「私有」：     說真，
  "scripts": {
    「test」：「echo \\「錯誤：未指定測試\\」&& exit 1」
  }
}
EOF
$ npm初始化-y
$ npm安裝瀏覽器
```

#### Protobufjs

該庫為`.proto`接口定義提供了一個解析器，並為消息解析和生成提供了一個代碼生成器。

在這個例子中，我們將使用gRPC例子中的[helloworld.proto](https://github.com/grpc/grpc/blob/master/examples/protos/helloworld.proto)文件。我們的目標是創建兩條消息：`HelloRequest`和`HelloResponse`。我們將使用protobufjs的[static](https://github.com/protobufjs/protobuf.js/blob/master/README.md#reflection-vs-static-code)模式，而不是動態生成類，因為出於安全考慮，njs不支持動態添加新函數。

接下來，安裝該庫，並從協議定義中生成實現消息編組的JavaScript代碼：

```
$ npm安裝協議緩衝區
$ npx pbjs -t靜態模塊helloworld.proto >靜態模塊. js
```

因此，`static.js`文件成為我們的新依賴項，它存儲了實現消息處理所需的所有代碼。`set_buffer()`函數包含的代碼使用庫來創建一個緩衝區，其中包含序列化的`HelloRequest`消息。該代碼位於`code.js`文件中：

```javascript
var pb = require('./static.js');

//protobuf庫的用法示例：準備一個要發送的緩衝區
>函數set_buffer（pb）
{
    //設置gRPC負載的欄位
    var payload = { name: "TestString" };

    //創建一個對象
    var message = pb.helloworld.HelloRequest.create(payload);

    //將對象序列化到緩衝區
    var buffer = pb.helloworld.HelloRequest.encode(message).finish();

    var n = buffer.length;

    var frame = new Uint8Array(5 + buffer.length);

    frame[0] = 0;                        // 'compressed' flag
    frame[1] = (n & 0xFF000000) >>> 24;  // length: uint32 in network byte order
    frame[2] = (n & 0x00FF0000) >>> 16;
    frame[3] = (n & 0x0000FF00) >>>  8;
    frame[4] = (n & 0x000000FF) >>>  0;

    frame.set(buffer, 5);

    return frame;
}

var frame = set_buffer(pb);
```

為了確保它工作，我們使用node執行代碼：

```
$ node./code. js
Uint8Array [
    0,   0,   0,   0,  12, 10,
   10,  84, 101, 115, 116, 83,
  116, 114, 105, 110, 103
]
```

你可以看到這得到了一個正確編碼的`gRPC`幀。現在讓我們用njs運行它：

> $ njs./code. js
> Thrown:
> >錯誤：找不到模塊「./js」
>     at require（native）
>     at main（本地）

不支持模塊，所以我們收到了一個異常。要解決這個問題，讓我們使用`browserify`或其他類似的工具。

嘗試處理我們現有的`code.js`文件將導致一堆JS代碼，這些代碼應該在瀏覽器中運行，即在加載時立即運行。這不是我們實際想要的。相反，我們希望有一個可以從nginx配置中引用的導出函數。這需要一些包裝器代碼。

> >在本指南中，為了簡單起見，我們在所有示例中使用njs[cli](https://nginx.org/en/docs/njs/cli.html)。在真實的生活中，您將使用nginx njs模塊來運行代碼。

`load.js`文件包含庫加載代碼，該代碼將其句柄存儲在全局命名空間中：

```nginx
global.hello = require('./static.js');
```

此代碼將被合併的內容替換。我們的代碼將使用「`global.hello`」句柄訪問庫。

接下來，我們使用`browserify`處理它，以將所有依賴項放入一個文件中：

```
$ npx browserify load. js-o. js-d
```

結果是一個巨大的文件，其中包含我們所有的依賴項：

> (function(){function......
> ...
> ...
> },{"protobufjs/minimal":9}\]},{},\[1\])
> //# sourceMappingURL..............

為了得到最終的「`njs_bundle.js`」文件，我們將「`bundle.js`」和以下代碼連接起來：

```javascript
//protobuf庫的用法示例：準備一個要發送的緩衝區
>函數set_buffer（pb）
{
    //設置gRPC payload的欄位
    var payload = { name: "TestString" };

    //創建一個對象
    var message = pb.helloworld.HelloRequest.create(payload);

    //將對象序列化到緩衝區
    var buffer = pb.helloworld.HelloRequest.encode(message).finish();

    var n = buffer.length;

    var frame = new Uint8Array(5 + buffer.length);

    frame[0] = 0;                        // 'compressed' flag
    frame[1] = (n & 0xFF000000) >>> 24;  // length: uint32 in network byte order
    frame[2] = (n & 0x00FF0000) >>> 16;
    frame[3] = (n & 0x0000FF00) >>>  8;
    frame[4] = (n & 0x000000FF) >>>  0;

    frame.set(buffer, 5);

    return frame;
}

//從外部調用的函數
function setbuf（）
{
    return set_buffer(global.hello);
}

//調用代碼
var frame = setbuf();
console.log(frame);
```

讓我們使用node來運行文件，以確保一切正常：

```
$ node ./njs_bundle.js
Uint8Array [
    0,   0,   0,   0,  12, 10,
   10,  84, 101, 115, 116, 83,
  116, 114, 105, 110, 103
]
```

現在讓我們繼續使用njs：

```
$ njs ./njs_bundle.js
Uint8Array [0，0，0，0，12，10，10，84，101，115，116，83，116，114，105，110，103]
```

最後一件事是使用njs特定的API將數組轉換為字節字符串，以便nginx模塊可以使用。我們可以在`return frame; }`行之前添加以下片段：

```nginx
if (global.njs) {
    return String.bytesFrom（frame）
}
```

最後，我們得到了它的工作：

```
$ njs ./njs_bundle.js|十六進位轉儲-C
00000000  00 00 00 00 0c 0a 0a 54  65 73 74 53 74 72 69 6e  |.......TestStrin|
00000010  67 0a                                             |g.|
00000012
```

這是預期的結果。響應解析可以類似地實現：

```javascript
>函數parse_msg（pb，msg）
{
    //將字符串轉換為整數數組
    var bytes = msg.split('').map(v=>v.charCodeAt(0));

    if (bytes.length < 5) {
        throw 'message too short';
    }

    //前5個字節是gRPC幀（壓縮+長度）
    var head = bytes.splice(0, 5);

    //確保消息長度正確
    變量長度=（頭[1] << 24）
              + (head[2] << 16)
              + (head[3] << 8)
              + head[4];

    if (len != bytes.length) {
        throw 'header length mismatch';
    }

    //調用protobufjs解碼消息
    var response = pb.helloworld.HelloReply.decode(bytes);

    console.log('Reply is:' + response.message);
}
```

#### DNS數據包

這個例子使用了一個庫來生成和解析DNS包。這個例子值得考慮，因為這個庫及其依賴項使用了njs還不支持的現代語言結構。反過來，這需要我們額外的步驟：解析原始碼。

需要其他節點程式包：

```
$ npm install @babel/core @babel/cli @babel/preset-env babel-loader
$ npm安裝網絡包網絡包-客戶端
$ npm安裝緩衝區
$ npm安裝DNS數據包
```

配置文件webpack.config.js：

```javascript
const path = require('path');

module.exports = {
    項目：'./load.js'，
    模式：'生產'，
    output: {
        文件名稱：'wp_out.js'，
        路徑：路徑.解析（__目錄名，'dist'），
    },
    optimization: {
        最小化：false
    },
    node: {
        全局：正確，
    },
    module : {
        rules: [{
            測試：/\\.m？js$$/，
            exclude: /(bower_components)/,
            use: {
                裝載機：「巴比倫裝載機」，
                options: {
                    presets: ['@babel/preset-env']
                }
            }
        }]
    }
};
```

注意，我們使用的是「`production`」模式。在這種模式下，webpack不使用njs不支持的「`eval`」結構。引用的`load.js`文件是我們的入口點：

```
global.dns = require（'dns-packet'）
global.Buffer = require（'buffer/'）.Buffer
```

我們以同樣的方式開始，為庫生成一個文件：

```
$ npx browserify load. js-o. js-d
```

接下來，我們用webpack處理文件，它本身調用babel：

```
$ npx webpack --config webpack.js.js
```

此命令生成`dist/wp_out.js`文件，它是`bundle.js`的編譯版本。我們需要將它與存儲我們代碼的`code.js`連接起來：

```javascript
functionset_buffer（dnsPacket）
{
    //創建DNS數據包字節
    var buf = dnsPacket.encode({
        類型：'查詢'，
        id: 1,
        flags：dnsPacket.RECURSION_RED，
        questions: [{
            type: 'A',
            name：'good.com'
        }]
    })

    return buf;
}
```

請注意，在這個例子中，生成的代碼沒有包裝到函數中，我們不需要顯式調用它。結果在「`dist`」目錄中：

```
$ cat dist/wp_out.js code.js > njs_dns_bundle.js
```

讓我們在文件末尾調用代碼：

```javascript
var b = set_buffer(global.dns);
console.log(b);
```

並使用node執行它：

```
$ node ./njs_dns_bundle_final.js
Buffer [Uint8Array] [
    0,   1,   1, 0,  0,   1,   0,   0,
    0,   0,   0, 0,  6, 103, 111, 111,
  103, 108, 101, 3, 99, 111, 109,   0,
    0,   1,   0, 1
]
```

確保它能按預期工作，然後用njs運行它：

```
$ njs ./njs_dns_bundle_final.js
Uint8Array [0，1，1，0，0，1，0，0，0，6，103，111，111，103，108，101，3，99，111，109，0，0，1，0，1]
```

響應可以如下解析：

```javascript
>函數parse_response（buf）
{
    var bytes = buf.split('').map(v=>v.charCodeAt(0));

    var b = global.Buffer.from(bytes);

    var packet = dnsPacket.decode(b);

    var resolved_name = packet.answers[0].name;

    //根據我們上面的請求，預期名稱為'google.com'
}
```