/*
 * Copyright 2018-2024 the original author or authors.
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

package org.springframework.batch.item.json;

import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;

/**
 * Strategy interface for Json readers. Implementations are expected to use a streaming
 * API in order to read Json objects one at a time.
 *
 * @param <T> type of the target object
 * @author Mahmoud Ben Hassine
 * @author Jimmy Praet
 * @since 4.1
 */
public interface JsonObjectReader<T> {

	/**
	 * Open the Json resource for reading.
	 * @param resource the input resource
	 * @throws Exception if unable to open the resource
	 */
	default void open(Resource resource) throws Exception {

	}

	/**
	 * Read the next object in the Json resource if any.
	 * @return the next object or {@code null} if the resource is exhausted
	 * @throws Exception if unable to read the next object
	 */
	@Nullable
	T read() throws Exception;

	/**
	 * Close the input resource.
	 * @throws Exception if unable to close the input resource
	 */
	default void close() throws Exception {

	}

	/**
	 * Move to the given item index. Implementations should override this method if there
	 * is a more efficient way of moving to given index than re-reading the input using
	 * {@link #read()}.
	 * @param itemIndex index of item (0 based) to jump to.
	 * @throws Exception Allows implementations to throw checked exceptions for
	 * interpretation by the framework
	 * @since 5.2
	 */
	default void jumpToItem(int itemIndex) throws Exception {
		for (int i = 0; i < itemIndex; i++) {
			read();
		}
	}

}
