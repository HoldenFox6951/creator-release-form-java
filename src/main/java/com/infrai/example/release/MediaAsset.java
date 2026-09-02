package com.infrai.example.release;

/** One ingested master file plus the creator and payout terms the release form has to state. */
public final class MediaAsset {

    public final String assetId;
    public final String title;
    public final String creatorName;
    public final String creatorEmail;
    public final String territory;
    public final int revenueSharePercent;
    public final String processingJobId;

    public MediaAsset(String assetId,
                      String title,
                      String creatorName,
                      String creatorEmail,
                      String territory,
                      int revenueSharePercent,
                      String processingJobId) {
        this.assetId = assetId;
        this.title = title;
        this.creatorName = creatorName;
        this.creatorEmail = creatorEmail;
        this.territory = territory;
        this.revenueSharePercent = revenueSharePercent;
        this.processingJobId = processingJobId;
    }
}
