package com.siliconvalve.demo;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import com.azure.communication.email.models.*;
import com.azure.communication.email.*;

import com.apptasticsoftware.rssreader.*;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Azure Functions with HTTP Trigger.
 */
public class Function {


    // @FunctionName("TimerTrigger")
    // public void timerHandler(
    //     @TimerTrigger(name = "timerInfo", schedule = "0 */5 * * * *") String timerInfo,
    //     final ExecutionContext context
    // ) {


    /**
     * This function listens at endpoint "/api/HttpExample". Two ways to invoke it using "curl" command in bash:
     * 1. curl -d "HTTP Body" {your host}/api/HttpExample
     * 2. curl "{your host}/api/HttpExample?name=HTTP%20Query"
     */
    @FunctionName("HttpExample")
    public HttpResponseMessage run(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.GET, HttpMethod.POST},
                authLevel = AuthorizationLevel.ANONYMOUS)
                HttpRequestMessage<Optional<String>> request,
            // @TableInput(
            //     name="persons", 
            //     partitionKey="{partitionKey}", 
            //     tableName="%MyTableName%", 
            //     connection="MyConnectionString") Person[] persons,
            // @TableOutput(
            //     name="person", 
            //     partitionKey="{partitionKey}", 
            //     rowKey = "{rowKey}", 
            //     tableName="%MyTableName%", 
            //     connection="MyConnectionString") OutputBinding<Person> person,
            final ExecutionContext context)
             {

        context.getLogger().info("Java HTTP trigger processed a request.");

        StringBuilder headings = new StringBuilder("<h1>Retirement announcements</h1>");

        List<Item> articles = readUpdatesRssFeed(context);

        while(articles.iterator().hasNext())
        {
            headings.append(articles.iterator().next().getTitle() + "<br/>");
        }

        return request.createResponseBuilder(HttpStatus.OK).body(headings).build();
    }


    private List<Item> readUpdatesRssFeed(ExecutionContext context)
    { 
        List<Item> articles = null;
        RssReader reader = new RssReader();
        try 
        {
            articles = reader.read(System.getenv("UpdatesURL")).collect(Collectors.toList());
        } catch (IOException e) {
            context.getLogger().info(e.getMessage());
            e.printStackTrace();
        }
        return articles;
    }

    // private boolean prepareAndSendAlertEmail()
    // {}
}