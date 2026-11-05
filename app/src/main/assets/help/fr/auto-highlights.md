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
- **Marqueur de montée:** Se déclenche à la pente de montée choisie, avec une vitesse minimale facultative.
- **Marqueur de descente:** Se déclenche à la pente de descente choisie, avec une vitesse minimale facultative.
- **Repères de début/fin de sortie:** Marque le début et la fin de la sortie uniquement si la caméra enregistre déjà. Les pauses et reprises n’ajoutent aucun repère. Ne démarre jamais un enregistrement.

Seuls les déclencheurs activés dans les réglages sont utilisés.

## Seuils de montée et de descente

Active la montée, la descente ou les deux séparément. Utilise une pente positive en montée et négative en descente. Désactive **Exiger aussi une vitesse** pour utiliser uniquement la pente. Sinon, les deux seuils doivent être atteints.

Par exemple, **+10%** sans condition de vitesse marque une montée raide à toute allure. **+5%** et **30 km/h** marquent une montée rapide. Le même choix existe en descente avec une pente négative. Les petites fluctuations ne créent pas de marqueurs répétés.

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
