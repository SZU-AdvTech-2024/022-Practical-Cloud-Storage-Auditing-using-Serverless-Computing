package com.fchen_group.mhj.Run;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fchen_group.mhj.Core.ChallengeData;
import com.fchen_group.mhj.Core.IntegrityAuditing;
import com.fchen_group.mhj.Core.ProofData;
import com.fchen_group.mhj.Utils.CloudAPI;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;

import java.util.logging.Logger;

/**
 * This class define the action of the serverless cloud function in the audit process
 */
public class ScfHandle {
    @FunctionName("scf")
    public HttpResponseMessage scf(
            @HttpTrigger(name = "req", methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<String> request,
            final ExecutionContext context) {

        Logger logger = context.getLogger();
        logger.info("ScfHandle - 审计云函数已触发");


        String receiveBody = request.getBody();
        logger.info("请求体接收成功：" + receiveBody);

        JSONObject jsonObject = JSON.parseObject(receiveBody);

        JSONObject challengeDataJson = jsonObject.getJSONObject("challengeData");
        if (challengeDataJson == null) {
            logger.severe("Failed to find 'challengeData' field in request.");
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("Missing challengeData field")
                    .build();
        }

        int[] index = challengeDataJson.getObject("index", int[].class);
        if (index == null) {
            logger.severe("Failed to parse 'index' in challengeData.");
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("Invalid 'index' in challengeData")
                    .build();
        }

        String coefficientsBase64 = challengeDataJson.getString("coefficients");
        byte[] coefficients;
        try {
            coefficients = java.util.Base64.getDecoder().decode(coefficientsBase64);
            logger.info("Decoded coefficients length: " + coefficients.length); // 打印解码后数组的长度
        } catch (IllegalArgumentException e) {
            logger.severe("Failed to decode 'coefficients' in challengeData: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("Invalid 'coefficients' format in challengeData")
                    .build();
        }

        ChallengeData challengeData = new ChallengeData(index, coefficients);
        logger.info("ChallengeData parsed successfully with index and coefficients.");



        String connectionString = "DefaultEndpointsProtocol=https;AccountName=inteaudit;AccountKey=fdj3+Ek1UT5DXfHEmoxVsbI9JhPqwO8aFNa5qR8M09glw4bmpnolW8tnotwKXUqo9Dr4h2w5fqR7+AStzGuybQ==;EndpointSuffix=core.windows.net";
        CloudAPI cloudAPI = new CloudAPI(connectionString, "mhj-container");
        logger.info("Azure Blob Storage client initialized.");

        int DATA_SHARDS;
        int PARITY_SHARDS;
        try {
            DATA_SHARDS = Integer.parseInt(jsonObject.getString("DATA_SHARDS"));
            PARITY_SHARDS = Integer.parseInt(jsonObject.getString("PARITY_SHARDS"));
            logger.info("DATA_SHARDS and PARITY_SHARDS parsed successfully: DATA_SHARDS=" + DATA_SHARDS + ", PARITY_SHARDS=" + PARITY_SHARDS);
        } catch (NumberFormatException e) {
            logger.severe("Failed to parse DATA_SHARDS or PARITY_SHARDS.");
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("Invalid shard parameters")
                    .build();
        }

        byte[][] downloadData = new byte[challengeData.index.length][DATA_SHARDS];
        byte[][] downloadParity = new byte[challengeData.index.length][PARITY_SHARDS];
        try {
            for (int i = 0; i < challengeData.index.length; i++) {
                downloadData[i] = cloudAPI.downloadPartFile("sourceFile.txt", challengeData.index[i] * DATA_SHARDS, DATA_SHARDS);
                downloadParity[i] = cloudAPI.downloadPartFile("parities.txt", challengeData.index[i] * PARITY_SHARDS, PARITY_SHARDS);
            }
            logger.info("Downloaded data blocks and parity blocks from Azure Blob Storage successfully.");
        } catch (Exception e) {
            logger.severe("Failed to download data or parity blocks from Azure Blob Storage: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to download data or parity blocks")
                    .build();
        }

        IntegrityAuditing integrityAuditing = new IntegrityAuditing(DATA_SHARDS, PARITY_SHARDS);
        ProofData proofData;
        try {
            proofData = integrityAuditing.prove(challengeData, downloadData, downloadParity);
            logger.info("ProofData generated successfully.");
        } catch (Exception e) {
            logger.severe("Failed to generate ProofData: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to generate ProofData")
                    .build();
        }

        logger.info("Returning ProofData response.");
        return request.createResponseBuilder(HttpStatus.OK)
                .body(JSON.toJSONString(proofData))
                .header("Content-Type", "application/json")
                .build();
    }
}

