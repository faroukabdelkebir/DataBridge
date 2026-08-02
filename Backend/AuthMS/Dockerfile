FROM eclipse-temurin:21-jre-alpine
EXPOSE 8090
COPY target/*.jar auth.jar
ENTRYPOINT ["java","-jar","/auth.jar"]