/*
 * Copyright 2018-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.batch.integration.partition;

import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;

/**
 * Convenient factory for a {@link RemotePartitioningWorkerStepBuilder} which sets the
 * {@link JobRepository} and {@link BeanFactory} automatically.
 *
 * @since 4.1
 * @author Mahmoud Ben Hassine
 */
public class RemotePartitioningWorkerStepBuilderFactory implements BeanFactoryAware {

	private BeanFactory beanFactory;

	final private JobRepository jobRepository;

	/**
	 * Create a new {@link RemotePartitioningWorkerStepBuilderFactory}.
	 * @param jobRepository the job repository to use
	 */
	public RemotePartitioningWorkerStepBuilderFactory(JobRepository jobRepository) {
		this.jobRepository = jobRepository;
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
	}

	/**
	 * Creates a {@link RemotePartitioningWorkerStepBuilder} and initializes its job
	 * repository, job explorer, bean factory and transaction manager.
	 * @param name the name of the step
	 * @return a {@link RemotePartitioningWorkerStepBuilder}
	 */
	public RemotePartitioningWorkerStepBuilder get(String name) {
		return new RemotePartitioningWorkerStepBuilder(name, this.jobRepository).beanFactory(this.beanFactory);
	}

}
