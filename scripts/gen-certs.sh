#!/bin/sh
set -e
: "${TLS_PASSWORD:?TLS_PASSWORD manquant}"

CA=/out/ca
BR=/out/kafka
SAN="dns:localhost,dns:kafka,ip:127.0.0.1"
mkdir -p "$CA" "$BR"

echo "== 1. Autorité de certification (CA) locale =="
keytool -genkeypair -alias ca -keyalg RSA -keysize 4096 \
  -dname "CN=DevSecOps Local CA,O=Projet DevSecOps" \
  -validity 1825 -ext bc:c \
  -keystore "$CA/ca.p12" -storetype PKCS12 -storepass "$TLS_PASSWORD"

keytool -exportcert -alias ca -rfc \
  -keystore "$CA/ca.p12" -storepass "$TLS_PASSWORD" -file "$CA/ca.crt"

echo "== 2. Clé du broker + demande de certificat (CSR) =="
keytool -genkeypair -alias kafka -keyalg RSA -keysize 2048 \
  -dname "CN=localhost,O=Projet DevSecOps" -ext "SAN=$SAN" \
  -validity 365 \
  -keystore "$BR/kafka.keystore.p12" -storetype PKCS12 -storepass "$TLS_PASSWORD"

keytool -certreq -alias kafka -ext "SAN=$SAN" \
  -keystore "$BR/kafka.keystore.p12" -storepass "$TLS_PASSWORD" \
  -file /tmp/kafka.csr

echo "== 3. La CA signe le certificat du broker =="
keytool -gencert -alias ca -ext "SAN=$SAN" -ext eku=serverAuth \
  -validity 365 -rfc \
  -keystore "$CA/ca.p12" -storepass "$TLS_PASSWORD" \
  -infile /tmp/kafka.csr -outfile /tmp/kafka.crt

echo "== 4. Import dans le keystore du broker (CA d'abord, puis certificat) =="
keytool -importcert -noprompt -alias ca -file "$CA/ca.crt" \
  -keystore "$BR/kafka.keystore.p12" -storepass "$TLS_PASSWORD"
keytool -importcert -noprompt -alias kafka -file /tmp/kafka.crt \
  -keystore "$BR/kafka.keystore.p12" -storepass "$TLS_PASSWORD"

echo "== 5. Truststore (les CA auxquelles on fait confiance) =="
keytool -importcert -noprompt -alias ca -file "$CA/ca.crt" \
  -keystore "$BR/client.truststore.p12" -storetype PKCS12 \
  -storepass "$TLS_PASSWORD"

echo "== Terminé =="