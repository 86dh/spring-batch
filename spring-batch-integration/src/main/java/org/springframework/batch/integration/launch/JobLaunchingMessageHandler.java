/*
 * Copyright 2006-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.batch.integration.launch;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobExecutionException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.integration.annotation.ServiceActivator;

/**
 * Message handler which uses strategies to convert a Message into a job and a set of job
 * parameters
 *
 * @author Jonas Partner
 * @author Dave Syer
 * @author Gunnar Hillert
 * @author Mahmoud Ben Hassine
 *
 */
public class JobLaunchingMessageHandler implements JobLaunchRequestHandler {

	private final JobOperator jobOperator;

	/**
	 * @param jobOperator {@link org.springframework.batch.core.launch.JobOperator} used
	 * to execute Spring Batch jobs
	 */
	public JobLaunchingMessageHandler(JobOperator jobOperator) {
		super();
		this.jobOperator = jobOperator;
	}

	@Override
	@ServiceActivator
	public JobExecution launch(JobLaunchRequest request) throws JobExecutionException {
		Job job = request.getJob();
		JobParameters jobParameters = request.getJobParameters();

		return jobOperator.start(job, jobParameters);
	}

}
