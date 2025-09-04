# RFC: Create OpenSearch Direct Query Plugin

## Overview

This RFC proposes the creation of a new OpenSearch plugin repository called `direct-query` that will enable OpenSearch to interact with external data sources beyond the native OpenSearch indices. This plugin will provide a unified interface for not only querying data but also managing resources in various data sources including Prometheus, Amazon S3, and support extensibility for custom data source implementations. The plugin will support full CRUD (Create, Read, Update, Delete) operations on datasource-specific resources such as alerts, metrics, configurations, and more.

## Motivation

Currently, the [OpenSearch SQL plugin](https://github.com/opensearch-project/sql) contains mixed responsibilities: SQL query engine functionality and data source connectivity. This coupling creates several challenges:

1. **Tight Coupling**: Data source implementations are tightly coupled with the SQL engine, making it difficult to maintain and extend
2. **Limited Extensibility**: Adding new data sources requires modifying the core SQL plugin
3. **Code Complexity**: The SQL plugin has grown large and complex with multiple concerns
4. **Reusability**: Data source connectivity cannot be easily reused by other OpenSearch components
5. **Limited Resource Management**: Current implementation focuses only on querying data, not managing datasource resources like alerts, rules, or configurations
6. **No Unified API**: Each datasource requires custom implementations for resource operations

## Proposed Solution

### Summary

**Solution Overview**: This RFC proposes creating a new OpenSearch direct-query plugin by extracting data source connectivity from the SQL plugin, implementing a handler-based architecture, and establishing a complete migration plan to transform OpenSearch's external data interaction capabilities.

**Proposed changes**:
- **New repository**: Create `opensearch-project/direct-query` repository with extracted modules (direct-query-core, async-query, datasources, connectors)
- **Handler-based architecture**: Three specialized interfaces - QueryHandler (data access), ReadResourcesHandler (resource reading), WriteResourcesHandler (resource management)
- **Comprehensive functionality**: Full CRUD operations on both data queries AND external resources (Prometheus alerts, S3 bucket policies, database configurations)
- **Clean separation**: SQL plugin focuses solely on SQL/PPL parsing/execution, delegates all external operations to direct-query plugin
- **Extensible connector system**: Simple API for third-party developers to create connectors with unified query and resource management capabilities
- **REST API framework**: Complete API endpoints for data source management, query execution, and resource operations

**Migration Plan (5 Phases)**:
1. **Repository Setup**: Create new repo, CI/CD pipelines, build system
2. **Code Migration**: Extract direct-query, async-query, and datasources modules from SQL plugin with backward compatibility
3. **Refactoring**: Implement handler interfaces, create connector abstraction layer, establish plugin integration
4. **Integration**: Update SQL plugin to use direct-query plugin, comprehensive testing and validation
5. **Release**: Beta release, community feedback, GA release

**Key Benefits**:
1. **Separation of Concerns**: Clear boundary between query engine and data source connectivity
2. **Unified Resource Management**: Single API for managing resources across all data sources
3. **Extensibility**: Easy addition of new data sources and resource types without modifying core plugins
4. **Maintainability**: Smaller, focused codebases easier to maintain and evolve
5. **Reusability**: Direct query and resource management capabilities available to other OpenSearch components
6. **Community Contributions**: Lower barrier for contributing new connectors with both query and resource support
7. **Performance**: Optimized execution for both queries and resource operations specific to each data source
8. **Operational Efficiency**: Manage external resources directly from OpenSearch without switching tools


### Repository Structure

Create a new `opensearch-project/direct-query` repository that will:

1. **Extract from SQL Plugin**:
   - Move `direct-query` and `direct-query-core` modules from the SQL plugin
   - Migrate [`async-query`](https://github.com/opensearch-project/sql/tree/main/async-query) and [`async-query-core`](https://github.com/opensearch-project/sql/tree/main/async-query-core) modules
   - Transfer `datasources` module for data source management
   - Include recent Prometheus integration work (PRs [`#3440`](https://github.com/opensearch-project/sql/pull/3440) and [`#3441`](https://github.com/opensearch-project/sql/pull/3441))

2. **Core Components**:
   ```
   direct-query/
   ├── direct-query-core/          # Core query interfaces and abstractions
   ├── async-query-core/           # Async query execution framework
   ├── async-query/                # OpenSearch-specific async implementations
   ├── datasources/                # Data source management
   ├── connectors/                 # Built-in connectors
   │   ├── prometheus/
   │   ├── s3/
   │   └── ...
   └── plugin/                     # OpenSearch plugin integration
   ```

### Architecture

#### Core Interfaces

1. **DataSourceEngine Interface**:
   ```java
   public interface DataSourceEngine {
       // Connect to external data source
       DataSourceConnection connect(DataSourceConfig config);
       
       // Get handler for query operations
       QueryHandler getQueryHandler();
       
       // Get handler for read resource operations
       ReadResourcesHandler getReadResourcesHandler();
       
       // Get handler for write resource operations
       WriteResourcesHandler getWriteResourcesHandler();
       
       // Schema discovery
       Schema discoverSchema(DataSourceConfig config);
       
       // Health check
       HealthStatus checkHealth();
   }
   ```

2. **QueryHandler Interface**:
   ```java
   public interface QueryHandler {
       // Execute synchronous query
       QueryResult executeQuery(Query query, DataSourceConnection connection);
       
       // Execute asynchronous query
       CompletableFuture<QueryResult> executeAsyncQuery(Query query, DataSourceConnection connection);
       
       // Validate query syntax
       ValidationResult validateQuery(Query query);
       
       // Get query capabilities
       QueryCapabilities getCapabilities();
   }
   ```

3. **ReadResourcesHandler Interface**:
   ```java
   public interface ReadResourcesHandler {
       // List available resource types
       Set<ResourceType> getSupportedResourceTypes();
       
       // List resources of a specific type
       ResourceList listResources(ResourceType type, ResourceFilter filter, DataSourceConnection connection);
       
       // Get a specific resource
       Resource getResource(ResourceType type, String resourceId, DataSourceConnection connection);
       
       // Search resources with advanced filters
       ResourceSearchResult searchResources(ResourceType type, SearchQuery query, DataSourceConnection connection);
       
       // Get resource metadata
       ResourceMetadata getResourceMetadata(ResourceType type, String resourceId, DataSourceConnection connection);
   }
   ```

4. **WriteResourcesHandler Interface**:
   ```java
   public interface WriteResourcesHandler {
       // Create a new resource
       Resource createResource(ResourceType type, ResourceDefinition definition, DataSourceConnection connection);
       
       // Update an existing resource
       Resource updateResource(ResourceType type, String resourceId, ResourceDefinition definition, DataSourceConnection connection);
       
       // Delete a resource
       void deleteResource(ResourceType type, String resourceId, DataSourceConnection connection);
       
       // Bulk operations
       BulkOperationResult bulkCreate(ResourceType type, List<ResourceDefinition> definitions, DataSourceConnection connection);
       BulkOperationResult bulkUpdate(ResourceType type, Map<String, ResourceDefinition> updates, DataSourceConnection connection);
       BulkOperationResult bulkDelete(ResourceType type, List<String> resourceIds, DataSourceConnection connection);
       
       // Validate resource definition before write
       ValidationResult validateResourceDefinition(ResourceType type, ResourceDefinition definition);
   }
   ```

5. **ResourceType Enum** (Examples):
   ```java
   public enum ResourceType {
       // Prometheus resources
       ALERT_RULE,
       RECORDING_RULE,
       SILENCE,
              
       // S3 resources
       BUCKET_POLICY,
       LIFECYCLE_RULE,
       
       // Generic
       CONFIGURATION,
       PERMISSION,
       CUSTOM
   }
   ```

6. **Query Interface**:
   ```java
   public interface Query {
       String getQueryString();
       Map<String, Object> getParameters();
       QueryType getType(); // SQL, PPL, NATIVE, etc.
       TimeRange getTimeRange();
   }
   ```

7. **Extensibility API**:
   ```java
   public interface DataSourceConnector {
       // Unique identifier for the connector
       String getType();
       
       // Create engine instance
       DataSourceEngine createEngine(ConnectorConfig config);
       
       // Supported query types
       Set<QueryType> getSupportedQueryTypes();
       
       // Supported resource operations
       Set<ResourceType> getSupportedResourceTypes();
       
       // Configuration schema
       ConfigSchema getConfigurationSchema();
   }
   ```

### Key Features

1. **Plugin Architecture**:
   - Independent OpenSearch plugin deployable alongside SQL plugin
   - RESTful APIs for data source management, query execution, and resource operations
   - Integration with OpenSearch security and access control
   - Unified interface for both data access and resource management

2. **Built-in Connectors with Resource Management**:
   - **Prometheus**: Time-series queries (PromQL) + Alert rules, recording rules, and silences management
   - **Amazon S3**: Query structured data (Parquet, JSON, CSV) + Bucket policies and lifecycle rules
   - **JDBC**: Generic database connectivity with configuration management (future)

3. **Comprehensive Resource Operations**:
   - Full CRUD operations on datasource-specific resources
   - Bulk operations for efficient resource management
   - Resource filtering and search capabilities
   - Resource versioning and change tracking

4. **Async Query Support**:
   - Long-running query execution
   - Result caching and pagination
   - Query status tracking and cancellation
   - Background resource synchronization

5. **Developer Experience**:
   - Simple connector development API with resource management interfaces
   - Maven/Gradle artifacts for third-party connector development
   - Comprehensive documentation with examples for both queries and resources
   - Type-safe resource definitions and operations

### API Design

#### REST Endpoints

```
# Data source management
PUT /_plugins/_direct_query/datasource/{name}
GET /_plugins/_direct_query/datasource/{name}
DELETE /_plugins/_direct_query/datasource/{name}
GET /_plugins/_direct_query/datasource

# Query execution
POST /_plugins/_direct_query/_execute
{
  "datasource": "my-prometheus",
  "query": "rate(http_requests_total[5m])",
  "format": "json"
}

# Async query
POST /_plugins/_direct_query/_async_execute
GET /_plugins/_direct_query/_async_query/{query_id}
DELETE /_plugins/_direct_query/_async_query/{query_id}

# Schema discovery
GET /_plugins/_direct_query/datasource/{name}/_schema

# Resource management operations
GET /_plugins/_direct_query/datasource/{name}/resources/{type}
POST /_plugins/_direct_query/datasource/{name}/resources/{type}/_search
GET /_plugins/_direct_query/datasource/{name}/resources/{type}/{id}
PUT /_plugins/_direct_query/datasource/{name}/resources/{type}/{id}
POST /_plugins/_direct_query/datasource/{name}/resources/{type}
DELETE /_plugins/_direct_query/datasource/{name}/resources/{type}/{id}

# Bulk resource operations
POST /_plugins/_direct_query/datasource/{name}/resources/{type}/_bulk
{
  "operations": [
    {"action": "create", "definition": {...}},
    {"action": "update", "id": "resource1", "definition": {...}},
    {"action": "delete", "id": "resource2"}
  ]
}
```

### Integration with SQL Plugin

The SQL plugin will be refactored to:

1. **Remove** data source-specific code
2. **Depend on** direct-query plugin for external data source queries
3. **Focus on** SQL/PPL parsing, planning, and execution
4. **Delegate** external queries to direct-query plugin via well-defined interfaces

```java
// SQL Plugin integration
public class DirectQueryStorageEngine implements StorageEngine {
    private final DirectQueryClient client;
    
    @Override
    public Table getTable(DataSourceSchemaName dataSourceSchemaName, String tableName) {
        return client.getTable(dataSourceSchemaName, tableName);
    }
}
```

## Migration Plan

### Phase 1: Repository Setup
- Create new repository `opensearch-project/direct-query`
- Set up CI/CD pipelines
- Establish code structure and build system

### Phase 2: Code Migration
- Extract direct-query modules from SQL plugin
- Move async-query modules
- Migrate datasources module
- Ensure backward compatibility

### Phase 3: Refactoring
- Define clean interfaces and APIs
- Refactor existing code to new architecture
- Create connector abstraction layer
- Implement plugin integration

### Phase 4: Integration
- Update SQL plugin to use direct-query plugin
- Testing and validation
- Documentation updates
- Performance optimization

### Phase 5: Release
- Beta release with core functionality
- Gather feedback from community
- Address issues and improvements
- GA release

## Risks and Mitigation

| Risk | Mitigation |
|------|------------|
| Breaking changes for existing users | Maintain backward compatibility layer during transition |
| Increased deployment complexity | Provide clear migration guides and tooling |
| Performance overhead from plugin communication | Optimize inter-plugin communication, consider native integration |
| Connector quality variance | Establish certification program and quality standards |


## Open Questions

1. Should the direct-query plugin be required for SQL plugin operation or optional?
2. How to handle version compatibility between SQL and direct-query plugins?
3. Should we support federation queries across multiple data sources?
4. What level of SQL/PPL support should each connector provide?

## References

- [OpenSearch SQL Plugin](https://github.com/opensearch-project/sql)
- [Async Query Design](https://github.com/opensearch-project/sql/tree/main/async-query-core)
- [Prometheus Integration PR](#) (to be linked)
- [Apache Calcite](https://calcite.apache.org/) - Query optimization framework


## Conclusion

The proposed OpenSearch Direct Query Plugin represents a significant architectural improvement that will transform how OpenSearch interacts with external data sources. By introducing a handler-based architecture with separate QueryHandler, ReadResourcesHandler, and WriteResourcesHandler interfaces, this plugin will provide a unified, extensible framework for both data querying and comprehensive resource management across diverse data sources.

This separation of concerns will not only simplify the SQL plugin's architecture but also create new opportunities for the OpenSearch ecosystem. The plugin will enable developers to build rich connectors that go beyond simple data access to provide full lifecycle management of external resources like Prometheus alerts and S3 policies. The comprehensive example implementations demonstrate how this architecture can be practically applied to real-world data sources.

The direct-query plugin will serve as a foundation for OpenSearch's evolution into a unified data platform that can seamlessly integrate with and manage resources across the entire data infrastructure landscape, while maintaining the simplicity and extensibility that developers expect from the OpenSearch ecosystem.

---

*This RFC is open for community feedback. Please comment with your thoughts, concerns, and suggestions.*

---

## Appendix

### Example: Prometheus Resource Management

```java
// Fetching alerts from Prometheus
GET /_plugins/_direct_query/datasource/my-prometheus/resources/ALERT_RULE
Response:
{
  "resources": [
    {
      "id": "high_cpu_usage",
      "type": "ALERT_RULE",
      "definition": {
        "alert": "HighCPUUsage",
        "expr": "avg(rate(cpu_usage[5m])) > 0.8",
        "for": "10m",
        "labels": {
          "severity": "warning"
        },
        "annotations": {
          "summary": "High CPU usage detected"
        }
      },
      "metadata": {
        "created": "2024-01-15T10:00:00Z",
        "modified": "2024-01-20T15:30:00Z",
        "state": "firing"
      }
    }
  ]
}

// Creating a silence in Prometheus
POST /_plugins/_direct_query/datasource/my-prometheus/resources/SILENCE
{
  "definition": {
    "matchers": [
      {
        "name": "alertname",
        "value": "HighCPUUsage",
        "isRegex": false
      }
    ],
    "startsAt": "2024-01-25T10:00:00Z",
    "endsAt": "2024-01-25T12:00:00Z",
    "createdBy": "admin",
    "comment": "Scheduled maintenance window"
  }
}

// Updating an alert rule
PUT /_plugins/_direct_query/datasource/my-prometheus/resources/ALERT_RULE/high_cpu_usage
{
  "definition": {
    "alert": "HighCPUUsage",
    "expr": "avg(rate(cpu_usage[5m])) > 0.9",  // Changed threshold
    "for": "5m",  // Reduced time window
    "labels": {
      "severity": "critical"  // Increased severity
    },
    "annotations": {
      "summary": "Critical CPU usage detected",
      "description": "CPU usage has exceeded 90% for 5 minutes"
    }
  }
}
```

### Example: Custom Connector Implementation

```java
@DirectQueryConnector
public class MongoDBConnector implements DataSourceConnector {
    
    @Override
    public String getType() {
        return "mongodb";
    }
    
    @Override
    public DataSourceEngine createEngine(ConnectorConfig config) {
        return new MongoDBEngine(config);
    }
    
    @Override
    public Set<QueryType> getSupportedQueryTypes() {
        return Set.of(QueryType.SQL, QueryType.NATIVE);
    }
    
    @Override
    public Set<ResourceType> getSupportedResourceTypes() {
        return Set.of(
            ResourceType.CONFIGURATION,
            ResourceType.PERMISSION,
            ResourceType.CUSTOM
        );
    }
    
    @Override
    public ConfigSchema getConfigurationSchema() {
        return ConfigSchema.builder()
            .required("connectionString", STRING)
            .required("database", STRING)
            .optional("authMechanism", STRING, "SCRAM-SHA-256")
            .build();
    }
}

// MongoDB Engine Implementation
public class MongoDBEngine implements DataSourceEngine {
    private final ConnectorConfig config;
    private final MongoDBQueryHandler queryHandler;
    private final MongoDBReadResourcesHandler readHandler;
    private final MongoDBWriteResourcesHandler writeHandler;
    
    public MongoDBEngine(ConnectorConfig config) {
        this.config = config;
        this.queryHandler = new MongoDBQueryHandler();
        this.readHandler = new MongoDBReadResourcesHandler();
        this.writeHandler = new MongoDBWriteResourcesHandler();
    }
    
    @Override
    public DataSourceConnection connect(DataSourceConfig config) {
        MongoClient client = MongoClients.create(config.getString("connectionString"));
        return new MongoDBConnection(client, config.getString("database"));
    }
    
    @Override
    public QueryHandler getQueryHandler() {
        return queryHandler;
    }
    
    @Override
    public ReadResourcesHandler getReadResourcesHandler() {
        return readHandler;
    }
    
    @Override
    public WriteResourcesHandler getWriteResourcesHandler() {
        return writeHandler;
    }
    
    @Override
    public Schema discoverSchema(DataSourceConfig config) {
        try (DataSourceConnection conn = connect(config)) {
            // Discover collections and their schemas
            return new MongoDBSchemaDiscoverer().discover(conn);
        }
    }
    
    @Override
    public HealthStatus checkHealth() {
        try (DataSourceConnection conn = connect(config)) {
            ((MongoDBConnection) conn).getDatabase().runCommand(new Document("ping", 1));
            return HealthStatus.healthy();
        } catch (Exception e) {
            return HealthStatus.unhealthy(e.getMessage());
        }
    }
}

// MongoDB Query Handler Implementation
public class MongoDBQueryHandler implements QueryHandler {
    
    @Override
    public QueryResult executeQuery(Query query, DataSourceConnection connection) {
        MongoDBConnection mongoConn = (MongoDBConnection) connection;
        MongoDatabase db = mongoConn.getDatabase();
        
        if (query.getType() == QueryType.NATIVE) {
            // Execute native MongoDB query
            Document mongoQuery = Document.parse(query.getQueryString());
            MongoCollection<Document> collection = db.getCollection(query.getParameters().get("collection").toString());
            List<Document> results = collection.find(mongoQuery).into(new ArrayList<>());
            return new QueryResult(results);
        } else if (query.getType() == QueryType.SQL) {
            // Convert SQL to MongoDB query
            MongoQuery mongoQuery = new SqlToMongoTranslator().translate(query.getQueryString());
            return executeMongoQuery(mongoQuery, db);
        }
        
        throw new UnsupportedOperationException("Query type not supported: " + query.getType());
    }
    
    @Override
    public CompletableFuture<QueryResult> executeAsyncQuery(Query query, DataSourceConnection connection) {
        return CompletableFuture.supplyAsync(() -> executeQuery(query, connection));
    }
    
    @Override
    public ValidationResult validateQuery(Query query) {
        try {
            if (query.getType() == QueryType.NATIVE) {
                Document.parse(query.getQueryString());
            } else if (query.getType() == QueryType.SQL) {
                new SqlToMongoTranslator().validate(query.getQueryString());
            }
            return ValidationResult.valid();
        } catch (Exception e) {
            return ValidationResult.invalid(e.getMessage());
        }
    }
    
    @Override
    public QueryCapabilities getCapabilities() {
        return QueryCapabilities.builder()
            .supportsAggregation(true)
            .supportsJoins(false)
            .supportsTimeRange(true)
            .supportsFullTextSearch(true)
            .build();
    }
}

// MongoDB Read Resources Handler Implementation
public class MongoDBReadResourcesHandler implements ReadResourcesHandler {
    
    @Override
    public Set<ResourceType> getSupportedResourceTypes() {
        return Set.of(
            ResourceType.CONFIGURATION,
            ResourceType.PERMISSION,
            ResourceType.CUSTOM
        );
    }
    
    @Override
    public ResourceList listResources(ResourceType type, ResourceFilter filter, DataSourceConnection connection) {
        MongoDBConnection mongoConn = (MongoDBConnection) connection;
        MongoDatabase db = mongoConn.getDatabase();
        
        switch (type) {
            case CONFIGURATION:
                return listDatabaseConfigurations(db, filter);
            case PERMISSION:
                return listDatabasePermissions(db, filter);
            case CUSTOM:
                return listCustomResources(db, filter);
            default:
                throw new UnsupportedOperationException("Resource type not supported: " + type);
        }
    }
    
    @Override
    public Resource getResource(ResourceType type, String resourceId, DataSourceConnection connection) {
        MongoDBConnection mongoConn = (MongoDBConnection) connection;
        MongoDatabase db = mongoConn.getDatabase();
        
        Document resource = db.getCollection(getCollectionForType(type))
            .find(new Document("_id", resourceId))
            .first();
            
        if (resource == null) {
            throw new ResourceNotFoundException(type, resourceId);
        }
        
        return new MongoDBResource(resourceId, type, resource);
    }
    
    @Override
    public ResourceSearchResult searchResources(ResourceType type, SearchQuery query, DataSourceConnection connection) {
        MongoDBConnection mongoConn = (MongoDBConnection) connection;
        MongoDatabase db = mongoConn.getDatabase();
        
        Document searchQuery = buildSearchQuery(query);
        List<Document> results = db.getCollection(getCollectionForType(type))
            .find(searchQuery)
            .into(new ArrayList<>());
            
        return new ResourceSearchResult(
            results.stream()
                .map(doc -> new MongoDBResource(doc.getString("_id"), type, doc))
                .collect(Collectors.toList()),
            results.size()
        );
    }
    
    @Override
    public ResourceMetadata getResourceMetadata(ResourceType type, String resourceId, DataSourceConnection connection) {
        Resource resource = getResource(type, resourceId, connection);
        MongoDBResource mongoResource = (MongoDBResource) resource;
        
        return ResourceMetadata.builder()
            .resourceId(resourceId)
            .resourceType(type)
            .createdAt(mongoResource.getDocument().getDate("createdAt"))
            .modifiedAt(mongoResource.getDocument().getDate("modifiedAt"))
            .version(mongoResource.getDocument().getInteger("version", 1))
            .build();
    }
    
    private String getCollectionForType(ResourceType type) {
        switch (type) {
            case CONFIGURATION: return "configurations";
            case PERMISSION: return "permissions";
            case CUSTOM: return "custom_resources";
            default: throw new IllegalArgumentException("Unknown resource type: " + type);
        }
    }
}

// MongoDB Write Resources Handler Implementation
public class MongoDBWriteResourcesHandler implements WriteResourcesHandler {
    
    @Override
    public Resource createResource(ResourceType type, ResourceDefinition definition, DataSourceConnection connection) {
        MongoDBConnection mongoConn = (MongoDBConnection) connection;
        MongoDatabase db = mongoConn.getDatabase();
        
        ValidationResult validation = validateResourceDefinition(type, definition);
        if (!validation.isValid()) {
            throw new ValidationException(validation.getErrors());
        }
        
        Document document = new Document()
            .append("_id", generateResourceId())
            .append("type", type.name())
            .append("definition", definition.toDocument())
            .append("createdAt", new Date())
            .append("modifiedAt", new Date())
            .append("version", 1);
            
        db.getCollection(getCollectionForType(type)).insertOne(document);
        
        return new MongoDBResource(document.getString("_id"), type, document);
    }
    
    @Override
    public Resource updateResource(ResourceType type, String resourceId, ResourceDefinition definition, DataSourceConnection connection) {
        MongoDBConnection mongoConn = (MongoDBConnection) connection;
        MongoDatabase db = mongoConn.getDatabase();
        
        ValidationResult validation = validateResourceDefinition(type, definition);
        if (!validation.isValid()) {
            throw new ValidationException(validation.getErrors());
        }
        
        Document update = new Document("$set", new Document()
            .append("definition", definition.toDocument())
            .append("modifiedAt", new Date()))
            .append("$inc", new Document("version", 1));
            
        Document result = db.getCollection(getCollectionForType(type))
            .findOneAndUpdate(
                new Document("_id", resourceId),
                update,
                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
            );
            
        if (result == null) {
            throw new ResourceNotFoundException(type, resourceId);
        }
        
        return new MongoDBResource(resourceId, type, result);
    }
    
    @Override
    public void deleteResource(ResourceType type, String resourceId, DataSourceConnection connection) {
        MongoDBConnection mongoConn = (MongoDBConnection) connection;
        MongoDatabase db = mongoConn.getDatabase();
        
        DeleteResult result = db.getCollection(getCollectionForType(type))
            .deleteOne(new Document("_id", resourceId));
            
        if (result.getDeletedCount() == 0) {
            throw new ResourceNotFoundException(type, resourceId);
        }
    }
    
    @Override
    public BulkOperationResult bulkCreate(ResourceType type, List<ResourceDefinition> definitions, DataSourceConnection connection) {
        List<Resource> created = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        for (ResourceDefinition definition : definitions) {
            try {
                created.add(createResource(type, definition, connection));
            } catch (Exception e) {
                errors.add("Failed to create resource: " + e.getMessage());
            }
        }
        
        return new BulkOperationResult(created.size(), errors.size(), errors);
    }
    
    @Override
    public BulkOperationResult bulkUpdate(ResourceType type, Map<String, ResourceDefinition> updates, DataSourceConnection connection) {
        int successCount = 0;
        List<String> errors = new ArrayList<>();
        
        for (Map.Entry<String, ResourceDefinition> entry : updates.entrySet()) {
            try {
                updateResource(type, entry.getKey(), entry.getValue(), connection);
                successCount++;
            } catch (Exception e) {
                errors.add("Failed to update resource " + entry.getKey() + ": " + e.getMessage());
            }
        }
        
        return new BulkOperationResult(successCount, errors.size(), errors);
    }
    
    @Override
    public BulkOperationResult bulkDelete(ResourceType type, List<String> resourceIds, DataSourceConnection connection) {
        int successCount = 0;
        List<String> errors = new ArrayList<>();
        
        for (String resourceId : resourceIds) {
            try {
                deleteResource(type, resourceId, connection);
                successCount++;
            } catch (Exception e) {
                errors.add("Failed to delete resource " + resourceId + ": " + e.getMessage());
            }
        }
        
        return new BulkOperationResult(successCount, errors.size(), errors);
    }
    
    @Override
    public ValidationResult validateResourceDefinition(ResourceType type, ResourceDefinition definition) {
        List<String> errors = new ArrayList<>();
        
        // Validate required fields based on resource type
        switch (type) {
            case CONFIGURATION:
                if (definition.get("name") == null) {
                    errors.add("Configuration name is required");
                }
                if (definition.get("value") == null) {
                    errors.add("Configuration value is required");
                }
                break;
            case PERMISSION:
                if (definition.get("role") == null) {
                    errors.add("Permission role is required");
                }
                if (definition.get("resource") == null) {
                    errors.add("Permission resource is required");
                }
                break;
            case CUSTOM:
                // Custom resources have flexible schema
                break;
        }
        
        return errors.isEmpty() ? ValidationResult.valid() : ValidationResult.invalid(errors);
    }
    
    private String getCollectionForType(ResourceType type) {
        switch (type) {
            case CONFIGURATION: return "configurations";
            case PERMISSION: return "permissions";
            case CUSTOM: return "custom_resources";
            default: throw new IllegalArgumentException("Unknown resource type: " + type);
        }
    }
    
    private String generateResourceId() {
        return UUID.randomUUID().toString();
    }
}
```
