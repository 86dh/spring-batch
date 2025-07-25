/*
 * Copyright 2009-2025 the original author or authors.
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
package org.springframework.batch.core.step.item;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.listener.ChunkListener;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.listener.StepListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.JdbcJobRepositoryFactoryBean;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.FatalStepExecutionException;
import org.springframework.batch.core.step.factory.FaultTolerantStepFactoryBean;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.batch.item.support.SynchronizedItemReader;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.batch.support.transaction.TransactionAwareProxyFactory;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.util.StringUtils;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.batch.core.BatchStatus.FAILED;

/**
 * Tests for {@link FaultTolerantStepFactoryBean}.
 */
class FaultTolerantStepFactoryBeanRollbackTests {

	protected final Log logger = LogFactory.getLog(getClass());

	private FaultTolerantStepFactoryBean<String, String> factory;

	private SkipReaderStub<String> reader;

	private SkipProcessorStub<String> processor;

	private SkipWriterStub<String> writer;

	private JobExecution jobExecution;

	private StepExecution stepExecution;

	private JobRepository repository;

	@BeforeEach
	void setUp() throws Exception {
		reader = new SkipReaderStub<>();
		processor = new SkipProcessorStub<>();
		writer = new SkipWriterStub<>();

		factory = new FaultTolerantStepFactoryBean<>();

		factory.setBeanName("stepName");
		ResourcelessTransactionManager transactionManager = new ResourcelessTransactionManager();
		factory.setTransactionManager(transactionManager);
		factory.setCommitInterval(2);

		reader.clear();
		reader.setItems("1", "2", "3", "4", "5");
		factory.setItemReader(reader);
		processor.clear();
		factory.setItemProcessor(processor);
		writer.clear();
		factory.setItemWriter(writer);

		factory.setSkipLimit(2);

		factory.setSkippableExceptionClasses(Map.of(Exception.class, true));

		EmbeddedDatabase embeddedDatabase = new EmbeddedDatabaseBuilder()
			.addScript("/org/springframework/batch/core/schema-drop-hsqldb.sql")
			.addScript("/org/springframework/batch/core/schema-hsqldb.sql")
			.build();
		JdbcJobRepositoryFactoryBean repositoryFactory = new JdbcJobRepositoryFactoryBean();
		repositoryFactory.setDataSource(embeddedDatabase);
		repositoryFactory.setTransactionManager(new JdbcTransactionManager(embeddedDatabase));
		repositoryFactory.afterPropertiesSet();
		repository = repositoryFactory.getObject();
		factory.setJobRepository(repository);

		jobExecution = repository.createJobExecution("skipJob", new JobParameters());
		stepExecution = jobExecution.createStepExecution(factory.getName());
		repository.add(stepExecution);
	}

	@AfterEach
	void tearDown() {
		reader = null;
		processor = null;
		writer = null;
		factory = null;
	}

	@Test
	void testBeforeChunkListenerException() throws Exception {
		factory.setListeners(new StepListener[] { new ExceptionThrowingChunkListener(1) });
		Step step = factory.getObject();
		step.execute(stepExecution);
		assertEquals(FAILED, stepExecution.getStatus());
		assertEquals(FAILED.toString(), stepExecution.getExitStatus().getExitCode());
		assertEquals(0, stepExecution.getCommitCount());// Make sure exception was thrown
														// in after, not before
		Throwable e = stepExecution.getFailureExceptions().get(0);
		assertThat(e, instanceOf(FatalStepExecutionException.class));
		assertThat(e.getCause(), instanceOf(IllegalArgumentException.class));
	}

	@Test
	void testAfterChunkListenerException() throws Exception {
		factory.setListeners(new StepListener[] { new ExceptionThrowingChunkListener(2) });
		Step step = factory.getObject();
		step.execute(stepExecution);
		assertEquals(FAILED, stepExecution.getStatus());
		assertEquals(FAILED.toString(), stepExecution.getExitStatus().getExitCode());
		assertTrue(stepExecution.getCommitCount() > 0);// Make sure exception was thrown
														// in after, not before
		Throwable e = stepExecution.getFailureExceptions().get(0);
		assertThat(e, instanceOf(FatalStepExecutionException.class));
		assertThat(e.getCause(), instanceOf(IllegalArgumentException.class));
	}

	/**
	 * Scenario: Exception in reader that should not cause rollback
	 */
	@Test
	void testReaderDefaultNoRollbackOnCheckedException() throws Exception {
		reader.setItems("1", "2", "3", "4");
		reader.setFailures("2", "3");
		reader.setExceptionType(SkippableException.class);

		Step step = factory.getObject();

		step.execute(stepExecution);
		assertEquals(BatchStatus.COMPLETED, stepExecution.getStatus());
		assertEquals(2, stepExecution.getSkipCount());
		assertEquals(0, stepExecution.getRollbackCount());
	}

	/**
	 * Scenario: Exception in reader that should not cause rollback
	 */
	@Test
	void testReaderAttributesOverrideSkippableNoRollback() throws Exception {
		reader.setFailures("2", "3");
		reader.setItems("1", "2", "3", "4");
		reader.setExceptionType(SkippableException.class);

		// No skips by default
		factory.setSkippableExceptionClasses(Map.of(RuntimeException.class, true));
		// But this one is explicit in the tx-attrs so it should be skipped
		factory.setNoRollbackExceptionClasses(List.of(SkippableException.class));

		Step step = factory.getObject();

		step.execute(stepExecution);
		assertEquals(BatchStatus.COMPLETED, stepExecution.getStatus());
		assertEquals(0, stepExecution.getSkipCount());
		assertEquals(0, stepExecution.getRollbackCount());
	}

	/**
	 * Scenario: Exception in processor that should cause rollback because of checked
	 * exception
	 */
	@Test
	void testProcessorDefaultRollbackOnCheckedException() throws Exception {
		reader.setItems("1", "2", "3", "4");

		processor.setFailures("1", "3");
		processor.setExceptionType(SkippableException.class);

		Step step = factory.getObject();

		step.execute(stepExecution);
		assertEquals(BatchStatus.COMPLETED, stepExecution.getStatus());
		assertEquals(2, stepExecution.getSkipCount());
		assertEquals(2, stepExecution.getRollbackCount());
	}

	/**
	 * Scenario: Exception in processor that should cause rollback
	 */
	@Test
	void testProcessorDefaultRollbackOnRuntimeException() throws Exception {
		reader.setItems("1", "2", "3", "4");

		processor.setFailures("1", "3");
		processor.setExceptionType(SkippableRuntimeException.class);

		Step step = factory.getObject();

		step.execute(stepExecution);
		assertEquals(BatchStatus.COMPLETED, stepExecution.getStatus());
		assertEquals(2, stepExecution.getSkipCount());
		assertEquals(2, stepExecution.getRollbackCount());
	}

	@Test
	void testNoRollbackInProcessorWhenSkipExceeded() throws Throwable {

		jobExecution = repository.createJobExecution("noRollbackJob", new JobParameters());

		factory.setSkipLimit(0);

		reader.clear();
		reader.setItems("1", "2", "3", "4", "5");
		factory.setItemReader(reader);
		writer.clear();
		factory.setItemWriter(writer);
		processor.clear();
		factory.setItemProcessor(processor);

		factory.setNoRollbackExceptionClasses(List.of(Exception.class));
		factory.setSkippableExceptionClasses(Map.of(Exception.class, true));

		processor.setFailures("2");

		Step step = factory.getObject();

		stepExecution = jobExecution.createStepExecution(factory.getName());
		repository.add(stepExecution);
		step.execute(stepExecution);
		assertEquals(BatchStatus.COMPLETED, stepExecution.getStatus());

		assertEquals("[1, 3, 4, 5]", writer.getCommitted().toString());
		// No rollback on 2 so processor has side effect
		assertEquals("[1, 2, 3, 4, 5]", processor.getCommitted().toString());
		List<String> processed = new ArrayList<>(processor.getProcessed());
		Collections.sort(processed);
		assertEquals("[1, 2, 3, 4, 5]", processed.toString());
		assertEquals(0, stepExecution.getSkipCount());

	}

	@Test
	void testProcessSkipWithNoRollbackForCheckedException() throws Exception {
		processor.setFailures("4");
		processor.setExceptionType(SkippableException.class);

		factory.setNoRollbackExceptionClasses(List.of(SkippableException.class));

		Step step = factory.getObject();

		step.execute(stepExecution);

		assertEquals(BatchStatus.COMPLETED, stepExecution.getStatus());
		assertEquals(1, stepExecution.getSkipCount());
		assertEquals(0, stepExecution.getReadSkipCount());
		assertEquals(5, stepExecution.getReadCount());
		assertEquals(1, stepExecution.getProcessSkipCount());
		assertEquals(0, stepExecution.getRollbackCount());

		// skips "4"
		assertTrue(reader.getRead().contains("4"));
		assertFalse(writer.getCommitted().contains("4"));

		List<String> expectedOutput = Arrays.asList(StringUtils.commaDelimitedListToStringArray("1,2,3,5"));
		assertEquals(expectedOutput, writer.getCommitted());

	}

	/**
	 * Scenario: Exception in writer that should not cause rollback and scan
	 */
	@Test
	void testWriterDefaultRollbackOnCheckedException() throws Exception {
		writer.setFailures("2", "3");
		writer.setExceptionType(SkippableException.class);

		Step step = factory.getObject();

		step.execute(stepExecution);
		assertEquals(BatchStatus.COMPLETED, stepExecution.getStatus());
		assertEquals(2, stepExecution.getSkipCount());
		assertEquals(4, stepExecution.getRollbackCount());
	}

	/**
	 * Scenario: Exception in writer that should not cause rollback and scan
	 */
	@Test
	void testWriterDefaultRollbackOnError() throws Exception {
		writer.setFailures("2", "3");
		writer.setExceptionType(AssertionError.class);

		Step step = factory.getObject();

		step.execute(stepExecution);
		assertEquals(BatchStatus.FAILED, stepExecution.getStatus());
		assertEquals(0, stepExecution.getSkipCount());
		assertEquals(1, stepExecution.getRollbackCount());
	}

	/**
	 * Scenario: Exception in writer that should not cause rollback and scan
	 */
	@Test
	void testWriterDefaultRollbackOnRuntimeException() throws Exception {
		writer.setFailures("2", "3");
		writer.setExceptionType(SkippableRuntimeException.class);

		Step step = factory.getObject();

		step.execute(stepExecution);
		assertEquals(BatchStatus.COMPLETED, stepExecution.getStatus());
		assertEquals(2, stepExecution.getSkipCount());
		assertEquals(4, stepExecution.getRollbackCount());

	}

	/**
	 * Scenario: Exception in writer that should not cause rollback and scan
	 */
	@Test
	void testWriterNoRollbackOnRuntimeException() throws Exception {

		writer.setFailures("2", "3");
		writer.setExceptionType(SkippableRuntimeException.class);

		factory.setNoRollbackExceptionClasses(List.of(SkippableRuntimeException.class));

		Step step = factory.getObject();

		step.execute(stepExecution);
		assertEquals(BatchStatus.COMPLETED, stepExecution.getStatus());
		assertEquals(2, stepExecution.getSkipCount());
		// Two multi-item chunks rolled back. When the item was encountered on
		// its own it can proceed
		assertEquals(2, stepExecution.getRollbackCount());

	}

	/**
	 * Scenario: Exception in writer that should not cause rollback and scan
	 */
	@Test
	void testWriterNoRollbackOnCheckedException() throws Exception {
		writer.setFailures("2", "3");
		writer.setExceptionType(SkippableException.class);

		factory.setNoRollbackExceptionClasses(List.of(SkippableException.class));

		Step step = factory.getObject();

		step.execute(stepExecution);
		assertEquals(BatchStatus.COMPLETED, stepExecution.getStatus());
		assertEquals(2, stepExecution.getSkipCount());
		// Two multi-item chunks rolled back. When the item was encountered on
		// its own it can proceed
		assertEquals(2, stepExecution.getRollbackCount());
	}

	@Test
	void testSkipInProcessor() throws Exception {
		processor.setFailures("4");
		factory.setCommitInterval(30);

		Step step = factory.getObject();

		step.execute(stepExecution);
		assertEquals(BatchStatus.COMPLETED, stepExecution.getStatus());

		assertEquals("[1, 2, 3, 4, 1, 2, 3, 5]", processor.getProcessed().toString());
		assertEquals("[1, 2, 3, 5]", processor.getCommitted().toString());
		assertEquals("[1, 2, 3, 5]", writer.getWritten().toString());
		assertEquals("[1, 2, 3, 5]", writer.getCommitted().toString());
	}

	@Test
	void testMultipleSkipsInProcessor() throws Exception {
		processor.setFailures("2", "4");
		factory.setCommitInterval(30);

		Step step = factory.getObject();

		step.execute(stepExecution);
		assertEquals(BatchStatus.COMPLETED, stepExecution.getStatus());

		assertEquals("[1, 3, 5]", processor.getCommitted().toString());
		assertEquals("[1, 3, 5]", writer.getWritten().toString());
		assertEquals("[1, 3, 5]", writer.getCommitted().toString());
		assertEquals("[1, 2, 1, 3, 4, 1, 3, 5]", processor.getProcessed().toString());
	}

	@Test
	void testMultipleSkipsInNonTransactionalProcessor() throws Exception {
		processor.setFailures("2", "4");
		factory.setCommitInterval(30);
		factory.setProcessorTransactional(false);

		Step step = factory.getObject();

		step.execute(stepExecution);
		assertEquals(BatchStatus.COMPLETED, stepExecution.getStatus());

		assertEquals("[1, 3, 5]", writer.getWritten().toString());
		assertEquals("[1, 3, 5]", writer.getCommitted().toString());
		// If non-transactional, we should only process each item once
		assertEquals("[1, 2, 3, 4, 5]", processor.getProcessed().toString());
	}

	@Test
	void testFilterInProcessor() throws Exception {
		processor.setFailures("4");
		processor.setFilter(true);
		factory.setCommitInterval(30);

		Step step = factory.getObject();

		step.execute(stepExecution);
		assertEquals(BatchStatus.COMPLETED, stepExecution.getStatus());

		assertEquals("[1, 2, 3, 4, 5]", processor.getProcessed().toString());
		assertEquals("[1, 2, 3, 4, 5]", processor.getCommitted().toString());
		assertEquals("[1, 2, 3, 5]", writer.getWritten().toString());
		assertEquals("[1, 2, 3, 5]", writer.getCommitted().toString());
	}

	@Test
	void testSkipInWriter() throws Exception {
		writer.setFailures("4");
		factory.setCommitInterval(30);

		Step step = factory.getObject();

		step.execute(stepExecution);
		assertEquals(BatchStatus.COMPLETED, stepExecution.getStatus());

		assertEquals("[1, 2, 3, 5]", processor.getCommitted().toString());
		assertEquals("[1, 2, 3, 5]", writer.getCommitted().toString());
		assertEquals("[1, 2, 3, 4, 1, 2, 3, 4, 5]", writer.getWritten().toString());
		assertEquals("[1, 2, 3, 4, 5, 1, 2, 3, 4, 5]", processor.getProcessed().toString());

		assertEquals(1, stepExecution.getWriteSkipCount());
		assertEquals(5, stepExecution.getReadCount());
		assertEquals(4, stepExecution.getWriteCount());
		assertEquals(0, stepExecution.getFilterCount());
	}

	@Test
	void testSkipInWriterNonTransactionalProcessor() throws Exception {
		writer.setFailures("4");
		factory.setCommitInterval(30);
		factory.setProcessorTransactional(false);

		Step step = factory.getObject();

		step.execute(stepExecution);
		assertEquals(BatchStatus.COMPLETED, stepExecution.getStatus());

		assertEquals("[1, 2, 3, 5]", writer.getCommitted().toString());
		assertEquals("[1, 2, 3, 4, 1, 2, 3, 4, 5]", writer.getWritten().toString());
		assertEquals("[1, 2, 3, 4, 5]", processor.getProcessed().toString());
	}

	@Test
	void testSkipInWriterTransactionalReader() throws Exception {
		writer.setFailures("4");
		ItemReader<String> reader = new ListItemReader<>(
				TransactionAwareProxyFactory.createTransactionalList(Arrays.asList("1", "2", "3", "4", "5")));
		factory.setItemReader(reader);
		factory.setCommitInterval(30);
		factory.setSkipLimit(10);
		factory.setIsReaderTransactionalQueue(true);

		Step step = factory.getObject();

		step.execute(stepExecution);
		assertEquals(BatchStatus.COMPLETED, stepExecution.getStatus());

		assertEquals("[]", writer.getCommitted().toString());
		assertEquals("[1, 2, 3, 4]", writer.getWritten().toString());
		assertEquals("[1, 2, 3, 4, 5, 1, 2, 3, 4, 5]", processor.getProcessed().toString());
	}

	@Test
	void testMultithreadedSkipInWriter() throws Exception {
		factory.setItemReader(new SynchronizedItemReader<>(reader));
		writer.setFailures("1", "2", "3", "4", "5");
		factory.setCommitInterval(3);
		factory.setSkipLimit(10);
		factory.setTaskExecutor(new SimpleAsyncTaskExecutor());

		Step step = factory.getObject();

		step.execute(stepExecution);
		assertEquals(BatchStatus.COMPLETED, stepExecution.getStatus());

		assertEquals("[]", writer.getCommitted().toString());
		assertEquals("[]", processor.getCommitted().toString());
		assertEquals(5, stepExecution.getSkipCount());
	}

	@Test
	void testMultipleSkipsInWriter() throws Exception {
		writer.setFailures("2", "4");
		factory.setCommitInterval(30);

		Step step = factory.getObject();

		step.execute(stepExecution);
		assertEquals(BatchStatus.COMPLETED, stepExecution.getStatus());

		assertEquals("[1, 3, 5]", writer.getCommitted().toString());
		assertEquals("[1, 2, 1, 2, 3, 4, 5]", writer.getWritten().toString());
		assertEquals("[1, 3, 5]", processor.getCommitted().toString());
		assertEquals("[1, 2, 3, 4, 5, 1, 2, 3, 4, 5]", processor.getProcessed().toString());

		assertEquals(2, stepExecution.getWriteSkipCount());
		assertEquals(5, stepExecution.getReadCount());
		assertEquals(3, stepExecution.getWriteCount());
		assertEquals(0, stepExecution.getFilterCount());
	}

	@Test
	void testMultipleSkipsInWriterNonTransactionalProcessor() throws Exception {
		writer.setFailures("2", "4");
		factory.setCommitInterval(30);
		factory.setProcessorTransactional(false);

		Step step = factory.getObject();

		step.execute(stepExecution);
		assertEquals(BatchStatus.COMPLETED, stepExecution.getStatus());

		assertEquals("[1, 3, 5]", writer.getCommitted().toString());
		assertEquals("[1, 2, 1, 2, 3, 4, 5]", writer.getWritten().toString());
		assertEquals("[1, 2, 3, 4, 5]", processor.getProcessed().toString());
	}

	static class ExceptionThrowingChunkListener implements ChunkListener {

		private final int phase;

		public ExceptionThrowingChunkListener(int throwPhase) {
			this.phase = throwPhase;
		}

		@Override
		public void beforeChunk(ChunkContext context) {
			if (phase == 1) {
				throw new IllegalArgumentException("Planned exception");
			}
		}

		@Override
		public void afterChunk(ChunkContext context) {
			if (phase == 2) {
				throw new IllegalArgumentException("Planned exception");
			}
		}

		@Override
		public void afterChunkError(ChunkContext context) {
			if (phase == 3) {
				throw new IllegalArgumentException("Planned exception");
			}
		}

	}

}
