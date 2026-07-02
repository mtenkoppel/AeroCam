# Fonctionnement du maintien actif

## Pourquoi cette fonction existe

AeroCam ne peut pas rallumer de façon fiable une DJI Osmo Action complètement éteinte.

**Maintenir active** contourne cette limite. AeroCam envoie régulièrement une pression sur le bouton QS pour réinitialiser la minuterie d’arrêt automatique. La caméra reste connectée et prête, même lorsque ses écrans sont éteints.

> **Batterie :** Le maintien actif consomme beaucoup plus qu’une vraie veille.

## Configurer la caméra

1. Réglez **Single Screen Preview** sur **Screen off when locked** afin d’éteindre les écrans sans rendre la caméra indisponible.
2. Réglez **Auto Power Off** sur au moins **2 minutes**.
3. Connectez la caméra au Karoo, puis verrouillez son écran. Cela éteint les écrans et économise un peu d’énergie.
4. Désactivez les sons si vous ne voulez pas entendre un clic à chaque commande QS.

Les noms des menus peuvent varier légèrement selon le firmware.

## Pendant une sortie

Activez Maintenir active depuis Commande caméra. AeroCam envoie immédiatement une commande QS puis la répète selon l’intervalle choisi.

Si la connexion est perdue, AeroCam reprend après la reconnexion. Il attend brièvement que la caméra soit prête.

La fonction est suspendue pendant un enregistrement, car la même commande QS crée un temps fort. Elle reprend lorsque l’enregistrement s’arrête.

## Utilisation de la batterie

Lors d’un test, la batterie est passée de **100 % à 64 % en environ 1 heure et 56 minutes**. Il s’agit seulement d’un repère : la température, l’enregistrement, les écrans et l’état de la batterie influencent le résultat.

> **Important :** Le maintien actif empêche l’arrêt. Il ne peut pas rallumer une caméra déjà complètement éteinte.
