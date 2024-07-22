# download-manager [![Apache 2.0 Licence](https://img.shields.io/github/license/novoda/download-manager.svg)](https://github.com/novoda/download-manager/blob/release/LICENSE) [![](https://jitci.com/gh/kaan-celen/download-manager/svg)](https://jitci.com/gh/kaan-celen/download-manager)

A library that handles long-running downloads, handling the network interactions and retrying downloads automatically after failures. Clients can request
downloads in batches, receiving a single notification for all of the files allocated to a batch while being able to retrieve the single files after downloads complete.

## Adding to your project

To start using this library, add these lines to the `build.gradle` of your project:

```groovy
repositories {
    maven { url "https://jitpack.io" }
}

dependencies {
    implementation 'com.github.kaan-celen:download-manager:<latest-version>'
}
```

## Simple usage

1. Provide `DelegatingWorkerFactory` for `WorkManager`, not needed if using `DownloadManagerBuilder.withoutNetworkRecovery`
```java
public class MyApplication extends Application implements Configuration.Provider {

    private final  Configuration workManagerConfig = new Configuration.Builder()
            .setWorkerFactory(new DelegatingWorkerFactory())
            .build();

    @Override
    public Configuration getWorkManagerConfiguration() {
        return workManagerConfig;
    }

}
```

2. Create a `DownloadManager`:

```java
DownloadManager downloadManager = DownloadManagerBuilder
        .newInstance(this, handler, R.mipmap.ic_launcher_round)
        .withLogHandle(new DemoLogHandle())
        .withStorageRequirementRules(StorageRequirementRuleFactory.createByteBasedRule(TWO_HUNDRED_MB_IN_BYTES))
        .build();
```

3. Create a `Batch` of files to download:

```java
Batch batch = Batch.with(primaryStorageWithDownloadsSubpackage, DownloadBatchIdCreator.createSanitizedFrom("batch_id_1"), "batch one title")
        .downloadFrom("http://ipv4.download.thinkbroadband.com/5MB.zip").saveTo("foo/bar", "local-filename-5mb.zip").withIdentifier(DownloadFileIdCreator.createFrom("file_id_1")).apply()
        .downloadFrom("http://ipv4.download.thinkbroadband.com/10MB.zip").apply()
        .build();
```   

4. Schedule the batch for download:

```java
downloadManager.download(batch);
```

## Snapshots [![AAR builds](https://cdn-icons-png.flaticon.com/512/25/25231.png)](https://github.com/kaan-celen/download-manager/releases)

## Contributing

We always welcome people to contribute new features or bug fixes, [here is how](https://github.com/novoda/novoda/blob/master/CONTRIBUTING.md).

If you have a problem, check the [Issues Page](https://github.com/novoda/download-manager/issues) first to see if we are already working on it.
