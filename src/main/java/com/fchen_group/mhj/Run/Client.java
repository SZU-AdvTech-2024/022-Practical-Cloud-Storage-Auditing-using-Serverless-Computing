package com.fchen_group.mhj.Run;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fchen_group.mhj.Core.ChallengeData;
import com.fchen_group.mhj.Core.IntegrityAuditing;
import com.fchen_group.mhj.Core.ProofData;
import com.fchen_group.mhj.Utils.CloudAPI;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * This class define the action of the client in the audit process
 */

public class Client {
    /**
     * the action of the client in the audit process
     * @param filePath
     * @param BLOCK_SHARDS PROTOCOL PARAMETER
     * @param DATA_SHARDS PROTOCOL PARAMETER
     * @param taskCount  used in this method for control when writing system result
     * */
    public static void auditTask(String filePath, int BLOCK_SHARDS, int DATA_SHARDS, int taskCount) throws IOException {

        IntegrityAuditing integrityAuditing = new IntegrityAuditing(filePath, BLOCK_SHARDS, DATA_SHARDS);


        String cosConfigFilePath = "src/main/resources/Properties.properties";

        //0-KeyGen , 1-DataProcess , 2-OutSource , 3-Audit , 4-Verify
        long time[] = new long[5];


        //start auditing

        System.out.println("---KeyGen phase start---");
        long start_time_genKey = System.nanoTime();
        integrityAuditing.genKey();
        long end_time_genKey = System.nanoTime();
        time[0] = end_time_genKey - start_time_genKey;
        System.out.println("---KeyGen phase finished---");


        //cal tags , divide source file by block,and then upload tags and file block
        System.out.println("---OutSource phase start---");
        time[1] = integrityAuditing.outSource();// tags,source file were ready ; return data process time
        String uploadSourceFilePath = "C:\\Users\\dell\\Desktop\\tpds\\tpdsUploadFile\\sourceFile.txt";
        String uploadParitiesPath = "C:\\Users\\dell\\Desktop\\tpds\\tpdsUploadFile\\parities.txt";

        //firstly store file in local
        File uploadSourceFile = new File(uploadSourceFilePath);
        uploadSourceFile.createNewFile();
        OutputStream osFile = new FileOutputStream(uploadSourceFile, false);

        for (int i = 0; i < integrityAuditing.originalData.length; i++) {
            osFile.write(integrityAuditing.originalData[i]);
        }
        osFile.close();
        System.out.println("store file in local");
        //cal source file size
        long sourceFileSize = uploadSourceFile.length();

        //then store tags in local
        File uploadParities = new File(uploadParitiesPath);
        uploadParities.createNewFile();
        OutputStream osParities = new FileOutputStream(uploadParities, false);
        for (int i = 0; i < integrityAuditing.parity.length; i++) {
            osParities.write(integrityAuditing.parity[i]);
        }
        osParities.close();
        System.out.println("store tags in local");
        //cal Extra storage cost
        long extraStorageSize = uploadParities.length();
        System.out.println("extraStorageSize is " + extraStorageSize + " Bytes");

        /**
         * upload File and tags to COS
         * 将文件和标签上传到COS
         */

        long start_time_upload = System.nanoTime();

        CloudAPI cloudAPI = new CloudAPI(cosConfigFilePath);

        cloudAPI.uploadFile(uploadSourceFilePath, "sourceFile.txt");
        cloudAPI.uploadFile(uploadParitiesPath, "parities.txt");
        System.out.println("upload File and tags to COS");
        long end_time_upload = System.nanoTime();
        time[2] = end_time_upload - start_time_upload;
        System.out.println("---OutSource phase finished---");


        //trigger ScfHandle
        //get connection
        String reqPath = "https://app-241019174421.azurewebsites.net/api/scf";
        URL url = new URL(reqPath);

        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);

        //write header
        connection.setRequestProperty("Content-Type", "application/json");

        //prepare challengeData
        System.out.println("---Audit phase start---");
        long start_time_audit = System.nanoTime();
        ChallengeData challengeData = integrityAuditing.audit(460);   // 调用 audit 方法生成一个挑战数据对象
        // System.out.println("Encoded coefficients (Base64): " + Base64.getEncoder().encodeToString(challengeData.coefficients));
        long end_time_audit = System.nanoTime();
        time[3] = end_time_audit - start_time_audit;

        //write body , and send the challenge data to SCF and trigger SCF to start audit
        try (PrintWriter writer = new PrintWriter(connection.getOutputStream())) {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("DATA_SHARDS", Integer.toString(DATA_SHARDS));
            requestBody.put("PARITY_SHARDS", Integer.toString((BLOCK_SHARDS - DATA_SHARDS)));
            requestBody.put("challengeData", challengeData);

            writer.write(JSON.toJSONString(requestBody));
            writer.flush();
        }
        System.out.println("---Audit phase finished---");
        System.out.println("Waiting SCF return Proof for verifying");

        //read response
        StringBuilder responseDataStr = new StringBuilder();
        InputStream inputStream = null;
        BufferedReader reader = null;

        try {
            try {
                inputStream = connection.getInputStream();
            } catch (IOException e) {
                inputStream = connection.getErrorStream();
                System.out.println("请求失败，正在从 ErrorStream 获取详细错误信息...");
            }

            if (inputStream != null) {
                reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                while ((line = reader.readLine()) != null) {
                    responseDataStr.append(line);
                }
                System.out.println("服务器响应：" + responseDataStr.toString());
            } else {
                System.out.println("无法获取服务器响应的 InputStream 或 ErrorStream。");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }


        //Extract the serialized proof data in the body
        ProofData proofData = JSON.parseObject(responseDataStr.toString(), ProofData.class);
        System.out.println("Received ProofData from cloud function.");


        //write proofData to file，calculate the communication cost
        String proofDataStoragePath = "C:\\Users\\dell\\Desktop\\tpds\\tpdsProofData\\proofData.txt";
        File proofDataCost = new File(proofDataStoragePath);
        proofDataCost.createNewFile();
        OutputStream osProofData = new FileOutputStream(proofDataCost, false);
        osProofData.write(proofData.parityProof);
        osProofData.write(proofData.dataProof);
        osProofData.close();

        //cal communication cost
        long proofDataSize = proofDataCost.length();
        System.out.println("proofDataSize is " + proofDataSize + " Bytes");   // 255字节


        //execute verify parse
        System.out.println("---Verify phase start---");
        long start_time_verify = System.nanoTime();
        if (integrityAuditing.verify(challengeData, proofData)) {
            System.out.println("---Verify phase finished---");
            System.out.println("The data is intact in the cloud.The auditing process is success!");
        }
        long end_time_verify = System.nanoTime();
        time[4] = end_time_verify - start_time_verify;


        //store the performance in local
        String performanceFilePath = new String("C:\\Users\\dell\\Desktop\\tpds\\performanceResult\\result.txt");
        File performanceFile = new File(performanceFilePath);

        if (performanceFile.exists() && taskCount == 1) {
            performanceFile.delete();
        }
        performanceFile.createNewFile();
        FileWriter resWriter = new FileWriter(performanceFile, true);

        String title = "Audit data size is " + String.valueOf(sourceFileSize) + ". No." + String.valueOf(taskCount) + " audit process. \r\n";
        resWriter.write(title);

        resWriter.write("StorageCost " + String.valueOf(extraStorageSize) + "  CommunicationCost " + String.valueOf(proofDataSize) + "\r\n");

        for (int i = 0; i < 5; i++) {
            resWriter.write("time[" + i + "] = " + String.valueOf(time[i]) + "  ");
        }
        resWriter.write("\r\n");
        resWriter.close();
    }
}
