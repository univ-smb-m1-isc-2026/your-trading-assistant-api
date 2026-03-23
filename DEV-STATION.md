## Fichier sur la dev station

### Start de la dev station
`$ ./mvnw clean package -DskipTests && docker compose -f dev-station/docker-compose.yml up --build`


### RESET 
1.  Stopper les conteneurs (si ce n'est pas déjà fait) :
        docker compose -f dev-station/docker-compose.yml down
    
2.  Supprimer le dossier de données local avec sudo (car il appartient au user postgres du conteneur) :
        sudo rm -rf dev-station/postgres-data/
        (C'est cette étape cruciale qui va vraiment vider la base de données).
3.  Relancer le docker-compose :
        docker compose -f dev-station/docker-compose.yml up --build