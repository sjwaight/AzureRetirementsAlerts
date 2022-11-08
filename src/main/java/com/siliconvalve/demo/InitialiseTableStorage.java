package com.siliconvalve.demo;

import java.util.*;
import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;

import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableClientBuilder;
import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.TableServiceClientBuilder;
import com.azure.data.tables.models.ListEntitiesOptions;
import com.azure.data.tables.models.TableEntity;
import com.azure.data.tables.models.TableEntityUpdateMode;
import com.azure.data.tables.models.TableTransactionAction;
import com.azure.data.tables.models.TableTransactionActionType;

/**
 * Azure Functions with HTTP Trigger.
 */
public class InitialiseTableStorage {
    /**
     * This function listens at endpoint "/api/InitialiseTableStorage". Two ways to invoke it using "curl" command in bash:
     * 1. curl -d "HTTP Body" {your host}/api/InitialiseTableStorage
     * 2. curl {your host}/api/InitialiseTableStorage?name=HTTP%20Query
     */
    @FunctionName("InitialiseTableStorage")
    public HttpResponseMessage run(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET}, authLevel = AuthorizationLevel.FUNCTION) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        context.getLogger().info("Initialising Table Storage.");

        try 
        {
            // Create a TableServiceClient with a connection string.
            TableServiceClient tableServiceClient = new TableServiceClientBuilder()
                    .connectionString(System.getenv("AzureWebJobsStorage"))
                    .buildClient();

            // Create the table if it does not exist.
            TableClient tableClient = tableServiceClient.createTableIfNotExists(System.getenv("TrackerTableName"));

            // Create a new TableEntity.
            String partitionKey = System.getenv("TrackerEntityPartitionKey");
            String rowKey = System.getenv("TrackerEntityRowKey");
            Map<String, Object> lastDate = new HashMap<>();
            lastDate.put(System.getenv("TrackerEntityDataField"),"2022-01-01");
            TableEntity entityItem = new TableEntity(partitionKey, rowKey).setProperties(lastDate);
            
            // Upsert the entity into the table
            tableClient.upsertEntity(entityItem);

        }
        catch (Exception e)
        {
            context.getLogger().info(e.getMessage());
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body("Failed to initialise table entity. Check logs.").build();
        }

        return request.createResponseBuilder(HttpStatus.OK).body("Table and entity created").build();
    }
}
