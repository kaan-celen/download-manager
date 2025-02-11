package com.novoda.downloadmanager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

final class DownloadBatchFactory {

    private static final boolean NOTIFICATION_NOT_SEEN = false;
    private static final int BYTES_DOWNLOADED = 0;
    private static final int TOTAL_BATCH_SIZE_BYTES = 0;
    private static final Optional<DownloadError> DOWNLOAD_ERROR = Optional.absent();

    private DownloadBatchFactory() {
        // non instantiable factory class
    }

    // The download batch is where the majority of the logic sits
    @SuppressWarnings("checkstyle:parameternumber")
    static DownloadBatch newInstance(Batch batch,
                                     FileOperations fileOperations,
                                     DownloadsBatchPersistence downloadsBatchPersistence,
                                     DownloadsFilePersistence downloadsFilePersistence,
                                     FileCallbackThrottle fileCallbackThrottle,
                                     ConnectionChecker connectionChecker,
                                     DownloadBatchRequirementRule downloadBatchRequirementRule,
                                     boolean enableConcurrentFileDownloading) {
        DownloadBatchTitle downloadBatchTitle = DownloadBatchTitleCreator.createFrom(batch);
        StorageRoot storageRoot = batch.storageRoot();
        DownloadBatchId downloadBatchId = batch.downloadBatchId();
        long downloadedDateTimeInMillis = System.currentTimeMillis();

        List<BatchFile> batchFiles = batch.batchFiles();
        List<DownloadFile> downloadFiles = new ArrayList<>(batchFiles.size());

        for (BatchFile batchFile : batchFiles) {
            String networkAddress = batchFile.networkAddress();

            InternalFileSize fileSize = InternalFileSizeCreator.unknownFileSize();
            if (batchFile.fileSize().isPresent()) {
                fileSize = InternalFileSizeCreator.from(batchFile.fileSize().get());
            }

            FilePersistence filePersistence = fileOperations.filePersistenceCreator().create();

            FilePath filePath = FilePathCreator.create(batchFile.path(), batchFile.path());

            DownloadFileId downloadFileId = FallbackDownloadFileIdProvider.downloadFileIdFor(batch.downloadBatchId(), batchFile);
            InternalDownloadFileStatus downloadFileStatus = new LiteDownloadFileStatus(
                    downloadBatchId,
                    downloadFileId,
                    InternalDownloadFileStatus.Status.QUEUED,
                    fileSize,
                    filePath
            );

            FileDownloader fileDownloader = fileOperations.fileDownloaderCreator().create();
            FileSizeRequester fileSizeRequester = fileOperations.fileSizeRequester();

            DownloadFile downloadFile = new DownloadFile(
                    downloadBatchId,
                    downloadFileId,
                    networkAddress,
                    downloadFileStatus,
                    filePath,
                    fileSize,
                    fileDownloader,
                    fileSizeRequester,
                    filePersistence,
                    downloadsFilePersistence
            );
            downloadFiles.add(downloadFile);
        }

        InternalDownloadBatchStatus liteDownloadBatchStatus = new LiteDownloadBatchStatus(
                downloadBatchId,
                downloadBatchTitle,
                storageRoot.path(),
                downloadedDateTimeInMillis,
                BYTES_DOWNLOADED,
                TOTAL_BATCH_SIZE_BYTES,
                DownloadBatchStatus.Status.UNKNOWN,
                NOTIFICATION_NOT_SEEN,
                DOWNLOAD_ERROR
        );

        FilesDownloader filesDownloader = createFilesDownloader(
                enableConcurrentFileDownloading,
                downloadsBatchPersistence,
                connectionChecker,
                liteDownloadBatchStatus
        );

        return new DownloadBatch(
                liteDownloadBatchStatus,
                downloadFiles,
                new ConcurrentHashMap<>(),
                downloadsBatchPersistence,
                fileCallbackThrottle,
                connectionChecker,
                downloadBatchRequirementRule,
                filesDownloader
        );
    }

    private static FilesDownloader createFilesDownloader(boolean enableConcurrentFileDownloading,
                                                         DownloadsBatchPersistence downloadsBatchPersistence,
                                                         ConnectionChecker connectionChecker,
                                                         InternalDownloadBatchStatus liteDownloadBatchStatus) {
        if (enableConcurrentFileDownloading) {
              return new ConcurrentFilesDownloader(liteDownloadBatchStatus, connectionChecker, downloadsBatchPersistence);
        } else {
              return new SequentialFilesDownloader(liteDownloadBatchStatus, connectionChecker, downloadsBatchPersistence);
        }
    }
}
