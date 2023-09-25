/*
 * Copyright 2023 the original author or authors.
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
package org.springframework.batch.sample.jdbc;

import javax.sql.DataSource;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.sample.domain.trade.CustomerCredit;
import org.springframework.batch.sample.domain.trade.internal.CustomerCreditIncreaseProcessor;
import org.springframework.batch.sample.domain.trade.internal.CustomerCreditRowMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.support.JdbcTransactionManager;

/**
 * @author Mahmoud Ben Hassine
 */
@Configuration
@EnableBatchProcessing
public class JdbcCursorReaderBatchWriterSampleJob {

	@Bean
	public JdbcCursorItemReader<CustomerCredit> itemReader() {
		String sql = "select ID, NAME, CREDIT from CUSTOMER";
		return new JdbcCursorItemReaderBuilder<CustomerCredit>().name("customerReader")
			.dataSource(dataSource())
			.sql(sql)
			.rowMapper(new CustomerCreditRowMapper())
			.build();
	}

	@Bean
	public JdbcBatchItemWriter<CustomerCredit> itemWriter() {
		String sql = "UPDATE CUSTOMER set credit = :credit where id = :id";
		return new JdbcBatchItemWriterBuilder<CustomerCredit>().dataSource(dataSource())
			.sql(sql)
			.itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
			.assertUpdates(true)
			.build();
	}

	@Bean
	public Job job(JobRepository jobRepository, JdbcTransactionManager transactionManager) {
		return new JobBuilder("ioSampleJob", jobRepository)
			.start(new StepBuilder("step1", jobRepository).<CustomerCredit, CustomerCredit>chunk(2, transactionManager)
				.reader(itemReader())
				.processor(new CustomerCreditIncreaseProcessor())
				.writer(itemWriter())
				.build())
			.build();
	}

	// Infrastructure beans

	@Bean
	public DataSource dataSource() {
		return new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.HSQL)
			.addScript("/org/springframework/batch/core/schema-drop-hsqldb.sql")
			.addScript("/org/springframework/batch/core/schema-hsqldb.sql")
			.addScript("/org/springframework/batch/sample/jdbc/sql/schema.sql")
			.build();
	}

	@Bean
	public JdbcTransactionManager transactionManager(DataSource dataSource) {
		return new JdbcTransactionManager(dataSource);
	}

}
