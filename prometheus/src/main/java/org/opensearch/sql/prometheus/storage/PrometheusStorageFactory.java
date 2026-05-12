/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.sql.prometheus.storage;

import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.opensearch.sql.common.setting.Settings;
import org.opensearch.sql.datasource.model.DataSource;
import org.opensearch.sql.datasource.model.DataSourceMetadata;
import org.opensearch.sql.datasource.model.DataSourceType;
import org.opensearch.sql.datasources.auth.AuthenticationType;
import org.opensearch.sql.datasources.utils.DatasourceValidationUtils;
import org.opensearch.sql.prometheus.client.PrometheusClient;
import org.opensearch.sql.prometheus.utils.PrometheusClientUtils;
import org.opensearch.sql.storage.DataSourceFactory;
import org.opensearch.sql.storage.StorageEngine;

@RequiredArgsConstructor
public class PrometheusStorageFactory implements DataSourceFactory {

  public static final String URI = "prometheus.uri";
  public static final String AUTH_TYPE = "prometheus.auth.type";
  public static final String USERNAME = "prometheus.auth.username";
  public static final String PASSWORD = "prometheus.auth.password";
  public static final String REGION = "prometheus.auth.region";
  public static final String ACCESS_KEY = "prometheus.auth.access_key";
  public static final String SECRET_KEY = "prometheus.auth.secret_key";

  public static final String RULER_TYPE = "prometheus.ruler.type";
  public static final String RULER_WORKSPACE_ID = "prometheus.ruler.workspace_id";
  public static final String RULER_ENDPOINT = "prometheus.ruler.endpoint";
  private static final String RULER_TYPE_CORTEX = "cortex";
  private static final String RULER_TYPE_AMP = "amp";

  private final Settings settings;

  @Override
  public DataSourceType getDataSourceType() {
    return DataSourceType.PROMETHEUS;
  }

  @Override
  public DataSource createDataSource(DataSourceMetadata metadata) {
    return new DataSource(
        metadata.getName(), DataSourceType.PROMETHEUS, getStorageEngine(metadata.getProperties()));
  }

  StorageEngine getStorageEngine(Map<String, String> requiredConfig) {
    PrometheusClient prometheusClient;
    try {
      validateDataSourceConfigProperties(requiredConfig);
      DataSourceMetadata metadata =
          new DataSourceMetadata.Builder()
              .setName("prometheus")
              .setConnector(DataSourceType.PROMETHEUS)
              .setProperties(requiredConfig)
              .build();
      prometheusClient = PrometheusClientUtils.createPrometheusClient(metadata, settings);
    } catch (URISyntaxException | UnknownHostException e) {
      throw new IllegalArgumentException(
          String.format("Invalid URI in prometheus properties: %s", e.getMessage()));
    }
    return new PrometheusStorageEngine(prometheusClient);
  }

  // Need to refactor to a separate Validator class.
  private void validateDataSourceConfigProperties(Map<String, String> dataSourceMetadataConfig)
      throws URISyntaxException, UnknownHostException {
    if (dataSourceMetadataConfig.get(AUTH_TYPE) != null) {
      AuthenticationType authenticationType =
          AuthenticationType.get(dataSourceMetadataConfig.get(AUTH_TYPE));
      if (AuthenticationType.BASICAUTH.equals(authenticationType)) {
        DatasourceValidationUtils.validateLengthAndRequiredFields(
            dataSourceMetadataConfig, Set.of(URI, USERNAME, PASSWORD));
      } else if (AuthenticationType.AWSSIGV4AUTH.equals(authenticationType)) {
        DatasourceValidationUtils.validateLengthAndRequiredFields(
            dataSourceMetadataConfig, Set.of(URI, ACCESS_KEY, SECRET_KEY, REGION));
      }
    } else {
      DatasourceValidationUtils.validateLengthAndRequiredFields(
          dataSourceMetadataConfig, Set.of(URI));
    }
    DatasourceValidationUtils.validateHost(
        dataSourceMetadataConfig.get(URI),
        settings.getSettingValue(Settings.Key.DATASOURCES_URI_HOSTS_DENY_LIST));
    validateRulerProperties(dataSourceMetadataConfig);
  }

  private void validateRulerProperties(Map<String, String> config)
      throws URISyntaxException, UnknownHostException {
    String rulerType = config.get(RULER_TYPE);
    if (rulerType == null || RULER_TYPE_CORTEX.equalsIgnoreCase(rulerType)) {
      return;
    }
    if (!RULER_TYPE_AMP.equalsIgnoreCase(rulerType)) {
      throw new IllegalArgumentException(
          String.format(
              "Unsupported %s value '%s'. Supported values: %s, %s.",
              RULER_TYPE, rulerType, RULER_TYPE_CORTEX, RULER_TYPE_AMP));
    }
    DatasourceValidationUtils.validateLengthAndRequiredFields(
        config, Set.of(RULER_WORKSPACE_ID, RULER_ENDPOINT));
    DatasourceValidationUtils.validateHost(
        config.get(RULER_ENDPOINT),
        settings.getSettingValue(Settings.Key.DATASOURCES_URI_HOSTS_DENY_LIST));
  }
}
