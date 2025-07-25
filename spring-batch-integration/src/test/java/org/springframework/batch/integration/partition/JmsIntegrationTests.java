/*
 * Copyright 2006-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.springframework.batch.integration.partition;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Dave Syer
 * @author Mahmoud Ben Hassine
 *
 */
@SpringJUnitConfig
@DirtiesContext
class JmsIntegrationTests {

	private final Log logger = LogFactory.getLog(getClass());

	@Autowired
	private JobOperator jobOperator;

	@Autowired
	private Job job;

	@Autowired
	private JobRepository jobRepository;

	@Test
	void testSimpleProperties() {
		assertNotNull(jobOperator);
	}

	@Test
	void testLaunchJob() throws Exception {
		int before = jobRepository.getJobInstances(job.getName(), 0, 100).size();
		assertNotNull(jobOperator.start(job, new JobParameters()));
		List<JobInstance> jobInstances = jobRepository.getJobInstances(job.getName(), 0, 100);
		int after = jobInstances.size();
		assertEquals(1, after - before);
		JobExecution jobExecution = jobRepository.getJobExecutions(jobInstances.get(jobInstances.size() - 1)).get(0);
		assertEquals(BatchStatus.COMPLETED, jobExecution.getStatus(),
				jobExecution.getExitStatus().getExitDescription());
		assertEquals(3, jobExecution.getStepExecutions().size());
		for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
			// BATCH-1703: we are using a map dao so the step executions in the job
			// execution are old and we need to
			// pull them back out of the repository...
			stepExecution = jobRepository.getStepExecution(jobExecution.getId(), stepExecution.getId());
			logger.debug(String.valueOf(stepExecution));
			assertEquals(BatchStatus.COMPLETED, stepExecution.getStatus());
		}
	}

}
