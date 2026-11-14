package com.qst.smartbuildings.vo;

/**
 * 设备合格证上传结果。
 */
public class CertificateUploadResult {

    /** 可访问的附件完整地址 */
    private String fileUrl;

    public CertificateUploadResult() {
    }

    public CertificateUploadResult(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }
}
