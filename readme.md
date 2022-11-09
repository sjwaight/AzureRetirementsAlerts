# Use Azure Functions to Generate Azure Service Retirement Emails

This solution is used to periodically generate and send an email that contains newly annouced Azure Service retirements. Blog to follow.

The Azure Function is written using Java 11.

## Debug locally

You can debug or play with this solution either by cloning locally to your machine or via use of a Dev Container or GitHub Codespaces. The Dev Containter or Codespaces options present an easier path as they have all the necessary dependencies pre-installed. If you wish to run locally you will need to have a Java 11 JDK installed, the [Azure Functions Core Tools](https://learn.microsoft.com/en-us/azure/azure-functions/functions-run-local) and the [Azurite Azure Storage](https://learn.microsoft.com/azure/storage/common/storage-use-azurite) emulator. There is no local substitute for the email service, but you can comment out that code if you wish, or provision a service by using the Bicep file mentioned below in "Setup in Azure".

When you spin up locally or in a Dev Container or Codespace you will need to create a local.settings.json file as per the defintion below.

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

## Setup in Azure

Start by forking this repository on GitHub. Once forked, clone the repository to your local developer machine, or open in a GitHub Codespace.

```bash
$ git clone https://github.com/your_user/AzureRetirementsAlerts.git
$ cd AzureRetirementsAlerts
```

Start by deploying the necessary Azure services by using the [Bicep file](infra-deploy/deploy.bicep) contained in the infra-deploy folder. You will need to use the Azure CLI and log into your Subscription first.

You will need to select an Azure Region when deploying. You should supply the `Name` of the Region which can be obtained using this Azure CLI command:  
`az account list-locations -o table`.

It is also recommended to create a relatively short Resource Group name as this name is used as the seed for a random string suffix that is used for all created Resources. If you create a long Resource Group name you may run into issues with Resource naming length restrictions.

```bash
$ az login
$ az group create --location your_region --resource-group your_group_name
$ az deployment group create --resource-group your_group_name --template-file infra-deploy/deploy.bicep
```

You will be prompted for an email address to receive the alert emails at

Depending on the Region, and time of day, the template will take around 5 minutes to deploy. You should receive no errors. If you do, please [open an issue](https://github.com/sjwaight/AzureRetirementsAlerts/issues) on the origianl repository so we can take a look - please make sure to include your error message.

The Bicep file will deploy the following resources for you:

- Azure Communication Service account
- Azure Communication Service - Email Service
- Azure Communication Service - Email Service Managed Domain
- Azure Storage Account with defined containers
- Azure Function App (v4 runtime, Java 11) with configured Application Settings.

Once deployed you can wire up the GitHub Action for this repository so it deploys the Azure Functions code to your Subscription.