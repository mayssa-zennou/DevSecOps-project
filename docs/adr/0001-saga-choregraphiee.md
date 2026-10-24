# ADR-0001 : Saga chorégraphiée plutôt qu'orchestrée

## Statut
Accepté

## Contexte
Une commande doit réserver du stock dans un autre service. Une transaction
distribuée (2PC) est écartée : couplage fort, mauvaise disponibilité.
Il faut une cohérence éventuelle avec compensation en cas d'échec.

## Décision
Saga chorégraphiée : chaque service publie des événements
(OrderCreated, StockReserved, StockRejected) et réagit à ceux des autres.

## Conséquences
+ Pas de point central unique, couplage faible, simple à 3 services
- Le flux global est moins lisible (réparti entre les services)
- Le débogage exige du traçage distribué (voir phase 3)
- À réévaluer vers une orchestration si le nombre d'étapes grossit