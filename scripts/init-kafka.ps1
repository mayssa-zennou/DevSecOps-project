# scripts/init-kafka.ps1  (à lancer depuis la racine, Kafka déjà démarré)

# ---- group-id réels : à vérifier avec Select-String ... "group-id|groupId" ----
$GroupOrder        = "order-service"
$GroupInventory    = "inventory-service"
$GroupNotification = "notification-group"
# ------------------------------------------------------------------------------

function Fail($m) { Write-Host "ERREUR : $m" -ForegroundColor Red; exit 1 }
function Step($m) { Write-Host "`n== $m ==" -ForegroundColor Cyan }

if (-not (Test-Path .env)) { Fail ".env introuvable (lancez depuis la racine)" }

$e = @{}
Get-Content .env | ForEach-Object {
  if ($_ -match '^\s*([^#=\s]+)\s*=(.*)$') { $e[$matches[1]] = $matches[2].Trim() }
}
foreach ($n in "KAFKA_ADMIN_PASSWORD","KAFKA_SSL_PASSWORD","KAFKA_ORDER_PASSWORD","KAFKA_NOTIFICATION_PASSWORD","KAFKA_STOCK_PASSWORD") {
  if (-not $e[$n]) { Fail "variable $n absente de .env" }
}

$K = "/opt/kafka/bin"
$B = @("--bootstrap-server","localhost:9092","--command-config","/tmp/admin.properties")

# ---- Connexion admin (fichier écrit dans le conteneur uniquement) ----
Step "Connexion admin"
$cfg = @"
security.protocol=SASL_SSL
sasl.mechanism=SCRAM-SHA-512
sasl.jaas.config=org.apache.kafka.common.security.scram.ScramLoginModule required username="admin" password="$($e["KAFKA_ADMIN_PASSWORD"])";
ssl.truststore.location=/etc/kafka/secrets/client.truststore.p12
ssl.truststore.type=PKCS12
ssl.truststore.password=$($e["KAFKA_SSL_PASSWORD"])
"@ -replace "`r`n","`n"

$ready = $false
for ($i = 1; $i -le 40; $i++) {
  $cfg | docker exec -i kafka sh -c "cat > /tmp/admin.properties" 2>$null
  docker exec kafka "$K/kafka-topics.sh" @B --list 2>$null | Out-Null
  if ($LASTEXITCODE -eq 0) { $ready = $true; break }
  Write-Host "  attente de Kafka ($i/40)..."
  Start-Sleep -Seconds 5
}
if (-not $ready) { Fail "Kafka ne répond pas (docker logs kafka ; mot de passe admin ?)" }
Write-Host "  Kafka prêt"

# ---- Comptes ----
Step "Comptes SCRAM"
function NewUser($name, $pw) {
  docker exec kafka "$K/kafka-configs.sh" @B --alter --add-config "SCRAM-SHA-512=[iterations=8192,password=$pw]" `
    --entity-type users --entity-name $name | Out-Null
  Write-Host "  $name"
}
NewUser "order-service"        $e["KAFKA_ORDER_PASSWORD"]
NewUser "notification-service" $e["KAFKA_NOTIFICATION_PASSWORD"]
NewUser "inventory-service"    $e["KAFKA_STOCK_PASSWORD"]

# ---- Topics ----
Step "Topics"
foreach ($t in "order.created","stock.reserved","stock.rejected","order.confirmed","order.rejected") {
  docker exec kafka "$K/kafka-topics.sh" @B --create --if-not-exists --topic $t --partitions 1 --replication-factor 1 | Out-Null
  Write-Host "  $t"
}

# ---- ACL (tableau) ----
Step "ACL"
function Acl($user, $ops, $topic) {
  $a = @("--add","--allow-principal","User:$user")
  foreach ($o in $ops) { $a += @("--operation",$o) }
  $a += @("--topic",$topic)
  docker exec kafka "$K/kafka-acls.sh" @B @a | Out-Null
  Write-Host "  $user : $($ops -join '+') sur $topic"
}
function AclGroup($user, $group) {
  docker exec kafka "$K/kafka-acls.sh" @B --add --allow-principal "User:$user" --operation Read --group $group | Out-Null
  Write-Host "  $user : Read sur le groupe $group"
}

Acl "order-service" @("Write","Describe") "order.created"
Acl "order-service" @("Write","Describe") "order.confirmed"
Acl "order-service" @("Write","Describe") "order.rejected"
Acl "order-service" @("Read","Describe")  "stock.reserved"
Acl "order-service" @("Read","Describe")  "stock.rejected"
AclGroup "order-service" $GroupOrder

Acl "inventory-service" @("Read","Describe")  "order.created"
Acl "inventory-service" @("Write","Describe") "stock.reserved"
Acl "inventory-service" @("Write","Describe") "stock.rejected"
AclGroup "inventory-service" $GroupInventory

Acl "notification-service" @("Read","Describe") "order.confirmed"
Acl "notification-service" @("Read","Describe") "order.rejected"
AclGroup "notification-service" $GroupNotification

# ---- Nettoyage ----
docker exec kafka rm -f /tmp/admin.properties
Write-Host "`nTerminé." -ForegroundColor Green
