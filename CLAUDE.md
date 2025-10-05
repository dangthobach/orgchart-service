# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spring Boot 3.4.6 service for organizational chart management and Excel data migration, built with Java 17. The project uses **Hexagonal Architecture (Clean Architecture)** and features a high-performance Excel migration system capable of handling 1-2 million records.

## Build & Development Commands

```bash
# Build the project
./mvnw clean install

# Run tests
./mvnw test

# Run specific test
./mvnw test -Dtest=ClassName#methodName

# Run the application
./mvnw spring-boot:run

# Build without tests
./mvnw clean install -DskipTests

# Package as JAR
./mvnw clean package
```

## Architecture

### Hexagonal Architecture Pattern

The codebase follows Hexagonal Architecture (Ports & Adapters) with clear separation:

- **Domain Layer** (`com.learnmore.domain.*`): Core business entities and domain models
  - Pure Java objects, no framework dependencies
  - Examples: `User`, `Role`, `Team`, `MigrationJob`

- **Application Layer** (`com.learnmore.application.*`): Business logic and use cases
  - `port.input.*`: Service interfaces (use cases)
  - `port.output.*`: Repository interfaces (output ports)
  - `service.*`: Service implementations
  - `dto.*`: Data Transfer Objects

- **Infrastructure Layer** (`com.learnmore.infrastructure.*`): External integrations
  - `persistence.entity.*`: JPA entities (separate from domain)
  - `persistence.mapper.*`: Map between domain and persistence entities
  - `repository.*`: JPA repositories implementing output ports
  - `adapter.*`: Adapters implementing port interfaces

- **Controller Layer** (`com.learnmore.controller.*`): REST API endpoints
  - HTTP layer, uses Application services

### Key Architecture Principles

1. **Dependency Rule**: Dependencies point inward (Infrastructure → Application → Domain)
2. **Mappers**: Use `AbstractMapper` pattern to convert between domain and persistence entities
3. **Bean Configuration**: Service beans wired in `BeanConfiguration.java`
4. **Security**: All APIs currently permitAll (see `SecurityConfig.java`)

## Excel Migration System

### 4-Phase Migration Architecture

The migration system processes large Excel files through 4 distinct phases:

#### Phase 1: Ingest & Staging
- Service: `ExcelIngestService`
- Reads Excel with **SAX-based streaming** (no WorkbookFactory)
- Normalizes data and saves to `staging_raw` table
- Batch processing: 5,000 records per batch (configurable)

#### Phase 2: Validation
- Service: `ValidationService`
- Validates required fields, formats, enums
- Checks duplicates in file and database
- Validates references to master tables
- Errors saved to `staging_error`, valid data to `staging_valid`

#### Phase 3: Apply Data
- Service: `DataApplyService`
- Inserts validated data into master tables
- Follows dependency order
- Bulk inserts for performance
- Idempotent operations

#### Phase 4: Reconciliation & Monitoring
- Service: `MonitoringService`
- Reconciles data between staging and master tables
- Generates statistics and metrics
- Cleanup staging data (optional)

### Excel Processing Performance

The project uses **ExcelFacade** with Strategy Pattern to handle large files efficiently:

**Architecture:**
- **ExcelFacade**: Main entry point for all Excel operations (READ THIS FIRST!)
- **ExcelReadingService / ExcelWritingService**: Core services with strategy selection
- **Strategy Pattern**: Automatic selection of optimal read/write strategy

**Key Components:**
- **TrueStreamingSAXProcessor**: Pure SAX parsing, processes batches immediately without accumulating in memory
- **ExcelEarlyValidator**: Validates file size before processing to prevent OOM errors
- **TrueStreamingMultiSheetProcessor**: SAX-based multi-sheet processing
- **@ExcelColumn**: Annotation for mapping Excel columns to Java fields

**Read Strategies (auto-selected by ReadStrategySelector):**
- **StreamingReadStrategy** (default): SAX streaming for any file size
- **ParallelReadStrategy**: Multi-threaded processing for large files
- **CachedReadStrategy**: Cache results for repeated reads (requires CacheManager)
- **ValidatingReadStrategy**: JSR-303 validation (requires spring-boot-starter-validation)
- **MultiSheetReadStrategy**: Process all sheets in workbook

**Write Strategies (auto-selected by WriteStrategySelector):**
- **XSSFWriteStrategy**: Standard write for ≤100K records
- **SXSSFWriteStrategy**: Streaming write for 100K-1M records
- **CSVWriteStrategy**: CSV fallback for >1M records
- **StyledWriteStrategy**: Professional styling (skeleton)
- **TemplateWriteStrategy**: Template-based reports (skeleton)
- **MultiSheetWriteStrategy**: Multiple sheets (skeleton)

**IMPORTANT - Migration from ExcelUtil:**
- ❌ **DEPRECATED**: `ExcelUtil` class has been removed
- ✅ **USE**: `ExcelFacade` (injected via @Autowired or constructor)
- ✅ **USE**: `ExcelProcessingService` for high-level operations
- ✅ **USE**: `ReactiveExcelUtil` for reactive/WebFlux apps

**Example Usage:**
```java
// Inject ExcelFacade
@Autowired
private ExcelFacade excelFacade;

// Simple read (small files)
List<User> users = excelFacade.readExcel(inputStream, User.class);

// Batch read (large files)
excelFacade.readExcel(inputStream, User.class, batch -> {
    userRepository.saveAll(batch);
});

// Simple write
excelFacade.writeExcel("output.xlsx", users);

// Fluent API
List<User> users = excelFacade.reader(User.class)
    .batchSize(10000)
    .parallel()
    .read(inputStream);
```

### Database Schema

**Master Tables** (business data):
- `warehouse`, `unit`, `doc_type`, `status`, `location`, `retention_period`
- `box`, `case_detail` (main table)

**Staging Tables** (migration tracking):
- `staging_raw`: Raw Excel data
- `staging_valid`: Validated records
- `staging_error`: Validation errors
- `migration_job`: Job tracking and status

### Migration API Endpoints

All endpoints under `/api/migration/`:

```bash
# Synchronous upload and migration
POST /excel/upload

# Asynchronous upload and migration
POST /excel/upload-async

# Check job status
GET /job/{jobId}/status

# Manual phase execution (debugging)
POST /excel/ingest-only
POST /job/{jobId}/validate
POST /job/{jobId}/apply
POST /job/{jobId}/reconcile

# System monitoring
GET /system/metrics

# Cleanup
DELETE /job/{jobId}/cleanup?keepErrors=true
```

## Database Configuration

- **Development**: H2 in-memory database (default)
  - Console: http://localhost:8080/api/h2-console
  - URL: `jdbc:h2:mem:devdb`
  - Username: `sa`, Password: `password`

- **Production**: PostgreSQL (configure in `application.yml`)

- **JPA**:
  - DDL auto: `create-drop` (dev), change to `validate` for production
  - Physical naming: Standard (no snake_case conversion)
  - SQL logging enabled for debugging

## Key Technologies

- **Spring Boot 3.4.6** with Spring Data JPA, Spring Security, Spring Web
- **Apache POI 5.2.5**: Excel processing with SAX streaming
- **Spring WebFlux + Project Reactor**: Reactive support for streaming
- **Lombok**: Reduce boilerplate
- **Swagger/OpenAPI 2.2.0**: API documentation at `/api/swagger-ui/`
- **H2 Database**: In-memory for dev/test
- **PostgreSQL**: Production database
- **DataFaker 2.0.2**: Generate realistic test data

## Application Configuration

Server runs on port 8080 with context path `/api`:
- Base URL: `http://localhost:8080/api`
- Swagger UI: `http://localhost:8080/api/swagger-ui/`
- H2 Console: `http://localhost:8080/api/h2-console`

Active profile: `dev` (configurable in `application.yml`)

## Testing

Test structure mirrors main source:
- Unit tests: `*Test.java`
- Integration tests: `*IntegrationTest.java`
- Performance benchmarks: `*PerformanceBenchmark.java`, `*BenchmarkTest.java`

Key test classes:
- `MigrationControllerTest`: API endpoint testing
- `ExcelIngestServiceTest`: Migration ingest testing
- `ExcelDimensionValidatorTest`: Dimension validation testing
- `MultiSheetExcelTest`: Multi-sheet processing tests

## Additional Documentation

For detailed information, refer to these files:
- `EXCEL_MIGRATION_README.md`: Complete migration system documentation
- `TRUE_STREAMING_README.md`: True streaming implementation details
- `EXCEL_PERFORMANCE_ANALYSIS.md`: Performance analysis and optimization
