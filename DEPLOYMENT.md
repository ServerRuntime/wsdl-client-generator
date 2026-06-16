# Linux / CentOS Sunucuda Kurulum

## 1. Gereksinimler

### Java 17 Kur

```bash
# CentOS / RHEL
sudo yum install -y java-17-openjdk-devel

# Ubuntu / Debian
sudo apt install -y openjdk-17-jdk

# Versiyon teyidi
java -version
```

### Maven Kur

```bash
# CentOS / RHEL
sudo yum install -y maven

# Ubuntu / Debian
sudo apt install -y maven

# Versiyon teyidi
mvn -version
```

> Maven bulunamazsa manuel kurulum:
> ```bash
> cd /opt
> sudo wget https://downloads.apache.org/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.tar.gz
> sudo tar -xzf apache-maven-3.9.6-bin.tar.gz
> sudo ln -s /opt/apache-maven-3.9.6 /opt/maven
> echo 'export PATH=/opt/maven/bin:$PATH' | sudo tee /etc/profile.d/maven.sh
> source /etc/profile.d/maven.sh
> ```

---

## 2. Uygulamayı Hazırla

### JAR'ı Derle (geliştirme makinesinde)

```bash
mvn package -DskipTests
```

Çıktı: `target/wsdl-client-generator-1.0.0.jar`

### JAR'ı Sunucuya Kopyala

```bash
scp target/wsdl-client-generator-1.0.0.jar kullanici@sunucu-ip:/opt/wsdl-generator/
```

---

## 3. application.yml Yapılandır

Sunucuda `/opt/wsdl-generator/application.yml` oluşturun:

```yaml
app:
  maven-executable: mvn              # Linux'ta mvn.cmd değil mvn
  temp-dir: /tmp/wsdl-generator
  job-ttl-minutes: 60
  async:
    core-pool-size: 4
    max-pool-size: 10
    queue-capacity: 50

server:
  port: 8080
```

> Spring Boot, JAR'ın yanındaki `application.yml`'yi otomatik okur.

---

## 4. Systemd Servis Olarak Çalıştır

### Servis Kullanıcısı Oluştur

```bash
sudo useradd -r -s /bin/false wsdlgen
sudo mkdir -p /opt/wsdl-generator
sudo chown wsdlgen:wsdlgen /opt/wsdl-generator
```

### Servis Dosyası Oluştur

```bash
sudo nano /etc/systemd/system/wsdl-generator.service
```

```ini
[Unit]
Description=WSDL Client Generator
After=network.target

[Service]
Type=simple
User=wsdlgen
WorkingDirectory=/opt/wsdl-generator
ExecStart=/usr/bin/java -jar /opt/wsdl-generator/wsdl-client-generator-1.0.0.jar
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal
Environment="JAVA_HOME=/usr/lib/jvm/java-17-openjdk"

[Install]
WantedBy=multi-user.target
```

### Servisi Başlat

```bash
sudo systemctl daemon-reload
sudo systemctl enable wsdl-generator
sudo systemctl start wsdl-generator

# Durum kontrol
sudo systemctl status wsdl-generator

# Loglar
sudo journalctl -u wsdl-generator -f
```

---

## 5. Güvenlik Duvarı

```bash
# firewalld (CentOS/RHEL)
sudo firewall-cmd --permanent --add-port=8080/tcp
sudo firewall-cmd --reload

# ufw (Ubuntu)
sudo ufw allow 8080/tcp
```

---

## 6. Nginx ile Reverse Proxy (Opsiyonel)

Uygulamayı `http://sunucu-ip` adresinde yayınlamak için:

```bash
# CentOS
sudo yum install -y nginx

# Ubuntu
sudo apt install -y nginx
```

`/etc/nginx/conf.d/wsdl-generator.conf`:

```nginx
server {
    listen 80;
    server_name sunucu-ip-veya-domain;

    client_max_body_size 50M;

    location / {
        proxy_pass         http://127.0.0.1:8080;
        proxy_set_header   Host $host;
        proxy_set_header   X-Real-IP $remote_addr;
        proxy_set_header   X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_read_timeout 600s;
        proxy_send_timeout 600s;
    }
}
```

```bash
sudo nginx -t
sudo systemctl enable nginx
sudo systemctl start nginx
```

> `proxy_read_timeout 600s` önemli — Maven build 1-3 dakika sürebilir,
> varsayılan 60s timeout build bitmeden bağlantıyı keser.

---

## 7. Güncelleme

Yeni JAR yüklenince:

```bash
scp wsdl-client-generator-1.0.0.jar kullanici@sunucu:/opt/wsdl-generator/
sudo systemctl restart wsdl-generator
```

---

## 8. Log Takibi

```bash
# Canlı log
sudo journalctl -u wsdl-generator -f

# Son 100 satır
sudo journalctl -u wsdl-generator -n 100

# Belirli tarihten itibaren
sudo journalctl -u wsdl-generator --since "2026-01-01 00:00:00"
```
