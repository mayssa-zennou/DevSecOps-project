# ADR-0002 : Idempotence et gestion des doublons dans la saga

## Statut
Accepté

## Contexte

Kafka garantit une livraison au moins une fois : un message peut être livré
plusieurs fois (crash d'un consommateur avant le commit de l'offset, envoi
réussi suivi d'un échec du commit en base, remise à zéro volontaire de
l'offset). Les trois services réagissent à des événements, donc chacun doit
rester correct quand le même événement arrive deux fois. Leurs effets n'ont
pas la même nature : réserver du stock n'est pas idempotent par lui-même,
alors que changer l'état d'une commande peut l'être.

## Décision

Chaque service applique le mécanisme le plus simple adapté à son effet :

1. **Inventory** enregistre l'`eventId` de chaque `order.created` traité dans
   une table `processed_events`, par une insertion `ON CONFLICT DO NOTHING`
   exécutée dans la **même transaction** que la réservation. Si l'insertion
   n'ajoute aucune ligne, l'événement est ignoré.
2. **Order** n'utilise pas de table : la transition vers `CONFIRMED` ou
   `REJECTED` n'est possible que depuis `PENDING`. Le premier résultat gagne,
   tout événement suivant (rejeu ou résultat contradictoire) est ignoré.
   Les messages d'une même commande portent la clé `orderId` et arrivent donc
   sur la même partition de leur topic, traités l'un après l'autre.
3. **Les événements dérivés** (`order.confirmed`, `order.rejected`) ont un
   `eventId` **déterministe** : un UUID v3 calculé à partir de
   `<topic>:<orderId>`. Une republication après un échec partiel porte donc le
   même identifiant, et **Notification** l'écarte grâce à la contrainte
   d'unicité sur `eventId` (un seul mail par commande).

## Conséquences

**Positives.** Un message rejoué ne double ni une réservation ni un mail.
Chaque mécanisme est testable isolément (voir le test de remise à zéro des
offsets). Order reste sans table supplémentaire.

**Limites acceptées.**
- Tant que l'envoi Kafka et le commit en base sont deux opérations séparées,
  un échec entre les deux peut produire un doublon : il est absorbé par les
  mécanismes ci-dessus, et c'est l'**outbox** qui supprimera la cause.
- L'`eventId` déterministe suppose qu'une commande émet au plus un événement
  par topic ; une étape ajoutant plusieurs événements par commande (paiement
  partiel, par exemple) imposera une autre dérivation.
- Les événements `stock.*` d'Inventory gardent un `eventId` aléatoire ; Order
  les absorbe par sa machine à états.
- Avec plusieurs instances d'Order, il faudra compléter par un champ
  `@Version` sur l'entité dès qu’une commande peut recevoir deux résultats 
  concurrents (threads ou instances).
- **L'idempotence par `eventId` ne protège pas contre un message forgé.**
  Un attaquant qui republie un événement avec un `eventId` neuf passe cette
  défense. Elle est couverte à une autre couche (TLS, authentification et
  ACL par topic, Phase 2), pas ici.

## Alternatives écartées

- **Table `processed_events` dans Order aussi** : redondante avec la machine
  à états, qui garantit déjà la même chose.
- **`eventId` aléatoire partout** : un doublon de publication produirait deux
  événements distincts et donc deux mails.
- **Livraison « exactement une fois » (transactions Kafka)** : plus complexe
  et sans bénéfice ici, puisque l'effet de chaque consommateur est rendu
  idempotent côté applicatif.