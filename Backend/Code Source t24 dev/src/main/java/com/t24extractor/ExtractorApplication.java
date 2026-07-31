package com.t24extractor;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Point d'entrée de l'extracteur T24.
 *
 * <p>Au démarrage, le fuseau est forcé à GMT+1 : les dates T24 sont manipulées
 * sans zone, et un décalage déplacerait la frontière de journée d'extraction.
 *
 * <p>Chaque instance s'enregistre auprès du registre de services sous un nom
 * horodaté unique, plusieurs extracteurs pouvant tourner de front.
 *
 * @see com.t24extractor.configuration.BatchConfiguration
 */
@EnableDiscoveryClient
@SpringBootApplication
@EnableScheduling
public class ExtractorApplication
{
	/**
	 * @param args arguments de ligne de commande, complétés par le nom
	 *             d'instance généré
	 */
	public static void main(String[] args)
	{
		TimeZone timezone = TimeZone.getTimeZone("GMT+1");
		TimeZone.setDefault(timezone);

		String appNameArg = generateAppNameArgument();
		
		List<String> argsList = new ArrayList<>(Arrays.asList(args));
		argsList.add(appNameArg);
		
		String[] newArgs = argsList.toArray(new String[argsList.size()]) ;

		SpringApplication.run(ExtractorApplication.class, newArgs) ;
	}
	
	/***************************************************
	 * 
	 * 	Generate application name dynamically
	 *  to be registered in the monitor.
	 * 
	 * 	@return generated name.
	 * 
	 ***************************************************/
	
	private static String generateAppNameArgument() 
	{
		String argument = "--spring.application.name=Extractor-";

		LocalDateTime localTime = LocalDateTime.now();
		DateTimeFormatter format = DateTimeFormatter.ofPattern("HH-mm-ss");

		String time = localTime.format(format);
		int nano = localTime.atZone(ZoneId.systemDefault()).getNano();

		argument = argument + time + "-" + nano;

		return (argument);
	}
}
