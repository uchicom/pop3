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

### TLS オプション
| オプション | 既定値 | 説明 |
| --- | --- | --- |
| `-keyStoreName` / `-keyStorePass` | なし | キーストアのパスとパスワード。指定すると STLS が有効になる |
| `-keyStoreType` | `JKS` | キーストア種別(`PKCS12` など)。起動時に1回だけ読み込み、全接続で共有する |
| `-tlsProtocols` | `TLSv1.3,TLSv1.2` | 有効にするプロトコル(カンマ区切り) |
| `-cipherSuites` | JVM の既定 | 有効にする暗号スイート(カンマ区切り) |
| `-implicitTls` | `false` | `true` で接続直後から TLS で待ち受ける(995番など。`-port 995`)。キーストアが必須 |

AUTH PLAIN / LOGIN は TLS 確立後(STLS 後、または暗黙的 TLS)のみ受け付け、CAPA にも TLS 下でのみ通知する。

```
mvn exec:java "-Dexec.mainClass=com.uchicom.pop3.Main" -Dexec.args="-port 995 -implicitTls true -keyStoreName keystore.p12 -keyStoreType PKCS12 -keyStorePass changeit"
```

## keytool
```
keytool -genkey -alias pop3 -keyalg RSA -keystore keystore -storepass changeit
```
