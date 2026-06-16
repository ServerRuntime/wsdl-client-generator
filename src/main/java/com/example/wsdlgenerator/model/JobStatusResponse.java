package com.example.wsdlgenerator.model;

public class JobStatusResponse {

    private String jobId;
    private String status;
    private String message;
    private int progress;
    private String downloadUrl;
    private String errorDetail;

    public static JobStatusResponse from(GenerationJob job) {
        JobStatusResponse r = new JobStatusResponse();
        r.jobId = job.getId();
        r.status = job.getStatus().name();
        r.message = job.getMessage();
        r.progress = job.getProgress();
        r.errorDetail = job.getErrorDetail();
        if (job.getStatus() == JobStatus.COMPLETED) {
            r.downloadUrl = "/api/jobs/" + job.getId() + "/download";
        }
        return r;
    }

    public String getJobId()       { return jobId; }
    public String getStatus()      { return status; }
    public String getMessage()     { return message; }
    public int getProgress()       { return progress; }
    public String getDownloadUrl() { return downloadUrl; }
    public String getErrorDetail() { return errorDetail; }
}
