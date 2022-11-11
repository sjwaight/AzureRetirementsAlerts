param alert_email_recipient string

param deployment_location string = resourceGroup().location
var unique_name = uniqueString(resourceGroup().id)

param azure_function_name string = 'depalertsfunc'
param serverfarm_dynamic_name string = 'deparltplan'
param components_depalertsfunc_name string = 'depalertsfunc'

param storageaccount_name string = 'depstore'

param communication_services_name string = 'acsdepserv'
param email_service_name string = 'acsdepmail'

param workspace_name string = 'depalertsworkspace'
param app_insights_name string = 'depalertsai'

// Azure Communication Services

resource communication_services_resource 'Microsoft.Communication/CommunicationServices@2022-07-01-preview' = {
  name: '${communication_services_name}${unique_name}'
  location: 'global'
  properties: {
    dataLocation: 'United States'
    linkedDomains: [
      emailservices_azuremanageddomain_resource.id
    ]
  }
}

// Email Service
resource emailservices_resource 'Microsoft.Communication/emailServices@2022-07-01-preview' = {
  name: '${email_service_name}${unique_name}'
  location: 'global'
  properties: {
    dataLocation: 'United States'
  }
}

// Email Service Domain (using in-built randomised domain)
resource emailservices_azuremanageddomain_resource 'Microsoft.Communication/emailServices/domains@2022-07-01-preview' = {
  parent: emailservices_resource
  name: 'AzureManagedDomain'
  location: 'global'
  properties: {
    domainManagement: 'AzureManaged'
    validSenderUsernames: {
      DoNotReply: 'DoNotReply'
    }
    userEngagementTracking: 'Disabled'
  }
}

// END: Azure Communication Services

// Storage Account

resource storageaccount_resource 'Microsoft.Storage/storageAccounts@2022-05-01' = {
  name:  '${storageaccount_name}${unique_name}' 
  location: deployment_location
  sku: {
    name: 'Standard_LRS'
  }
  kind: 'Storage'
  properties: {
    minimumTlsVersion: 'TLS1_2'
    allowBlobPublicAccess: true
    networkAcls: {
      bypass: 'AzureServices'
      virtualNetworkRules: []
      ipRules: []
      defaultAction: 'Allow'
    }
    supportsHttpsTrafficOnly: true
    encryption: {
      services: {
        file: {
          keyType: 'Account'
          enabled: true
        }
        blob: {
          keyType: 'Account'
          enabled: true
        }
      }
      keySource: 'Microsoft.Storage'
    }
  }
}

// Blob Service configuration
resource storageaccount_blob_default_resource 'Microsoft.Storage/storageAccounts/blobServices@2021-06-01' = {
  parent: storageaccount_resource
  name: 'default'
  properties: {
    changeFeed: {
      enabled: false
    }
    restorePolicy: {
      enabled: false
    }
    containerDeleteRetentionPolicy: {
      enabled: true
      days: 7
    }
    cors: {
      corsRules: []
    }
    deleteRetentionPolicy: {
      enabled: true
      days: 7
    }
    isVersioningEnabled: false
  }
}


resource storageaccount_blob_default_webjobs_hosts_container_resource 'Microsoft.Storage/storageAccounts/blobServices/containers@2022-05-01' = {
  parent: storageaccount_blob_default_resource
  name: 'azure-webjobs-hosts'
  properties: {
    immutableStorageWithVersioning: {
      enabled: false
    }
    defaultEncryptionScope: '$account-encryption-key'
    denyEncryptionScopeOverride: false
    publicAccess: 'None'
  }
}

resource storageaccount_blob_default_webjobs_secrets_container_resource 'Microsoft.Storage/storageAccounts/blobServices/containers@2022-05-01' = {
  parent: storageaccount_blob_default_resource
  name: 'azure-webjobs-secrets'
  properties: {
    immutableStorageWithVersioning: {
      enabled: false
    }
    defaultEncryptionScope: '$account-encryption-key'
    denyEncryptionScopeOverride: false
    publicAccess: 'None'
  }
}

resource storageaccount_blob_default_scm_releases_container_resource 'Microsoft.Storage/storageAccounts/blobServices/containers@2022-05-01' = {
  parent: storageaccount_blob_default_resource
  name: 'scm-releases'
  properties: {
    immutableStorageWithVersioning: {
      enabled: false
    }
    defaultEncryptionScope: '$account-encryption-key'
    denyEncryptionScopeOverride: false
    publicAccess: 'None'
  }
}

// END: Storage Account

// Azure Function

// Hosting Plan (dynamic)
resource functions_comsumption_plan_resource 'Microsoft.Web/serverfarms@2022-03-01' = {
  name: '${serverfarm_dynamic_name}${unique_name}'
  location: deployment_location
  sku: {
    name: 'Y1'
    tier: 'Dynamic'
    size: 'Y1'
    family: 'Y'
    capacity: 0
  }
  kind: 'functionapp'
  properties: {
    perSiteScaling: false
    elasticScaleEnabled: false
    maximumElasticWorkerCount: 1
    isSpot: false
    reserved: true
    isXenon: false
    hyperV: false
    targetWorkerCount: 0
    targetWorkerSizeId: 0
    zoneRedundant: false
  }
}

// Azure Function
resource sites_depalertsfunc_name_resource 'Microsoft.Web/sites@2022-03-01' = {
  name:  '${azure_function_name}${unique_name}'
  location: deployment_location
  tags: {
    'hidden-link: /app-insights-resource-id': '/subscriptions/a5e235c9-0511-4aa4-bc4a-3a0ebc8841e0/resourceGroups/azure-deprecation-alerts/providers/Microsoft.Insights/components/depalertsfunc'
  }
  kind: 'functionapp,linux'
  properties: {
    enabled: true
    hostNameSslStates: [
      {
        name: '${azure_function_name}${unique_name}.azurewebsites.net'
        sslState: 'Disabled'
        hostType: 'Standard'
      }
      {
        name: '${azure_function_name}${unique_name}.scm.azurewebsites.net'
        sslState: 'Disabled'
        hostType: 'Repository'
      }
    ]
    serverFarmId: functions_comsumption_plan_resource.id
    reserved: true
    isXenon: false
    hyperV: false
    vnetRouteAllEnabled: false
    vnetImagePullEnabled: false
    vnetContentShareEnabled: false
    siteConfig: {
      numberOfWorkers: 1
      linuxFxVersion: 'Java|11'
      acrUseManagedIdentityCreds: false
      alwaysOn: false
      http20Enabled: false
      functionAppScaleLimit: 200
      minimumElasticInstanceCount: 0
      ftpsState: 'Disabled'
    }
    scmSiteAlsoStopped: false
    clientAffinityEnabled: false
    clientCertEnabled: false
    clientCertMode: 'Required'
    hostNamesDisabled: false
    customDomainVerificationId: '7AC681103D4DA69FE0C7DA8C07E02F5A8AE10EE43B9303D49A8C6C0B99769039'
    containerSize: 1536
    dailyMemoryTimeQuota: 0
    httpsOnly: true
    redundancyMode: 'None'
    storageAccountRequired: false
    keyVaultReferenceIdentity: 'SystemAssigned'
  }
}

// Azure Function Config
resource sites_depalertsfunc_name_web 'Microsoft.Web/sites/config@2022-03-01' = {
  parent: sites_depalertsfunc_name_resource
  name: 'web'
  location: deployment_location
  tags: {
    'hidden-link: /app-insights-resource-id': '/subscriptions/a5e235c9-0511-4aa4-bc4a-3a0ebc8841e0/resourceGroups/azure-deprecation-alerts/providers/Microsoft.Insights/components/depalertsfunc'
  }
  properties: {
    numberOfWorkers: 1
    defaultDocuments: [
      'Default.htm'
      'Default.html'
      'Default.asp'
      'index.htm'
      'index.html'
      'iisstart.htm'
      'default.aspx'
      'index.php'
    ]
    netFrameworkVersion: 'v4.0'
    linuxFxVersion: 'Java|11'
    requestTracingEnabled: false
    remoteDebuggingEnabled: false
    httpLoggingEnabled: false
    acrUseManagedIdentityCreds: false
    logsDirectorySizeLimit: 35
    detailedErrorLoggingEnabled: false
    publishingUsername: '$depalertsfunc'
    scmType: 'None'
    use32BitWorkerProcess: false
    webSocketsEnabled: false
    alwaysOn: false
    managedPipelineMode: 'Integrated'
    virtualApplications: [
      {
        virtualPath: '/'
        physicalPath: 'site\\wwwroot'
        preloadEnabled: false
      }
    ]
    loadBalancing: 'LeastRequests'
    experiments: {
      rampUpRules: []
    }
    autoHealEnabled: false
    vnetRouteAllEnabled: false
    vnetPrivatePortsCount: 0
    cors: {
      allowedOrigins: [
        'https://portal.azure.com'
      ]
      supportCredentials: false
    }
    localMySqlEnabled: false
    ipSecurityRestrictions: [
      {
        ipAddress: 'Any'
        action: 'Allow'
        priority: 2147483647
        name: 'Allow all'
        description: 'Allow all access'
      }
    ]
    scmIpSecurityRestrictions: [
      {
        ipAddress: 'Any'
        action: 'Allow'
        priority: 2147483647
        name: 'Allow all'
        description: 'Allow all access'
      }
    ]
    scmIpSecurityRestrictionsUseMain: false
    http20Enabled: false
    minTlsVersion: '1.2'
    scmMinTlsVersion: '1.2'
    ftpsState: 'FtpsOnly'
    preWarmedInstanceCount: 0
    functionAppScaleLimit: 200
    functionsRuntimeScaleMonitoringEnabled: false
    minimumElasticInstanceCount: 0
    azureStorageAccounts: {
    }
    appSettings: [
      {
        name: 'AzureWebJobsStorage'
        value: 'DefaultEndpointsProtocol=https;AccountName=${storageaccount_resource.name};EndpointSuffix=${environment().suffixes.storage};AccountKey=${storageaccount_resource.listKeys().keys[0].value}'
      }
      {
        name: 'FUNCTIONS_EXTENSION_VERSION'
        value: '~4'
      }
      {
        name: 'FUNCTIONS_WORKER_RUNTIME'
        value: 'java'
      }
      {
        name: 'UpdatesURL'
        value: 'https://azurecomcdn.azureedge.net/en-us/updates/feed/?updateType=retirements'
      }
      {
        name: 'CommunicationURL'
        value: 'https://${communication_services_resource.name}.communication.azure.com/'
      }
      {
        name: 'CommunicationKey'
        value: communication_services_resource.listKeys().primaryKey
      }
      {
        name: 'AlertSubjectLine'
        value: 'Azure Service Retirement Announcements'
      }
      {
        name: 'AlertSenderEmail'
        value: 'DoNotReply@${emailservices_azuremanageddomain_resource.properties.fromSenderDomain}'
      }
      {
        name: 'AlertRecipient'
        value: alert_email_recipient
      }     
    ]
  }
}

// Application Insights

resource workspace_insights 'Microsoft.OperationalInsights/workspaces@2020-08-01' = {
  location: deployment_location
  name: '${workspace_name}${unique_name}' 
}

resource function_application_insights 'microsoft.insights/components@2020-02-02' = {
  name: '${app_insights_name}${unique_name}'
  location: deployment_location
  kind: 'web'
  properties: {
    Application_Type: 'web'
    Request_Source: 'rest'
    RetentionInDays: 90
    WorkspaceResourceId: workspace_insights.id
    IngestionMode: 'LogAnalytics'
    publicNetworkAccessForIngestion: 'Enabled'
    publicNetworkAccessForQuery: 'Enabled'
  }
}

/// OUTPUTS

output deployed_location string = deployment_location
output deployed_resource_group string = resourceGroup().name
output function_app_name string = sites_depalertsfunc_name_resource.name
