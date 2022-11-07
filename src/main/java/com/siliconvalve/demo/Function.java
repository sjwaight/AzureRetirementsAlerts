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
import com.azure.core.credential.AzureKeyCredential;
import com.azure.communication.email.*;

import com.apptasticsoftware.rssreader.*;

import java.io.IOException;
import java.util.ArrayList;
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
            //     name="announcmenttrack", 
            //     partitionKey="lastreaditem", 
            //     tableName="%MyTableName%", 
            //     connection="AzureWebJobsStorage") Person[] persons,
            // @TableOutput(
            //     name="announcmenttrack", 
            //     partitionKey="{partitionKey}", 
            //     rowKey = "{rowKey}", 
            //     tableName="%MyTableName%", 
            //     connection="AzureWebJobsStorage") OutputBinding<Person> person,
            final ExecutionContext context)
             {

        String headings = "<html><body><h1>Azure Retirement announcements</h1>";

        List<Item> articles = readUpdatesRssFeed(context);

        for(Item post :articles)
        {
            headings = headings + "<p><strong>" + post.getTitle().get() + "</strong> - <a href=\"" + post.getLink().get() + "\">read more &gt;&gt;</a></p>";
        }

        headings = headings + "</body></html>";

        prepareAndSendAlertEmail(headings);

        return request.createResponseBuilder(HttpStatus.OK).body(headings).build();
    }

    private List<Item> readUpdatesRssFeed(ExecutionContext context)
    { 
        RssReader reader = new RssReader();
        try 
        {
            return reader.read(System.getenv("UpdatesURL")).collect(Collectors.toList());
        } catch (IOException e) {
            context.getLogger().info(e.getMessage());
            //e.printStackTrace();
        }

        return new ArrayList<Item>(0);
    }

    private boolean prepareAndSendAlertEmail(String retirementHtmlSnippet)
    {

        // merge html snippet

        // Azure Communication Serivce - Email Service Client
        EmailClient emailClient = new EmailClientBuilder()
            .endpoint(System.getenv("CommunicationURL"))
            .credential(new AzureKeyCredential(System.getenv("CommunicationKey")))
            .buildClient();

        EmailAddress emailAddress = new EmailAddress(System.getenv("AlertRecipient"));

        ArrayList<EmailAddress> addressList = new ArrayList<>();
        addressList.add(emailAddress);
        
        EmailRecipients emailRecipients = new EmailRecipients(addressList);
        
        EmailContent content = new EmailContent(System.getenv("AlertSubjectLine"))
            .setHtml(retirementHtmlSnippet);
        
        EmailMessage emailMessage = new EmailMessage(System.getenv("AlertSenderEmail"), content)
            .setRecipients(emailRecipients);
        
        SendEmailResult response = emailClient.send(emailMessage);

        return true;
        
    }
}