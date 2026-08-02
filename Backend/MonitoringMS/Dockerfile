FROM eclipse-temurin:21-jre-alpine
EXPOSE 65010
ADD target/*.jar monitoring.jar
ENTRYPOINT ["java","-jar","/monitoring.jar"]