/**
 * Déclenchement automatique des extractions à heure fixe.
 *
 * <p>La planification n'est pas décrite par une expression cron figée : elle est
 * lue en base (BATCH_CONFIG) et modifiable depuis l'IHM via
 * {@link com.t24extractor.controller.SchedulerConfigController}.
 *
 * @see com.t24extractor.scheduler.AutoExtractionScheduler
 */
package com.t24extractor.scheduler;
