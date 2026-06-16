# WSDL Client Generator

SOAP servis WSDL dosyalarından otomatik olarak Java client projesi üreten web uygulaması.

---

## Özellikler

- **Prod + Test WSDL** desteği — URL veya dosya yükleme
- **Java 8, 11, 17, 21** desteği
  - Java 8/11 → JAX-WS RI · `javax.*` namespace
  - Java 17/21 → Apache CXF 4.x · `jakarta.*` namespace
- **Otomatik stub üretimi** — CXF `WSDLToJava` ile
- **ServiceBean** — ortam seçimli (prod/test) singleton SOAP client yardımcı sınıfı
- **Yeniden üretim** — ZIP'ten çıkan projede `mvn clean install` ile stub'lar yeniden üretilebilir
- **Asenkron build** — her iş izole temp dizinde, birbirini etkilemez
- **Dark / Light tema**

---

## ZIP İçeriği

```
artifactId-version-client.zip
├── pom.xml                                  # Maven proje tanımı + yeniden üretim plugin
├── src/
│   ├── main/
│   │   ├── java/…/
│   │   │   ├── <stub sınıfları>
│   │   │   └── util/XxxServiceBean.java     # Hazır SOAP client yardımcısı
│   │   └── resources/wsdl/
│   │       ├── artifactId-Prod.wsdl
│   │       └── artifactId-Test.wsdl
├── lib/
│   └── artifactId-version.jar              # Fat JAR (tüm bağımlılıklar dahil)
├── install-local-m2.bat                    # Windows — .m2'ye kur
└── install-local-m2.sh                     # Linux/macOS — .m2'ye kur
```

---

## Hızlı Başlangıç

### Gereksinimler

| Araç | Versiyon |
|------|----------|
| Java | 17+ |
| Maven | 3.8+ |

### Çalıştırma

```bash
mvn spring-boot:run
```

Uygulama `http://localhost:8080` adresinde ayağa kalkar.

### JAR ile Çalıştırma

```bash
mvn package -DskipTests
java -jar target/wsdl-client-generator-1.0.0.jar
```

---

## Yapılandırma

`src/main/resources/application.yml` dosyasından özelleştirilebilir:

```yaml
app:
  maven-executable: mvn.cmd          # Windows: mvn.cmd | Linux: mvn
  temp-dir: ${java.io.tmpdir}/wsdl-generator
  job-ttl-minutes: 60                # Tamamlanan işlerin silinme süresi
  async:
    core-pool-size: 4                # Eş zamanlı build sayısı
    max-pool-size: 10
    queue-capacity: 50

server:
  port: 8080
```

---

## Üretilen Projeyi Kullanma

### 1. ZIP'i Aç ve IntelliJ'e Aktar

ZIP'i çıkartın, IntelliJ'de **File → Open** ile `pom.xml` seçin.

### 2. Yerel Maven Deposuna Kur

```bat
# Windows
install-local-m2.bat

# Linux / macOS
chmod +x install-local-m2.sh && ./install-local-m2.sh
```

### 3. Başka Projeden Bağımlılık Olarak Ekle

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>soap-client</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 4. ServiceBean Kullanımı

```java
MuhasebePortType port = MuhasebeServiceBean.SINGLETON.getPort();
GetFisResponse response = port.getFis(request);
```

### 5. WSDL Değişince Yeniden Üret

`src/main/resources/wsdl/` altındaki WSDL'i güncelleyin:

```bash
mvn clean install
```

---

## Ortam Değişkenleri

ServiceBean aşağıdaki ortam değişkenlerini okur:

| Değişken | Değer | Açıklama |
|----------|-------|----------|
| `SOA_ENVIRONMENT` | `prod` \| `test` | Kullanılacak endpoint. Varsayılan: `prod` |
| `SOA_WEBSERVICE_USERNAME` | string | WS-Security kullanıcı adı |
| `SOA_WEBSERVICE_PASSWORD` | string | WS-Security şifresi |

> System property alternatifleri: `soa.environment`, `soa.webservice.username`, `soa.webservice.password`

---

## Linux / CentOS Sunucuda Çalıştırma

Detaylı kurulum adımları için [DEPLOYMENT.md](DEPLOYMENT.md) dosyasına bakın.

---

## Teknolojiler

| Katman | Teknoloji |
|--------|-----------|
| Backend | Spring Boot 3.2, Java 17 |
| Kod Üretimi | Apache CXF 4.0 WSDLToJava |
| Java 8/11 Build | JAX-WS Maven Plugin 2.3.7 |
| Java 17/21 Build | CXF Codegen Plugin 4.0.4 |
| UI | Thymeleaf, Vanilla JS |
| Paketleme | Maven Shade Plugin |
