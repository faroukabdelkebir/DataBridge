FROM eclipse-temurin:21-jre-alpine
EXPOSE 8761
COPY target/EurekaServer-0.0.1-SNAPSHOT.jar eureka.jar
ENTRYPOINT ["java","-jar","/eureka.jar"]