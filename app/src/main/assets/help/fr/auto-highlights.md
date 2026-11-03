# Comment fonctionnent les temps forts automatiques

## Qu’est-ce qu’un temps fort ?

Un temps fort marque un moment intéressant dans un enregistrement vidéo. Il permet de le retrouver plus tard sans regarder toute la vidéo.

AeroCam peut ajouter un temps fort lorsqu’un événement se produit pendant ta sortie. Cet événement est appelé **déclencheur**.

## Déclencheurs disponibles

- **Marqueur de tour :** Un nouveau tour commence.
- **Pic de puissance :** Ta puissance dépasse le seuil choisi.
- **Vitesse maximale :** Ta vitesse dépasse le seuil choisi.
- **Sommet de montée :** Le Karoo signale la fin d’une montée.
- **Pic de fréquence cardiaque :** Ta fréquence cardiaque dépasse le seuil choisi.
- **Marqueur de montée :** Les seuils choisis de vitesse en montée et de pente positive sont tous les deux atteints.
- **Marqueur de descente :** Les seuils choisis de vitesse en descente et de pente négative sont tous les deux atteints.
- **Marqueurs de début et de fin :** Ta sortie commence ou se termine.

Seuls les déclencheurs activés dans les réglages sont utilisés.

## Seuils de montée et de descente

Active la montée, la descente ou les deux indépendamment. Chaque direction possède ses propres seuils de vitesse et de pente, et **les deux seuils doivent être atteints**.

Utilise une **pente positive en montée** et une **pente négative en descente**. Par exemple, en montée, **20 km/h** et **+5%** signifient au moins 20 km/h sur une pente montante de 5% ou plus. En descente, **60 km/h** et **−6%** signifient au moins 60 km/h sur une pente descendante de 6% ou plus.

Les valeurs de descente existantes restent **50 km/h** et **−3%** ; le déclencheur reste désactivé tant que tu ne l’actives pas. Le nouveau déclencheur de montée est lui aussi désactivé au départ, avec **20 km/h** et **+3%** comme valeurs initiales. De petites fluctuations autour d’un seuil ne créent pas de temps forts répétés.

## Lorsqu’un enregistrement est en cours

Lorsqu’un déclencheur se produit pendant un enregistrement, AeroCam envoie à la caméra une commande d’ajout de temps fort. Les champs de données Video et Cam Control indiquent brièvement le déclencheur détecté.

## Démarrage automatique de l’enregistrement

Normalement, les temps forts automatiques ne sont ajoutés que lorsqu’un enregistrement est déjà en cours.

Active **Démarrer automatiquement l’enregistrement** si tu veux qu’un déclencheur démarre un enregistrement lorsque la caméra n’enregistre pas.

Le premier déclencheur démarre l’enregistrement, mais n’est **pas** ajouté comme temps fort. Les déclencheurs suivants peuvent ajouter des temps forts à cet enregistrement.

Les commandes de la caméra restent ainsi dans un ordre sûr, sans envoyer Démarrer l’enregistrement et Ajouter un temps fort presque simultanément.

## Choisir les seuils

Choisis des valeurs correspondant à des moments que tu souhaites retrouver. Un seuil trop facile à atteindre peut produire trop de temps forts.

Commence par une valeur exigeante et ajuste-la après quelques sorties.
