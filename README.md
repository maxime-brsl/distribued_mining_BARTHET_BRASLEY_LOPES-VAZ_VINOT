Pour les commits, les filtres ne marchent pas, julie et mathieu n'apparaissent pas. Il faut regarder directement le commit et voir son contributeur, nous en sommes désolé.
# Projet Réseau - Minage distribué

## Description

Ce projet Java consiste en la recherche d'un hash spécifique en utilisant un serveur et plusieurs clients (workers).\
Pour cela, on utilise uniquement ce que la JDK 21 fournit, sans aucune dépendance externe, ainsi qu'une webapp fournie.\
La webapp dispose de deux webservices : `generate_work` et `validate_work`, le premier récupère le travail et la seconde vérifie le hash.\
Nous avons une interface en ligne de commande pour interagir avec le serveur qui se chargera de dialoguer avec les workers et la webapp.\
Une fois un travail récupéré par le serveur, il est distribué aux workers disponible pour commencer la recherche.\
La recherche fonctionne en brute force, c'est-à-dire que chaque worker va tester des combinaisons de hash jusqu'à trouver le bon.\
Lorsqu'un worker trouve le hash, il le transmet au serveur qui le fait valider par l'API. Les autres workers s'arrêtent et redeviennent disponible.\

## Comment lancer le projet ?

Il faut dans un premier temps lancer le serveur avant de lancer les workers.
### Serveur
Ouvrez un terminal et placez-vous dans le répertoire du LauncherServeur, puis exécutez la commande suivante : `java -jar LauncherServer.jar`.\
Le serveur est maintenant lancé et écoute les connexions des workers sur le port 1337.\
Si lors du lancement, vous avez un souci, c'est probablement, car votre port 1337 est déjà utilisé.\
Dans ce cas, vous pouvez modifier le port dans le fichier `server/src/main/java/Server.java` à la ligne 15 ou bien, tuer le processus qui utilise le port 1337.
Dès qu'un worker se connecte au serveur, le protocole de communication commence.\
Après l'identification du worker faite, vous pouvez entrer des commandes dans le terminal pour interagir avec le serveur.
### Worker
Ouvrez un terminal et placez-vous dans le répertoire de la classe Worker.java, puis exécutez la commande suivante : `java -jar Worker.jar`.\
Une fois le worker lancé, il se connecte au serveur et attend des tâches à effectuer.\
Vous pouvez lancer plusieurs workers pour accélérer la recherche du hash.

## Utilisation
Une fois le serveur et les workers lancés, vous pouvez interagir avec le serveur via le terminal.\
Voici les commandes disponibles :
- `solve <d>` : Démarre la recherche du hash pour la difficulté `d` pour tous les workers disponible.
- `cancel` : Annule la recherche en cours sur tous les workers.  
- `status` : Affiche l'état des workers.
- `exit` : Arrête le serveur
- `help` : Affiche les commandes disponibles

## Auteurs
- [Julie Barthet]()
- [Maxime Brasley]()
- [Alexis Lopes Vaz]()
- [Mathieu Vinot]()
