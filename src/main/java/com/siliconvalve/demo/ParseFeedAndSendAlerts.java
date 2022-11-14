package com.siliconvalve.demo;

import com.siliconvalve.models.TrackingEntity;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;

import com.azure.communication.email.models.*;
import com.azure.communication.email.*;
import com.azure.core.credential.AzureKeyCredential;

import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.TableServiceClientBuilder;
import com.azure.data.tables.models.TableEntity;

import com.apptasticsoftware.rssreader.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.*;
import java.util.Optional;
import java.util.stream.Collectors;

import java.time.Instant;

/**
 * Azure Functions with HTTP Trigger.
 */
public class ParseFeedAndSendAlerts {


    // @FunctionName("TimerTrigger")
    // public void timerHandler(
    //     @TimerTrigger(name = "timerInfo", schedule = "0 */5 * * * *") String timerInfo,
    //     final ExecutionContext context
    // ) {


    /**
     * This function listens at endpoint "/api/ParseFeedAndSendAlerts". Two ways to invoke it using "curl" command in bash:
     * 1. curl -d "HTTP Body" {your host}/api/ParseFeedAndSendAlerts
     * 2. curl "{your host}/api/ParseFeedAndSendAlerts?name=HTTP%20Query"
     */
    @FunctionName("ParseFeedAndSendAlerts")
    public HttpResponseMessage run(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.GET},
                authLevel = AuthorizationLevel.ANONYMOUS)
                HttpRequestMessage<Optional<String>> request,
            @TableInput(
                name="announcmenttrack", 
                partitionKey="%TrackerEntityPartitionKey%",
                rowKey="%TrackerEntityRowKey%", 
                tableName="%TrackerTableName%", 
                connection="AzureWebJobsStorage") TrackingEntity[] trackingEntity,
            final ExecutionContext context)
    {
        // Note: would have loved to have used a Table Storage Output Binding but it turns out that the Binding can't be
        //       used to update an existing Entity which is what I'd like to do here, so instead I am using the TableClient.

        try 
        {
            List<Item> articles = readUpdatesRssFeed(trackingEntity[0].getLastReadItemDateTime());

            String emailBody = buildEmailBody(articles);

            prepareAndSendAlertEmail(emailBody);

            // we are going to assume the list is in order of the incoming RSS XML
            // which should have the most recent article as the first time.
            String lastDateTime = articles.get(0).getPubDate().get();

            updateLastProcessedAlert(lastDateTime);

        } catch (IOException e) {
            context.getLogger().info(e.getMessage());
        }

        return request.createResponseBuilder(HttpStatus.OK).body("OK").build();
    }

    /**
     * Build the email body (HTML).
     * 
     * @param articles list of Azure Update articles to send as email.
     * @return String containing the HTML which consists of all article supplied in the original parameter.
     * 
     */
    private String buildEmailBody(List<Item> articles) {
        String headings = "<html><body><h1>Azure Retirement announcements</h1>";
        String bodyItems = "";
        for(Item post :articles)
        {
            bodyItems = bodyItems + "<p><strong>" + post.getTitle().get() + "</strong> - <a href=\"" + post.getLink().get() + "\">read more &gt;&gt;</a></p>";
        }
        if(bodyItems.isBlank())
        {
            bodyItems = "<p><strong>No new retirement updates since last email.";
        }
        headings = bodyItems + "</body></html>";
        return headings;
    }

    /**
     * Reads the Azure Updates RSS feed and converts it into a local Java List.
     * 
     * @param lastProcessedPubDate string containing the date / time of the most recently published update
     * @return List<Item> that has only new articles since last run (if any)
     * @throws IOException if unable to read the source RSS feed
     */
    private List<Item> readUpdatesRssFeed(String lastProcessedPubDate) throws IOException
    { 
        RssReader reader = new RssReader();

        DateTimeFormatter df = DateTimeFormatter.ofPattern("E, MMM dd yyyy HH:mm:ss"); // Sat, 12 Nov 2022 00:00:22 Z

        Instant lastProcessedArticle = Instant.parse(lastProcessedPubDate);

        //LocalDateTime lastProcessedArticle = LocalDateTime.parse(lastProcessedPubDate,df);

        List<Item> soureArticles = reader.read(System.getenv("UpdatesURL")).collect(Collectors.toList());

        List<Item> newArticles = soureArticles.stream().filter(i -> {
            String newArticleDate = i.getPubDate().get();
            return (Instant.parse(newArticleDate).compareTo(lastProcessedArticle) <= 0);
            // return LocalDateTime.parse(newArticleDate.substring(0,newArticleDate.length()-2), df).compareTo(lastProcessedArticle) <= 0;
        }).collect(Collectors.toList());;

        return newArticles; 
    }

    /**
     * Take the HTML snippet and send an email containg it to the designated recipient(s)
     * 
     * @param emailHtmlBody String containing the HTML body text for the email
     * @return boolean denoting whether the mail send succeeded or not.
     */
    private boolean prepareAndSendAlertEmail(String emailHtmlBody)
    {
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
            .setHtml(emailHtmlBody);
        
        EmailMessage emailMessage = new EmailMessage(System.getenv("AlertSenderEmail"), content)
            .setRecipients(emailRecipients);
        
        SendEmailResult response = emailClient.send(emailMessage);

        SendStatusResult result = emailClient.getSendStatus(response.getMessageId());
        
        if(result.getStatus() == SendStatus.DROPPED)
        {
            return false;
        }
        return true;        
    }

    /**
     * Update the tracking Azure Storage Table Entity with the date of the most recent update.
     * 
     * @param mostRecentDate String containing the date/time of the most recently use Azure Update item
     */
    private void updateLastProcessedAlert(String mostRecentDate)
    {
        // Create a TableServiceClient with a connection string.
        TableServiceClient tableServiceClient = new TableServiceClientBuilder()
        .connectionString(System.getenv("AzureWebJobsStorage"))
        .buildClient();

        TableClient tableClient = tableServiceClient.getTableClient(System.getenv("TrackerTableName"));

        // Create a new TableEntity.
        String partitionKey = System.getenv("TrackerEntityPartitionKey");
        String rowKey = System.getenv("TrackerEntityRowKey");
        Map<String, Object> lastDate = new HashMap<>();
        lastDate.put(System.getenv("TrackerEntityDataField"),mostRecentDate);
            
        TableEntity entityItem = new TableEntity(partitionKey, rowKey).setProperties(lastDate);
            
        // Upsert the entity into the table
        tableClient.upsertEntity(entityItem);
    }
}