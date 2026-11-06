# pop3


[![Maven Central](https://img.shields.io/maven-central/v/com.uchicom/pop3.svg)](http://search.maven.org/#search|ga|1|com.uchicom.pop3)
[![License](https://img.shields.io/github/license/uchicom/pop3.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt)
[![Java CI with Maven](https://github.com/uchicom/pop3/actions/workflows/package.yml/badge.svg)](https://github.com/uchicom/pop3/actions/workflows/package.yml)


pop3 server

## mvn
### サーバ起動
```
mvn exec:java "-Dexec.mainClass=com.uchicom.pop3.Main"

mvn exec:java "-Dexec.mainClass=com.uchicom.pop3.Main" -Dexec.args="-port 8110"

mvn exec:java "-Dexec.mainClass=com.uchicom.pop3.Main" -Dexec.args="-port 8110 -keyStoreName keystore -keyStorePass changeit"
```

### オプション
| オプション | 既定値 | 説明 |
| --- | --- | --- |
| `-dir` | `mailbox` | メールボックスの基準フォルダ |
| `-type` | `pool` | サーバ種別。`single`(単一スレッドで逐次処理)/ `multi`(接続ごとにスレッド生成)/ `pool`(スレッドプール)/ `virtual`(接続ごとに仮想スレッド生成。同時接続数は無制限で `-pool` は無効)。それ以外はエラーメッセージを出して起動しない |
| `-host` | `localhost` | ホスト名(greeting のタイムスタンプに使用) |
| `-port` | `8110` | 待ち受けポート |
| `-back` | `10` | 受信する接続要求キューの最大長 |
| `-pool` | `10` | `pool` 時のスレッド数 |
| `-timeout` | `60000` | 読み取りタイムアウト(ミリ秒) |
| `-maxLine` | `1024` | 1行の最大長 |

実行基盤から停止するときは `Main.shutdown()` を呼ぶ。`System.exit` は呼ばない。

### メールボックス構造
`-dir` 配下のユーザー名のフォルダが1ユーザーのメールボックスになる。フォルダ直下の読み取り可能な通常ファイル(`.pop3`、`.lock`、隠しファイルを除く)が1通のメールとして扱われる。
```
mailbox/
	alice/
		.pop3        ← パスワードファイル
		20260101_120000.000
		20260102_090000.123
```

`.pop3` の先頭の空でない行がパスワードで、**平文のまま保存**する(APOP / SCRAM 認証でサーバ側に平文が必要なため)。`.pop3` がないユーザーは認証できない。

### セキュリティ上の注意
- パスワードを平文で保存するため、メールボックスのフォルダは実行ユーザー以外が読めないようにする
- TLS なしの場合、USER / PASS や本文が平文で流れる。公開環境では TLS を有効にする(AUTH PLAIN / LOGIN は TLS 確立後のみ受け付ける)

### TLS オプション
| オプション | 既定値 | 説明 |
| --- | --- | --- |
| `-keyStoreName` / `-keyStorePass` | なし | キーストアのパスとパスワード。指定すると STLS が有効になる |
| `-keyStoreType` | `JKS` | キーストア種別(`PKCS12` など)。起動時に1回だけ読み込み、全接続で共有する |
| `-tlsProtocols` | `TLSv1.3,TLSv1.2` | 有効にするプロトコル(カンマ区切り) |
| `-cipherSuites` | JVM の既定 | 有効にする暗号スイート(カンマ区切り) |
| `-implicitTls` | `false` | `true` で接続直後から TLS で待ち受ける(995番など。`-port 995`)。キーストアが必須 |

AUTH PLAIN / LOGIN は TLS 確立後(STLS 後、または暗黙的 TLS)のみ受け付け、CAPA にも TLS 下でのみ通知する。

TLS 確立後は `SCRAM-*-PLUS`(チャネルバインディング)も提示する。対応するのは `tls-server-end-point`(RFC 5929)のみで、サーバー証明書のハッシュを使う(署名が MD5 / SHA-1 の場合は SHA-256)。`Ed25519` / `Ed448` 証明書ではハッシュが定義されないため提示しない。`tls-unique` と `tls-exporter`(RFC 9266)は Java の標準 API で必要な値を取得できないため未対応。

```
mvn exec:java "-Dexec.mainClass=com.uchicom.pop3.Main" -Dexec.args="-port 995 -implicitTls true -keyStoreName keystore.p12 -keyStoreType PKCS12 -keyStorePass changeit"
```

## keytool
```
keytool -genkey -alias pop3 -keyalg RSA -keystore keystore -storepass changeit
```
