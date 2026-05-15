/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.commons.transport.ppl;

import java.util.Map;
import org.opensearch.core.tasks.TaskId;
import org.opensearch.tasks.CancellableTask;

/** Cancellable task representing an in-flight PPL query. */
public class PPLQueryTask extends CancellableTask {

  public PPLQueryTask(
      long id,
      String type,
      String action,
      String description,
      TaskId parentTaskId,
      Map<String, String> headers) {
    super(id, type, action, description, parentTaskId, headers);
  }

  @Override
  public boolean shouldCancelChildrenOnCancellation() {
    return true;
  }
}
