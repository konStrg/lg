# classe de personnage
- les joueurs sont assignés à une classe de personnage (1er, 2eme, 3eme), position de chambre individuelle 

# roles
## verts
 - voyageur
   - role de base, pas de pouvoir, peut voter et participer aux events
 - medecin legiste
   - peut soigner le joueur mort du cannibale, 1 utilisation. son pouvoir doit intervenir apres la mort du joueur
 - baricadeur
   - peut barricader une chambre pour proteger du cannibale ou un wagon entier, ne peut pas barricader deux nuits de suite
 - radio amateur
   - peut ecouter/regarder une chambre par nuit de cannibale et que pendant la traque des cannibales
 - veteran
   - peut se defendre pendant la nuit du cannibale avec une arme plus faible que celle des cannibales
 - kamikaze 
   - peut se sacrifier pour tuer un cannibale, mais meurt dans le processus
 - cupidon
   - peut assigner deux joueurs pour qu ils s aiment, si l un meurt l autre meurt aussi. 
   - le second conjoind apres la mort du premier mort, il se pendra juste avant le vote du jour suivant.

## rouges
 - cannibale
   - durant la nuit, ils debatent pour la victime du cannibale, le cannibale peut choisir de ne pas manger un joueur lorsqu un joueur est assigne pour mourrir, un des cannibales est assignes pour le tuer. Les autres cannibales rentre dans leur chambre. La cannible recoit une arme pour aller tuer la victime. La chambre de la victime s ouvre, cela ressembre a tom et jerry mais la victime dois mourrir s il est ne possede pas la capacite de se defendre. La nuit dure tant que la victime n est pas morte
## gris
 - pyromane
   - peut tremper un wagon par nuit, apres 3 wagons trempes, il peut allumer un feu et gagner seul
 - ange
    - doit mourir au premier vote des joueurs
# chronologie
- les jours impairs sont des jours de vote
- les nuits impaires sont les nuits pour les pouvoirs verts
- les jours pairs sont les jours d event
- les nuits paires sont les nuit des pouvoirs rouges

## init
- un gris obligatoirement
- 30%  1 rouge/vert
- 15%  2 rouge/vert
- 1%  3 rouge/vert
  - ici je parle de vert avec pouvoir

## jour 1 
- vote le capitaine

## nuit 1
- role des verts seulement

## jour 2
- event qui assigne un role si reussit rouge/vert peut reassigner un role villageois si le joueur est deja villageois
  - definir probabilite d assigner rouge/vert dependement du nombre de joueurs restants et du nombre de roles rouges/verts deja assignes

## nuit 2
- role des rouges seulement
  - le cannibale peut manger un joueur de choix s il le souhaite, sinon il peut choisir de ne pas manger.
- lorsque le cannibale decide du joueur a tueur cela donne lui a la traque de la victime, le cannibale recoit une arme pour tuer la victime. La chambre de la victime s ouvre, cela ressembre a tom et jerry mais la victime dois mourrir s il est ne possede pas la capacite de se defendre. La nuit dure tant que la victime n est pas morte

## jour 3 
- conseille, on decide de faire un vote pour in joueur.
  - le joueur peut etre assigne dans sa chambre ou expulsé du train.
  - le joueur assigne dans sa chambre ne peut participer au jour et nuit pair suivante et la nuit impaire suivante. Il peut participer au jour et nuit impaire suivante 

## etc