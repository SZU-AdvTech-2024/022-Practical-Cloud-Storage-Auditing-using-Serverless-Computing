package com.fchen_group.mhj.Utils;


import com.azure.storage.blob.*;
import com.azure.storage.blob.models.BlobRange;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.specialized.BlockBlobClient;
import com.azure.core.util.Context;
import java.io.*;
import java.util.logging.Logger;

import java.time.Duration;
import java.util.Properties;

/**
 * Provide a unified interface to access the cloud object storage
 * This class have two method:    uploadFile, downloadPartFile
 * */

public class CloudAPI {
    private static final Logger logger = Logger.getLogger(CloudAPI.class.getName());


    private BlobServiceClient blobServiceClient;
    private BlobContainerClient containerClient;
    private String containerName;

    /**
     * Initialize an Azure Blob client with a configuration file
     * @param cosConfigFilePath the path of the configuration file
     */

    public CloudAPI(String cosConfigFilePath) {
        String connectionString = null;


        //read configuration file
        try (FileInputStream propertiesFIS = new FileInputStream(cosConfigFilePath)) {
            Properties properties = new Properties();
            properties.load(propertiesFIS);

            connectionString = properties.getProperty("connectionString");
            containerName = properties.getProperty("containerName");

        } catch (IOException e) {
            e.printStackTrace();
        }

        //check whether thr variables read successfully
        assert connectionString != null;
        assert containerName != null;

        // Initialize Azure Blob client
        blobServiceClient = new BlobServiceClientBuilder().connectionString(connectionString).buildClient();
        containerClient = blobServiceClient.getBlobContainerClient(containerName);

        // Ensure the container exists
        if (!containerClient.exists()) {
            containerClient.create();
        }
    }

    /**
     * @param connectionString Azure Blob Storage 的连接字符串
     * @param containerName 要使用的 Blob 容器名称
     */
    public CloudAPI(String connectionString, String containerName) {
        BlobServiceClient serviceClient = new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient();
        this.containerClient = serviceClient.getBlobContainerClient(containerName);
        System.out.println("Azure Blob Storage client initialized for container: " + containerName);
    }

    /**
     * Upload data to Azure Blob Storage
     * @param localFilePath the path of the local file to be uploaded
     * @param cloudFileName the file name to be used in Azure Blob Storage
     */
    public void uploadFile(String localFilePath, String cloudFileName) {
        BlobClient blobClient = containerClient.getBlobClient(cloudFileName);
        blobClient.uploadFromFile(localFilePath, true);
        System.out.println("Uploaded " + cloudFileName + " to Azure Blob Storage.");
    }

    /**
     * Download part of a file from Azure Blob Storage
     * @param cloudFileName the file name in Azure Blob Storage
     * @param startPos the starting position in bytes
     * @param length the length of data to download in bytes
     * @return byte array of the downloaded part of the file
     */
    public byte[] downloadPartFile(String cloudFileName, long startPos, int length) {
        BlockBlobClient blobClient = containerClient.getBlobClient(cloudFileName).getBlockBlobClient();
        byte[] fileBlock;

        BlobRange range = new BlobRange(startPos, (long) length);



        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            blobClient.downloadStreamWithResponse(outputStream, range, null, null, false, null, Context.NONE);
            fileBlock = outputStream.toByteArray();
            // System.out.println("Downloaded part of " + cloudFileName + " from position " + startPos + " with length " + length);
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Failed to download part of " + cloudFileName + " from position " + startPos + " with length " + length);
            return null;
        }


        System.out.println("Downloaded part of " + cloudFileName + " from Azure Blob Storage.");
        return fileBlock;
    }

}

