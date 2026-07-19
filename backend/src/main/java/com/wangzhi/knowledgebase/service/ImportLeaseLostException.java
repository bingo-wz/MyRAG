package com.wangzhi.knowledgebase.service;

public class ImportLeaseLostException extends RuntimeException {

    public ImportLeaseLostException(String batchId) {
        super("导入批次租约已失效：" + batchId);
    }

    public ImportLeaseLostException(String batchId, Throwable cause) {
        super("无法确认导入批次租约：" + batchId, cause);
    }
}
