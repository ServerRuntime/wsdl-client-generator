# WSDL Client Generator — Teknik Dokümantasyon

## İçindekiler

1. [Genel Bakış](#1-genel-bakış)
2. [Mimari](#2-mimari)
3. [Proje Yapısı](#3-proje-yapısı)
4. [Servisler](#4-servisler)
5. [Desteklenen Java Versiyonları](#5-desteklenen-java-versiyonları)
6. [İş Akışı](#6-iş-akışı)
7. [Üretilen ZIP İçeriği](#7-üretilen-zip-içeriği)
8. [ServiceBean](#8-servicebean)
9. [Yeniden Üretim (Regenerate)](#9-yeniden-üretim-regenerate)
10. [Yapılandırma](#10-yapılandırma)
11. [Eş Zamanlı Kullanım](#11-eş-zamanlı-kullanım)
12. [Hata Yönetimi](#12-hata-yönetimi)

---

## 1. Genel Bakış

WSDL Client Generator, SOAP servis entegrasyonlarında tekrarlayan kod üretim sürecini otomatize eden bir Spring Boot web uygulamasıdır.

**Temel işlev:** WSDL dosyası (URL veya upload) → Hedef Java versiyonuna göre derlenmiş Maven client projesi → ZIP olarak indir.

**Desteklenen senaryolar:**
- Prod ve Test ortamı için ayrı WSDL desteği
- URL veya dosya yükleme ile WSDL girişi
- Java 8, 11, 17, 21 hedef versiyonları
- Eş zamanlı çoklu kullanım (izole iş yönetimi)

---

## 2. Mimari

```
┌─────────────────────────────────────────────────────┐
│                    Web Katmanı                       │
│  GeneratorController   GeneratorApiController        │
└────────────────────────┬────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────┐
│               Orkestrasyon Katmanı                   │
│              GenerationOrchestrator                  │
│         (Async · UUID izolasyonu · Job yönetimi)     │
└──┬──────┬──────┬──────┬──────┬──────────────────────┘
   │      │      │      │      │
   ▼      ▼      ▼      ▼      ▼
Fetch  Parse  Code  Pom   Bean   Build   Zip
WSDL   WSDL   Gen   Gen   Gen    (mvn)  Package
```

### Katmanlar

| Katman | Sorumluluk |
|--------|------------|
| **Web** | HTTP istek/yanıt, form validasyonu, yönlendirme |
| **Orkestrasyon** | İş yönetimi, adım sıralama, hata yakalama |
| **Servisler** | Tek sorumlu iş birimleri (WSDL fetch, parse, codegen vb.) |
| **Model** | `GenerationRequest`, `GenerationJob`, `JobStatus`, `WsdlMetadata` |

---

## 3. Proje Yapısı

```
src/main/java/com/example/wsdlgenerator/
│
├── config/
│   ├── AsyncConfig.java            # Thread pool yapılandırması
│   └── CleanupScheduler.java       # Süresi dolan job temizliği (30 dk'da bir)
│
├── controller/
│   ├── GeneratorController.java    # Thymeleaf web controller
│   └── GeneratorApiController.java # REST API (job status polling)
│
├── model/
│   ├── GenerationRequest.java      # Form input modeli
│   ├── GenerationJob.java          # İş durumu taşıyıcı
│   ├── JobStatus.java              # Enum: PENDING → ... → COMPLETED/FAILED
│   ├── JobStatusResponse.java      # API yanıt DTO
│   ├── JavaVersion.java            # Enum: JAVA_8/11/17/21
│   └── WsdlMetadata.java           # Parse edilen WSDL bilgisi
│
└── service/
    ├── GenerationOrchestrator.java     # Ana orkestratör
    ├── WsdlFetchService.java           # WSDL indirme/kaydetme
    ├── WsdlParserService.java          # WSDL parse → WsdlMetadata
    ├── CodeGenService.java             # CXF WSDLToJava stub üretimi
    ├── PomGeneratorService.java        # pom.xml üretimi (versiyon bazlı)
    ├── ServiceBeanGeneratorService.java# ServiceBean util sınıfı üretimi
    ├── BuildService.java               # Maven build yürütücü
    └── ZipPackageService.java          # ZIP paketleme
```

---

## 4. Servisler

### 4.1 GenerationOrchestrator

Tüm üretim adımlarını yöneten merkezi servistir. Her iş `@Async` ile ayrı thread'de çalışır.

**İş yaşam döngüsü:**

```
createJob() → UUID oluştur → jobs map'e ekle → runAsync() başlat → jobId dön
                                                      │
                                        ┌─────────────▼─────────────┐
                                        │  1. WSDL indir/kaydet     │ %10
                                        │  2. WSDL parse et         │ %25
                                        │  3. targetPackage türet   │
                                        │  4. Stub kodları üret     │ %40
                                        │  5. pom.xml üret          │ %35
                                        │  6. ServiceBean üret      │ %45
                                        │  7. Maven build           │ %55
                                        │  8. ZIP oluştur           │ %88
                                        │  9. job.complete()        │ %100
                                        └───────────────────────────┘
```

**Namespace → Package dönüşümü:**

```java
// http://mali.ibb.gov.tr/muhasebe → tr.gov.ibb.mali.muhasebe
static String namespaceToPackage(String namespace)
```

Bu algoritma CXF'in kendi dönüşümüyle birebir eşleşir; üretilen stubs ile ServiceBean aynı pakette konumlanır.

---

### 4.2 WsdlFetchService

URL veya yüklenen dosyadan WSDL'i alır, belirtilen dizine kaydeder.

---

### 4.3 WsdlParserService

DOM parser ile WSDL'den metadata çıkarır:

| Alan | Kaynak |
|------|--------|
| `targetNamespace` | `<definitions targetNamespace>` |
| `serviceName` | `<service name>` |
| `portTypeName` | `<portType name>` |
| `portName` | `<service><port name>` |

XXE saldırılarına karşı korumalıdır (`disallow-doctype-decl`, external entity'ler devre dışı).

---

### 4.4 CodeGenService

CXF 4.x `WSDLToJava` API'sini programatik olarak çağırır (Maven plugin değil — classloader çakışması olmaz).

**Kullanılan flag'ler:**

| Flag | Açıklama |
|------|----------|
| `-d` | Çıktı dizini (`src/main/java`) |
| `-p` | Hedef paket (namespace'den türetilen) |
| `-exsh true` | Exception handler sınıflarını da üret |
| `-autoNameResolution` | Aynı isimli tip çakışmalarını otomatik çöz |
| `-verbose` | Üretim logları |

**Java 8/11 için namespace dönüşümü:**

CXF 4.x her zaman `jakarta.*` üretir. Java 8/11 seçildiğinde üretim sonrası tüm `.java` dosyalarında:

```
jakarta.xml.bind.  →  javax.xml.bind.
jakarta.xml.ws.    →  javax.xml.ws.
jakarta.jws.       →  javax.jws.
```

dönüşümü yapılır.

---

### 4.5 PomGeneratorService

Hedef Java versiyonuna göre farklı `pom.xml` üretir.

| Versiyon | Bağımlılıklar | Yeniden üretim |
|----------|---------------|----------------|
| Java 8 | `javax.xml.ws:jaxws-api`, `com.sun.xml.ws:rt`, `javax.xml.bind:jaxb-api`, `com.sun.xml.bind:jaxb-impl` | `jaxws-maven-plugin` |
| Java 11 | Java 8 ile aynı | `jaxws-maven-plugin` |
| Java 17 | `jakarta.xml.ws-api`, `cxf-rt-frontend-jaxws`, `cxf-rt-transports-http`, `jaxb-runtime`, `jakarta.activation-api`, `slf4j-simple` | `cxf-codegen-plugin` |
| Java 21 | Java 17 ile aynı | `cxf-codegen-plugin` |

Yeniden üretim plugin'leri `regenerate` profili altında tanımlıdır — normal build'i etkilemez.

---

### 4.6 ServiceBeanGeneratorService

WSDL metadata'sından singleton SOAP client yardımcı sınıfı üretir.

**Üretim kuralları:**

| Alan | Kaynak |
|------|--------|
| Paket | `targetPackage + ".util"` |
| Sınıf adı | WSDL `serviceName` + `Bean` (zaten `Service` ile bitiyorsa) |
| Port tipi | WSDL `portTypeName` |
| Namespace | WSDL `targetNamespace` |

**Üretilen sınıf yetenekleri:**
- Singleton pattern (`SINGLETON` sabiti)
- Ortam değişkeninden prod/test seçimi
- WS-Security kullanıcı adı/şifre entegrasyonu
- Classpath'ten WSDL yükleme
- `javax.*` / `jakarta.*` namespace otomatik seçimi (Java versiyonuna göre)

---

### 4.7 BuildService

Üretilen projeyi Maven ile derler.

```bash
mvn package -f /tmp/wsdl-generator/job-{uuid}/pom.xml -DskipTests --batch-mode -q
```

- `JAVA_HOME` Spring Boot'un çalıştığı JDK'ya sabitlenir
- Timeout: 10 dakika
- Build log `build.log` dosyasına yazılır; hata durumunda son 3000 karakter exception mesajına dahil edilir

---

### 4.8 ZipPackageService

Üretilen dosyaları ZIP olarak paketler.

**ZIP içerik haritası:**

| Kaynak | ZIP'teki Yolu |
|--------|---------------|
| `pom.xml` | `pom.xml` |
| `src/main/resources/wsdl/*.wsdl` | `src/main/resources/wsdl/` |
| `src/main/java/**/*.java` | `src/main/java/` |
| `target/*.jar` (original hariç) | `lib/` |
| `build.log` | `build.log` |
| install script | `install-local-m2.bat` / `install-local-m2.sh` |

---

## 5. Desteklenen Java Versiyonları

| Versiyon | Araç | Namespace | Özellik |
|----------|------|-----------|---------|
| **Java 8** | JAX-WS RI 2.3.7 | `javax.*` | JDK yerleşik API |
| **Java 11** | JAX-WS RI 2.3.7 | `javax.*` | JAXB bağımlılık eklenir |
| **Java 17** | Apache CXF 4.0.4 | `jakarta.*` | Önerilen versiyon |
| **Java 21** | Apache CXF 4.0.4 | `jakarta.*` | En güncel |

---

## 6. İş Akışı

```
Kullanıcı → Form Gönder
                │
                ▼
        GeneratorController
        POST /generate
                │
                ▼
        GenerationOrchestrator.createJob()
        ├── UUID oluştur
        ├── Jobs map'e ekle
        └── runAsync() → @Async thread
                │
                ▼
        redirect: /status/{jobId}
                │
                ▼
        status.html — polling /api/jobs/{jobId}
        (her 2 saniyede bir)
                │
                ▼
        COMPLETED → Download butonu
        FAILED    → Hata detayı
```

---

## 7. Üretilen ZIP İçeriği

```
{artifactId}-{version}-client.zip
│
├── pom.xml                               # Maven proje tanımı
│   ├── Bağımlılıklar (javax.* veya jakarta.*)
│   ├── maven-compiler-plugin
│   ├── maven-shade-plugin (Java 17/21)
│   └── profiles/regenerate
│       └── jaxws-maven-plugin veya cxf-codegen-plugin
│
├── src/
│   └── main/
│       ├── java/
│       │   └── {targetPackage}/          # namespace'den türetilir
│       │       ├── *.java                # WSDL stub sınıfları
│       │       └── util/
│       │           └── XxxServiceBean.java
│       └── resources/
│           └── wsdl/
│               ├── {artifactId}-Prod.wsdl
│               └── {artifactId}-Test.wsdl
│
├── lib/
│   └── {artifactId}-{version}.jar       # Fat JAR
│
├── install-local-m2.bat                  # Windows kurulum scripti
├── install-local-m2.sh                   # Linux/macOS kurulum scripti
└── build.log                             # Maven build çıktısı
```

---

## 8. ServiceBean

### Üretilen Kod Örneği

```java
package tr.gov.ibb.mali.muhasebe.util;

public class MuhasebeServiceBean {

    public static final MuhasebeServiceBean SINGLETON = new MuhasebeServiceBean();

    private final MuhasebePortType port;

    private MuhasebeServiceBean() {
        String env      = getConfigValue("SOA_ENVIRONMENT",        "soa.environment");
        String username = getConfigValue("SOA_WEBSERVICE_USERNAME", "soa.webservice.username");
        String password = getConfigValue("SOA_WEBSERVICE_PASSWORD", "soa.webservice.password");

        String wsdlFile = "test".equalsIgnoreCase(env)
            ? "wsdl/muhasebe-Test.wsdl"
            : "wsdl/muhasebe-Prod.wsdl";

        URL wsdlUrl = getClass().getClassLoader().getResource(wsdlFile);
        QName qname = new QName("http://mali.ibb.gov.tr/muhasebe", "MuhasebeService");
        Service svc = Service.create(wsdlUrl, qname);
        this.port   = svc.getPort(MuhasebePortType.class);

        Map<String, Object> ctx = ((BindingProvider) port).getRequestContext();
        ctx.put("com.sun.xml.ws.security.auth.username", username);
        ctx.put("com.sun.xml.ws.security.auth.password", password);
    }

    public MuhasebePortType getPort() { return port; }
}
```

### Kullanım

```java
// Spring Bean olarak
@Bean
public MuhasebePortType muhasebePort() {
    return MuhasebeServiceBean.SINGLETON.getPort();
}

// Doğrudan çağrı
GetFisResponse resp = MuhasebeServiceBean.SINGLETON.getPort().getFis(request);
```

### Ortam Değişkenleri

| Değişken | System Property | Açıklama | Varsayılan |
|----------|----------------|----------|------------|
| `SOA_ENVIRONMENT` | `soa.environment` | `prod` veya `test` | `prod` |
| `SOA_WEBSERVICE_USERNAME` | `soa.webservice.username` | WS-Security kullanıcı adı | `""` |
| `SOA_WEBSERVICE_PASSWORD` | `soa.webservice.password` | WS-Security şifresi | `""` |

---

## 9. Yeniden Üretim (Regenerate)

WSDL değiştiğinde stub sınıflarını yeniden üretmek için:

### Adımlar

1. `src/main/resources/wsdl/` altındaki WSDL dosyasını güncelleyin
2. Aşağıdaki komutu çalıştırın:

```bash
# Java 8 / 11
mvn clean install -Pregenerate

# Java 17 / 21
mvn clean install -Pregenerate
```

### Nasıl Çalışır?

- Normal `mvn clean install` → sadece mevcut kodları derler, plugin çalışmaz
- `-Pregenerate` profiliyle → plugin devreye girer, `src/main/java/` altındaki stubs yeniden üretilir

### Neden Profil?

`jaxws-maven-plugin`, `skip` mekanizmasını Maven property flag'leriyle desteklemez. Profil yaklaşımı plugin'i tamamen lifecycle dışında tutarak generator build'inin etkilenmesini engeller.

---

## 10. Yapılandırma

`application.yml`:

```yaml
app:
  maven-executable: mvn.cmd          # Windows: mvn.cmd | Linux/macOS: mvn
  temp-dir: ${java.io.tmpdir}/wsdl-generator
  job-ttl-minutes: 60                # Tamamlanan işlerin bellekten ve diskten silinme süresi
  async:
    core-pool-size: 4                # Başlangıç thread sayısı
    max-pool-size: 10                # Maksimum eş zamanlı build
    queue-capacity: 50               # Sıra kapasitesi

server:
  port: 8080
```

---

## 11. Eş Zamanlı Kullanım

### İzolasyon Modeli

Her iş tamamen izoledir:

- **Benzersiz ID:** `UUID.randomUUID()` — çakışma ihtimali sıfır
- **Ayrı dizin:** `/tmp/wsdl-generator/job-{uuid}-{random}/`
- **Ayrı thread:** `ThreadPoolTaskExecutor` ile asenkron yürütme
- **In-memory state:** `ConcurrentHashMap<String, GenerationJob>`

### Kapasite

| Parametre | Varsayılan | Açıklama |
|-----------|------------|----------|
| `core-pool-size` | 4 | Bu kadar build eş zamanlı başlar |
| `max-pool-size` | 10 | Kuyruk dolunca bu kadar thread açılır |
| `queue-capacity` | 50 | Bekleyebilecek maksimum iş sayısı |

### Kaynak Tüketimi (tahmin)

| Kaynak | Build başına | 4 eş zamanlı |
|--------|-------------|--------------|
| RAM | ~200–400 MB | ~800 MB–1.6 GB |
| CPU | 1–2 çekirdek | 4–8 çekirdek |
| Disk (temp) | ~50–200 MB | ~800 MB |

> Disk temizliği: Her 30 dakikada bir, 60 dakikadan eski tamamlanmış/başarısız işler silinir.

---

## 12. Hata Yönetimi

### Build Hataları

Maven build başarısız olursa `build.log`'un son 3000 karakteri exception mesajına dahil edilir. Status API'si bu mesajı frontend'e iletir.

### Bilinen WSDL Sorunları ve Çözümleri

| Hata | Neden | Çözüm |
|------|-------|-------|
| `A class with the same name is already in use` | WSDL'de aynı isimli iki tip tanımı var | `-autoNameResolution` flag'i otomatik devrededir |
| `package jakarta.xml.bind does not exist` | CXF 4.x üretimi Java 8/11 ile derleniyor | `CodeGenService` otomatik `jakarta→javax` dönüşümü yapar |
| `Could not resolve dependencies` | Bağımlılık artifact'ı Central'da yok | `pom.xml`'de groupId/artifactId'yi kontrol edin |
| Path boşluk sorunu (Windows) | CXF fork'lu JVM path'i bozuyor | `cxf-codegen-plugin` için `<fork>false</fork>` uygulandı |
