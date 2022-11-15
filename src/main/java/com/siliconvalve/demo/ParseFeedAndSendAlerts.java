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

    /**
     * This function runs on a schedule determined by the cron defintion set for the 'schedule' argument
     * You can manually invoke it by following this: https://learn.microsoft.com/azure/azure-functions/functions-manually-run-non-http 
     */
    @FunctionName("ParseFeedAndSendAlerts")
    public void run(
            @TimerTrigger(name = "timerInfo", schedule = "0 0 * * 0") String timerInfo,
            @TableInput(
                name="announcmenttrack", 
                partitionKey="%TrackerEntityPartitionKey%",
                rowKey="%TrackerEntityRowKey%", 
                tableName="%TrackerTableName%", 
                connection="AzureWebJobsStorage") TrackingEntity[] trackingEntity,
            final ExecutionContext context)
    {
        try 
        {
            List<Item> articles = readUpdatesRssFeed(trackingEntity[0]);

            context.getLogger().info("Sending " + articles.size() + " new retirement alerts as an email.");

            String emailBody = buildEmailBody(articles);

            if(prepareAndSendAlertEmail(emailBody))
            {
                // if there are any new Items we need to update the list
                if(articles.size() > 0 )
                {
                    // we are going to assume the list is in order of the incoming RSS XML
                    // which should have the most recent article as the first time.
                    Item mostRecentPost = articles.get(0);
                    updateLastProcessedAlert(mostRecentPost);

                    context.getLogger().info("Updated tracker table with most recent post: " + mostRecentPost.getGuid().get());
                }
                else
                {
                    context.getLogger().info("No need to update Table Storage as no new retirement announcements.");
                }
            }
            else
            {
                context.getLogger().info("Didn't update Table Storage as Alerts email was not sent.");
            }

        } catch (IOException e) {
            context.getLogger().info(e.getMessage());
        }
    }

    /**
     * Reads the Azure Updates RSS feed and converts it into a local Java List.
     * 
     * @param lastProcessedItemEntity POJO containing the details of the update that was sent last time this Function ran
     * @return List<Item> that has only new articles since last run (if any)
     * @throws IOException if unable to read the source RSS feed
     */
    private List<Item> readUpdatesRssFeed(TrackingEntity lastProcessedItemEntity) throws IOException
    { 
        RssReader reader = new RssReader();

        String lastArticleDate = lastProcessedItemEntity.getPubDate();
        String lastArticleGuid = lastProcessedItemEntity.getGuid();

        List<Item> sourceArticles = reader.read(System.getenv("UpdatesURL")).collect(Collectors.toList());
        List<Item> newArticles = new ArrayList<Item>();

        for(Item article: sourceArticles)
        {
            String newArticleDate = article.getPubDate().get();
            String newArticleGuid = article.getGuid().get();

            // Once we hit the last processed item we end loop.
            if(newArticleDate.equals(lastArticleDate) && newArticleGuid.equals(lastArticleGuid))
                break;

            newArticles.add(article);
        }

        return newArticles; 
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
     * @param mostRecentPost RSS Item containing the most recently used Azure Update item
     */
    private void updateLastProcessedAlert(Item mostRecentPost)
    {
        // Create a TableServiceClient with a connection string.
        TableServiceClient tableServiceClient = new TableServiceClientBuilder()
        .connectionString(System.getenv("AzureWebJobsStorage"))
        .buildClient();

        TableClient tableClient = tableServiceClient.getTableClient(System.getenv("TrackerTableName"));

        // Create a new TableEntity.
        String partitionKey = System.getenv("TrackerEntityPartitionKey");
        String rowKey = System.getenv("TrackerEntityRowKey");

        TableEntity entityItem = new TableEntity(partitionKey, rowKey);
        
        entityItem.addProperty("PubDate",mostRecentPost.getPubDate().get());
        entityItem.addProperty("Guid",mostRecentPost.getGuid().get());
    
        // Upsert the entity into the table
        tableClient.upsertEntity(entityItem);
    }
}