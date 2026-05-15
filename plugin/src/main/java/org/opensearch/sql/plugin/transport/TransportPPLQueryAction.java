/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.plugin.transport;

import static org.opensearch.rest.BaseRestHandler.MULTI_ALLOW_EXPLICIT_INDEX;
import static org.opensearch.sql.executor.ExecutionEngine.ExplainResponse.normalizeLf;
import static org.opensearch.sql.lang.PPLLangSpec.PPL_SPEC;
import static org.opensearch.sql.protocol.response.format.JsonResponseFormatter.Style.PRETTY;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.calcite.rel.RelNode;
import org.json.JSONObject;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.analytics.exec.QueryPlanExecutor;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Guice;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.inject.Injector;
import org.opensearch.common.inject.ModulesBuilder;
import org.opensearch.core.action.ActionListener;
import org.opensearch.sql.common.response.ResponseListener;
import org.opensearch.sql.common.setting.Settings;
import org.opensearch.sql.common.utils.QueryContext;
import org.opensearch.sql.commons.transport.ppl.PPLQueryAction;
import org.opensearch.sql.commons.transport.ppl.PPLQueryRequest;
import org.opensearch.sql.commons.transport.ppl.PPLQueryResponse;
import org.opensearch.sql.commons.transport.ppl.PPLQueryTask;
import org.opensearch.sql.datasource.DataSourceService;
import org.opensearch.sql.datasources.service.DataSourceServiceImpl;
import org.opensearch.sql.executor.ExecutionEngine;
import org.opensearch.sql.executor.QueryType;
import org.opensearch.sql.legacy.metrics.MetricName;
import org.opensearch.sql.legacy.metrics.Metrics;
import org.opensearch.sql.monitor.profile.QueryProfiling;
import org.opensearch.sql.opensearch.executor.OpenSearchQueryManager;
import org.opensearch.sql.opensearch.setting.OpenSearchSettings;
import org.opensearch.sql.plugin.config.EngineExtensionsHolder;
import org.opensearch.sql.plugin.config.OpenSearchPluginModule;
import org.opensearch.sql.plugin.rest.AnalyticsExecutorHolder;
import org.opensearch.sql.plugin.rest.RestUnifiedQueryAction;
import org.opensearch.sql.ppl.PPLService;
import org.opensearch.sql.protocol.response.QueryResult;
import org.opensearch.sql.protocol.response.format.CsvResponseFormatter;
import org.opensearch.sql.protocol.response.format.Format;
import org.opensearch.sql.protocol.response.format.JsonResponseFormatter;
import org.opensearch.sql.protocol.response.format.RawResponseFormatter;
import org.opensearch.sql.protocol.response.format.ResponseFormatter;
import org.opensearch.sql.protocol.response.format.SimpleJsonResponseFormatter;
import org.opensearch.sql.protocol.response.format.VisualizationResponseFormatter;
import org.opensearch.sql.protocol.response.format.YamlResponseFormatter;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.node.NodeClient;

/** Send PPL query transport action. */
public class TransportPPLQueryAction
    extends HandledTransportAction<PPLQueryRequest, PPLQueryResponse> {

  private final Injector injector;

  private final Supplier<Boolean> pplEnabled;

  /** Null when analytics-engine plugin is absent; set via {@link #setQueryPlanExecutor}. */
  private volatile RestUnifiedQueryAction unifiedQueryHandler;

  private final NodeClient clientRef;
  private final ClusterService clusterServiceRef;
  private final org.opensearch.sql.common.setting.Settings pluginSettingsRef;

  @Inject
  public TransportPPLQueryAction(
      TransportService transportService,
      ActionFilters actionFilters,
      NodeClient client,
      ClusterService clusterService,
      DataSourceServiceImpl dataSourceService,
      org.opensearch.common.settings.Settings clusterSettings,
      EngineExtensionsHolder extensionsHolder) {
    super(PPLQueryAction.NAME, transportService, actionFilters, PPLQueryRequest::new);
    this.clientRef = client;
    this.clusterServiceRef = clusterService;

    ModulesBuilder modules = new ModulesBuilder();
    modules.add(new OpenSearchPluginModule(extensionsHolder.engines()));
    org.opensearch.sql.common.setting.Settings pluginSettings =
        new OpenSearchSettings(clusterService.getClusterSettings());
    this.pluginSettingsRef = pluginSettings;
    modules.add(
        b -> {
          b.bind(NodeClient.class).toInstance(client);
          b.bind(org.opensearch.sql.common.setting.Settings.class).toInstance(pluginSettings);
          b.bind(DataSourceService.class).toInstance(dataSourceService);
        });
    this.injector = Guice.createInjector(modules);
    this.pplEnabled =
        () ->
            MULTI_ALLOW_EXPLICIT_INDEX.get(clusterSettings)
                && (Boolean)
                    injector
                        .getInstance(org.opensearch.sql.common.setting.Settings.class)
                        .getSettingValue(Settings.Key.PPL_ENABLED);
  }

  /** Invoked by Guice iff analytics-engine bound {@code QueryPlanExecutor}. */
  @Inject(optional = true)
  public void setQueryPlanExecutor(
      QueryPlanExecutor<RelNode, Iterable<Object[]>> queryPlanExecutor) {
    AnalyticsExecutorHolder.set(queryPlanExecutor);
    this.unifiedQueryHandler =
        new RestUnifiedQueryAction(
            clientRef, clusterServiceRef, queryPlanExecutor, pluginSettingsRef);
  }

  /**
   * Map a commons wire request onto the legacy {@link
   * org.opensearch.sql.ppl.domain.PPLQueryRequest}.
   */
  private static org.opensearch.sql.ppl.domain.PPLQueryRequest toLegacyRequest(
      PPLQueryRequest commonsRequest) {
    JSONObject jsonContent =
        commonsRequest.getJsonContentRaw() == null
            ? null
            : new JSONObject(commonsRequest.getJsonContentRaw());
    org.opensearch.sql.ppl.domain.PPLQueryRequest legacy =
        new org.opensearch.sql.ppl.domain.PPLQueryRequest(
            commonsRequest.getRequest(),
            jsonContent,
            commonsRequest.getPath(),
            commonsRequest.getFormat(),
            commonsRequest.getExplainMode(),
            commonsRequest.profile());
    legacy.sanitize(commonsRequest.sanitize());
    legacy.style(JsonResponseFormatter.Style.valueOf(commonsRequest.style().name()));
    legacy.queryId(commonsRequest.queryId());
    return legacy;
  }

  /** {@inheritDoc} */
  @Override
  protected void doExecute(
      Task task, PPLQueryRequest request, ActionListener<PPLQueryResponse> listener) {
    if (!pplEnabled.get()) {
      listener.onFailure(
          new IllegalAccessException(
              "Either plugins.ppl.enabled or rest.action.multi.allow_explicit_index setting is"
                  + " false"));
      return;
    }

    if (request.isGrammarRequest()) {
      // Authorization is enforced by this transport action before returning grammar metadata in
      // REST.
      listener.onResponse(new PPLQueryResponse("{}"));
      return;
    }

    if (task instanceof PPLQueryTask pplQueryTask) {
      OpenSearchQueryManager.setCancellableTask(pplQueryTask);
    }
    Metrics.getInstance().getNumericalMetric(MetricName.PPL_REQ_TOTAL).increment();
    Metrics.getInstance().getNumericalMetric(MetricName.PPL_REQ_COUNT_TOTAL).increment();

    QueryContext.addRequestId();

    org.opensearch.sql.ppl.domain.PPLQueryRequest transformedRequest = toLegacyRequest(request);
    QueryContext.setProfile(transformedRequest.profile());
    ActionListener<PPLQueryResponse> clearingListener = wrapWithProfilingClear(listener);

    // Route to analytics engine for non-Lucene (e.g., Parquet-backed) indices.
    if (unifiedQueryHandler != null
        && unifiedQueryHandler.isAnalyticsIndex(transformedRequest.getRequest(), QueryType.PPL)) {
      if (transformedRequest.isExplainRequest()) {
        unifiedQueryHandler.explain(
            transformedRequest.getRequest(),
            QueryType.PPL,
            transformedRequest.mode(),
            createExplainResponseListener(transformedRequest, clearingListener));
      } else {
        unifiedQueryHandler.execute(
            transformedRequest.getRequest(),
            QueryType.PPL,
            transformedRequest.profile(),
            clearingListener);
      }
      return;
    }

    PPLService pplService = injector.getInstance(PPLService.class);

    if (transformedRequest.isExplainRequest()) {
      pplService.explain(
          transformedRequest, createExplainResponseListener(transformedRequest, clearingListener));
    } else {
      pplService.execute(
          transformedRequest,
          createListener(transformedRequest, clearingListener),
          createExplainResponseListener(transformedRequest, clearingListener));
    }
  }

  private ResponseListener<ExecutionEngine.ExplainResponse> createExplainResponseListener(
      org.opensearch.sql.ppl.domain.PPLQueryRequest request,
      ActionListener<PPLQueryResponse> listener) {
    return new ResponseListener<ExecutionEngine.ExplainResponse>() {
      @Override
      public void onResponse(ExecutionEngine.ExplainResponse response) {
        Optional<Format> isYamlFormat =
            Format.ofExplain(request.getFormat()).filter(format -> format.equals(Format.YAML));
        ResponseFormatter<ExecutionEngine.ExplainResponse> formatter;
        if (isYamlFormat.isPresent()) {
          formatter =
              new YamlResponseFormatter<>() {
                @Override
                protected Object buildYamlObject(ExecutionEngine.ExplainResponse response) {
                  return normalizeLf(response);
                }
              };
        } else {
          formatter =
              new JsonResponseFormatter<>(PRETTY) {
                @Override
                protected Object buildJsonObject(ExecutionEngine.ExplainResponse response) {
                  return response;
                }
              };
        }
        listener.onResponse(
            new PPLQueryResponse(formatter.format(response), formatter.contentType()));
      }

      @Override
      public void onFailure(Exception e) {
        listener.onFailure(e);
      }
    };
  }

  private ResponseListener<ExecutionEngine.QueryResponse> createListener(
      org.opensearch.sql.ppl.domain.PPLQueryRequest pplRequest,
      ActionListener<PPLQueryResponse> listener) {
    Format format = format(pplRequest);
    ResponseFormatter<QueryResult> formatter;
    if (format.equals(Format.CSV)) {
      formatter = new CsvResponseFormatter(pplRequest.sanitize());
    } else if (format.equals(Format.RAW)) {
      formatter = new RawResponseFormatter();
    } else if (format.equals(Format.VIZ)) {
      formatter = new VisualizationResponseFormatter(pplRequest.style());
    } else {
      formatter = new SimpleJsonResponseFormatter(JsonResponseFormatter.Style.PRETTY);
    }

    return new ResponseListener<ExecutionEngine.QueryResponse>() {
      @Override
      public void onResponse(ExecutionEngine.QueryResponse response) {
        String responseContent =
            formatter.format(
                new QueryResult(
                    response.getSchema(), response.getResults(), response.getCursor(), PPL_SPEC));
        listener.onResponse(new PPLQueryResponse(responseContent));
      }

      @Override
      public void onFailure(Exception e) {
        listener.onFailure(e);
      }
    };
  }

  private Format format(org.opensearch.sql.ppl.domain.PPLQueryRequest pplRequest) {
    String format = pplRequest.getFormat();
    Optional<Format> optionalFormat = Format.of(format);
    if (optionalFormat.isPresent()) {
      return optionalFormat.get();
    } else {
      throw new IllegalArgumentException(
          String.format(Locale.ROOT, "response in %s format is not supported.", format));
    }
  }

  private ActionListener<PPLQueryResponse> wrapWithProfilingClear(
      ActionListener<PPLQueryResponse> delegate) {
    return new ActionListener<>() {
      @Override
      public void onResponse(PPLQueryResponse pplQueryResponse) {
        try {
          delegate.onResponse(pplQueryResponse);
        } finally {
          QueryProfiling.clear();
        }
      }

      @Override
      public void onFailure(Exception e) {
        try {
          delegate.onFailure(e);
        } finally {
          QueryProfiling.clear();
        }
      }
    };
  }
}
