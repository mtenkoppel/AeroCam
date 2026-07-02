# Fonctionnement des temps forts automatiques

## Qu’est-ce qu’un temps fort ?

Un temps fort marque un moment intéressant dans un enregistrement et permet de le retrouver sans revoir toute la vidéo.

AeroCam peut l’ajouter lorsqu’un événement survient pendant la sortie. Cet événement est appelé **déclencheur**.

## Déclencheurs disponibles

- **Tour :** Un nouveau tour commence.
- **Puissance maximale :** Votre puissance dépasse le seuil choisi.
- **Vitesse maximale :** Votre vitesse dépasse le seuil choisi.
- **Sommet :** Karoo signale la fin d’une montée.
- **Fréquence cardiaque maximale :** Votre fréquence dépasse le seuil choisi.
- **Descente rapide :** AeroCam détecte une descente rapide.
- **Début/fin de sortie :** Votre sortie commence ou se termine.

Seuls les déclencheurs activés dans Réglages sont utilisés.

## Pendant un enregistrement

Lorsqu’un déclencheur survient, AeroCam envoie une commande de temps fort. Les champs Vidéo et Commande caméra affichent brièvement le déclencheur détecté.

## Démarrage automatique

Par défaut, les temps forts automatiques ne fonctionnent que lorsqu’un enregistrement est déjà actif.

Activez **Démarrer automatiquement l’enregistrement** pour qu’un déclencheur démarre un enregistrement lorsque la caméra est inactive.

Le premier déclencheur lance l’enregistrement mais n’est **pas** ajouté comme temps fort. Les suivants peuvent l’être.

Cela conserve un ordre sûr et évite d’envoyer deux commandes presque simultanément.

## Choisir les seuils

Choisissez des valeurs qui correspondent à des moments que vous souhaitez vraiment retrouver. Un seuil trop facile peut créer trop de temps forts.

Commencez avec une valeur exigeante et ajustez-la après quelques sorties.
