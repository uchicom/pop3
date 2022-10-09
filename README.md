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

## keytool
```
keytool -genkey -alias pop3 -keyalg RSA -keystore keystore -storepass changeit
```
