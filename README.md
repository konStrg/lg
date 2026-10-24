# Transperceneige - Loup-Garou pour Minecraft (Paper)

Plugin Paper pour Minecraft Java 26.3 : un loup-garou dans un train. Cabines par classe sociale, wagons, traques de nuit, evenements de jour, votes, interfaces en inventaire. Compatible (optionnel) avec le mod **Simple Voice Chat**.

> Les regles viennent de `bite.md`. Le plugin n'a pas encore ete valide en partie complete sur un serveur reel (il charge et demarre correctement sur Paper 26.3).

## Prerequis

- Serveur Paper `26.3` (build 222 ou plus recent), Java 25
- Optionnel : plugin serveur Simple Voice Chat + mod client pour les joueurs
- Minimum 5 joueurs

## Compilation

```
mvn package
```

Le jar est genere dans `target/Transperceneige-0.1.0.jar` ; copiez-le dans `plugins/`.

## Preparation de l'arene (une seule arene)

Construisez le train, puis, avec la permission `lg.admin`, placez-vous aux bons endroits :

| Commande | Role |
|---|---|
| `/lg setup lobby` | Point d'attente |
| `/lg setup council` | Salle du conseil (votes) |
| `/lg setup debate` | Lieu du debat des cannibales |
| `/lg setup eject` | Point d'ejection du train |
| `/lg setup pos1` puis `pos2` puis `/lg setup wagon <nom>` | Definit un wagon (zone cuboide) |
| `/lg setup cabin <FIRST\|SECOND\|THIRD>` | Ajoute une cabine pour une classe (une par joueur) |
| `/lg setup clearcabins` | Efface les cabines |
| `/lg setup status` | Verifie la configuration |

La configuration est sauvegardee dans `arena.yml`.

## Commandes

| Commande | Description |
|---|---|
| `/lg join` / `/lg leave` | Rejoindre / quitter la partie |
| `/lg start` | Lance le decompte (admin) |
| `/lg stop` | Arrete la partie (admin) |
| `/lg menu` | Rouvre le menu d'action en attente |
| `/lg role` | Rappelle votre role et votre classe |

Alias : `/loupgarou`, `/transperceneige`. Permissions : `lg.play` (par defaut), `lg.admin`.

## Deroulement

- Jour impair = vote ; jour pair = evenement. Nuit impaire = pouvoirs verts ; nuit paire = pouvoirs rouges.
- **Jour 1** : election du Capitaine (sa voix compte double ; il choisit expulsion ou confinement, au hasard sans capitaine).
- **Nuit 1** : pouvoirs verts uniquement.
- **Jour 2** : evenement (code a saisir) ; les gagnants Voyageurs peuvent recevoir un nouveau role.
- **Nuit 2** : les Cannibales debattent en prive et choisissent une victime ou s'abstiennent. Un cannibale recoit une arme et traque la victime ; la nuit dure jusqu'a la mort ou le delai max.
- **Jour 3+** : vote du conseil, le joueur est confine dans sa cabine (nuit N, jour N+1, nuit N+1) ou ejecte.

## Roles

**Verts** : Voyageur, Medecin legiste (ressuscite la victime de la traque, 1 fois), Barricadeur (cabine ou wagon, pas deux nuits de suite), Radio-amateur (ecoute une cabine pendant la traque), Veteran (arme plus faible), Kamikaze (se sacrifie pour tuer un Cannibale), Cupidon (lie deux amoureux).

**Rouges** : Cannibale.

**Gris** : Pyromane (trempe un wagon par nuit **verte** ; apres 3 wagons, il peut allumer le feu et gagne seul), Ange (gagne seul s'il est elimine au premier vote).

## Conditions de victoire

- Les Verts gagnent ensemble quand tous les Rouges sont morts.
- Les Rouges gagnent ensemble uniquement quand tous les non-Rouges (Verts et Gris) sont morts.
- Pyromane et Ange gagnent seuls.

## Configuration (`config.yml`, `messages.yml`)

- `game` : joueurs minimum, decompte, rayon des cabines
- `durations` : durees des votes, de l'evenement, des nuits et de la traque (`hunt-max`)
- `roles.initial-chance` : probabilites d'avoir 1/2/3 roles a pouvoir au depart ; `guarantee-initial-red`
- `events` : ratios de l'evenement du jour 2
- `hunt` : armes du chasseur et du veteran
- `effects` : heure, orage, obscurite de nuit
- `voice` : activation et distance de proximite

Tous les textes sont dans `messages.yml` (francais).

## Simple Voice Chat

Si le plugin Voice Chat est present, le plugin route la voix : seul votre cabine ou wagon vous entend, la nuit seul le groupe de debat des Cannibales est audible, et les morts n'entendent / ne sont entendus qu'entre eux. Sans Voice Chat, le plugin fonctionne normalement (`softdepend`).

## Limites connues

- Verrouillage des cabines par rayon de deplacement, pas par portes.
- Les inventaires sont restaures en memoire seulement (pas apres une deconnexion pendant l'arret de partie).
- Le routage vocal lit l'etat du jeu depuis le thread vocal sans synchronisation.
