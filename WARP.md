# WARP.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

## Project Overview

Spring Boot 3.4.6 service for organizational chart management and Excel data migration, built with Java 17. The project implements **Hexagonal Architecture (Clean Architecture)** and features a high-performance Excel migration system capable of handling 1-2 million records.

## Build & Development Commands

**Build the project:**
```bash
./mvnw clean install
```

**Run tests:**
```bash
./mvnw test
```

**Run specific test:**
```bash
./mvnw test -Dtest=ClassName#methodName
```

**Run the application:**
```bash
./mvnw spring-boot:run
```

**Build without tests:**
```bash
./mvnw clean install -DskipTests
```

**Package as JAR:**
```bash
./mvnw clean package
```

## Architecture Overview

### Hexagonal Architecture Implementation

The codebase follows strict **Hexagonal Architecture (Ports & Adapters)** with clear separation:

- **Domain Layer** (`com.learnmore.domain.*`): Pure business entities
  - No framework dependencies, contains core models like `User`, `Role`, `Team`, `MigrationJob`

- **Application Layer** (`com.learnmore.application.*`): Business logic and use cases
  - `port.input.*`: Service interfaces (use cases)
  - `port.output.*`: Repository interfaces (output ports) 
  - `service.*`: Service implementations
  - `dto.*`: Data Transfer Objects

- **Infrastructure Layer** (`com.learnmore.infrastructure.*`): External integrations
  - `persistence.entity.*`: JPA entities (separate from domain)
  - `persistence.mapper.*`: Map between domain and persistence entities using `AbstractMapper` pattern
  - `repository.*`: JPA repositories implementing output ports
  - `adapter.*`: Adapters implementing port interfaces

- **Controller Layer** (`com.learnmore.controller.*`): REST API endpoints

**Key Principles:**
- Dependencies point inward (Infrastructure → Application → Domain)
- Service beans are wired in `BeanConfiguration.java`
- All APIs currently permit all access (see `SecurityConfig.java`)

## Excel Migration System Architecture

### 4-Phase Migration Process

The migration system processes large Excel files through 4 distinct phases:

#### Phase 1: Ingest & Staging
- **Service**: `ExcelIngestService`
- Reads Excel using **SAX-based streaming** (no WorkbookFactory)
- Normalizes data and saves to `staging_raw` table
- Batch processing: 5,000 records per batch (configurable)

#### Phase 2: Validation  
- **Service**: `ValidationService`
- Validates required fields, formats, enums
- Checks duplicates in file and database
- Validates references to master tables
- Errors saved to `staging_error`, valid data to `staging_valid`

#### Phase 3: Apply Data
- **Service**: `DataApplyService`
- Inserts validated data into master tables
- Follows dependency order: `warehouse`, `unit`, `doc_type`, `status`, etc.
- Bulk inserts for performance, idempotent operations

#### Phase 4: Reconciliation & Monitoring
- **Service**: `MonitoringService`
- Reconciles data between staging and master tables
- Generates statistics and metrics
- Optional cleanup of staging data

### Excel Processing Architecture

**Core Entry Point**: Always use `ExcelFacade` (NOT the deprecated `ExcelUtil`)

**Strategy Pattern Implementation:**
- **ExcelFacade**: Main entry point with simple API
- **ExcelReadingService/ExcelWritingService**: Core services with automatic strategy selection
- **ReadStrategySelector/WriteStrategySelector**: Intelligent strategy selection

**Read Strategies (auto-selected):**
- **StreamingReadStrategy** (Priority 0): SAX streaming for any file size
- **MultiSheetReadStrategy** (Priority 5): Process all sheets in workbook
- **ParallelReadStrategy** (Priority 10): Multi-threaded for large files

**Write Strategies (auto-selected):**
- **XSSFWriteStrategy** (≤100K records): Standard Excel format
- **SXSSFWriteStrategy** (100K-1M records): Streaming write
- **CSVWriteStrategy** (>1M records): 10x faster CSV fallback

**Key Components:**
- **TrueStreamingSAXProcessor**: Pure SAX parsing with immediate batch processing
- **ExcelEarlyValidator**: Validates file size before processing to prevent OOM
- **TrueStreamingMultiSheetProcessor**: SAX-based multi-sheet processing
- **@ExcelColumn**: Annotation for mapping Excel columns to Java fields

## Database Configuration

- **Development**: H2 in-memory database (default)
  - Console: http://localhost:8080/api/h2-console
  - URL: `jdbc:h2:mem:devdb`
  - Username: `sa`, Password: `password`

- **Production**: PostgreSQL (configure in `application.yml`)

- **JPA Settings**:
  - DDL auto: `create-drop` (dev) - change to `validate` for production
  - Physical naming: Standard (no snake_case conversion)

## Key API Endpoints

Base URL: `http://localhost:8080/api`

**Excel Migration APIs:**
```bash
# Upload Excel synchronously
POST /migration/excel/upload
# multipart/form-data: file, createdBy, maxRows

# Upload Excel asynchronously  
POST /migration/excel/upload-async
# multipart/form-data: file, createdBy, maxRows

# Check job status
GET /migration/job/{jobId}/status

# System metrics
GET /migration/system/metrics

# Manual phase execution (debugging)
POST /migration/excel/ingest-only
POST /migration/job/{jobId}/validate
POST /migration/job/{jobId}/apply
POST /migration/job/{jobId}/reconcile

# Cleanup staging data
DELETE /migration/job/{jobId}/cleanup?keepErrors=true
```

**Other Important URLs:**
- Swagger UI: http://localhost:8080/api/swagger-ui/
- H2 Console: http://localhost:8080/api/h2-console

## ExcelFacade Usage Examples

**IMPORTANT**: Always use `ExcelFacade` instead of the deprecated `ExcelUtil`:

```java
// Inject ExcelFacade
@Autowired
private ExcelFacade excelFacade;

// Simple read (small files)
List<User> users = excelFacade.readExcel(inputStream, User.class);

// Batch read (large files) - RECOMMENDED
excelFacade.readExcel(inputStream, User.class, batch -> {
    userRepository.saveAll(batch);
});

// Simple write (auto-selects strategy)
excelFacade.writeExcel("output.xlsx", users);

// Fluent API
List<User> users = excelFacade.reader(User.class)
    .batchSize(10000)
    .parallel()
    .read(inputStream);
```

## Testing Commands

**Upload Excel file for testing:**
```bash
# PowerShell
$form = @{
    file = Get-Item -Path "test-data.xlsx"
    createdBy = "admin"
    maxRows = 1000
}
Invoke-RestMethod -Uri "http://localhost:8080/api/migration/excel/upload-async" -Method Post -Form $form

# cURL
curl -X POST "http://localhost:8080/api/migration/excel/upload-async" \
  -F "file=@test-data.xlsx" \
  -F "createdBy=admin" \
  -F "maxRows=1000"
```

## Key Technologies

- **Spring Boot 3.4.6** with Spring Data JPA, Security, Web, WebFlux
- **Apache POI 5.2.5**: Excel processing with SAX streaming  
- **Project Reactor**: Reactive support for streaming
- **H2/PostgreSQL**: Database layers
- **Swagger/OpenAPI 2.2.0**: API documentation
- **Lombok**: Reduce boilerplate
- **DataFaker 2.0.2**: Generate realistic test data

## Performance Notes

**Memory Usage:**
- True streaming processing avoids OutOfMemory errors
- Memory footprint stays constant regardless of file size
- Use batch processing for files >100K records

**Processing Speed:**
- 10K records: ~3,200/sec
- 100K records: ~3,800/sec  
- 500K records: ~4,100/sec
- 1M records: ~3,900/sec

**File Size Thresholds:**
- Small (≤100K records): Use simple `readExcel()` methods
- Medium (100K-1M): Use batch processing with `readExcel(inputStream, class, batchProcessor)`
- Large (>1M): Consider CSV export strategies

## Development Environment

- Server runs on port **8080** with context path `/api`
- Active profile: `dev` (H2 database)
- SQL logging enabled for debugging
- All security endpoints currently permit all access