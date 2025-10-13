# Piknik

Aplikace pro PC na pracovišti PIK.



**📋 Priorita konfigurací (od nejvyšší po nejnižší):**

    1. Command line:    java -Dprinter.ip=10.0.0.200 -jar piknik.jar
    2. Environment:     export PRINTER_IP=10.0.0.200
    3. External file:   config/application.properties (next to JAR)
    4. Classpath:       application.properties (in JAR)
    5. Code defaults:   PrinterConstants, ServerConstants

