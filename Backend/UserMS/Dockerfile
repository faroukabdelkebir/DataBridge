FROM eclipse-temurin:21-jre-alpine
EXPOSE 8085
COPY target/UserMS-0.0.1-SNAPSHOT.jar user.jar
ENTRYPOINT ["java","-jar","/user.jar"]