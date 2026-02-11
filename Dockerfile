FROM eclipse-temurin:21-jre-alpine
# On définit une variable pour le nom du JAR pour plus de flexibilité
ARG JAR_FILE=target/trading-assistant-0.0.1-SNAPSHOT.jar
# Copie du JAR dans l'image sous un nom générique 'app.jar'
COPY ${JAR_FILE} app.jar
EXPOSE 8080
# Utilisation de votre structure de commande pour la gestion de la mémoire
CMD ["sh","-c","java -XX:InitialRAMPercentage=50 -XX:MaxRAMPercentage=70 -XshowSettings $JAVA_OPTS -jar app.jar"]