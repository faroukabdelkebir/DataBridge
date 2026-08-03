FROM eclipse-temurin:21-jre-alpine
EXPOSE 65005
ADD target/*.jar extractor-T24.jar
ENTRYPOINT ["java","-jar","/extractor-T24.jar"]

