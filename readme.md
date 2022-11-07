# Use Azure Functions to Generate Azure Service Retirement Emails

Requires use of the [Azurite Azure Storage](https://learn.microsoft.com/azure/storage/common/storage-use-azurite) emulator. If you are using Dev Containers or GitHub Codespaces then this VS Code extension will be automatically installed for you.

Sample local.settings.json

```json
{
    "IsEncrypted": false,
    "Values": {
        "AzureWebJobsStorage": "UseDevelopmentStorage=true",
        "FUNCTIONS_WORKER_RUNTIME": "java",
        "UpdatesURL": "https://azurecomcdn.azureedge.net/en-us/updates/feed/?updateType=retirements",
        "CommunicationURL": "https://{your_instance}.communication.azure.com/",
        "CommunicationKey": "{your_acs_key}",
        "AlertRecipient": "recipient@somesampleemaildomain.com",
        "AlertSubjectLine": "Azure Service Retirement Announcements",
        "AlertSenderEmail": "DoNotReply@{your_acs_approved_email_domain}.azurecomm.net"
    }
}
```