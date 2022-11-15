package com.siliconvalve.demo;

import java.util.*;
import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;

import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.TableServiceClientBuilder;
import com.azure.data.tables.models.TableEntity;

/**
 * Azure Functions with HTTP Trigger.
 * 
 * Use this Function to create an Azure Table Storage Table and insert an Entity into it. This Entity will be used
 * for tracking the most recently emailed retirement notification.
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

            String tableName = System.getenv("TrackerTableName");

            // Create the table if it does not exist.
            TableClient tableClient = tableServiceClient.createTableIfNotExists(tableName);

            String partitionKey = System.getenv("TrackerEntityPartitionKey");
            String rowKey = System.getenv("TrackerEntityRowKey");
            
            // If the table already exists then the tableClient will be NULL
            if (tableClient == null)
            {
                tableClient = tableServiceClient.getTableClient(tableName);
                tableClient.deleteEntity(tableName, tableName);
            }

            // Create a new TableEntity.
            TableEntity entityItem = new TableEntity(partitionKey, rowKey);
        
            entityItem.addProperty("PubDate","Sat, 1 Jan 2022 00:00:00 Z");
            entityItem.addProperty("Guid","random-text-no-matches");
            
            // Upsert the entity into the table
            tableClient.upsertEntity(entityItem);
        }
        catch (Exception e)
        {
            context.getLogger().info(e.getMessage());
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body("Failed to initialise table entity. Check Functions logs.").build();
        }

        context.getLogger().info("Successfully initialised Table Storage.");
        return request.createResponseBuilder(HttpStatus.OK).body("Table and entity created").build();
    }
}