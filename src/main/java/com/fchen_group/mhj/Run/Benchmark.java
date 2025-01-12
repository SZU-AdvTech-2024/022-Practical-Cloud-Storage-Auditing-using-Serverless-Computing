package com.fchen_group.mhj.Run;

import java.io.IOException;
import java.util.Scanner;
/**
 * The Benchmark class,  used to execute the performance experiment
 * */
public class Benchmark {
    public static void main(String args[]) throws IOException {

        String filePath = "C:\\Users\\dell\\Desktop\\testFolder.zip";    // 审计文件对象
        int BLOCK_SHARDS;
        int DATA_SHARDS;
        int AUDIT_TASK_NUM;

        Scanner scan = new Scanner(System.in);
        System.out.println("Please input the BLOCK_SHARDS：");
        BLOCK_SHARDS = scan.nextInt();
        System.out.println("Please input the DATA_SHARDS：");
        DATA_SHARDS = scan.nextInt();
        System.out.println("Please input the AUDIT_TASK_NUM：");
        AUDIT_TASK_NUM = scan.nextInt();

        System.out.println("The ReedSolomon parameters: (BLOCK_SHARDS, DATA_SHARDS)=(" + BLOCK_SHARDS + "," + DATA_SHARDS + "),AUDIT_TASK_NUM="+AUDIT_TASK_NUM);

        for(int i=1; i<=AUDIT_TASK_NUM ; i++){
            System.out.println("*** NO."+i+" Audit Task ***");
            Client.auditTask(filePath, BLOCK_SHARDS, DATA_SHARDS,i);
        }
        System.out.println("Benchmark finished!");
    }


}

