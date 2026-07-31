FROM eclipse-temurin:21-jre-alpine
EXPOSE 8090
COPY target/AuthMS-0.0.1-SNAPSHOT.jar auth.jar
ENTRYPOINT ["java","-jar","/auth.jar"]