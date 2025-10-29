# Piknik

Aplikace pro PC na pracovišti PIK (DP Most) pro ovládání termotiskárny Epson TM-T20III, VFD displeje VIRTUOS FV-2030B 
a čtečky čipových karet Ingenico OPEN1500.

## 📋 Stručný návod pro kontributory

Aplikace je napsána v jazyku Java verze 21 a provozována na OpenJDK/JRE 25 LTS. JRE (Adoptium Temurin JRE 25) se nemusí 
instalovat, je přibalena v distribučním balíku spolu s aplikací.

Pro vývoj bylo použito prostředí IntelliJ IDEA (doporučeno), ale lze použít i jiné, jako např. Eclipse nebo NetBeans.
Pro vývoj a testování použit Kubuntu Linux 25.04 a deployment platforma byly Windows 11 Pro. 

### &#9881; Distribuce a provoz

Ditribuce probíhá formou GZ (pro Linux) a ZIP (pro Windows) archivů.

**Proces instalace zahrnuje 2 kroky:**

1. Instalace služby nebo ovladače Epson dle způsobu připojení tiskárny 
   1. U síťové (Ethernet) tiskárny je třeba nainstalovat ovladače Epson spuštěním (jako Administrátor !!!) souboru `installJavaPOSFull-64.bat` z instalačníko balíku `Epson_JavaPOS_ADK_11438_x64.zip`. Tímto se zároveň nainstaluje služba PCSSV, bez které síťová tiskárna nefunguje!
   2. U tiskárny připojené pomocí VCP/USB je třeba nainstalovat ovladač Epson Virtual Com Port `TMVirtualPortDriver870d for Secure Printing.exe` a následně utilitou `EPSON Virtual Com Port Driver Com Port Asignment Tool` asociovat COM port pro tiskárnu.
   3. Společné - dle typu připojení tiskárny je třeba mít v adresáři `/config` správně vygenerovaný konfigurák `jpos.xml` pro Epson JavaPOS API. Bez něj bude tiskárna pro aplikaci nedostupná !!!  
2. Rozbalení archivu s aplikací Piknik na vhodném místě na disku. Spuštění aplikace se provede pomocí skriptu "start.sh" (pro Linux) nebo "start.bat" (pro Windows). Tento skript by se měl zavést do systému, aby startoval automatisky jako service.

### 🗜 Struktura distribučního balíku

```
piknik
├── config
│   ├── application.properties    <- konfigurace aplikace Piknik
│   ├── jpos.xml                  <- konfigurace Epson JavaPOS
│   └── logback.xml               <- konfigurace logování aplikace Piknik
├── jre
│   ├── bin                       <-
│   ├── lib                       <- Java run-time environment pro běh aplikace
|   ├── ...                       <-
├── logs                          <- adresář s logy aplikace
├── res                           <- resources pro aplikaci
│   ├── bin                       <- nativní knihovny .so/.dll pro Linux/Windows
│   ├── html                      <- statické stránky
│   └── lib                       <- Epson JavaPOS ADK 
├── piknik-1.0-deps.jar           <- aplikace Piknik
├── start_debug.sh                <- spouštěcí skript pro vzdálené ladění
└── start.sh                      <- provozní spouštěcí skript
```

### &#8690; Priorita konfigurací

Konfiguraci aplikace Piknik lze provést několika způsoby, přičemž jejich priorita (od nejvyšší po nejnižší) včetně 
ukázek je tato:

    1. Command line:            java -Dprinter.ip=10.0.0.200 -cp "<CLASSPATH>" pik.Piknik
    2. Environment Lin/Win:     export PRINTER_IP=10.0.0.200 / set PRINTER_IP=10.0.0.200
    3. External file:           config/application.properties (next to JAR)
    4. Classpath:               config/application.properties (in JAR)
    5. Code defaults (class):   PrinterConstants, ServerConstants

#### 1. application.properties

	printer.name=Epson_TM_T20III                    <- logické jméno tiskárny (musí být stejné jako v jpos.xml !!!!!)
	printer.ip=10.0.0.150                           <- IP adresa tiskárny
	printer.port=9100                               <- komunikační port, 9100 je standard, jinak se musí použít konfig. nástroj Epson
	printer.connection.timeout=10000                <- timeout při nečinnosti
	
	vfd.type=FV_2030B                               <- logické jméno VFD displeje
	vfd.port=COM13                                  <- COM port (pro Linux použít např. /dev/ttyUSB13)
	vfd.baud=9600                                   <- rychlost komunikace přes VCP/USB
	
	server.port=8080                                <- port, na kterém je vystaveno REST API pro klienty
	server.host=127.0.0.1                           <- omezení přístupu k REST API: 127.0.0.1 - pouze lokální, 0.0.0.0. - všichni
	server.thread.pool=3                            <- počet vláken background jobů, inicializovaných při startu aplikace
	
	# STRICT - all services must initialize (production)
	# LENIENT - at least one service must initialize (default)
	# PERMISSIVE - application starts regardless (testing/demo)
	server.startup.mode=LENIENT                     <- režim startu aplikace, viz komentáře
	
	monitor.status.interval=5000                    <- heartbeat [ms] pro kontrolu stavu periferií
	monitor.enabled=true                            <- povolení/zákaz backgroud tasku pro monitoring stavu
	

#### 2. jpos.xml
Extrémně citlivý konfigurační soubor s popisem parametrů připojené tiskárny. Obsah souboru je nutné změnit zejména v těchto případech:

* Změna připojení tiskárny VCP/USB versus Ethernet.
* Změna IP adresy síťové tiskárny.
* Změna logického jména tiskárny.

Změny lze provést ručně nebo lze vygenerovat nový XML soubor pomocí utility "SetupPOS", která je součástí Epson JavaPOS ADK 
pro Linux a Windows.

### 🔨 Build aplikace a distribučních balíčků
Aplikace si závislosti dotahuje z veřejné Maven CENTRAL repository až na 1 výjimku a to Epson JavaPOS ADK, které se musí 
ručně stáhnout a linkovat jako externí knihovny.
Dalším krokem je vygenerování instalačních balíčků s platformě závislými knihovnami pomocí Maven ve fázi sestavení "package".

Aby oba výše popsané kroky proběhly úspěšně, doporučuji mít připravenou níže popsanou stromovou strukturu před spuštěním 
fáze sestavení "compile" nebo "package". Pokud je požadována jiná struktura adresářů je nutno změnit konfigurační soubory 
pro Maven: `pom.xml`, `distribution-linux.xml` a `distribution-windows.xml`.

**1. Adresářový strom pro build projektu**

	.
	├── Java
	│   ├── linux                         <- rozbalené JRE pro Linux
	│   │   ├── bin
	│   │   ├── lib
	│   │   ├── ...
	│   │   └── ...
	│   └── win                           <- rozbalené JRE pro Windows
	│       ├── bin
	│       ├── lib
	│       ├── ...
	│       └── ...
	├── JavaPOS
	│   ├── bin
	│   │   ├── linux                     <- nativní JavaPOS knihovny pro Linux
	│   │   │   ├── libepsonjpos.so
	│   │   │   ├── libethernetio31.so
	│   │   │   ├── libserialio31.so
	│   │   │   └── libusbio31.so
	│   │   └── win                       <- nativní JavaPOS knihovny pro Windows
	│   │       ├── BluetoothIO.DLL
	│   │       ├── epsonjpos.dll
	│   │       ├── EthernetIO31.DLL
	│   │       ├── jSerialComm.dll
	│   │       ├── SerialIO31.dll
	│   │       └── USBIO31.DLL
	│   └── lib                           <- Epson JavaPOS implementace
	│       ├── epsonjpos.jar
	│       ├── jpos1141.jar
	│       ├── xercesImpl.jar
	│       └── xml-apis.jar
	└── Piknik
	    ├── src
	    │   ├── assembly                  <- build scripty pro Maven
	    │   ├── main                      <- Java zdrojáky aplikace a další věcičky
	    │   │   ├── java
	    │   │   ├── resources
	    │   │   └── scripts
	    │   └── test                      <- Java Unit testy
	    │       └── java
	    ├── pom.xml                       <- hlavní build script pro Maven
	    └── README.md                     <- to, co je na obrazovce...
	
Postup přípravy:

* Rozbalit a nakopírovat JRE pro příslušnou platformu do správného podaresáře "Java/linux" nebo "Java/win".
* Nakopírovat JAR knihovny z instalace Epson JavaPOS ADK do "JavaPos/lib".
* Nakopírovat nativní knihovny z instalace Epson JavaPOS ADK do správného adresáře "JavaPOS/bin/linux" nebo "JavaPOS/bin/win".

**2. Očekávaný výsledek build procesu:**

    piknik-1.0.jar               <- build aplikace
    piknik-1.0-deps.jar          <- build aplikace se závislostmi
    piknik-1.0-linux.tar.gz      <- distribuční balíček pro Linux
    piknik-1.0-windows.zip       <- distribuční balíček pro Windows

A to je vše... 👍  

---
