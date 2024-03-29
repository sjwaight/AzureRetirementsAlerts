# Use Azure Functions to Generate Azure Service Retirement Emails

This solution is used to periodically generate and send an email that contains newly annouced Azure Service retirements. 

If you want more background to this solution you can [read the blog](https://blog.siliconvalve.com/2022/11/16/build-your-own-azure-retirements-email-alerts-service-using-java-azure-functions-and-communication-services/). 

The Azure Function is written using Java 11.

If you continue to see "no retirements" emails and feel this is incorrect, then check your configuration. This solution is functional and running as expected in Azure. Thankfully there aren't too many retirement announcements!

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
        "AlertSenderEmail": "DoNotReply@{your_acs_approved_email_domain}.azurecomm.net",
        "TrackerTableName": "updatetracker01",
        "TrackerEntityPartitionKey": "updates01",
        "TrackerEntityRowKey": "LastReadItemDateTime"
    }
}
```

You can find a sample snippet of the RSS feed in the [rss-data-sample.xml file](rss-data-sample.xml) in this repo.

Note: depending on your configuration you may need to add a couple of additional entries that point to JAVA_HOME and also set the debugger correctly. Add these two entries (and update accordingly to suit your machine).

```
"JAVA_HOME": "/usr",
"JAVA_OPTS": "-Djava.net.preferIPv4Stack=true -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=127.0.0.1:5005"
```

On Ubuntu you can install all the necessaru Java bits by running this update:

```bash
sudo apt install openjdk-11-source
```

If you want to manually invoke the Timer trigger Function (ParseFeedAndSendAlerts) you can do this locally by using `curl` or a Visual Studio Code extension such as [Thunderclient](https://marketplace.visualstudio.com/items?itemName=rangav.vscode-thunder-client).

Invoke this URL: http://localhost:7071/admin/functions/ParseFeedAndSendAlerts and send a POST request with a Content-Type of `application/json` and abody of: 

```json
{ "input": "test" }
```

## Setup in Azure

Start by forking this repository on GitHub. Once forked, clone the repository to your local developer machine, or open in a GitHub Codespace.

```bash
$ git clone https://github.com/your_user/AzureRetirementsAlerts.git
$ cd AzureRetirementsAlerts
```

Start by deploying the necessary Azure services by using the [Bicep file](infra-deploy/deploy.bicep) contained in the infra-deploy folder. You will need to use the Azure CLI and log into your Subscription first.

You will need to select an Azure Region when deploying. You should supply the `Name` of the Region which can be obtained using this Azure CLI command:

```
az account list-locations -o table
```

It is also recommended to create a relatively short Resource Group name as this name is used as the seed for a random string suffix that is used for all created Resources. If you create a long Resource Group name you may run into issues with Resource naming length restrictions.

```bash
$ az login
$ az group create --location your_region --resource-group your_group_name
$ az deployment group create --resource-group your_group_name --template-file infra-deploy/deploy.bicep
```

_Note:_ if you are doing this from within Codespaces, use the `az login --use-device-code` option to login.

You will be prompted for an email address to receive the alert emails at. The current setup only supports a single recipient.

Depending on the Azure Region, and time of day, the template will take around 5 minutes to deploy. You should receive no errors. If you do, please [open an issue](https://github.com/sjwaight/AzureRetirementsAlerts/issues) on the origianl repository so we can take a look - please make sure to include your error message.

The Bicep file will deploy the following resources for you:

- Azure Communication Service account
- Azure Communication Service - Email Service
- Azure Communication Service - Email Service Managed Domain
- Azure Storage Account with defined containers
- Azure Function App (v4 runtime, Java 11) with configured Application Settings.

The Bicep file will output 3 values. Record the Azure Function name for use in steps below.

Once the infrastructure is deployed you can wire up the GitHub Action for this repository so it deploys the Azure Functions code to your Subscription.

### Deploy Azure Function

The easiest way to wire up the Action is to use the `az functionapp app up` [command](https://learn.microsoft.com/cli/azure/functionapp/app?view=azure-cli-latest#az-functionapp-app-up)).

```bash
$ az functionapp app up --app-name your_function_app_name --branch-name main --repository  https://github.com/your_user/AzureRetirementsAlerts.git 
```

If you are prompted to update or add a new GitHub Action defintion, select to add a new one.

If for you are unable to deploy the application via this method you can use the [Deployment Center](https://learn.microsoft.com/azure/azure-functions/functions-continuous-deployment) option in the Azure Portal to configure your deployment.

### Configure Table Storage

Before you can successfully use the Azure Function solution you will need an entity in an Azure Storage Table. You can initialise this by performing the following steps.

```bash
$ FUNC_URL=$(az functionapp function show --resource-group your_resource_group --name your_function_app_name --function-name InitialiseTableStorage --function-name InitialiseTableStorage --query invokeUrlTemplate --output tsv)
$ FUNC_KEY=$(az functionapp function keys list --resource-group your_resource_group --name your_function_app_name --function-name InitialiseTableStorage --query default --output tsv)
$ curl "$FUNC_URL?code=$FUNC_KEY"
```

Once this is completed you will find a new Table in your Azure Storage Account that contains exactly one Entity.
