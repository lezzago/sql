/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.plugin.transport;

import static org.junit.Assert.*;

import java.util.Map;
import org.junit.Test;
import org.opensearch.core.tasks.TaskId;
import org.opensearch.sql.commons.transport.ppl.PPLQueryRequest;
import org.opensearch.sql.commons.transport.ppl.PPLQueryTask;

public class PPLQueryTaskTest {

  @Test
  public void testShouldCancelChildrenReturnsTrue() {
    PPLQueryTask pplQueryTask =
        new PPLQueryTask(
            1,
            "transport",
            "cluster:admin/opensearch/ppl",
            "test query",
            TaskId.EMPTY_TASK_ID,
            Map.of());
    assertTrue(pplQueryTask.shouldCancelChildrenOnCancellation());
  }

  @Test
  public void testCreateTaskReturnsPPLQueryTask() {
    PPLQueryRequest pplRequest = new PPLQueryRequest("source=t a=1", null, "/_plugins/_ppl");
    PPLQueryTask task =
        pplRequest.createTask(
            1, "transport", "cluster:admin/opensearch/ppl", TaskId.EMPTY_TASK_ID, Map.of());
    assertNotNull(task);
  }

  @Test
  public void testWithQueryId() {
    PPLQueryRequest pplRequest = new PPLQueryRequest("source=t a=1", null, "/_plugins/_ppl");
    pplRequest.queryId("test-123");
    assertEquals("PPL [queryId=test-123]: source=t a=1", pplRequest.getDescription());
  }

  @Test
  public void testWithoutQueryId() {
    PPLQueryRequest pplRequest = new PPLQueryRequest("source=t a=1", null, "/_plugins/_ppl");
    assertEquals("PPL: source=t a=1", pplRequest.getDescription());
  }

  @Test
  public void testCooperativeModel() {
    PPLQueryRequest pplRequest = new PPLQueryRequest("source=t a=1", null, "/_plugins/_ppl");
    PPLQueryTask task =
        pplRequest.createTask(
            1, "transport", "cluster:admin/opensearch/ppl", TaskId.EMPTY_TASK_ID, Map.of());
    assertFalse(task.isCancelled());
    task.cancel("Test");
    assertTrue(task.isCancelled());
  }
}
