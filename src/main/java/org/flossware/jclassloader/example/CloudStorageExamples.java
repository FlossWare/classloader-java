package org.flossware.jclassloader.example;

import org.flossware.jclassloader.JClassLoader;
import org.flossware.jclassloader.cloud.AzureBlobClassSource;
import org.flossware.jclassloader.cloud.GcsClassSource;
import org.flossware.jclassloader.cloud.S3ClassSource;
import software.amazon.awssdk.regions.Region;

public class CloudStorageExamples {

    public static void main(String[] args) {
        awsS3Example();
        azureBlobExample();
        googleCloudStorageExample();
        multiCloudExample();
    }

    public static void awsS3Example() {
        System.out.println("=== AWS S3 Class Loading Example ===");

        // S3 with explicit credentials
        S3ClassSource s3Source = S3ClassSource.builder()
            .region(Region.US_EAST_1)
            .bucket("my-classes-bucket")
            .prefix("production/classes")
            .credentials("AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY")
            .build();

        JClassLoader loader = JClassLoader.builder()
            .addS3Source(s3Source)
            .build();

        System.out.println("S3 ClassLoader configured:");
        System.out.println("  " + s3Source.getDescription());

        // S3 using default credentials (IAM role, environment, etc.)
        S3ClassSource autoCredsSource = S3ClassSource.builder()
            .region("us-west-2")
            .bucket("company-classes")
            .prefix("releases/v1.0")
            .build();

        System.out.println("\nS3 with auto credentials:");
        System.out.println("  " + autoCredsSource.getDescription());
        System.out.println("\nClass files organized as:");
        System.out.println("  s3://my-classes-bucket/production/classes/com/example/MyClass.class");
        System.out.println();
    }

    public static void azureBlobExample() {
        System.out.println("=== Azure Blob Storage Example ===");

        // Azure with connection string
        AzureBlobClassSource azureSource = AzureBlobClassSource.builder()
            .connectionString("DefaultEndpointsProtocol=https;AccountName=myaccount;AccountKey=...")
            .container("classes-container")
            .prefix("prod/classes")
            .build();

        JClassLoader loader = JClassLoader.builder()
            .addAzureBlobSource(azureSource)
            .build();

        System.out.println("Azure Blob ClassLoader configured:");
        System.out.println("  " + azureSource.getDescription());

        // Azure with account name and key
        AzureBlobClassSource keySource = AzureBlobClassSource.builder()
            .accountName("mycompanystorage")
            .accountKey("account-key-here")
            .container("application-classes")
            .prefix("v2/classes")
            .build();

        System.out.println("\nAzure with account credentials:");
        System.out.println("  " + keySource.getDescription());
        System.out.println("\nClass files organized as:");
        System.out.println("  https://myaccount.blob.core.windows.net/classes-container/prod/classes/com/example/MyClass.class");
        System.out.println();
    }

    public static void googleCloudStorageExample() {
        System.out.println("=== Google Cloud Storage Example ===");

        // GCS with project ID
        GcsClassSource gcsSource = GcsClassSource.builder()
            .projectId("my-gcp-project")
            .bucket("company-classes-bucket")
            .prefix("releases/production")
            .build();

        JClassLoader loader = JClassLoader.builder()
            .addGcsSource(gcsSource)
            .build();

        System.out.println("Google Cloud Storage ClassLoader configured:");
        System.out.println("  " + gcsSource.getDescription());

        // GCS with default credentials
        GcsClassSource defaultSource = GcsClassSource.builder()
            .bucket("app-classes")
            .build();

        System.out.println("\nGCS with default credentials:");
        System.out.println("  " + defaultSource.getDescription());
        System.out.println("\nClass files organized as:");
        System.out.println("  gs://company-classes-bucket/releases/production/com/example/MyClass.class");
        System.out.println();
    }

    public static void multiCloudExample() {
        System.out.println("=== Multi-Cloud Hybrid Example ===");

        // Production scenario: multiple cloud providers with fallbacks

        // Primary: AWS S3 (production classes)
        S3ClassSource primaryS3 = S3ClassSource.builder()
            .region(Region.US_EAST_1)
            .bucket("prod-classes-primary")
            .prefix("classes")
            .build();

        // Backup: Azure Blob (DR region)
        AzureBlobClassSource backupAzure = AzureBlobClassSource.builder()
            .accountName("drbackupstorage")
            .accountKey("backup-key")
            .container("classes-backup")
            .build();

        // Shared resources: Google Cloud Storage
        GcsClassSource sharedGcs = GcsClassSource.builder()
            .projectId("shared-resources-project")
            .bucket("shared-libraries")
            .prefix("common/classes")
            .build();

        JClassLoader multiCloudLoader = JClassLoader.builder()
            .addS3Source(primaryS3)           // Check S3 first
            .addAzureBlobSource(backupAzure)  // Azure fallback
            .addGcsSource(sharedGcs)          // Shared GCS resources
            .build();

        System.out.println("Multi-cloud hybrid ClassLoader:");
        System.out.println("Resolution order:");
        int i = 1;
        for (org.flossware.jclassloader.ClassSource source : multiCloudLoader.getClassSources()) {
            System.out.println("  " + i++ + ". " + source.getDescription());
        }

        System.out.println("\nBenefits:");
        System.out.println("  - High availability across cloud providers");
        System.out.println("  - Disaster recovery with Azure backup");
        System.out.println("  - Shared resources via GCS");
        System.out.println("  - No vendor lock-in");
        System.out.println();
    }

    public static void realWorldCloudExample() {
        System.out.println("=== Real-World Enterprise Cloud Example ===");

        // Production setup with cloud + traditional sources

        System.out.println("Hybrid cloud + on-premises architecture:");
        System.out.println("\n1. Local Development Overrides");
        System.out.println("   Path: /opt/app/local-overrides");
        System.out.println("\n2. AWS S3 (Production Classes)");
        System.out.println("   Bucket: prod-app-classes");
        System.out.println("   Region: us-east-1");
        System.out.println("   Prefix: v3/classes");
        System.out.println("\n3. Azure Blob (Shared Libraries)");
        System.out.println("   Container: shared-libs");
        System.out.println("   Account: companyshared");
        System.out.println("\n4. Google Cloud Storage (ML Models)");
        System.out.println("   Bucket: ml-model-classes");
        System.out.println("   Project: ml-platform");
        System.out.println("\n5. Internal Maven (Enterprise Artifacts)");
        System.out.println("   Repository: https://maven.company.com/releases");
        System.out.println("\n6. Maven Central (Open Source Dependencies)");
        System.out.println("   Repository: https://repo1.maven.org/maven2/");

        System.out.println("\nConfiguration example:");
        System.out.println("  JClassLoader loader = JClassLoader.builder()");
        System.out.println("      .addLocalSource(\"/opt/app/local-overrides\")");
        System.out.println("      .addS3Source(s3Production)");
        System.out.println("      .addAzureBlobSource(azureShared)");
        System.out.println("      .addGcsSource(gcsModels)");
        System.out.println("      .addMavenRepository(internalMaven)");
        System.out.println("      .addMavenCentral(commonDependencies)");
        System.out.println("      .cache(new FileSystemCache(\"/var/cache/classes\"))");
        System.out.println("      .build();");
        System.out.println();
    }
}
