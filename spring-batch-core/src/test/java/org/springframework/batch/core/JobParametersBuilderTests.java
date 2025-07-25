/*
 * Copyright 2008-2025 the original author or authors.
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
package org.springframework.batch.core;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.batch.core.job.*;
import org.springframework.batch.core.job.parameters.JobParameter;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Lucas Ward
 * @author Michael Minella
 * @author Glenn Renfro
 * @author Mahmoud Ben Hassine
 *
 */
class JobParametersBuilderTests {

	private JobParametersBuilder parametersBuilder;

	private SimpleJob job;

	private List<JobInstance> jobInstanceList;

	private List<JobExecution> jobExecutionList;

	private final Date date = new Date(System.currentTimeMillis());

	@BeforeEach
	void initialize() {
		this.job = new SimpleJob("simpleJob");
		this.jobInstanceList = new ArrayList<>(1);
		this.jobExecutionList = new ArrayList<>(1);
		this.parametersBuilder = new JobParametersBuilder();
	}

	@Test
	void testAddingExistingJobParameters() {
		JobParameters params1 = new JobParametersBuilder().addString("foo", "bar")
			.addString("bar", "baz")
			.toJobParameters();

		JobParameters params2 = new JobParametersBuilder().addString("foo", "baz").toJobParameters();

		JobParameters finalParams = new JobParametersBuilder().addString("baz", "quix")
			.addJobParameters(params1)
			.addJobParameters(params2)
			.toJobParameters();

		assertEquals(finalParams.getString("foo"), "baz");
		assertEquals(finalParams.getString("bar"), "baz");
		assertEquals(finalParams.getString("baz"), "quix");
	}

	@Test
	void testAddingNullJobParameters() {
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> new JobParametersBuilder().addString("foo", null).toJobParameters());
		Assertions.assertEquals("Value for parameter 'foo' must not be null", exception.getMessage());
	}

	@Test
	void testNonIdentifyingParameters() {
		this.parametersBuilder.addDate("SCHEDULE_DATE", date, false);
		this.parametersBuilder.addLong("LONG", 1L, false);
		this.parametersBuilder.addString("STRING", "string value", false);
		this.parametersBuilder.addDouble("DOUBLE", 1.0d, false);

		JobParameters parameters = this.parametersBuilder.toJobParameters();
		assertEquals(date, parameters.getDate("SCHEDULE_DATE"));
		assertEquals(1L, parameters.getLong("LONG").longValue());
		assertEquals("string value", parameters.getString("STRING"));
		assertEquals(1, parameters.getDouble("DOUBLE"), 1e-15);
		assertFalse(parameters.getParameters().get("SCHEDULE_DATE").isIdentifying());
		assertFalse(parameters.getParameters().get("LONG").isIdentifying());
		assertFalse(parameters.getParameters().get("STRING").isIdentifying());
		assertFalse(parameters.getParameters().get("DOUBLE").isIdentifying());
	}

	@Test
	void testToJobRuntimeParameters() {
		this.parametersBuilder.addDate("SCHEDULE_DATE", date);
		this.parametersBuilder.addLong("LONG", 1L);
		this.parametersBuilder.addString("STRING", "string value");
		this.parametersBuilder.addDouble("DOUBLE", 1.0d);
		JobParameters parameters = this.parametersBuilder.toJobParameters();
		assertEquals(date, parameters.getDate("SCHEDULE_DATE"));
		assertEquals(1L, parameters.getLong("LONG").longValue());
		assertEquals(1, parameters.getDouble("DOUBLE"), 1e-15);
		assertEquals("string value", parameters.getString("STRING"));
	}

	@Test
	void testCopy() {
		this.parametersBuilder.addString("STRING", "string value");
		this.parametersBuilder = new JobParametersBuilder(this.parametersBuilder.toJobParameters());
		Iterator<String> parameters = this.parametersBuilder.toJobParameters().getParameters().keySet().iterator();
		assertEquals("STRING", parameters.next());
	}

	@Test
	void testNotOrderedTypes() {
		this.parametersBuilder.addDate("SCHEDULE_DATE", date);
		this.parametersBuilder.addLong("LONG", 1L);
		this.parametersBuilder.addString("STRING", "string value");
		Set<String> parameters = this.parametersBuilder.toJobParameters().getParameters().keySet();
		assertThat(parameters).containsExactlyInAnyOrder("STRING", "LONG", "SCHEDULE_DATE");
	}

	@Test
	void testNotOrderedStrings() {
		this.parametersBuilder.addString("foo", "value foo");
		this.parametersBuilder.addString("bar", "value bar");
		this.parametersBuilder.addString("spam", "value spam");
		Set<String> parameters = this.parametersBuilder.toJobParameters().getParameters().keySet();
		assertThat(parameters).containsExactlyInAnyOrder("foo", "bar", "spam");
	}

	@Test
	void testAddJobParameter() {
		JobParameter jobParameter = new JobParameter("bar", String.class);
		this.parametersBuilder.addJobParameter("foo", jobParameter);
		Map<String, JobParameter<?>> parameters = this.parametersBuilder.toJobParameters().getParameters();
		assertEquals(1, parameters.size());
		assertEquals("bar", parameters.get("foo").getValue());
	}

}
