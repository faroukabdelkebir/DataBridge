package com.t24extractor.decider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.flow.FlowExecutionStatus;
import org.springframework.batch.core.job.flow.JobExecutionDecider;

/**
 * Choisit la branche de traitement à emprunter après la préparation.
 *
 * <p>Le statut renvoyé est composé à partir du mode de stockage et du mode de
 * chargement : {@code Manager}, {@code BLOB-Manager},
 * {@code Incremental-ID-Manager} ou {@code BLOB-Incremental-ID-Manager}.
 *
 * <p>Cas particulier : si le comptage ({@code COUNT}) est nul, le statut
 * {@code EXIT} termine le job sans lancer d'extraction.
 *
 * <p>Le type est lu en priorité dans le contexte d'exécution (où
 * STANDARD.SELECTION a pu le corriger d'après TAFJ) et, à défaut, dans les
 * paramètres du job.
 */
@StepScope
public class ExecutionFlowDecider implements JobExecutionDecider
{
	private static final Logger LOG = LoggerFactory.getLogger(ExecutionFlowDecider.class) ;

	/**
	 * {@inheritDoc}
	 *
	 * @return le nom de la branche à suivre, ou {@code EXIT} s'il n'y a rien à extraire
	 */
	@Override
	public FlowExecutionStatus decide(JobExecution jobExecution, StepExecution stepExecution)
	{
		StringBuilder flowExecutionStatus = new StringBuilder();

		String type;
		if (jobExecution.getExecutionContext().get("TYPE") != null) {
			type = jobExecution.getExecutionContext().getString("TYPE");
		} else {
			type = jobExecution.getJobParameters().getString("Type", "");
		}
		type = type == null ? "" : type;

		String chargement = jobExecution.getJobParameters().getString("Chargement", "") ;
		chargement = chargement == null ? "" : chargement;

		if (stepExecution.getJobExecution().getExecutionContext().getInt("COUNT") == 0) {
			return (new FlowExecutionStatus("EXIT"));
		}
		if (type.contains("B") || type.contains("C"))
		{
			flowExecutionStatus.append("BLOB-");
		}
		
		if (chargement.equals("Incremental-id"))
		{
			flowExecutionStatus.append("Incremental-ID-");
		}
		
		flowExecutionStatus.append("Manager");




		String executionStatus = flowExecutionStatus.toString() ;
		
		LOG.trace("[ExecutionFlowDecider] executionStatus = {}" , executionStatus);
		return(new FlowExecutionStatus(flowExecutionStatus.toString()));
	}
}
